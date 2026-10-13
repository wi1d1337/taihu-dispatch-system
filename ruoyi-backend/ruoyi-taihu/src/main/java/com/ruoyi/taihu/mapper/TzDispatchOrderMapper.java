package com.ruoyi.taihu.mapper;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.taihu.domain.TzDispatchOrder;

/**
 * 调度单 数据层
 *
 * @author taihu
 */
public interface TzDispatchOrderMapper
{
    /**
     * 查询调度单列表（联表带出船名/分区名/分离站名）
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
     * 删除调度单
     *
     * @param orderId 调度单ID
     * @return 结果
     */
    public int deleteDispatchOrderById(Long orderId);

    /**
     * 批量删除调度单
     *
     * @param orderIds 需要删除的数据ID
     * @return 结果
     */
    public int deleteDispatchOrderByIds(Long[] orderIds);

    /**
     * 汇总某调度单关联台账的实收吨数（完成时缺省回填口径）
     *
     * @param orderId 调度单ID
     * @return 实收吨数合计（无台账时为 null）
     */
    public BigDecimal sumActualTonsByOrderId(@Param("orderId") Long orderId);
}
