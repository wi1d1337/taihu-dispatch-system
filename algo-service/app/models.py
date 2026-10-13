"""ORM 模型 v2：与 docs/contracts.md §2 的 tz_* 表一一对应。

v2 变化：
- 新增 tz_worker / tz_work_order / tz_order_log / tz_assessment / tz_algo_param / tz_param_log
- 保留 v1 的 tz_zone / tz_boat / tz_station / tz_run
- 废弃 v1 的 tz_dispatch_order / tz_salvage_log / tz_forecast_record（不建 ORM）
- 新增 algo 内部表 tz_chla_obs：逐日分区 Chl-a 序列（同比/近7日/回验的事实口径载体，
  algo-service 自建自管，RuoYi 侧不读写——契约 §2 有补注）
"""

from __future__ import annotations

import datetime as dt

from sqlalchemy import (DECIMAL, CHAR, DATE, DATETIME, BigInteger, Column,
                        Integer, String, Text, UniqueConstraint)
from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    pass


# BIGINT 主键：sqlite 下退化为 INTEGER 才能自增（MySQL DDL 不变，便于本地集成冒烟）
BigIntPK = BigInteger().with_variant(Integer, "sqlite")


def _audit():
    """RuoYi 风格审计字段。"""
    return [
        Column("create_by", String(64), default=""),
        Column("create_time", DATETIME, default=dt.datetime.now),
        Column("update_by", String(64), default=""),
        Column("update_time", DATETIME),
        Column("remark", String(200)),
    ]


# ---------------------------------------------------------------- 保留 v1 ----
class TzZone(Base):
    __tablename__ = "tz_zone"
    zone_id = Column(BigIntPK, primary_key=True, autoincrement=True)
    zone_name = Column(String(50), nullable=False, unique=True)
    zone_code = Column(String(20))
    area_km2 = Column(DECIMAL(8, 2))
    boundary = Column(Text)          # JSON 字符串 [[lng,lat],...]
    prob_72h = Column(DECIMAL(5, 2))  # v2: 概率预报并列展示（地图色阶改用 level）
    tons_q25 = Column(DECIMAL(10, 2))
    tons_q50 = Column(DECIMAL(10, 2))
    chla = Column(DECIMAL(7, 2))     # 最近一次实测/合成 Chl-a
    update_time = Column(DATETIME)
    create_by = Column(String(64), default="")
    create_time = Column(DATETIME, default=dt.datetime.now)
    update_by = Column(String(64))
    remark = Column(String(200))


class TzBoat(Base):
    __tablename__ = "tz_boat"
    boat_id = Column(BigIntPK, primary_key=True, autoincrement=True)
    boat_name = Column(String(50), nullable=False)
    boat_type = Column(String(20))
    capacity_t = Column(DECIMAL(8, 2))
    speed_kmh = Column(DECIMAL(5, 1))
    fuel_l_km = Column(DECIMAL(5, 2))
    status = Column(CHAR(1), default="0")
    home_port = Column(String(50))
    create_by = Column(String(64), default="")
    create_time = Column(DATETIME, default=dt.datetime.now)
    update_by = Column(String(64))
    remark = Column(String(200))


class TzStation(Base):
    __tablename__ = "tz_station"
    station_id = Column(BigIntPK, primary_key=True, autoincrement=True)
    station_name = Column(String(50), nullable=False)
    capacity_t_day = Column(DECIMAL(8, 2))
    status = Column(CHAR(1), default="0")
    create_by = Column(String(64), default="")
    create_time = Column(DATETIME, default=dt.datetime.now)
    update_by = Column(String(64))
    remark = Column(String(200))


class TzRun(Base):
    __tablename__ = "tz_run"
    run_id = Column(BigIntPK, primary_key=True, autoincrement=True)
    run_date = Column(DATE, nullable=False)
    step = Column(String(30))
    status = Column(CHAR(1))
    started_at = Column(DATETIME)
    ended_at = Column(DATETIME)
    model_version = Column(String(40))
    detail = Column(Text)


# ---------------------------------------------------------------- v2 新表 ----
class TzWorker(Base):
    __tablename__ = "tz_worker"
    worker_id = Column(BigIntPK, primary_key=True, autoincrement=True)
    worker_name = Column(String(50), nullable=False)
    role = Column(CHAR(1), nullable=False)       # 1片长 2打捞员(船长)
    phone = Column(String(20))
    zone_id = Column(BigInteger)                 # 负责分区（片长）
    team_name = Column(String(50))
    username = Column(String(64))                # 关联 sys_user 登录名(H5)
    status = Column(CHAR(1), default="0")
    create_by = Column(String(64), default="")
    create_time = Column(DATETIME, default=dt.datetime.now)
    update_by = Column(String(64))
    update_time = Column(DATETIME)
    remark = Column(String(200))


class TzWorkOrder(Base):
    __tablename__ = "tz_work_order"
    order_id = Column(BigIntPK, primary_key=True, autoincrement=True)
    order_no = Column(String(32), nullable=False, unique=True)  # TZ20261014-0001
    zone_id = Column(BigInteger, nullable=False)
    level = Column(CHAR(1), nullable=False)      # 1关注 2较大 3重大
    source = Column(CHAR(1), default="1")        # 1算法自动 2人工创建
    assessment_id = Column(BigInteger)
    status = Column(CHAR(1), default="0")        # 0待接单...6已撤销
    leader_id = Column(BigInteger)
    boat_id = Column(BigInteger)
    crew_ids = Column(String(200))               # 打捞员工号逗号分隔
    deadline = Column(DATETIME)
    dispatched_at = Column(DATETIME)
    accepted_at = Column(DATETIME)
    arrived_at = Column(DATETIME)
    submitted_at = Column(DATETIME)
    checked_at = Column(DATETIME)
    actual_tons = Column(DECIMAL(8, 2))
    photo_urls = Column(String(500))
    arrive_lng = Column(DECIMAL(10, 6))
    arrive_lat = Column(DECIMAL(10, 6))
    suggest_boats = Column(Text)                 # JSON [{boat_id,zone_id,tons}]
    revoke_reason = Column(String(200))
    check_comment = Column(String(200))
    model_version = Column(String(40))
    param_version = Column(Integer)
    create_by = Column(String(64), default="")
    create_time = Column(DATETIME, default=dt.datetime.now)
    update_by = Column(String(64))
    update_time = Column(DATETIME)
    remark = Column(String(200))


class TzOrderLog(Base):
    __tablename__ = "tz_order_log"
    log_id = Column(BigIntPK, primary_key=True, autoincrement=True)
    order_id = Column(BigInteger, nullable=False)
    action = Column(String(30))                  # 派单/接单/派船/到场/提交/核查/办结/退回/撤销/催办/改派/升级督办
    operator = Column(String(64))
    from_status = Column(CHAR(1))
    to_status = Column(CHAR(1))
    note = Column(String(300))
    created_at = Column(DATETIME)


class TzAssessment(Base):
    __tablename__ = "tz_assessment"
    assessment_id = Column(BigIntPK, primary_key=True, autoincrement=True)
    assess_date = Column(DATE, nullable=False)
    zone_id = Column(BigInteger, nullable=False)
    level = Column(CHAR(1))                      # 0无需干预 1关注 2较大 3重大
    predicted = Column(DECIMAL(7, 2))            # 同比外推预测 Chl-a
    yoy = Column(DECIMAL(7, 2))                  # 去年同期值
    recent7 = Column(DECIMAL(7, 2))              # 近7日均值
    trend = Column(DECIMAL(6, 2))                # 近7日趋势%
    basis = Column(Text)                         # JSON 依据明细（含参数快照/概率预报第二意见）
    confidence = Column(DECIMAL(4, 2))
    model_version = Column(String(40))
    param_version = Column(Integer)
    created_at = Column(DATETIME)


class TzAlgoParam(Base):
    __tablename__ = "tz_algo_param"
    param_id = Column(BigIntPK, primary_key=True, autoincrement=True)
    param_key = Column(String(64), nullable=False, unique=True)
    param_value = Column(DECIMAL(12, 4), nullable=False)
    min_value = Column(DECIMAL(12, 4))
    max_value = Column(DECIMAL(12, 4))
    unit = Column(String(20))
    meaning = Column(String(200))
    updated_by = Column(String(64))
    updated_at = Column(DATETIME)
    version = Column(Integer, default=1)


class TzParamLog(Base):
    __tablename__ = "tz_param_log"
    log_id = Column(BigIntPK, primary_key=True, autoincrement=True)
    param_key = Column(String(64), nullable=False)
    old_value = Column(DECIMAL(12, 4))
    new_value = Column(DECIMAL(12, 4))
    old_version = Column(Integer)
    new_version = Column(Integer)
    trigger = Column(CHAR(1))                    # 1人工 2自动调参 3自动建议待确认
    evidence = Column(Text)                      # JSON 证据
    operator = Column(String(64))
    created_at = Column(DATETIME)


# ------------------------------------------------------------ algo 内部表 ----
class TzChlaObs(Base):
    """逐日分区 Chl-a 序列（合成事实口径；同比/近7日/回验/autotune 回测的数据底座）。"""
    __tablename__ = "tz_chla_obs"
    obs_id = Column(BigIntPK, primary_key=True, autoincrement=True)
    zone_id = Column(BigInteger, nullable=False)
    obs_date = Column(DATE, nullable=False)
    chla = Column(DECIMAL(7, 2), nullable=False)
    create_time = Column(DATETIME, default=dt.datetime.now)
    __table_args__ = (UniqueConstraint("zone_id", "obs_date", name="uq_chla_zone_date"),)
