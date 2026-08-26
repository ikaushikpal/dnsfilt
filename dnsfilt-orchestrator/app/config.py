import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    APP_NAME: str = "dnsfilt-orchestrator"
    VERSION: str = "1.0.0"
    DEBUG: bool = True
    
    # Log settings
    LOG_DIR: str = os.getenv("LOG_DIR", "./logs")
    LOG_FILE_NAME: str = "dnsfilt-orchestrator.log"
    LOG_RETENTION_DAYS: int = 14 # 2 weeks log retention
    
    # Backend URL for Desired State retrieval
    BACKEND_API_URL: str = os.getenv("BACKEND_API_URL", "http://127.0.0.1:8080/api/v1")
    
    # SQLite local DB path for actual state tracking (saved in ./data for volume persistence)
    SQLITE_DB_PATH: str = os.getenv("SQLITE_DB_PATH", "sqlite:///./data/resolvers.db")
    
    # NGINX Stream Gateway & Load Balancer Configuration
    NGINX_STREAM_CONFIG_PATH: str = os.getenv("NGINX_STREAM_CONFIG_PATH", "/etc/nginx/conf.d/dns_stream.conf")
    NGINX_CONTAINER_NAME: str = os.getenv("NGINX_CONTAINER_NAME", "nginx")
    NGINX_BACKEND_HOST: str = os.getenv("NGINX_BACKEND_HOST", "127.0.0.1")

    # Static upstream IP:port pairs for dns_udp_cluster / dns_tcp_cluster.
    # When set, the orchestrator writes these verbatim into the Nginx upstream blocks
    # instead of using dynamically derived container addresses.
    # Format (env var): comma-separated "ip:port" pairs, e.g. "10.88.16.163:2054,10.88.16.164:2055"
    # Default: the three fixed Podman container IPs used in production.
    @property
    def NGINX_STATIC_UPSTREAM_SERVERS(self) -> list[tuple[str, int]]:
        raw = os.getenv(
            "NGINX_STATIC_UPSTREAM_SERVERS",
            "10.88.16.163:2054,10.88.16.164:2055,10.88.16.165:2056"
        )
        result = []
        for entry in raw.split(","):
            entry = entry.strip()
            if not entry:
                continue
            try:
                ip, port_str = entry.rsplit(":", 1)
                result.append((ip.strip(), int(port_str.strip())))
            except (ValueError, AttributeError):
                import logging
                logging.getLogger(__name__).warning(f"Ignoring malformed NGINX_STATIC_UPSTREAM_SERVERS entry: '{entry}'")
        return result
    
    # Docker settings
    RESOLVER_IMAGE_NAME: str = os.getenv("RESOLVER_IMAGE_NAME", "ikaushikpal/dnsfilt-resolver")
    RESOLVER_PORT_RANGE_START: int = int(os.getenv("RESOLVER_PORT_RANGE_START", 2054))
    RESOLVER_PORT_RANGE_END: int = int(os.getenv("RESOLVER_PORT_RANGE_END", 2090))
    DOCKER_NETWORK: str = os.getenv("DOCKER_NETWORK", "host")
    
    # Path to environment file to inject into spawned resolver instances
    RESOLVER_ENV_FILE: str = os.getenv("RESOLVER_ENV_FILE", "/app/resolver.env")
    
    # Reconciliation Interval (Seconds)
    RECONCILE_INTERVAL_SECONDS: int = int(os.getenv("RECONCILE_INTERVAL_SECONDS", 60))

    class Config:
        env_file = ".env"

settings = Settings()
