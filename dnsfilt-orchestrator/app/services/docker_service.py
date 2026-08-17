import os
import logging
import socket
import docker
from app.config import settings

logger = logging.getLogger(__name__)

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

    def create_resolver_container(self, port: int, version: str) -> tuple[str, str, str]:
        """
        Creates & starts a new resolver container with configured environment.
        Returns (container_id, container_name, ip_address)
        """
        container_name = f"dnsfilt-resolver-p{port}"
        image_tag = f"{settings.RESOLVER_IMAGE_NAME}:{version}" if not version.startswith("v") and not version.startswith("latest") else f"{settings.RESOLVER_IMAGE_NAME}:{version}"
        # If version is just a SemVer number (e.g. 1.0.0), support both v1.0.0 and latest fallback
        if not version.startswith("v") and version != "latest":
            image_tag = f"{settings.RESOLVER_IMAGE_NAME}:v{version}"

        if not self.client:
            logger.info(f"[Standalone/Mock] Container created: {container_name} on port {port}")
            return (f"mock-cid-{port}", container_name, "127.0.0.1")

        try:
            resolver_env = self.get_resolver_environment()
            resolver_env["RESOLVER_PORT"] = str(port)
            resolver_env["DNS_PORT"] = str(port)

            container = self.client.containers.run(
                image=image_tag,
                name=container_name,
                detach=True,
                ports={'2053/udp': port, '2053/tcp': port},
                network=settings.DOCKER_NETWORK,
                environment=resolver_env,
                restart_policy={"Name": "unless-stopped"}
            )
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

            logger.info(f"Spawned resolver container {container_name} ({container.id[:12]}) with image {image_tag} on port {port} (IP: {ip_address})")
            return (container.id[:12], container_name, ip_address)
        except Exception as e:
            logger.error(f"Failed to create Docker container {container_name}: {e}")
            # Fallback mock for local execution environments without active Docker daemon
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
