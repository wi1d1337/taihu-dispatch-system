package com.ruoyi.taihu.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.taihu.domain.TzOrderLog;

/**
 * 工单流转日志 数据层
 *
 * @author taihu
 */
public interface TzOrderLogMapper
{
    /**
     * 新增流转日志
     */
    public int insertOrderLog(TzOrderLog tzOrderLog);

    /**
     * 查询工单流转时间线（按时间升序）
     */
    public List<TzOrderLog> selectLogsByOrderId(Long orderId);

    /**
     * 查询工单最近一次指定动作的发生时间（督办计时用）
     *
     * @param orderId 工单ID
     * @param actions 动作集合（催办/升级督办…）
     * @return 最近一次时间，无则 null
     */
    public Date selectLastActionTime(@Param("orderId") Long orderId, @Param("actions") List<String> actions);

    /**
     * 删除工单日志（删单连带）
     */
    public int deleteLogsByOrderIds(@Param("orderIds") Long[] orderIds);
}
