import os
import logging
from logging.handlers import TimedRotatingFileHandler
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from apscheduler.schedulers.background import BackgroundScheduler

from app.config import settings
from app.database import engine, Base
from app.routers import internal
from app.services.reconciler import reconciler_service

# Ensure logs directory exists
os.makedirs(settings.LOG_DIR, exist_ok=True)
log_file_path = os.path.join(settings.LOG_DIR, settings.LOG_FILE_NAME)

# Configure logging with TimedRotatingFileHandler (rotates daily, retains 14 days / 2 weeks of logs)
log_formatter = logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s")

# 1. Console Stream Handler
console_handler = logging.StreamHandler()
console_handler.setFormatter(log_formatter)

# 2. Daily Timed Rotating File Handler (retains last 14 days / 2 weeks of logs)
file_handler = TimedRotatingFileHandler(
    filename=log_file_path,
    when="D",
    interval=1,
    backupCount=settings.LOG_RETENTION_DAYS,
    encoding="utf-8"
)
file_handler.setFormatter(log_formatter)

# Attach handlers to root logger
root_logger = logging.getLogger()
root_logger.setLevel(logging.INFO)
root_logger.addHandler(console_handler)
root_logger.addHandler(file_handler)

logger = logging.getLogger("dnsfilt-orchestrator")
logger.info(f"Log handler initialized. Logs will be saved to '{log_file_path}' with {settings.LOG_RETENTION_DAYS} days (2 weeks) retention.")

# Create SQLite database tables if they do not exist
try:
    Base.metadata.create_all(bind=engine)
except Exception as e:
    logger.warning(f"Initial DB create_all encountered '{e}'. Retrying with /tmp fallback...")
    try:
        from sqlalchemy import create_engine
        fallback_engine = create_engine("sqlite:////tmp/resolvers.db", connect_args={"check_same_thread": False})
        Base.metadata.create_all(bind=fallback_engine)
    except Exception as fe:
        logger.error(f"Fallback DB creation error: {fe}")

app = FastAPI(
    title="DNSFilt Orchestrator Controller",
    description="Custom lightweight Python orchestrator service for dynamic scaling, zero-downtime rolling upgrades, and HAProxy load distribution.",
    version=settings.VERSION
)

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include internal API routers
app.include_router(internal.router)

# Scheduler for 60-second automatic reconciliation loop
scheduler = BackgroundScheduler()

def scheduled_reconciliation():
    logger.info("Executing 60-second periodic background reconciliation loop...")
    reconciler_service.reconcile()

@app.on_event("startup")
def startup_event():
    logger.info("Starting dnsfilt-orchestrator FastAPI application...")
    
    # Run initial reconciliation
    try:
        reconciler_service.reconcile()
    except Exception as e:
        logger.warning(f"Initial startup reconciliation notice: {e}")
        
    # Start 60-second background scheduler
    scheduler.add_job(
        scheduled_reconciliation,
        'interval',
        seconds=settings.RECONCILE_INTERVAL_SECONDS,
        id='reconcile_job',
        replace_existing=True
    )
    scheduler.start()
    logger.info(f"Reconciliation loop scheduled to run every {settings.RECONCILE_INTERVAL_SECONDS} seconds.")

@app.on_event("shutdown")
def shutdown_event():
    logger.info("Shutting down dnsfilt-orchestrator service...")
    scheduler.shutdown()

@app.get("/")
def root():
    return {
        "service": settings.APP_NAME,
        "version": settings.VERSION,
        "docs": "/docs",
        "health": "/internal/health",
        "log_file": log_file_path,
        "retention_days": settings.LOG_RETENTION_DAYS
    }
