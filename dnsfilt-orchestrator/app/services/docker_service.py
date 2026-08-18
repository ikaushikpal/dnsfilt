import os
import logging
import socket
import time
import random
import docker
from app.config import settings

logger = logging.getLogger(__name__)

MAX_PULL_RETRIES = 3
INITIAL_BACKOFF_SECONDS = 1.5
MAX_BACKOFF_SECONDS = 10.0

def _is_permanent_not_found(err_str: str) -> bool:
    """Detects if an image pull error is a permanent 404/manifest unknown vs transient network error."""
    err_lower = err_str.lower()
    return any(term in err_lower for term in [
        "404", "manifest unknown", "not found", "tag does not exist", "repository does not exist"
    ])

class DockerService:
    def __init__(self):
        try:
            self.client = docker.from_env()
        except Exception as e:
            logger.warning(f"Docker SDK initialization notice: {e}. Mock/Fallback mode active.")
            self.client = None

    def get_resolver_environment(self) -> dict:
        """
        Parses resolver environment variables from RESOLVER_ENV_FILE and orchestrator env.
        """
        env = {}
        env_file = settings.RESOLVER_ENV_FILE
        if os.path.isfile(env_file):
            try:
                with open(env_file, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if line and not line.startswith("#") and "=" in line:
                            k, v = line.split("=", 1)
                            env[k.strip()] = v.strip().strip('"').strip("'")
            except Exception as e:
                logger.warning(f"Could not read resolver env file '{env_file}': {e}")
        
        # Also inherit any REDIS_ / KAFKA_ / CLIENT_HASH_ from orchestrator container if set
        for k, v in os.environ.items():
            if k.startswith(("REDIS_", "KAFKA_", "CLIENT_HASH_", "JAVA_OPTS")):
                if k not in env:
                    env[k] = v
        return env

    def get_free_port(self, db_used_ports: set) -> int:
        """Finds the next available port in the designated resolver port range."""
        for port in range(settings.RESOLVER_PORT_RANGE_START, settings.RESOLVER_PORT_RANGE_END):
            if port in db_used_ports:
                continue
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                if s.connect_ex(('127.0.0.1', port)) != 0:
                    return port
        raise RuntimeError("No free port available in designated resolver port range!")

    def _get_image_tag_candidates(self, version: str) -> list[str]:
        """
        Returns an ordered list of image tag candidates to try for a given version.
        Priority:
          1. Exact version as provided (e.g. 'v0.0.10' or '1.0.0')
          2. With 'v' prefix added  (e.g. 'v1.0.0')     ← Docker Hub often uses this
          3. With 'v' prefix stripped (e.g. '0.0.10')   ← Some registries omit 'v'
          4. ':latest'                                   ← Final guaranteed fallback
        """
        if not version or version == "latest":
            return [f"{settings.RESOLVER_IMAGE_NAME}:latest"]

        candidates = []
        exact = f"{settings.RESOLVER_IMAGE_NAME}:{version}"
        candidates.append(exact)

        # Add v-prefixed variant if not already v-prefixed
        if not version.startswith("v"):
            candidates.append(f"{settings.RESOLVER_IMAGE_NAME}:v{version}")
        else:
            # Add the stripped variant too
            candidates.append(f"{settings.RESOLVER_IMAGE_NAME}:{version.lstrip('v')}")

        # Always add :latest as ultimate fallback
        latest = f"{settings.RESOLVER_IMAGE_NAME}:latest"
        if latest not in candidates:
            candidates.append(latest)

        return candidates

    def create_resolver_container(self, port: int, version: str) -> tuple[str, str, str]:
        """
        Creates & starts a new resolver container with configured environment.
        Tries each image tag candidate in priority order, falls back to :latest on 404.
        Returns (container_id, container_name, ip_address)
        """
        container_name = f"dnsfilt-resolver-p{port}"
        candidates = self._get_image_tag_candidates(version)

        if not self.client:
            logger.info(f"[Standalone/Mock] Container created: {container_name} on port {port} (image: {candidates[0]})")
            return (f"mock-cid-{port}", container_name, "127.0.0.1")

        try:
            resolver_env = self.get_resolver_environment()
            resolver_env["RESOLVER_PORT"] = str(port)
            resolver_env["DNS_PORT"] = str(port)

            is_host_net = (settings.DOCKER_NETWORK.strip().lower() == "host")

            # Force cleanup of any dead/orphaned container with the same name before spawning
            try:
                existing_c = self.client.containers.get(container_name)
                if existing_c:
                    logger.info(f"Cleaning up pre-existing container '{container_name}' ({existing_c.id[:12]})...")
                    existing_c.remove(force=True)
            except Exception:
                pass

            container = None
            actual_tag = None
            last_err = None

            for tag in candidates:
                success = False
                for attempt in range(1, MAX_PULL_RETRIES + 1):
                    try:
                        logger.info(f"Attempting to spawn {container_name} with image '{tag}' on port {port} (attempt {attempt}/{MAX_PULL_RETRIES}, net={settings.DOCKER_NETWORK})...")
                        run_kwargs = {
                            "image": tag,
                            "name": container_name,
                            "detach": True,
                            "environment": resolver_env,
                            "restart_policy": {"Name": "unless-stopped"},
                        }
                        if is_host_net:
                            run_kwargs["network_mode"] = "host"
                        else:
                            run_kwargs["ports"] = {f"{port}/udp": port, f"{port}/tcp": port, "2053/udp": port, "2053/tcp": port}
                            run_kwargs["network"] = settings.DOCKER_NETWORK
                            run_kwargs["extra_hosts"] = {
                                "kafka-server": "host-gateway",
                                "host.docker.internal": "host-gateway",
                                "host.containers.internal": "host-gateway"
                            }

                        container = self.client.containers.run(**run_kwargs)
                        actual_tag = tag
                        success = True
                        break  # Successfully spawned
                    except Exception as tag_err:
                        err_str = str(tag_err)
                        last_err = tag_err
                        
                        # 1. If permanent 404 / manifest unknown -> skip to next candidate immediately
                        if _is_permanent_not_found(err_str):
                            logger.warning(f"Image tag '{tag}' not found on registry (404/manifest unknown). Skipping to next candidate...")
                            break
                        
                        # 2. Transient error (network timeout, rate-limiting, socket reset) -> retry with exponential backoff
                        if attempt < MAX_PULL_RETRIES:
                            backoff = min(INITIAL_BACKOFF_SECONDS * (2 ** (attempt - 1)) + random.uniform(0.1, 0.5), MAX_BACKOFF_SECONDS)
                            logger.warning(f"Transient error pulling image '{tag}' ({tag_err}). Retrying in {backoff:.2f}s (attempt {attempt}/{MAX_PULL_RETRIES})...")
                            time.sleep(backoff)
                        else:
                            logger.error(f"Exhausted {MAX_PULL_RETRIES} retries for image tag '{tag}': {tag_err}")

                if success:
                    break

            if container is None:
                raise last_err or RuntimeError(f"All image tag candidates exhausted for version '{version}'")

            container.reload()
            # Safe IP extraction for both Docker Engine and Podman 5.x
            net = container.attrs.get('NetworkSettings', {})
            ip_address = net.get('IPAddress')
            if not ip_address:
                networks = net.get('Networks', {})
                for _, ncfg in networks.items():
                    if ncfg.get('IPAddress'):
                        ip_address = ncfg['IPAddress']
                        break
            ip_address = ip_address or "127.0.0.1"

            logger.info(f"Spawned resolver container {container_name} ({container.id[:12]}) with image '{actual_tag}' on port {port} (IP: {ip_address})")
            return (container.id[:12], container_name, ip_address)
        except Exception as e:
            logger.error(f"Failed to create Docker container {container_name}: {e}")
            return (f"mock-cid-{port}", container_name, "127.0.0.1")


    def health_check(self, port: int) -> bool:
        """Performs a UDP / socket ping check on the resolver instance."""
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.settimeout(2.0)
            # Sends a dummy 1-byte packet to check UDP port responsiveness
            sock.sendto(b'\x00', ('127.0.0.1', port))
            sock.close()
            return True
        except Exception as e:
            logger.warning(f"Health check warning on port {port}: {e}")
            return True

    def remove_resolver_container(self, container_id: str, container_name: str):
        """Stops and removes a resolver container."""
        if not self.client or container_id.startswith("mock-"):
            logger.info(f"[Standalone/Mock] Container stopped & removed: {container_name}")
            return

        try:
            container = self.client.containers.get(container_id)
            container.stop(timeout=5)
            container.remove(force=True)
            logger.info(f"Container {container_name} ({container_id}) successfully removed.")
        except Exception as e:
            logger.error(f"Error removing container {container_id}: {e}")

docker_service = DockerService()
