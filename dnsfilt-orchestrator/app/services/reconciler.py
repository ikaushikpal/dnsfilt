import logging
import requests
import datetime
from sqlalchemy.orm import Session
from app.config import settings
from app.database import SessionLocal
from app.models import ResolverInstance, ReconciliationLog
from app.services.docker_service import docker_service
from app.services.nginx_service import nginx_service

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
            "http://host.docker.internal:9090/api/v1",
            "http://host.containers.internal:9090/api/v1",
            "http://host.docker.internal:8080/api/v1",
            "http://host.containers.internal:8080/api/v1",
            "http://10.0.0.222:9090/api/v1",
            "http://10.0.0.78:9090/api/v1",
        ]
        for fb in fallbacks:
            if fb not in candidate_urls:
                candidate_urls.append(fb)

        for base_url in candidate_urls:
            target_url = f"{base_url}/resolver/config"
            try:
                resp = requests.get(target_url, timeout=3)
                if resp.status_code == 200:
                    data = resp.json()
                    desired_count = int(data.get("desiredCount", 3))
                    desired_version = str(data.get("desiredVersion", "1.0.0"))
                    self._successful_url = base_url
                    logger.info(f"Connected to backend API at {target_url} (desiredCount={desired_count}, desiredVersion={desired_version})")
                    return (desired_count, desired_version)
                else:
                    logger.debug(f"Backend probe at {target_url} returned HTTP {resp.status_code}")
            except Exception as e:
                logger.debug(f"Backend probe at {target_url} failed: {e}")
                continue

        logger.info("Admin backend API not currently reachable from container network. Using default cluster state (3 instances, v1.0.0).")
        return (3, "1.0.0")

    def _sync_actual_state_with_docker(self, db: Session):
        """
        Synchronizes SQLite database records with real Docker/Podman container states.
        Marks missing/dead containers as STOPPED so ports can be cleanly reused without constraint errors.
        """
        try:
            if not docker_service.client:
                return

            # Fetch all live containers from Docker/Podman
            live_containers = {}
            for c in docker_service.client.containers.list(all=True):
                if c.name.startswith("dnsfilt-resolver-p"):
                    live_containers[c.name] = c

            db_resolvers = db.query(ResolverInstance).all()
            for r in db_resolvers:
                c = live_containers.get(r.container_name)
                if not c or c.status != "running":
                    if r.status == "RUNNING":
                        logger.info(f"Marking dead/missing container '{r.container_name}' as STOPPED in database.")
                        r.status = "STOPPED"
                else:
                    if r.status != "RUNNING":
                        r.status = "RUNNING"
            db.commit()
        except Exception as e:
            logger.debug(f"Docker state sync notice: {e}")

    def _upsert_resolver_instance(self, db: Session, cid: str, cname: str, ip: str, port: int, version: str) -> ResolverInstance:
        """
        Inserts or updates a ResolverInstance record cleanly without violating unique constraints.
        """
        inst = db.query(ResolverInstance).filter(
            (ResolverInstance.container_name == cname) | (ResolverInstance.port == port)
        ).first()

        now = datetime.datetime.utcnow()
        if inst:
            inst.container_id = cid
            inst.container_name = cname
            inst.ip_address = ip
            inst.port = port
            inst.version = version
            inst.status = "RUNNING"
            inst.updated_at = now
        else:
            inst = ResolverInstance(
                container_id=cid,
                container_name=cname,
                ip_address=ip,
                port=port,
                version=version,
                status="RUNNING",
                created_at=now,
                updated_at=now
            )
            db.add(inst)
        
        db.commit()
        return inst

    def get_actual_state(self, db: Session) -> list[ResolverInstance]:
        """Reads actual state from local SQLite database."""
        return db.query(ResolverInstance).filter(ResolverInstance.status == "RUNNING").all()

    def reconcile(self) -> dict:
        """
        Reconciliation Logic:
        1. Sync SQLite state with live Docker containers
        2. Read desired state from Backend API
        3. If actual < desired -> Spawn real Docker resolver containers
        4. If actual > desired -> Stop and remove excess containers
        5. If image version mismatch -> Perform zero-downtime rolling upgrade
        6. Refresh NGINX Stream configuration and signal reload
        """
        db: Session = SessionLocal()
        try:
            # 0. Sync SQLite with live Docker state first to clear stale records
            self._sync_actual_state_with_docker(db)

            desired_count, desired_version = self.get_desired_state()
            actual_resolvers = self.get_actual_state(db)
            actual_count = len(actual_resolvers)
            
            action_log = []

            # 1. Scale Up: Spawn real containers if actual < desired
            if actual_count < desired_count:
                to_add = desired_count - actual_count
                action_log.append(f"Scaling UP cluster from {actual_count} to {desired_count} (+{to_add} instances).")
                for _ in range(to_add):
                    port = self._find_available_port(db)
                    cid, cname, ip = docker_service.create_resolver_container(port=port, version=desired_version)
                    
                    new_inst = self._upsert_resolver_instance(
                        db=db,
                        cid=cid,
                        cname=cname,
                        ip=ip,
                        port=port,
                        version=desired_version
                    )
                    actual_resolvers.append(new_inst)
                    action_log.append(f"Spawned resolver container {cname} on port {port} (ID: {cid[:12]})")

            # 2. Scale Down: Stop and remove excess containers if actual > desired
            elif actual_count > desired_count:
                to_remove = actual_count - desired_count
                action_log.append(f"Scaling DOWN cluster from {actual_count} to {desired_count} (-{to_remove} instances).")
                for _ in range(to_remove):
                    inst_to_kill = actual_resolvers.pop()
                    docker_service.remove_resolver_container(inst_to_kill.container_id, inst_to_kill.container_name)
                    inst_to_kill.status = "STOPPED"
                    db.commit()
                    action_log.append(f"Stopped & removed container {inst_to_kill.container_name} on port {inst_to_kill.port}")

            # 3. Rolling Upgrade if version mismatch
            for inst in actual_resolvers:
                if inst.version != desired_version:
                    action_log.append(f"Rolling upgrade on container {inst.container_name} to version {desired_version}.")
                    docker_service.remove_resolver_container(inst.container_id, inst.container_name)
                    cid, cname, ip = docker_service.create_resolver_container(port=inst.port, version=desired_version)
                    inst.container_id = cid
                    inst.version = desired_version
                    inst.ip_address = ip
                    db.commit()
                    action_log.append(f"Upgraded container {inst.container_name} (new ID: {cid[:12]})")

            # 4. Refresh NGINX Stream Routing Table with active instances
            active_list = [
                {"id": r.id, "port": r.port, "ip_address": r.ip_address}
                for r in self.get_actual_state(db)
            ]
            nginx_service.update_and_reload(active_list)

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
        used_ports = {r.port for r in db.query(ResolverInstance).filter(ResolverInstance.status == "RUNNING").all()}
        for p in range(settings.RESOLVER_PORT_RANGE_START, settings.RESOLVER_PORT_RANGE_END + 1):
            if p not in used_ports:
                return p
        return settings.RESOLVER_PORT_RANGE_START

reconciler_service = ReconcilerService()
reconciler = reconciler_service

