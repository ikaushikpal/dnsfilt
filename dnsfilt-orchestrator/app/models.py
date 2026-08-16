import datetime
from sqlalchemy import Column, Integer, String, DateTime, Boolean
from app.database import Base

class ResolverInstance(Base):
    __tablename__ = "resolver_instances"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    container_id = Column(String(64), unique=True, index=True, nullable=False)
    container_name = Column(String(128), unique=True, nullable=False)
    ip_address = Column(String(45), nullable=False, default="127.0.0.1")
    port = Column(Integer, nullable=False)
    version = Column(String(32), nullable=False, default="1.0.0")
    status = Column(String(32), nullable=False, default="RUNNING") # RUNNING, HEALTHY, UNHEALTHY, REMOVED
    created_at = Column(DateTime, default=datetime.datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

class ReconciliationLog(Base):
    __tablename__ = "reconciliation_logs"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    desired_count = Column(Integer, nullable=False)
    actual_count = Column(Integer, nullable=False)
    desired_version = Column(String(32), nullable=False)
    action_taken = Column(String(255), nullable=False)
    status = Column(String(32), nullable=False, default="SUCCESS")
    timestamp = Column(DateTime, default=datetime.datetime.utcnow)
