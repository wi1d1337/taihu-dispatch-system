package com.ruoyi.taihu.service;

import java.util.Date;
import java.util.List;
import com.ruoyi.taihu.domain.TzOrderLog;

/**
 * 工单流转日志 服务层
 *
 * @author taihu
 */
public interface ITzOrderLogService
{
    /**
     * 新增流转日志
     */
    public int insertLog(TzOrderLog tzOrderLog);

    /**
     * 查询工单流转时间线（时间升序）
     */
    public List<TzOrderLog> selectLogsByOrderId(Long orderId);

    /**
     * 最近一次指定动作时间（督办计时）
     */
    public Date selectLastActionTime(Long orderId, List<String> actions);
}
