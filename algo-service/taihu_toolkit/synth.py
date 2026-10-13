"""合成太湖风格多源日序列（演示用，非真实观测）。

模拟结构：水温/辐射季节项 + 风速年际静风化趋势 + TP/TN 缓降 + 由式 2 生长率驱动、
受静风聚积放大的 Chl-a 动态，六分区各自加扰动。用于算法试用与回归测试。
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from .growth import growth_rate

ZONES = ["梅梁湾", "竺山湖", "贡湖", "湖心", "西部沿岸", "南部沿岸"]


def synth_taihu(days: int = 1095, seed: int = 0, zones=None) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    zones = zones or ZONES
    t = np.arange(days)
    doy = t % 365
    frames = []
    for zi, z in enumerate(zones):
        zr = np.random.default_rng(seed * 100 + zi)
        temp = 16.5 + 9.0 * np.sin(2 * np.pi * (doy - 110) / 365) + zr.normal(0, 1.2, days)
        wind = np.clip(
            3.3 - 0.5 * (t / 365.0) + 0.7 * np.sin(2 * np.pi * (doy - 30) / 365) + zr.normal(0, 0.9, days),
            0.2, None,
        )  # 年均风速逐年下降（静风化）
        tp = np.clip(0.115 - 0.012 * (t / 365.0) + zr.normal(0, 0.012, days), 0.02, None)
        tn = np.clip(2.2 - 0.18 * (t / 365.0) + zr.normal(0, 0.25, days), 0.4, None)
        rad = np.clip(180 + 90 * np.sin(2 * np.pi * (doy - 80) / 365) + zr.normal(0, 25, days), 10, None)

        mu = growth_rate(temp, rad, tn, tp)
        chla = np.zeros(days)
        chla[0] = 8.0
        seed_bank = np.clip(4 + 2 * np.sin(2 * np.pi * (doy - 60) / 365), 1.0, None)  # 越冬种源
        zone_gain = 0.8 + 0.5 * zi / max(len(zones) - 1, 1)  # 北部湾湾更肥
        for k in range(1, days):
            calm = 1.0 + 1.6 * max(0.0, 0.4 - wind[k - 1])  # 静风聚积放大
            chla[k] = np.clip(
                chla[k - 1] * (1 + mu[k - 1] * 0.35 * calm * zone_gain - 0.14)
                + 0.15 * seed_bank[k]
                + zr.normal(0, 1.5),
                0.5, 350.0,
            )
        frames.append(pd.DataFrame(
            {"zone": z, "temp": temp, "wind": wind, "tp": tp, "tn": tn, "rad": rad, "chla": chla},
            index=pd.date_range("2023-01-01", periods=days, freq="D"),
        ))
    return pd.concat(frames)


def zonal_loads() -> dict:
    """演示用分区参数：各分区预期收藻量（吨/日，保守分位）与离母港距离（km）。"""
    return {
        "梅梁湾": {"dist_km": 18.0, "tons_q25": 42.0, "tons_q50": 60.0},
        "竺山湖": {"dist_km": 26.0, "tons_q25": 30.0, "tons_q50": 45.0},
        "贡湖": {"dist_km": 15.0, "tons_q25": 22.0, "tons_q50": 33.0},
        "湖心": {"dist_km": 34.0, "tons_q25": 18.0, "tons_q50": 30.0},
        "西部沿岸": {"dist_km": 30.0, "tons_q25": 26.0, "tons_q50": 40.0},
        "南部沿岸": {"dist_km": 24.0, "tons_q25": 12.0, "tons_q50": 20.0},
    }


def fleet() -> list:
    """演示用船队参数。capacity: 吨/日；speed: km/h；fuel_per_km: L/km。"""
    return [
        {"name": "打捞-01", "capacity": 45.0, "speed": 12.0, "fuel_per_km": 0.55},
        {"name": "打捞-02", "capacity": 45.0, "speed": 12.0, "fuel_per_km": 0.55},
        {"name": "机动-03", "capacity": 30.0, "speed": 18.0, "fuel_per_km": 0.38},
        {"name": "机动-04", "capacity": 30.0, "speed": 18.0, "fuel_per_km": 0.38},
        {"name": "小型-05", "capacity": 15.0, "speed": 20.0, "fuel_per_km": 0.22},
        {"name": "小型-06", "capacity": 15.0, "speed": 20.0, "fuel_per_km": 0.22},
        {"name": "小型-07", "capacity": 15.0, "speed": 20.0, "fuel_per_km": 0.22},
        {"name": "小型-08", "capacity": 15.0, "speed": 20.0, "fuel_per_km": 0.22},
    ]


def separation_stations() -> dict:
    """演示用藻水分离站：单日处理能力（吨藻当量/日）。"""
    return {"分离站-马山": 120.0, "分离站-华庄": 90.0, "分离站-南泉": 60.0}
