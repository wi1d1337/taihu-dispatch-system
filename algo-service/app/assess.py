"""研判引擎 v2（决策主线）：同比外推 + 省预案阈值规则，概率预报作第二意见并列。

口径（契约 §3 末段）：
  预测值 = yoy_weight × 去年同期均值(±yoy_halfwin) + (1−yoy_weight) × 近7日均值 × (1+trend/100)
  等级   = predicted 对照 level1/2/3_chla；近 exceed_days 日全部超阈（level1 阈值）→ 升一档（封顶3）
  降级   = 去年同期窗口无数据 → predicted 回落为近7日均值，basis.degraded=true
  trend% = 近7日(d-7..d-1) 均值 vs 前7日(d-14..d-8) 均值的环比
拆分说明：extract_features（与参数无关的窗口统计）+ decide（快速定级），
autotune 回测在候选参数网格上只重放 decide，控制计算量。
"""

from __future__ import annotations

import datetime as dt
import json
import logging

from sqlalchemy.orm import Session

from .forecast import MODEL_VERSION as FC_MODEL_VERSION
from .forecast import est_tons, forecast_zone
from .models import TzAssessment, TzChlaObs, TzZone
from .params import current_param_version, get_params

log = logging.getLogger("algo.assess")

MODEL_VERSION = "yoy-threshold-v2"
ASSESS_KEYS = ("assess.level1_chla", "assess.level2_chla", "assess.level3_chla",
               "assess.yoy_halfwin", "assess.yoy_weight", "assess.exceed_days")

LEVEL_NAMES = {0: "无需干预", 1: "关注", 2: "较大(建议打捞)", 3: "重大(应急)"}


def level_of_value(value: float, p: dict) -> int:
    """Chl-a 值 → 等级（回验/autotune 的"实际等级"同一口径）。"""
    if value >= p["assess.level3_chla"]:
        return 3
    if value >= p["assess.level2_chla"]:
        return 2
    if value >= p["assess.level1_chla"]:
        return 1
    return 0


def extract_features(series: dict[dt.date, float], d: dt.date, p: dict) -> dict:
    """从 {date: chla} 提取窗口统计（只用 d 之前的数据；与阈值参数无关的部分）。"""
    halfwin = int(p["assess.yoy_halfwin"])
    exceed_days = int(p["assess.exceed_days"])

    def win(a: dt.date, b: dt.date):
        return [v for k, v in series.items() if a <= k <= b]

    recent = win(d - dt.timedelta(days=7), d - dt.timedelta(days=1))
    prev7 = win(d - dt.timedelta(days=14), d - dt.timedelta(days=8))
    exceed_win = win(d - dt.timedelta(days=exceed_days), d - dt.timedelta(days=1))
    yoy_win = win(d - dt.timedelta(days=365 + halfwin), d - dt.timedelta(days=365 - halfwin))
    recent7 = sum(recent) / len(recent) if recent else None
    prev7m = sum(prev7) / len(prev7) if prev7 else None
    trend = (recent7 - prev7m) / prev7m * 100.0 if (recent7 is not None and prev7m) else 0.0
    return {
        "recent7": recent7, "recent7_n": len(recent),
        "yoy": sum(yoy_win) / len(yoy_win) if yoy_win else None, "yoy_n": len(yoy_win),
        "trend": trend, "exceed_win": exceed_win, "exceed_days": exceed_days,
        "latest": max((k for k in series if k < d), default=None),
        "latest_chla": series[max((k for k in series if k < d), default=None)]
                       if series and max((k for k in series if k < d), default=None) else None,
    }


def decide(f: dict, p: dict) -> dict:
    """特征 + 参数 → 定级结论（纯函数；basis 含参数快照与依据明细）。"""
    w = float(p["assess.yoy_weight"])
    recent7, yoy, trend = f["recent7"], f["yoy"], f["trend"]

    if recent7 is None and yoy is None:
        pred, degraded, reason = 0.0, True, "无历史序列数据"
    elif yoy is None:
        pred, degraded, reason = recent7, True, "无去年同期数据，降级为近7日均值+阈值"
    elif recent7 is None:
        pred, degraded, reason = yoy, False, "近7日数据缺失，仅用去年同期"
    else:
        pred = w * yoy + (1 - w) * recent7 * (1 + trend / 100.0)
        degraded, reason = False, "正常（同比外推+趋势修正）"

    base_level = level_of_value(pred, p)
    # 升级口径（持续性确认）：近 exceed_days 日实测已持续达到"当前等级+1档"的阈值 → 升一档。
    # 阈值引用写入 basis（exceed_against），保证可解释：如"近3日实测持续≥50μg/L，关注→较大"。
    thr_keys = {0: "assess.level1_chla", 1: "assess.level2_chla", 2: "assess.level3_chla"}
    up_key = thr_keys.get(base_level)
    up_thr = float(p[up_key]) if up_key else None
    exceed_cnt = sum(1 for v in f["exceed_win"] if up_thr is not None and v >= up_thr) \
        if up_thr is not None else 0
    upgraded = (not degraded) and up_thr is not None and len(f["exceed_win"]) > 0 and \
        exceed_cnt >= f["exceed_days"]
    level = min(base_level + 1, 3) if upgraded else base_level

    halfwin = int(p["assess.yoy_halfwin"])
    conf = (0.55 if degraded
            else min(0.95, 0.60 + 0.25 * (f["recent7_n"] / 7.0)
                     + 0.10 * min(f["yoy_n"] / (2 * halfwin + 1), 1.0)))
    basis = {
        "params": {k: float(v) for k, v in p.items()},          # 参数快照 key:value
        "thresholds": {"level1": float(p["assess.level1_chla"]),
                       "level2": float(p["assess.level2_chla"]),
                       "level3": float(p["assess.level3_chla"])},
        "exceed_days_count": exceed_cnt, "exceed_window": f["exceed_days"],
        "exceed_against": up_key, "exceed_threshold": up_thr,
        "upgraded": upgraded, "degraded": degraded, "reason": reason,
        "yoy_samples": f["yoy_n"], "recent7_samples": f["recent7_n"],
        "yoy_deviation_pct": round((pred - yoy) / yoy * 100.0, 2) if yoy else None,
        "trend_pct": round(trend, 2),
    }
    return {"level": level, "predicted": round(pred, 2),
            "yoy": round(yoy, 2) if yoy is not None else None,
            "recent7": round(recent7, 2) if recent7 is not None else None,
            "trend": round(trend, 2), "basis": basis,
            "confidence": round(max(conf, 0.30), 2)}


def compute_assessment(series: dict[dt.date, float], d: dt.date, p: dict) -> dict:
    """一次完整研判（series 只需包含 d 之前的数据）。"""
    return decide(extract_features(series, d, p), p)


def load_series(db: Session, end_exclusive: dt.date, lookback_days: int = 800) -> dict[int, dict[dt.date, float]]:
    """zone_id -> {date: chla}（d 之前 lookback_days 天；tz_chla_obs 是事实底座）。"""
    since = end_exclusive - dt.timedelta(days=lookback_days)
    rows = (db.query(TzChlaObs)
            .filter(TzChlaObs.obs_date >= since, TzChlaObs.obs_date < end_exclusive).all())
    out: dict[int, dict[dt.date, float]] = {}
    for r in rows:
        out.setdefault(r.zone_id, {})[r.obs_date] = float(r.chla)
    return out


def assess_one(series: dict[dt.date, float], date: dt.date, p: dict,
               area_km2: float = 100.0) -> dict:
    """单区一次完整研判（纯计算）：定级 + basis（含概率预报第二意见）+ 吨位估计。

    assess_day（线上）与 seed（批量历史）共用，保证口径一致。
    """
    r = compute_assessment(series, date, p)
    fc = forecast_zone([v for _, v in sorted(series.items())][-180:])
    r["basis"]["forecast"] = {"model_version": FC_MODEL_VERSION,
                              "prob": fc["prob"], "q25": fc["q25"], "q50": fc["q50"],
                              "q75": fc["q75"], "bloom_threshold": fc["threshold"]}
    t25, t50 = est_tons(fc, area_km2)
    r.update({"forecast": fc, "tons_q25": t25, "tons_q50": t50, "prob": fc["prob"]})
    return r


def assess_day(db: Session, date: dt.date, created_at: dt.datetime | None = None) -> list[dict]:
    """对全部分区执行当日研判：upsert tz_assessment（assessment_id 稳定，供派单关联）+
    更新 tz_zone 缓存字段（prob_72h/tons/chla）。

    created_at 供 seed 回填历史研判时间戳（缺省 now）。
    """
    p = get_params(db)
    pv = current_param_version(db)
    zones = db.query(TzZone).all()
    series = load_series(db, date)
    now = created_at or dt.datetime.now()

    results = []
    for z in zones:
        s = series.get(z.zone_id, {})
        r = assess_one(s, date, p, float(z.area_km2 or 100))

        row = (db.query(TzAssessment)
               .filter(TzAssessment.assess_date == date, TzAssessment.zone_id == z.zone_id)
               .first())
        if row is None:
            row = TzAssessment(assess_date=date, zone_id=z.zone_id, created_at=now)
            db.add(row)
        row.level = str(r["level"])
        row.predicted = r["predicted"]
        row.yoy = r["yoy"]
        row.recent7 = r["recent7"]
        row.trend = r["trend"]
        row.basis = json.dumps(r["basis"], ensure_ascii=False)
        row.confidence = r["confidence"]
        row.model_version = MODEL_VERSION
        row.param_version = pv
        db.flush()  # 确保 assessment_id 可供 auto_dispatch 关联

        z.prob_72h = r["prob"]
        z.tons_q25, z.tons_q50 = r["tons_q25"], r["tons_q50"]
        if r["basis"].get("recent7_samples"):
            z.chla = r["recent7"]
        z.update_time = now

        results.append({"zone_id": z.zone_id, "zone_name": z.zone_name,
                        "zone_code": z.zone_code, "level": r["level"],
                        "level_name": LEVEL_NAMES[r["level"]],
                        "assessment_id": row.assessment_id,
                        "predicted": r["predicted"], "model_version": MODEL_VERSION,
                        "param_version": pv})
    db.commit()
    log.info("assess_day %s: %s", date, [(x["zone_code"], x["level"]) for x in results])
    return results
