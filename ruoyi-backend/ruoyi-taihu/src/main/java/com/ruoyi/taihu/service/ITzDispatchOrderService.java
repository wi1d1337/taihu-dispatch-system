package com.ruoyi.taihu.service;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.taihu.domain.TzDispatchOrder;

/**
 * 调度单 服务层
 *
 * 状态机：0待确认 → 1已确认 → 3已完成；0 → 2已驳回
 *
 * @author taihu
 */
public interface ITzDispatchOrderService
{
    /** 状态：待确认 */
    public static final String STATUS_PENDING = "0";
    /** 状态：已确认 */
    public static final String STATUS_CONFIRMED = "1";
    /** 状态：已驳回 */
    public static final String STATUS_REJECTED = "2";
    /** 状态：已完成 */
    public static final String STATUS_DONE = "3";

    /**
     * 查询调度单列表
     *
     * @param tzDispatchOrder 调度单
     * @return 调度单集合
     */
    public List<TzDispatchOrder> selectDispatchOrderList(TzDispatchOrder tzDispatchOrder);

    /**
     * 根据ID查询调度单
     *
     * @param orderId 调度单ID
     * @return 调度单
     */
    public TzDispatchOrder selectDispatchOrderById(Long orderId);

    /**
     * 新增调度单
     *
     * @param tzDispatchOrder 调度单
     * @return 结果
     */
    public int insertDispatchOrder(TzDispatchOrder tzDispatchOrder);

    /**
     * 修改调度单
     *
     * @param tzDispatchOrder 调度单
     * @return 结果
     */
    public int updateDispatchOrder(TzDispatchOrder tzDispatchOrder);

    /**
     * 批量删除调度单
     *
     * @param orderIds 需要删除的调度单ID
     * @return 结果
     */
    public int deleteDispatchOrderByIds(Long[] orderIds);

    /**
     * 确认调度单（0待确认 → 1已确认）
     *
     * @param orderId 调度单ID
     * @param operator 操作人
     * @return 结果
     */
    public int confirmOrder(Long orderId, String operator);

    /**
     * 驳回调度单（0待确认 → 2已驳回，remark 必填）
     *
     * @param orderId 调度单ID
     * @param remark 驳回原因（必填）
     * @param operator 操作人
     * @return 结果
     */
    public int rejectOrder(Long orderId, String remark, String operator);

    /**
     * 完成调度单（1已确认 → 3已完成）
     *
     * @param orderId 调度单ID
     * @param actualTons 实收吨数（选填，缺省取台账汇总）
     * @param operator 操作人
     * @return 结果
     */
    public int doneOrder(Long orderId, BigDecimal actualTons, String operator);
}
