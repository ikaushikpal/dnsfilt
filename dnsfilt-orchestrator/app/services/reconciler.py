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
    def get_desired_state(self) -> tuple[int, str]:
        """
        Reads desired state from MySQL / Admin Backend API.
        Returns (desired_count, desired_version)
        """
        url = f"{settings.BACKEND_API_URL}/resolver/config"
        try:
            resp = requests.get(url, timeout=3)
            if resp.status_code == 200:
                data = resp.json()
                desired_count = int(data.get("desiredCount", 3))
                desired_version = str(data.get("desiredVersion", "1.0.0"))
                return (desired_count, desired_version)
        except Exception as e:
            logger.warning(f"Could not fetch desired state from backend API ({url}): {e}. Using defaults.")

        # Default fallback desired state
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
                db.commit()
                actual_resolvers = self.get_actual_state(db)
                actual_count = len(actual_resolvers)

            # 1. Scale Up Case (desired > actual)
            if desired_count > actual_count:
                to_add = desired_count - actual_count
                for _ in range(to_add):
                    db_used_ports = {r.port for r in actual_resolvers}
                    free_port = docker_service.get_free_port(db_used_ports)
                    
                    # 1. Find free port, 2. Create container
                    cid, name, ip = docker_service.create_resolver_container(free_port, desired_version)
                    
                    # 3. Health check
                    is_healthy = docker_service.health_check(free_port)
                    
                    # 4. Update SQLite
                    new_inst = ResolverInstance(
                        container_id=cid,
                        container_name=name,
                        ip_address=ip,
                        port=free_port,
                        version=desired_version,
                        status="RUNNING" if is_healthy else "UNHEALTHY"
                    )
                    db.add(new_inst)
                    db.commit()
                    actual_resolvers.append(new_inst)
                    action_log.append(f"Scaled up: added {name} on port {free_port}")

                # 5. Register in HAProxy & 6. Reload HAProxy
                active_dicts = [{"id": r.id, "ip_address": r.ip_address, "port": r.port} for r in actual_resolvers]
                haproxy_service.update_and_reload(active_dicts)

            # 2. Scale Down Case (actual > desired)
            elif actual_count > desired_count:
                to_remove_count = actual_count - desired_count
                to_remove_list = actual_resolvers[-to_remove_count:]
                
                remaining_resolvers = [r for r in actual_resolvers if r not in to_remove_list]
                
                # 1. Remove from HAProxy & 2. Reload HAProxy
                active_dicts = [{"id": r.id, "ip_address": r.ip_address, "port": r.port} for r in remaining_resolvers]
                haproxy_service.update_and_reload(active_dicts)
                
                for r in to_remove_list:
                    # 3. Stop container
                    docker_service.remove_resolver_container(r.container_id, r.container_name)
                    # 4. Update SQLite
                    r.status = "REMOVED"
                    action_log.append(f"Scaled down: removed {r.container_name}")
                
                db.commit()
                actual_resolvers = remaining_resolvers

            # 3. Rolling Upgrade Case (image version mismatch)
            version_mismatch = any(r.version != desired_version for r in actual_resolvers)
            if version_mismatch:
                action_log.append(f"Version mismatch detected. Performing rolling upgrade to {desired_version}.")
                for r in actual_resolvers:
                    if r.version != desired_version:
                        # Perform zero-downtime container replacement
                        db_used_ports = {res.port for res in self.get_actual_state(db)}
                        new_port = docker_service.get_free_port(db_used_ports)
                        new_cid, new_name, new_ip = docker_service.create_resolver_container(new_port, desired_version)
                        
                        docker_service.health_check(new_port)
                        
                        new_inst = ResolverInstance(
                            container_id=new_cid,
                            container_name=new_name,
                            ip_address=new_ip,
                            port=new_port,
                            version=desired_version,
                            status="RUNNING"
                        )
                        db.add(new_inst)
                        db.commit()
                        
                        # Unregister old and register new in HAProxy
                        current_active = self.get_actual_state(db)
                        active_dicts = [{"id": x.id, "ip_address": x.ip_address, "port": x.port} for x in current_active]
                        haproxy_service.update_and_reload(active_dicts)
                        
                        # Stop old container
                        docker_service.remove_resolver_container(r.container_id, r.container_name)
                        r.status = "REMOVED"
                        db.commit()
                        action_log.append(f"Upgraded node {r.container_name} -> {new_name} ({desired_version})")

            # Always sync HAProxy
            active_dicts = [{"id": r.id, "ip_address": r.ip_address, "port": r.port} for r in self.get_actual_state(db)]
            haproxy_service.update_and_reload(active_dicts)

            action_summary = "; ".join(action_log) if action_log else "No action required. State in sync."

            # Save Reconciliation Log in SQLite
            log_entry = ReconciliationLog(
                desired_count=desired_count,
                actual_count=len(self.get_actual_state(db)),
                desired_version=desired_version,
                action_taken=action_summary,
                status="SUCCESS"
            )
            db.add(log_entry)
            db.commit()

            return {
                "status": "SUCCESS",
                "desired_count": desired_count,
                "actual_count": len(self.get_actual_state(db)),
                "desired_version": desired_version,
                "action_taken": action_summary,
                "timestamp": datetime.datetime.utcnow()
            }
        except Exception as e:
            logger.error(f"Reconciliation error: {e}")
            return {
                "status": "ERROR",
                "desired_count": 0,
                "actual_count": 0,
                "desired_version": "unknown",
                "action_taken": f"Error during reconciliation: {str(e)}",
                "timestamp": datetime.datetime.utcnow()
            }
        finally:
            db.close()

reconciler_service = ReconcilerService()
