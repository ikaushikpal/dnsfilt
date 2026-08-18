import os
import logging
from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from app.config import settings

logger = logging.getLogger(__name__)

db_url = settings.SQLITE_DB_PATH

if db_url.startswith("sqlite:///"):
    raw_path = db_url.replace("sqlite:///", "")
    if not os.path.isabs(raw_path):
        raw_path = os.path.abspath(raw_path)
    
    dir_path = os.path.dirname(raw_path)
    try:
        if dir_path:
            os.makedirs(dir_path, exist_ok=True)
            # Test write access
            test_file = os.path.join(dir_path, ".write_test")
            with open(test_file, "w") as tf:
                tf.write("ok")
            os.remove(test_file)
            db_url = f"sqlite:///{raw_path}"
    except Exception as e:
        logger.warning(f"Could not initialize SQLite in '{dir_path}' ({e}). Falling back to '/tmp/resolvers.db'")
        db_url = "sqlite:////tmp/resolvers.db"

try:
    engine = create_engine(
        db_url, connect_args={"check_same_thread": False}
    )
except Exception as ex:
    logger.warning(f"Engine creation failed for '{db_url}': {ex}. Using in-memory database.")
    engine = create_engine(
        "sqlite:///:memory:", connect_args={"check_same_thread": False}
    )

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
