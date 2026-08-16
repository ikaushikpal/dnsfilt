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
    
    # Backend URL for MySQL / Desired State retrieval
    BACKEND_API_URL: str = os.getenv("BACKEND_API_URL", "http://localhost:8080/api/v1")
    
    # SQLite local DB path for actual state tracking (saved in ./data for volume persistence)
    SQLITE_DB_PATH: str = os.getenv("SQLITE_DB_PATH", "sqlite:///./data/resolvers.db")
    
    # HAProxy config path
    HAPROXY_CONFIG_PATH: str = os.getenv("HAPROXY_CONFIG_PATH", "/etc/haproxy/haproxy.cfg")
    HAPROXY_CONTAINER_NAME: str = os.getenv("HAPROXY_CONTAINER_NAME", "haproxy")
    
    # Docker settings
    RESOLVER_IMAGE_NAME: str = os.getenv("RESOLVER_IMAGE_NAME", "dnsfilt-resolver")
    RESOLVER_PORT_RANGE_START: int = 2054
    RESOLVER_PORT_RANGE_END: int = 2090
    DOCKER_NETWORK: str = os.getenv("DOCKER_NETWORK", "bridge")
    
    # Reconciliation Interval (Seconds)
    RECONCILE_INTERVAL_SECONDS: int = 60

    class Config:
        env_file = ".env"

settings = Settings()
