"""预报模块（演示级）：基于历史 Chl-a 序列的持续性 + 分位数预报。

口径与 taihu_toolkit.forecast 一致（持续性/气候态基线）；真实部署按技术实施方案
3.2 节替换为 CNN-LSTM（接口不变：输入历史序列，输出概率与三分位）。
"""

from __future__ import annotations

import datetime as dt

import numpy as np
from sqlalchemy.orm import Session

from .models import TzForecastRecord, TzZone

MODEL_VERSION = "synth-persist-v1"
BLOOM_THRESHOLD_Q = 0.85  # 事件阈值分位（演示口径，真实口径由共建评测集定义）


def forecast_zone(history: np.ndarray) -> dict:
    """history: 某分区历史 Chl-a 日序列（升序时间）。返回 {prob, q25, q50, q75}。"""
    h = np.asarray(history, float)
    h = h[np.isfinite(h)]
    if len(h) < 30:
        h = np.concatenate([np.full(30 - len(h), 5.0), h])
    thr = float(np.quantile(h, BLOOM_THRESHOLD_Q))
    recent = h[-14:]
    q50 = float(np.median(recent))                      # 持续性中心
    q25 = float(np.quantile(recent, 0.25))              # 分位区间
    q75 = float(np.quantile(recent, 0.75))
    exceed = float((recent[-7:] >= thr).mean())          # 近 7 日超阈频率
    # 概率 = 超阈频率 × 当前强度 混合（保证分区之间有色阶差异，演示口径）
    intensity = min(max((q50 - 0.6 * thr) / max(thr, 1.0), 0.0), 1.0)
    prob = round(min(max(0.5 * exceed + 0.5 * intensity, 0.02), 0.98), 2)
    return {"prob": prob, "q25": round(max(q25, 0.5), 1), "q50": round(max(q50, 0.5), 1),
            "q75": round(max(q75, 0.5), 1), "threshold": round(thr, 1)}


def run_forecast(db: Session, fc_date: dt.date, history: dict[int, list[float]]) -> list[dict]:
    """写 tz_forecast_record 并更新 tz_zone 概率/吨位。history: zone_id -> Chl-a 序列。"""
    out = []
    for zone_id, seq in history.items():
        f = forecast_zone(seq)
        db.add(TzForecastRecord(zone_id=zone_id, fc_date=fc_date, horizon_h=72,
                                prob=f["prob"], chla_q25=f["q25"], chla_q50=f["q50"], chla_q75=f["q75"]))
        zone = db.get(TzZone, zone_id)
        if zone:
            # 可收藻量演示口径：与 Chl-a 超过阈值的强度成正比，乘分区面积系数
            intensity = max(0.0, f["q50"] - 0.6 * f["threshold"]) / max(f["threshold"], 1.0)
            base = float(zone.area_km2 or 100) / 100.0
            tons50 = round(min(120.0 * intensity * base, 90.0 * base), 1)
            tons25 = round(tons50 * 0.6, 1)
            zone.prob_72h = f["prob"]
            zone.tons_q25 = tons25
            zone.tons_q50 = tons50
            zone.chla = f["q50"]
            zone.update_time = dt.datetime.now()
        out.append({"zone_id": zone_id, **f})
    db.commit()
    return out
