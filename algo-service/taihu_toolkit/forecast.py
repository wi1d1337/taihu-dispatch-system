"""预报基线与事件检验（方案表 2、表 3）。

基线：持续性预报（persistence）、气候态（climatology）；可选梯度提升
（scikit-learn 存在时）。评测：POD / FAR / CSI（定义与方案表 3 一致）。
CNN-LSTM（方案表 2 主模型）在真实数据接入后替换 forecast() 即可，接口不变。
"""

from __future__ import annotations

import numpy as np
import pandas as pd


def make_features(df: pd.DataFrame, lags=(1, 3, 7)) -> pd.DataFrame:
    """第二章特征工程的最小集：原始量 + 滞后项 + 7 日滑动统计 + N:P 比。"""
    out = df.copy()
    for c in ("chla", "temp", "wind", "tp", "tn", "rad"):
        if c in out:
            for L in lags:
                out[f"{c}_lag{L}"] = out[c].shift(L)
            out[f"{c}_mean7"] = out[c].rolling(7).mean()
    if {"tn", "tp"} <= set(out.columns):
        out["n_p_ratio"] = (out["tn"] / 14.0) / (out["tp"] / 31.0)  # 摩尔比
    return out


def persistence_forecast(train: pd.Series, horizon: int = 3) -> np.ndarray:
    """持续性基线：未来 horizon 天 = 最后观测值。"""
    return np.full(horizon, float(train.iloc[-1]))


def climatology_forecast(train: pd.Series, dates, horizon: int = 3) -> np.ndarray:
    """气候态基线：同日历年均值。"""
    clim = train.groupby(train.index.dayofyear).mean()
    doy = pd.DatetimeIndex(dates).dayofyear
    return clim.reindex(doy).to_numpy()[:horizon]


def gbm_forecast(train_feats: pd.DataFrame, target: str, future_feats: pd.DataFrame):
    """梯度提升基线（可选依赖）。缺失 sklearn 时抛 RuntimeError。"""
    try:
        from sklearn.ensemble import GradientBoostingRegressor
    except ImportError as e:
        raise RuntimeError("scikit-learn 未安装：pip install scikit-learn") from e
    X = train_feats.drop(columns=[target, "zone"], errors="ignore").dropna()
    y = train_feats.loc[X.index, target]
    m = GradientBoostingRegressor(n_estimators=200, max_depth=3, random_state=0)
    m.fit(X, y)
    return m.predict(future_feats[X.columns])


def event_metrics(y_true: np.ndarray, y_pred: np.ndarray, threshold: float) -> dict:
    """POD / FAR / CSI（方案表 3）。命中=双超阈值；空报=预报超而实况未超。"""
    y_true = np.asarray(y_true, float)
    y_pred = np.asarray(y_pred, float)
    hit = int(((y_pred >= threshold) & (y_true >= threshold)).sum())
    miss = int(((y_pred < threshold) & (y_true >= threshold)).sum())
    fa = int(((y_pred >= threshold) & (y_true < threshold)).sum())
    pod = hit / (hit + miss) if hit + miss else np.nan
    far = fa / (hit + fa) if hit + fa else np.nan
    csi = hit / (hit + miss + fa) if hit + miss + fa else np.nan
    return {"hits": hit, "misses": miss, "false_alarms": fa, "POD": pod, "FAR": far, "CSI": csi}
