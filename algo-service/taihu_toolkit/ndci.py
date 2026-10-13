"""遥感反演：NDCI 指数与 Chl-a 经验式（方案式 4、式 5）。

哨兵-2 对应波段：R665 ≈ B4（红），R708 ≈ B5（红边）。
系数 a、b 必须用遥感-地面同步实测标定（留一交叉验证），下列默认值仅作演示。
"""

from __future__ import annotations

import numpy as np

DEFAULT_A, DEFAULT_B = 25.7, 2.4  # 演示值；真实使用须标定


def ndci(r708, r665) -> np.ndarray:
    """归一化差值叶绿素指数 NDCI = (R708 - R665) / (R708 + R665)。"""
    r708 = np.clip(np.asarray(r708, float), 1e-9, None)
    r665 = np.clip(np.asarray(r665, float), 1e-9, None)
    return (r708 - r665) / (r708 + r665)


def chla_from_ndci(x, a: float = DEFAULT_A, b: float = DEFAULT_B) -> np.ndarray:
    """Chl-a = a·exp(b·NDCI)，单位 ug/L（式 5，系数需标定）。"""
    return a * np.exp(b * np.clip(np.asarray(x, float), -0.6, 0.6))
