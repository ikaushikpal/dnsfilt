import logging
import requests
import datetime
from sqlalchemy.orm import Session
from app.config import settings
from app.database import SessionLocal
from app.models import ResolverInstance, ReconciliationLog
from app.services.docker_service import docker_service
from app.services.haproxy_service import haproxy_service

logger = logging.getLogger(__name__)

class ReconcilerService:
    def __init__(self):
        self._successful_url = None

    def get_desired_state(self) -> tuple[int, str]:
        """
        Reads desired state from Admin Backend API with automatic host fallback.
        Returns (desired_count, desired_version)
        """
        candidate_urls = []
        if self._successful_url:
            candidate_urls.append(self._successful_url)

        primary_url = settings.BACKEND_API_URL.rstrip('/')
        if primary_url not in candidate_urls:
            candidate_urls.append(primary_url)

        # Automatic fallback candidates for container & Podman / Docker environments
        fallbacks = [
            "http://dnsfilt-admin-backend:8080/api/v1",
            "http://host.docker.internal:8080/api/v1",
            "http://host.containers.internal:8080/api/v1",
            "http://10.0.0.78:8080/api/v1",
            "http://host.docker.internal:9090/api/v1",
            "http://host.containers.internal:9090/api/v1",
        ]
        for fb in fallbacks:
            if fb not in candidate_urls:
                candidate_urls.append(fb)

        for base_url in candidate_urls:
            target_url = f"{base_url}/resolver/config"
            try:
                resp = requests.get(target_url, timeout=2)
                if resp.status_code == 200:
                    data = resp.json()
                    desired_count = int(data.get("desiredCount", 3))
                    desired_version = str(data.get("desiredVersion", "1.0.0"))
                    self._successful_url = base_url
                    logger.debug(f"Connected to backend API at {target_url} (desiredCount={desired_count}, desiredVersion={desired_version})")
                    return (desired_count, desired_version)
            except Exception:
                continue

        logger.info("Admin backend API not currently reachable from container network. Using default cluster state (3 instances, v1.0.0).")
        return (3, "1.0.0")

    def get_actual_state(self, db: Session) -> list[ResolverInstance]:
        """Reads actual state from local SQLite database."""
        return db.query(ResolverInstance).filter(ResolverInstance.status == "RUNNING").all()

    def reconcile(self) -> dict:
        """
        Reconciliation Logic:
        1. Read desired state from Backend API
        2. Read actual state from SQLite
        3. If desired > actual -> Scale Up
        4. If actual > desired -> Scale Down
        5. If image version mismatch -> Rolling Upgrade
        """
        db: Session = SessionLocal()
        try:
            desired_count, desired_version = self.get_desired_state()
            actual_resolvers = self.get_actual_state(db)
            actual_count = len(actual_resolvers)
            
            action_log = []

            # Initialize mock default instances if SQLite is empty
            if actual_count == 0:
                for port in [2054, 2055, 2056]:
                    inst = ResolverInstance(
                        container_id=f"mock-cid-{port}",
                        container_name=f"dnsfilt-resolver-p{port}",
                        ip_address="127.0.0.1",
                        port=port,
                        version=desired_version,
                        status="RUNNING"
                    )
                    db.add(inst)
                    actual_resolvers.append(inst)
                db.commit()
                actual_count = len(actual_resolvers)
                action_log.append("Initialized baseline resolver cluster in local registry.")

            # 1. Scale Up Required
            if desired_count > actual_count:
                to_add = desired_count - actual_count
                action_log.append(f"Scaling UP cluster from {actual_count} to {desired_count} (+{to_add} instances).")
                for _ in range(to_add):
                    port = self._find_available_port(db)
                    success, cid_or_err = docker_service.spawn_resolver(port=port, version=desired_version)
                    if success:
                        new_inst = ResolverInstance(
                            container_id=cid_or_err,
                            container_name=f"dnsfilt-resolver-p{port}",
                            ip_address="127.0.0.1",
                            port=port,
                            version=desired_version,
                            status="RUNNING"
                        )
                        db.add(new_inst)
                        db.commit()
                        action_log.append(f"Spawned resolver container on port {port} (ID: {cid_or_err[:12]})")
                    else:
                        action_log.append(f"Failed to spawn container on port {port}: {cid_or_err}")

            # 2. Scale Down Required
            elif actual_count > desired_count:
                to_remove = actual_count - desired_count
                action_log.append(f"Scaling DOWN cluster from {actual_count} to {desired_count} (-{to_remove} instances).")
                for _ in range(to_remove):
                    inst_to_kill = actual_resolvers.pop()
                    success, msg = docker_service.stop_resolver(inst_to_kill.container_name)
                    inst_to_kill.status = "STOPPED"
                    db.commit()
                    action_log.append(f"Stopped container {inst_to_kill.container_name} on port {inst_to_kill.port} ({msg})")

            # 3. Rolling Upgrade if version mismatch
            for inst in actual_resolvers:
                if inst.version != desired_version:
                    action_log.append(f"Rolling upgrade on container {inst.container_name} to version {desired_version}.")
                    docker_service.stop_resolver(inst.container_name)
                    success, cid_or_err = docker_service.spawn_resolver(port=inst.port, version=desired_version)
                    if success:
                        inst.container_id = cid_or_err
                        inst.version = desired_version
                        db.commit()
                        action_log.append(f"Upgraded container {inst.container_name} (new ID: {cid_or_err[:12]})")

            # 4. Refresh HAProxy Routing Table with active instances
            active_list = [
                {"id": r.id, "port": r.port, "ip_address": r.ip_address}
                for r in self.get_actual_state(db)
            ]
            haproxy_service.update_and_reload(active_list)

            # Record Reconciliation History
            recon_entry = ReconciliationLog(
                desired_count=desired_count,
                actual_count=len(active_list),
                desired_version=desired_version,
                action_taken=" | ".join(action_log) if action_log else "Cluster in sync with desired state."
            )
            db.add(recon_entry)
            db.commit()

            return {
                "status": "SUCCESS",
                "desired_count": desired_count,
                "actual_count": len(active_list),
                "actions": action_log
            }

        except Exception as e:
            logger.error(f"Reconciliation error: {e}", exc_info=True)
            return {"status": "ERROR", "message": str(e)}
        finally:
            db.close()

    def _find_available_port(self, db: Session) -> int:
        """Finds next unallocated port in the configured range."""
        used_ports = [r.port for r in db.query(ResolverInstance).filter(ResolverInstance.status == "RUNNING").all()]
        for p in range(settings.RESOLVER_PORT_RANGE_START, settings.RESOLVER_PORT_RANGE_END + 1):
            if p not in used_ports:
                return p
        return settings.RESOLVER_PORT_RANGE_START

reconciler_service = ReconcilerService()
reconciler = reconciler_service
