"""ORM 模型：与 docs/contracts.md §2 的 tz_* 表一一对应。"""

from __future__ import annotations

import datetime as dt

from sqlalchemy import (DECIMAL, CHAR, DATE, DATETIME, INT, BigInteger, Column,
                        Integer, String, Text)

from .db import SessionLocal


def _auditColumns():
    return [
        Column("create_by", String(64), default=""),
        Column("create_time", DATETIME, default=dt.datetime.now),
        Column("update_by", String(64), default=""),
        Column("update_time", DATETIME),
        Column("remark", String(200)),
    ]


from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    pass


class TzZone(Base):
    __tablename__ = "tz_zone"
    zone_id = Column(BigInteger, primary_key=True, autoincrement=True)
    zone_name = Column(String(50), nullable=False, unique=True)
    zone_code = Column(String(20))
    area_km2 = Column(DECIMAL(8, 2))
    boundary = Column(Text)  # JSON 字符串：[[lng,lat],...]
    prob_72h = Column(DECIMAL(5, 2))
    tons_q25 = Column(DECIMAL(10, 2))
    tons_q50 = Column(DECIMAL(10, 2))
    chla = Column(DECIMAL(7, 2))
    update_time = Column(DATETIME)
    create_by = Column(String(64), default="")
    create_time = Column(DATETIME, default=dt.datetime.now)
    update_by = Column(String(64), default="")
    remark = Column(String(200))


class TzBoat(Base):
    __tablename__ = "tz_boat"
    boat_id = Column(BigInteger, primary_key=True, autoincrement=True)
    boat_name = Column(String(50), nullable=False)
    boat_type = Column(String(20))
    capacity_t = Column(DECIMAL(8, 2))
    speed_kmh = Column(DECIMAL(5, 1))
    fuel_l_km = Column(DECIMAL(5, 2))
    status = Column(CHAR(1), default="0")
    home_port = Column(String(50))
    create_by = Column(String(64), default="")
    create_time = Column(DATETIME, default=dt.datetime.now)
    update_by = Column(String(64), default="")
    remark = Column(String(200))


class TzStation(Base):
    __tablename__ = "tz_station"
    station_id = Column(BigInteger, primary_key=True, autoincrement=True)
    station_name = Column(String(50), nullable=False)
    capacity_t_day = Column(DECIMAL(8, 2))
    status = Column(CHAR(1), default="0")
    create_by = Column(String(64), default="")
    create_time = Column(DATETIME, default=dt.datetime.now)
    update_by = Column(String(64), default="")
    remark = Column(String(200))


class TzDispatchOrder(Base):
    __tablename__ = "tz_dispatch_order"
    order_id = Column(BigInteger, primary_key=True, autoincrement=True)
    order_date = Column(DATE, nullable=False)
    half_day = Column(CHAR(1), nullable=False)
    boat_id = Column(BigInteger, nullable=False)
    zone_id = Column(BigInteger, nullable=False)
    station_id = Column(BigInteger)
    plan_tons = Column(DECIMAL(8, 2))
    actual_tons = Column(DECIMAL(8, 2))
    status = Column(CHAR(1), default="0")
    solver = Column(String(20))
    objective = Column(DECIMAL(12, 2))
    model_version = Column(String(40))
    run_id = Column(BigInteger)
    create_by = Column(String(64), default="")
    create_time = Column(DATETIME, default=dt.datetime.now)
    update_by = Column(String(64), default="")
    update_time = Column(DATETIME)
    remark = Column(String(200))


class TzSalvageLog(Base):
    __tablename__ = "tz_salvage_log"
    log_id = Column(BigInteger, primary_key=True, autoincrement=True)
    order_id = Column(BigInteger)
    boat_id = Column(BigInteger, nullable=False)
    zone_id = Column(BigInteger, nullable=False)
    log_date = Column(DATE, nullable=False)
    half_day = Column(CHAR(1))
    actual_tons = Column(DECIMAL(8, 2), nullable=False)
    fuel_liters = Column(DECIMAL(8, 1))
    crew = Column(INT)
    photo_url = Column(String(200))
    lng = Column(DECIMAL(10, 6))
    lat = Column(DECIMAL(10, 6))
    create_by = Column(String(64), default="")
    create_time = Column(DATETIME, default=dt.datetime.now)
    update_by = Column(String(64), default="")
    update_time = Column(DATETIME)
    remark = Column(String(200))


class TzForecastRecord(Base):
    __tablename__ = "tz_forecast_record"
    fc_id = Column(BigInteger, primary_key=True, autoincrement=True)
    zone_id = Column(BigInteger, nullable=False)
    fc_date = Column(DATE, nullable=False)
    horizon_h = Column(Integer, default=72)
    prob = Column(DECIMAL(5, 2))
    chla_q25 = Column(DECIMAL(7, 2))
    chla_q50 = Column(DECIMAL(7, 2))
    chla_q75 = Column(DECIMAL(7, 2))
    create_time = Column(DATETIME, default=dt.datetime.now)


class TzRun(Base):
    __tablename__ = "tz_run"
    run_id = Column(BigInteger, primary_key=True, autoincrement=True)
    run_date = Column(DATE, nullable=False)
    step = Column(String(30))
    status = Column(CHAR(1))
    started_at = Column(DATETIME)
    ended_at = Column(DATETIME)
    model_version = Column(String(40))
    detail = Column(Text)
