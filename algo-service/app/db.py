"""SQLAlchemy 引擎与会话（TZ_DB_URL 环境变量注入）。"""

from __future__ import annotations

import os

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

DB_URL = os.environ.get(
    "TZ_DB_URL",
    "mysql+pymysql://root:root123@127.0.0.1:3306/ry-vue?charset=utf8mb4",
)

engine = create_engine(DB_URL, pool_pre_ping=True, pool_recycle=3600)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
