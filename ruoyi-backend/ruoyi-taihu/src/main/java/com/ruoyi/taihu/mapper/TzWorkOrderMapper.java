package com.ruoyi.taihu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.taihu.domain.TzWorkOrder;

/**
 * 打捞工单 数据层
 *
 * @author taihu
 */
public interface TzWorkOrderMapper
{
    /**
     * 查询工单列表（date/zone/level/status/source/leader/crew 过滤，联表 zone_name/leader_name/boat_name）
     */
    public List<TzWorkOrder> selectWorkOrderList(TzWorkOrder tzWorkOrder);

    /**
     * 根据ID查询工单（联表）
     */
    public TzWorkOrder selectWorkOrderById(Long orderId);

    /**
     * 查询督办扫描范围：status∈{0,1,2,3} 的活跃工单（不带展示联表）
     */
    public List<TzWorkOrder> selectActiveOrders();

    /**
     * 查当日工单号最大序号前缀（order_no 生成用）：select max(order_no) where order_no like 'prefix%'
     */
    public String selectMaxOrderNo(@Param("prefix") String prefix);

    /**
     * 新增工单
     */
    public int insertWorkOrder(TzWorkOrder tzWorkOrder);

    /**
     * 修改工单
     */
    public int updateWorkOrder(TzWorkOrder tzWorkOrder);

    /**
     * 改派时清空船只/打捞员/接单时间（工单回待接单）
     */
    public int clearAssignment(TzWorkOrder tzWorkOrder);

    /**
     * 删除工单
     */
    public int deleteWorkOrderById(Long orderId);

    /**
     * 批量删除工单（连带删除 tz_order_log）
     */
    public int deleteWorkOrderByIds(Long[] orderIds);
}
