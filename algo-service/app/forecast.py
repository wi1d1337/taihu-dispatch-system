"""概率预报（第二意见，v2 保留改造）。

v1 写 tz_forecast_record 的部分已下线（表废弃）；v2 由 assess 调 forecast_zones()
取 {prob, q25, q50, q75}，随 tz_assessment.basis 一并存档、/assess/today 并列返回。
口径与 taihu_toolkit.forecast 一致（持续性 + 分位数基线）；真实部署按技术实施方案
3.2 节替换为 CNN-LSTM（接口不变：输入历史序列，输出概率与三分位）。
"""

from __future__ import annotations

import numpy as np

MODEL_VERSION = "synth-persist-v1"   # 概率预报模型版本（与研判主线的 model_version 分开携带）
BLOOM_THRESHOLD_Q = 0.85             # 事件阈值分位（演示口径，真实口径由共建评测集定义）


def forecast_zone(history) -> dict:
    """history: 某分区历史 Chl-a 日序列（升序时间）。返回 {prob, q25, q50, q75, threshold}。"""
    h = np.asarray(history, float)
    h = h[np.isfinite(h)]
    if len(h) < 30:
        h = np.concatenate([np.full(30 - len(h), 5.0), h])
    thr = float(np.quantile(h, BLOOM_THRESHOLD_Q))
    recent = h[-14:]
    q50 = float(np.median(recent))                       # 持续性中心
    q25 = float(np.quantile(recent, 0.25))               # 分位区间
    q75 = float(np.quantile(recent, 0.75))
    exceed = float((recent[-7:] >= thr).mean())          # 近 7 日超阈频率
    # 概率 = 超阈频率 × 当前强度 混合（保证分区之间有色阶差异，演示口径）
    intensity = min(max((q50 - 0.6 * thr) / max(thr, 1.0), 0.0), 1.0)
    prob = round(min(max(0.5 * exceed + 0.5 * intensity, 0.02), 0.98), 2)
    return {"prob": prob, "q25": round(max(q25, 0.5), 1), "q50": round(max(q50, 0.5), 1),
            "q75": round(max(q75, 0.5), 1), "threshold": round(thr, 1)}


def forecast_zones(history: dict[int, list]) -> dict[int, dict]:
    """history: zone_id -> Chl-a 序列。返回 zone_id -> 预报字典（不落库）。"""
    return {zid: forecast_zone(seq) for zid, seq in history.items()}


def est_tons(f: dict, area_km2: float) -> tuple[float, float]:
    """可收藻量演示口径（与 v1 一致）：超阈强度 × 分区面积系数 → (tons_q25, tons_q50)。

    CP-SAT 推荐派船（solve.recommend_boats）的分区需求量用 tons_q50。
    """
    intensity = max(0.0, f["q50"] - 0.6 * f["threshold"]) / max(f["threshold"], 1.0)
    base = float(area_km2 or 100) / 100.0
    tons50 = round(min(120.0 * intensity * base, 90.0 * base), 1)
    return round(tons50 * 0.6, 1), tons50
