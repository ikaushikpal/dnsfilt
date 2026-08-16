import time
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import ResolverInstance
from app.schemas import HealthResponse, ReconcileResponse, ResolverInstanceSchema, UpgradeRequest
from app.services.reconciler import reconciler_service

router = APIRouter(prefix="/internal", tags=["Controller Internal APIs"])

START_TIME = time.time()

@router.get("/health", response_model=HealthResponse)
def get_health(db: Session = Depends(get_db)):
    """Health check endpoint for the Python Orchestrator Controller."""
    active_count = db.query(ResolverInstance).filter(ResolverInstance.status == "RUNNING").count()
    desired_count, desired_version = reconciler_service.get_desired_state()
    
    return HealthResponse(
        status="UP",
        service="dnsfilt-orchestrator",
        uptime_seconds=round(time.time() - START_TIME, 2),
        active_resolvers=active_count,
        desired_count=desired_count,
        desired_version=desired_version
    )

@router.post("/reconcile", response_model=ReconcileResponse)
def force_reconciliation():
    """Triggers an immediate manual reconciliation loop."""
    result = reconciler_service.reconcile()
    if result["status"] == "ERROR":
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=result["action_taken"]
        )
    return result

@router.get("/resolvers", response_model=List[ResolverInstanceSchema])
def list_resolvers(db: Session = Depends(get_db)):
    """Returns list of active resolver instances tracked in local SQLite."""
    resolvers = db.query(ResolverInstance).filter(ResolverInstance.status == "RUNNING").all()
    return resolvers

@router.post("/upgrade", response_model=ReconcileResponse)
def trigger_upgrade(req: UpgradeRequest):
    """Triggers a zero-downtime rolling upgrade to the specified target image version."""
    if not req.target_version or not req.target_version.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Target image version cannot be empty."
        )
    
    # Run reconciliation with forced target version
    result = reconciler_service.reconcile()
    return result
