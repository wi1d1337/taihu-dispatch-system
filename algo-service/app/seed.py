"""种子数据 v2：重置并生成演示数据（工单工作流版，days 默认 730）。

日期锚点对齐：合成序列以"昨天"结尾（评测按真实日期查询）；
两年史保证"去年同期"存在（同比外推不降级）。

演示口径校准（合成数据标注）：taihu_toolkit.synth 的第二年序列会在 350 上限长时间
饱和（全分区 L3、无梯度），seed 对原始序列做 chla' = k_z × chla^0.62 的分区校准
（k_z 由各分区近7日原始值对目标演示梯度解出），使分区等级分布适合演示。
合成概率/等级与合成事实由同一序列派生（先定事实再生成结论）。
"""

from __future__ import annotations

import datetime as dt
import json
import logging

import numpy as np
from sqlalchemy.orm import Session

from .assess import MODEL_VERSION, assess_one
from .db import engine
from .dispatch2 import auto_dispatch, build_order_no
from .models import (Base, TzAlgoParam, TzAssessment, TzBoat, TzChlaObs, TzOrderLog,
                     TzParamLog, TzRun, TzStation, TzWorkOrder, TzWorker, TzZone)
from .params import current_param_version, ensure_defaults, get_params
from .zones import ZONES, boundary_json

log = logging.getLogger("algo.seed")

FLEET = [
    ("打捞-01", "打捞", 45.0, 12.0, 0.55), ("打捞-02", "打捞", 45.0, 12.0, 0.55),
    ("机动-03", "机动", 30.0, 18.0, 0.38), ("机动-04", "机动", 30.0, 18.0, 0.38),
    ("小型-05", "小型", 15.0, 20.0, 0.22), ("小型-06", "小型", 15.0, 20.0, 0.22),
    ("小型-07", "小型", 15.0, 20.0, 0.22), ("小型-08", "小型", 15.0, 20.0, 0.22),
]
STATIONS = [("分离站-马山", 120.0), ("分离站-华庄", 90.0), ("分离站-南泉", 60.0)]
LEADER_NAMES = ["王卫东", "李国强", "张建平", "刘志明", "陈海涛", "赵国庆"]
CREW_NAMES = ["周水生", "吴长河", "郑守业", "冯建良", "褚同福", "卫明泉", "蒋兴华", "沈玉根"]
TEAMS = ["蓝藻打捞一队", "蓝藻打捞二队"]

HIST_ASSESS_DAYS = 90      # 历史研判回填天数
HIST_ORDER_DAYS = 90       # 历史工单回填天数

# 演示梯度目标（校准后各分区"昨日近7日均值"量级 μg/L）
CALIB_POW = 0.62
CALIB_TARGET = {"梅梁湾": 47.0, "竺山湖": 32.0, "贡湖": 85.0,
                "湖心": 28.0, "西部沿岸": 52.0, "南部沿岸": 88.0}


def _calibrate(raw_by_zone: dict[str, list[float]], today: dt.date) -> tuple[dict[str, dict[dt.date, float]], dict]:
    """原始合成序列 → 校准序列（分区 k_z × chla^POW）。返回 (序列, 校准系数)。"""
    yday = today - dt.timedelta(days=1)
    n = len(next(iter(raw_by_zone.values())))
    dates = [yday - dt.timedelta(days=n - 1 - i) for i in range(n)]
    series, coefs = {}, {}
    for zname, vals in raw_by_zone.items():
        recent7 = float(np.mean(vals[-7:]))
        k = CALIB_TARGET[zname] / recent7 ** CALIB_POW
        coefs[zname] = round(k, 4)
        series[zname] = {d: round(k * v ** CALIB_POW, 1) for d, v in zip(dates, vals)}
    return series, coefs


def _log(db, order_id, action, operator, t, frm, to, note=""):
    db.add(TzOrderLog(order_id=order_id, action=action, operator=operator,
                      from_status=frm, to_status=to, note=note, created_at=t))


def _hist_order(db, d, zone, level, chla_d, assessment_id, leader, boat_ids, crews, seq, p, pv, rng):
    """生成一张带完整流转时间戳的历史工单（合成事实与等级联动）。"""
    dispatch = dt.datetime.combine(d, dt.time(8, 30)) + dt.timedelta(minutes=int(rng.integers(0, 30)))
    accept_min = int(rng.choice([8, 15, 25, 40, 130, 200], p=[0.30, 0.25, 0.20, 0.15, 0.07, 0.03]))
    accepted = dispatch + dt.timedelta(minutes=accept_min)
    arrived = accepted + dt.timedelta(minutes=int(rng.integers(40, 130)))
    submitted = arrived + dt.timedelta(hours=float(rng.uniform(2.5, 6.5)))
    checked = submitted + dt.timedelta(hours=float(rng.uniform(0.5, 2.0)))

    # 藻水吨数与当日 Chl-a 联动（先定事实）
    l1, l3 = p["assess.level1_chla"], p["assess.level3_chla"]
    intensity = min(max((chla_d - l1) / (l3 - l1), 0.15), 1.1)
    area_f = float(zone.area_km2 or 100) / 150.0
    tons = round(float(np.clip(70.0 * (0.25 + 0.75 * intensity) * area_f
                               * rng.normal(1.0, 0.12), 3.0, 110.0)), 1)

    hours = p["dispatch.deadline_h_l3"] if level >= 3 else p["dispatch.deadline_h_l2"]
    boat_id = int(boat_ids[rng.integers(0, len(boat_ids))])
    crew = sorted(rng.choice(crews, size=int(rng.integers(2, 4)), replace=False).tolist())
    crew_ids = ",".join(str(c) for c in crew)
    leader_user = leader.username or leader.worker_name

    order_no = build_order_no(d, seq)
    yday = dt.date.today() - dt.timedelta(days=1)
    if d >= yday:                       # 昨天起留一部分在途单给演示操作
        final_status = "3" if rng.random() < 0.45 else "4"
    else:
        final_status = "4"

    order = TzWorkOrder(
        order_no=order_no, zone_id=zone.zone_id, level=str(level), source="1",
        assessment_id=assessment_id, status=final_status, leader_id=leader.worker_id,
        boat_id=boat_id, crew_ids=crew_ids,
        deadline=dispatch + dt.timedelta(hours=float(hours)),
        dispatched_at=dispatch, accepted_at=accepted, arrived_at=arrived,
        submitted_at=submitted, checked_at=checked if final_status == "4" else None,
        actual_tons=tons,
        photo_urls=f"/profile/photos/demo/{order_no}-1.jpg,/profile/photos/demo/{order_no}-2.jpg",
        arrive_lng=120.20 + float(rng.uniform(-0.1, 0.2)), arrive_lat=31.35 + float(rng.uniform(-0.1, 0.1)),
        model_version=MODEL_VERSION, param_version=pv,
        create_by="algo", create_time=dispatch,
    )
    db.add(order)
    db.flush()

    _log(db, order.order_id, "派单", "algo", dispatch, None, "0",
         f"算法自动派单：等级{level}，片长{leader.worker_name}，时限{float(hours):g}h")
    if accept_min > p["supervise.remind_h"] * 60:      # 接单慢 → 催办督办痕迹
        _log(db, order.order_id, "催办", "scheduler", dispatch + dt.timedelta(hours=float(p["supervise.remind_h"])),
             "0", "0", "超时未接单，系统催办")
        if accept_min > 150:
            _log(db, order.order_id, "升级督办", "scheduler", dispatch + dt.timedelta(hours=float(p["supervise.escalate_h"])),
                 "0", "0", "催办后仍未接单，升级督办")
    _log(db, order.order_id, "接单", leader_user, accepted, "0", "1", "")
    _log(db, order.order_id, "派船", leader_user, accepted, "1", "1",
         f"确认派船 {boat_id} 号，打捞员 {crew_ids}")
    _log(db, order.order_id, "到场", f"worker{crew[0]}", arrived, "1", "2", "到场打卡（定位已记录）")
    _log(db, order.order_id, "提交", f"worker{crew[0]}", submitted, "2", "3", f"藻水湿重 {tons} 吨，照片2张")
    if final_status == "4":
        _log(db, order.order_id, "核查", "dispatcher", submitted + dt.timedelta(minutes=20), "3", "3", "核对吨数与到场照片")
        _log(db, order.order_id, "办结", "dispatcher", checked, "3", "4", "核查通过，工单办结")
    return order


def seed(db: Session, days: int = 730) -> dict:
    from taihu_toolkit.synth import synth_taihu

    Base.metadata.create_all(bind=engine)   # 幂等建表（含 algo 内部表 tz_chla_obs）
    today = dt.date.today()

    # 1) 重置业务表（子表在前；若依系统表不动）
    for t in (TzOrderLog, TzWorkOrder, TzParamLog, TzAlgoParam, TzAssessment, TzChlaObs,
              TzWorker, TzRun, TzZone, TzBoat, TzStation):
        db.query(t).delete(synchronize_session=False)
    db.commit()

    # 2) 分区 / 船 / 站 / 人员 / 参数
    zone_ids, zones = {}, {}
    for z in ZONES:
        row = TzZone(zone_name=z["zone_name"], zone_code=z["zone_code"], area_km2=z["area_km2"],
                     boundary=boundary_json(z), update_time=dt.datetime.now())
        db.add(row)
        db.flush()
        zone_ids[z["zone_name"]] = row.zone_id
        zones[row.zone_id] = row
    for name, typ, cap, spd, fuel in FLEET:
        db.add(TzBoat(boat_name=name, boat_type=typ, capacity_t=cap, speed_kmh=spd,
                      fuel_l_km=fuel, status="0", home_port="马山"))
    for name, cap in STATIONS:
        db.add(TzStation(station_name=name, capacity_t_day=cap, status="0"))
    db.flush()
    boat_ids = [b.boat_id for b in db.query(TzBoat).all()]
    for i, (zname, zid) in enumerate(zone_ids.items()):
        db.add(TzWorker(worker_name=LEADER_NAMES[i], role="1", zone_id=zid,
                        phone=f"1380000{i + 1:02d}", team_name="太湖片区管理",
                        username=f"leader{i + 1:02d}", status="0"))
    for i, name in enumerate(CREW_NAMES):
        db.add(TzWorker(worker_name=name, role="2", phone=f"1390000{i + 1:02d}",
                        team_name=TEAMS[i % 2], username=f"worker{i + 1:02d}", status="0"))
    db.commit()
    ensure_defaults(db)
    p = get_params(db)
    pv = current_param_version(db)

    # 3) 两年合成史（校准后写 tz_chla_obs；以昨天结尾）
    rng = np.random.default_rng(42)
    df = synth_taihu(days=days, seed=42)
    raw_by_zone = {z: list(map(float, sub.chla)) for z, sub in df.groupby("zone")}
    series, coefs = _calibrate(raw_by_zone, today)
    obs_rows = [TzChlaObs(zone_id=zone_ids[z], obs_date=d, chla=v)
                for z, s in series.items() for d, v in s.items()]
    db.add_all(obs_rows)
    db.commit()

    leaders = {w.zone_id: w for w in db.query(TzWorker).filter(TzWorker.role == "1").all()}
    crews = [w.worker_id for w in db.query(TzWorker).filter(TzWorker.role == "2").all()]

    # 4) 历史研判（近90天，含概率预报第二意见；与线上 assess_one 同口径）
    assess_idx = {}          # (date, zone_id) -> assessment_id
    hist_days = [today - dt.timedelta(days=HIST_ASSESS_DAYS - i) for i in range(HIST_ASSESS_DAYS)]
    for d in hist_days:
        if d >= today:
            continue
        for zid, zone in zones.items():
            zname = zone.zone_name
            a = assess_one(series[zname], d, p, float(zone.area_km2 or 100))
            row = TzAssessment(assess_date=d, zone_id=zid, level=str(a["level"]),
                               predicted=a["predicted"], yoy=a["yoy"], recent7=a["recent7"],
                               trend=a["trend"], basis=json.dumps(a["basis"], ensure_ascii=False),
                               confidence=a["confidence"], model_version=MODEL_VERSION,
                               param_version=pv, created_at=dt.datetime.combine(d, dt.time(7, 0)))
            db.add(row)
            db.flush()
            assess_idx[(d, zid)] = row.assessment_id
    db.commit()

    # 5) 历史工单（近90天，等级≥2 的分区日；含完整流转/督办日志，吨数与事实联动）
    n_orders = 0
    for d in hist_days:
        if d >= today:
            continue
        seq = 0
        for zid, zone in zones.items():
            a = assess_one(series[zone.zone_name], d, p, float(zone.area_km2 or 100))
            if a["level"] < 2:
                continue
            seq += 1
            n_orders += 1
            _hist_order(db, d, zone, a["level"], series[zone.zone_name][d],
                        assess_idx[(d, zid)], leaders[zid], boat_ids, crews, seq, p, pv, rng)
        db.commit()

    # 6) 今日：研判（upsert，prob/tons 进 zone 缓存）+ 自动派单（附 CP-SAT 推荐派船）
    from .assess import assess_day
    today_assess = assess_day(db, today)
    dispatch = auto_dispatch(db, today)

    db.add(TzRun(run_date=today, step="seed", status="1",
                 started_at=dt.datetime.now(), ended_at=dt.datetime.now(),
                 model_version=MODEL_VERSION,
                 detail=json.dumps({"days": days, "obs": len(obs_rows), "orders": n_orders,
                                    "calibration": {"pow": CALIB_POW, "k": coefs}},
                                   ensure_ascii=False)))
    db.commit()
    return {"days": days, "obs_points": len(obs_rows), "zones": len(ZONES),
            "boats": len(FLEET), "stations": len(STATIONS),
            "workers": len(LEADER_NAMES) + len(CREW_NAMES),
            "assessments": HIST_ASSESS_DAYS * len(ZONES),
            "hist_orders": n_orders,
            "today_levels": {a["zone_name"]: a["level"] for a in today_assess},
            "today_orders_created": len(dispatch.get("created", [])),
            "today": today.isoformat()}
