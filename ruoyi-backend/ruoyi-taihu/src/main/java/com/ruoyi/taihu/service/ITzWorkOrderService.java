package com.ruoyi.taihu.service;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.taihu.domain.TzWorkOrder;

/**
 * 打捞工单 服务层（状态机校验在本层）
 *
 * 状态机（契约 §1/§4）：
 * 人工创建→0；revoke：0/1→6；reassign：0/1→0（换片长，清船/人）；
 * H5 accept：0→1（片长）；arrive：1→2（打捞员）；submit：2→3 或 5→3（整改重报）；
 * check：3→4（合格）/ 3→5（不合格退回）。非法流转抛 ServiceException。
 *
 * @author taihu
 */
public interface ITzWorkOrderService
{
    /**
     * 查询工单列表（date/zone/level/status 过滤，联表）
     */
    public List<TzWorkOrder> selectWorkOrderList(TzWorkOrder tzWorkOrder);

    /**
     * 根据ID查询工单（联表）
     */
    public TzWorkOrder selectWorkOrderById(Long orderId);

    /**
     * 督办扫描：status∈{0,1,2,3} 的活跃工单
     */
    public List<TzWorkOrder> selectActiveOrders();

    /**
     * 人工创建工单（source=2，status=0）：生成工单号/处置时限，片长缺省取分区首位片长，记「派单」日志
     */
    public int insertWorkOrder(TzWorkOrder tzWorkOrder);

    /**
     * 撤销工单：status∈{0,1}→6，revokeReason 必填
     */
    public int revokeOrder(Long orderId, String revokeReason, String operator);

    /**
     * 改派片长：status∈{0,1}→0，清船只/打捞员，记「改派」日志
     *
     * @param note 日志说明（人工改派/督办超时自动改派）
     */
    public int reassignOrder(Long orderId, Long leaderId, String operator, String note);

    /**
     * 核查：status=3，pass=true→4 / false→5（checkComment 写退回意见）
     */
    public int checkOrder(Long orderId, boolean pass, String checkComment, String operator);

    /**
     * H5 片长接单：status=0→1，指定 boatId+crewIds（boatId 空时默认 suggest_boats 首条），记「接单」+「派船」日志
     */
    public int acceptOrder(Long orderId, Long boatId, String crewIds, String operator);

    /**
     * H5 打捞员到场打卡：status=1→2，记坐标与「到场」日志
     */
    public int arriveOrder(Long orderId, BigDecimal lng, BigDecimal lat, String operator);

    /**
     * H5 打捞员提交完成：status=2→3（退回整改 5→3 重报），actualTons 必填，记「提交」日志
     */
    public int submitOrder(Long orderId, BigDecimal actualTons, String photoUrls, String operator);

    /**
     * 批量删除工单（连带删除流转日志；仅终态 4/6 建议删除）
     */
    public int deleteWorkOrderByIds(Long[] orderIds);
}
