"""自动派单 v2：读当日研判，level≥dispatch.auto_level 的分区自动建 tz_work_order。

两级派发第一跳：算法 → 片长（status=0 待接单，派给该分区 role=1 片长）；
suggest_boats 挂 CP-SAT 推荐派船方案（片长接单预填可改）；写 tz_order_log(action=派单)。
幂等：同日同分区已存在未撤销（status≠6）工单则跳过（人工撤单后重跑可重建）。
"""

from __future__ import annotations

import datetime as dt
import json
import logging

from sqlalchemy.orm import Session

from .assess import MODEL_VERSION
from .models import TzAssessment, TzOrderLog, TzWorkOrder, TzWorker, TzZone
from .params import current_param_version, get_params
from .solve import recommend_boats

log = logging.getLogger("algo.dispatch")

ORDER_ACTION = "派单"
OPERATOR = "algo"


def build_order_no(date: dt.date, seq: int) -> str:
    """纯函数：TZyyyymmdd-序号（4 位）。"""
    return f"TZ{date.strftime('%Y%m%d')}-{seq:04d}"


def _next_seq(db: Session, date: dt.date) -> int:
    """当日已有单数 + 1（order_no 前缀计数，含人工单，保证全局唯一）。"""
    prefix = f"TZ{date.strftime('%Y%m%d')}-"
    n = db.query(TzWorkOrder).filter(TzWorkOrder.order_no.like(prefix + "%")).count()
    return n + 1


def auto_dispatch(db: Session, date: dt.date) -> dict:
    p = get_params(db)
    pv = current_param_version(db)
    auto_level = int(p["dispatch.auto_level"])
    now = dt.datetime.now()

    zones = {z.zone_id: z for z in db.query(TzZone).all()}
    assess_rows = (db.query(TzAssessment)
                   .filter(TzAssessment.assess_date == date).all())
    if not assess_rows:
        return {"date": date.isoformat(), "created": [], "skipped": [],
                "note": "当日无研判记录（先跑 assess）"}

    # 片长索引：zone_id -> worker（role=1、在岗）
    leaders = {w.zone_id: w for w in
               db.query(TzWorker).filter(TzWorker.role == "1", TzWorker.status == "0").all()}

    # 达标分区 → 需求量（tons_q50 缓存字段，assess 步刚更新）
    targets = [a for a in assess_rows if int(a.level or 0) >= auto_level]
    demand = {a.zone_id: float(zones[a.zone_id].tons_q50 or 0) for a in targets
              if a.zone_id in zones}
    rec = recommend_boats(db, demand)
    by_zone: dict[int, list] = {}
    for b in rec.get("boats", []):
        by_zone.setdefault(b["zone_id"], []).append(b)

    created, skipped = [], []
    for a in targets:
        zid = a.zone_id
        zone = zones.get(zid)
        if zone is None:
            continue
        # 幂等：同日同分区未撤销单不重复建（含人工单）
        dup = (db.query(TzWorkOrder)
               .filter(TzWorkOrder.zone_id == zid, TzWorkOrder.status != "6",
                       TzWorkOrder.order_no.like(f"TZ{date.strftime('%Y%m%d')}-%")).count())
        if dup:
            skipped.append({"zone_id": zid, "zone_name": zone.zone_name,
                            "reason": "同日同分区已有未撤销工单"})
            continue
        leader = leaders.get(zid)
        if leader is None:
            skipped.append({"zone_id": zid, "zone_name": zone.zone_name,
                            "reason": "该分区无在岗片长，需人工创建"})
            continue

        level = int(a.level)
        hours = p["dispatch.deadline_h_l3"] if level >= 3 else p["dispatch.deadline_h_l2"]
        order = TzWorkOrder(
            order_no=build_order_no(date, _next_seq(db, date)),
            zone_id=zid, level=str(level), source="1", assessment_id=a.assessment_id,
            status="0", leader_id=leader.worker_id,
            deadline=now + dt.timedelta(hours=float(hours)),
            dispatched_at=now,
            suggest_boats=json.dumps(by_zone.get(zid, []), ensure_ascii=False),
            model_version=MODEL_VERSION, param_version=pv,
            create_by=OPERATOR, create_time=now,
        )
        db.add(order)
        db.flush()
        db.add(TzOrderLog(order_id=order.order_id, action=ORDER_ACTION, operator=OPERATOR,
                          from_status=None, to_status="0",
                          note=f"算法自动派单：等级{level}，片长{leader.worker_name}，"
                               f"时限{float(hours):g}h，推荐派船{len(by_zone.get(zid, []))}艘"
                               f"（{rec.get('solver')}）",
                          created_at=now))
        created.append({"order_id": order.order_id, "order_no": order.order_no,
                        "zone_id": zid, "zone_name": zone.zone_name, "level": level,
                        "leader": leader.worker_name,
                        "suggest_boats": by_zone.get(zid, [])})

    db.commit()
    log.info("auto_dispatch %s: created=%d skipped=%d solver=%s",
             date, len(created), len(skipped), rec.get("solver"))
    return {"date": date.isoformat(), "auto_level": auto_level,
            "solver": rec.get("solver"), "objective": rec.get("objective"),
            "created": created, "skipped": skipped}
