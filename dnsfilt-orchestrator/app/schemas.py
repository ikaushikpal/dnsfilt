from typing import List, Optional
from datetime import datetime
from pydantic import BaseModel

class ResolverInstanceSchema(BaseModel):
    id: int
    container_id: str
    container_name: str
    ip_address: str
    port: int
    version: str
    status: str
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True

class HealthResponse(BaseModel):
    status: str
    service: str
    uptime_seconds: float
    active_resolvers: int
    desired_count: Optional[int] = 3
    desired_version: Optional[str] = "1.0.0"

class ReconcileResponse(BaseModel):
    status: str
    desired_count: int
    actual_count: int
    desired_version: str
    action_taken: str
    timestamp: datetime

class UpgradeRequest(BaseModel):
    target_version: str
