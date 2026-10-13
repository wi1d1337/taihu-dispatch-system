"""算法参数注册表（tz_algo_param）读写 + 参数变更审计（tz_param_log）。

纪律：所有可调参数只在注册表；人工修改（PUT /params/{key}）与自动调参（autotune）
共用 apply_param()，走 min/max 校验 → 写库 → version+1 → 落 tz_param_log。
param_version（研判/工单携带）= 全表 max(version)，见 current_param_version()。
"""

from __future__ import annotations

import datetime as dt
import json

from sqlalchemy.orm import Session

from .models import TzAlgoParam, TzParamLog

# 契约 §2 初始参数集（key 区分大小写；value/min/max/unit/meaning）
DEFAULT_PARAMS: dict[str, dict] = {
    "assess.level1_chla":    {"value": 20.0,  "min": 5.0,   "max": 60.0,  "unit": "μg/L", "meaning": "关注阈值（省预案一般档叶绿素a）"},
    "assess.level2_chla":    {"value": 50.0,  "min": 25.0,  "max": 120.0, "unit": "μg/L", "meaning": "较大阈值（省预案较大档）"},
    "assess.level3_chla":    {"value": 80.0,  "min": 40.0,  "max": 200.0, "unit": "μg/L", "meaning": "重大阈值（省预案重大档）"},
    "assess.yoy_halfwin":    {"value": 7.0,   "min": 1.0,   "max": 21.0,  "unit": "天",   "meaning": "同比窗口：去年同期±N天"},
    "assess.yoy_weight":     {"value": 0.6,   "min": 0.1,   "max": 0.9,   "unit": "",     "meaning": "同比基线权重（近7日趋势占1-w）"},
    "assess.exceed_days":    {"value": 3.0,   "min": 1.0,   "max": 7.0,   "unit": "天",   "meaning": "近N日超阈天数计入升级"},
    "dispatch.auto_level":   {"value": 2.0,   "min": 1.0,   "max": 3.0,   "unit": "级",   "meaning": "达到该等级及以上自动派单"},
    "dispatch.deadline_h_l2": {"value": 24.0, "min": 4.0,   "max": 72.0,  "unit": "小时", "meaning": "较大等级处置时限"},
    "dispatch.deadline_h_l3": {"value": 4.0,  "min": 1.0,   "max": 24.0,  "unit": "小时", "meaning": "重大等级处置时限"},
    "supervise.remind_h":    {"value": 2.0,   "min": 0.5,   "max": 12.0,  "unit": "小时", "meaning": "超时多久开始催办"},
    "supervise.escalate_h":  {"value": 6.0,   "min": 1.0,   "max": 24.0,  "unit": "小时", "meaning": "催办多久未动升级督办"},
    "autotune.enabled":      {"value": 1.0,   "min": 0.0,   "max": 1.0,   "unit": "",     "meaning": "自动调参开关（1开 0关）"},
    "autotune.mode":         {"value": 2.0,   "min": 1.0,   "max": 2.0,   "unit": "",     "meaning": "1=建议 2=自动"},
    "autotune.window_days":  {"value": 30.0,  "min": 7.0,   "max": 90.0,  "unit": "天",   "meaning": "回看窗口"},
    "autotune.max_step_pct": {"value": 10.0,  "min": 1.0,   "max": 30.0,  "unit": "%",    "meaning": "单次调整最大幅度"},
    "autotune.min_gain":     {"value": 2.0,   "min": 0.5,   "max": 20.0,  "unit": "pp",   "meaning": "最小改进阈值（命中率百分点）"},
}

TRIGGER_MANUAL = "1"
TRIGGER_AUTO = "2"
TRIGGER_SUGGEST = "3"


def validate_value(spec: dict, value: float) -> tuple[bool, str]:
    """纯函数：按注册表条目校验取值。spec 需含 min_value/max_value/param_key。"""
    lo = spec.get("min_value")
    hi = spec.get("max_value")
    if lo is not None and value < float(lo):
        return False, f"低于下限 {lo}（{spec.get('meaning', '')}）"
    if hi is not None and value > float(hi):
        return False, f"高于上限 {hi}（{spec.get('meaning', '')}）"
    return True, ""


def ensure_defaults(db: Session) -> int:
    """幂等初始化：缺的参数补写默认值。返回补写条数。"""
    existing = {p.param_key for p in db.query(TzAlgoParam).all()}
    n = 0
    for key, spec in DEFAULT_PARAMS.items():
        if key not in existing:
            db.add(TzAlgoParam(param_key=key, param_value=spec["value"],
                               min_value=spec["min"], max_value=spec["max"],
                               unit=spec["unit"], meaning=spec["meaning"],
                               updated_by="seed", updated_at=dt.datetime.now(), version=1))
            n += 1
    if n:
        db.commit()
    return n


def get_params(db: Session) -> dict[str, float]:
    """key -> 当前值（float）。注册表为空时自动补默认（自愈，便于未 seed 直接起服务）。"""
    rows = db.query(TzAlgoParam).all()
    if not rows:
        ensure_defaults(db)
        rows = db.query(TzAlgoParam).all()
    return {p.param_key: float(p.param_value) for p in rows}


def current_param_version(db: Session) -> int:
    """研判/工单携带的 param_version：全表 max(version)（任一参数变更都会推进）。"""
    from sqlalchemy import func
    v = db.query(func.max(TzAlgoParam.version)).scalar()
    return int(v or 1)


def params_payload(db: Session) -> list[dict]:
    """GET /params 全量载荷（含区间/含义/版本——"算法痕迹"数据源）。"""
    return [{
        "param_key": p.param_key,
        "param_value": float(p.param_value),
        "min_value": float(p.min_value) if p.min_value is not None else None,
        "max_value": float(p.max_value) if p.max_value is not None else None,
        "unit": p.unit, "meaning": p.meaning,
        "updated_by": p.updated_by, "updated_at": p.updated_at.isoformat() if p.updated_at else None,
        "version": int(p.version or 1),
    } for p in db.query(TzAlgoParam).order_by(TzAlgoParam.param_key).all()]


def apply_param(db: Session, key: str, value: float, operator: str,
                trigger: str = TRIGGER_MANUAL, evidence: dict | None = None) -> dict:
    """写参数（人工/自动共用）：校验 → 更新 → version+1 → tz_param_log。

    返回更新后的参数行载荷；校验失败抛 ValueError（路由层转 400）。
    """
    p = db.query(TzAlgoParam).filter(TzAlgoParam.param_key == key).first()
    if not p:
        raise KeyError(f"未注册参数: {key}")
    ok, msg = validate_value({"min_value": p.min_value, "max_value": p.max_value,
                              "meaning": p.meaning}, value)
    if not ok:
        raise ValueError(msg)

    old_value, old_version = float(p.param_value), int(p.version or 1)
    p.param_value = value
    p.updated_by = operator
    p.updated_at = dt.datetime.now()
    p.version = old_version + 1
    log = TzParamLog(param_key=key, old_value=old_value, new_value=value,
                     old_version=old_version, new_version=old_version + 1,
                     trigger=trigger, evidence=json.dumps(evidence, ensure_ascii=False) if evidence else None,
                     operator=operator, created_at=dt.datetime.now())
    db.add(log)
    db.commit()
    return {"param_key": key, "param_value": value, "version": old_version + 1,
            "updated_by": operator, "log_id": log.log_id}


def param_history(db: Session, key: str | None = None, limit: int = 200) -> list[dict]:
    """tz_param_log 读取（autotune/history 与参数页证据列表共用）。"""
    q = db.query(TzParamLog)
    if key:
        q = q.filter(TzParamLog.param_key == key)
    rows = q.order_by(TzParamLog.created_at.desc(), TzParamLog.log_id.desc()).limit(limit).all()
    out = []
    for r in rows:
        ev = None
        if r.evidence:
            try:
                ev = json.loads(r.evidence)
            except Exception:
                ev = {"raw": r.evidence}
        out.append({
            "log_id": r.log_id, "param_key": r.param_key,
            "old_value": float(r.old_value) if r.old_value is not None else None,
            "new_value": float(r.new_value) if r.new_value is not None else None,
            "old_version": r.old_version, "new_version": r.new_version,
            "trigger": r.trigger, "operator": r.operator,
            "evidence": ev,
            "created_at": r.created_at.isoformat() if r.created_at else None,
        })
    return out
