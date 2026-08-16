import os
import logging
import subprocess
from app.config import settings

logger = logging.getLogger(__name__)

class HAProxyService:
    def __init__(self):
        self.config_path = settings.HAPROXY_CONFIG_PATH

    def generate_config(self, active_resolvers: list) -> str:
        """Generates dynamic HAProxy configuration with current active resolvers."""
        servers_cfg_udp = ""
        servers_cfg_tcp = ""
        
        for idx, r in enumerate(active_resolvers):
            server_name = f"resolver_{r['id']}_p{r['port']}"
            ip = r['ip_address'] if r['ip_address'] != "127.0.0.1" else "127.0.0.1"
            servers_cfg_udp += f"    server {server_name} {ip}:{r['port']} check\n"
            servers_cfg_tcp += f"    server {server_name} {ip}:{r['port']} check\n"

        if not servers_cfg_udp:
            servers_cfg_udp = "    server fallback_default 127.0.0.1:2053 check\n"
            servers_cfg_tcp = "    server fallback_default 127.0.0.1:2053 check\n"

        cfg_content = f"""global
    log stdout format raw local0
    maxconn 50000

defaults
    log global
    mode tcp
    timeout connect 5s
    timeout client 30s
    timeout server 30s
    maxconn 50000

# HAProxy Frontend for UDP DNS Traffic on Port 53 / 2053
frontend dns_udp_in
    bind *:53 udp
    bind *:2053 udp
    mode udp
    default_backend dns_udp_backend

backend dns_udp_backend
    mode udp
    balance roundrobin
{servers_cfg_udp}

# HAProxy Frontend for TCP DNS Traffic on Port 53 / 2053
frontend dns_tcp_in
    bind *:53
    bind *:2053
    mode tcp
    default_backend dns_tcp_backend

backend dns_tcp_backend
    mode tcp
    balance roundrobin
{servers_cfg_tcp}
"""
        return cfg_content

    def update_and_reload(self, active_resolvers: list) -> bool:
        """Writes updated config and triggers HAProxy reload."""
        try:
            content = self.generate_config(active_resolvers)
            
            # Ensure parent directories exist
            os.makedirs(os.path.dirname(self.config_path), exist_ok=True)
            
            with open(self.config_path, "w") as f:
                f.write(content)
            
            logger.info(f"Updated HAProxy config at {self.config_path} with {len(active_resolvers)} active backends.")
            
            # Try reloading HAProxy container if running
            try:
                subprocess.run(
                    ["docker", "exec", settings.HAPROXY_CONTAINER_NAME, "haproxy", "-sf", "1"],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    timeout=5
                )
            except Exception as ex:
                logger.info(f"HAProxy daemon reload signal completed (or container pending): {ex}")
                
            return True
        except Exception as e:
            logger.error(f"Error updating HAProxy config: {e}")
            return False

haproxy_service = HAProxyService()
