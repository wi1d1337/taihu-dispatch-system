package com.ruoyi.taihu.service.impl;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.taihu.domain.TzDispatchOrder;
import com.ruoyi.taihu.mapper.TzDispatchOrderMapper;
import com.ruoyi.taihu.service.ITzDispatchOrderService;

/**
 * 调度单 服务层实现
 *
 * 状态机：0待确认 → 1已确认 → 3已完成；0 → 2已驳回。
 * 非法流转一律抛 ServiceException，由全局异常处理器转为错误提示。
 *
 * @author taihu
 */
@Service
public class TzDispatchOrderServiceImpl implements ITzDispatchOrderService
{
    @Autowired
    private TzDispatchOrderMapper tzDispatchOrderMapper;

    @Override
    public List<TzDispatchOrder> selectDispatchOrderList(TzDispatchOrder tzDispatchOrder)
    {
        return tzDispatchOrderMapper.selectDispatchOrderList(tzDispatchOrder);
    }

    @Override
    public TzDispatchOrder selectDispatchOrderById(Long orderId)
    {
        return tzDispatchOrderMapper.selectDispatchOrderById(orderId);
    }

    @Override
    public int insertDispatchOrder(TzDispatchOrder tzDispatchOrder)
    {
        tzDispatchOrder.setCreateTime(DateUtils.getNowDate());
        return tzDispatchOrderMapper.insertDispatchOrder(tzDispatchOrder);
    }

    @Override
    public int updateDispatchOrder(TzDispatchOrder tzDispatchOrder)
    {
        tzDispatchOrder.setUpdateTime(DateUtils.getNowDate());
        return tzDispatchOrderMapper.updateDispatchOrder(tzDispatchOrder);
    }

    @Override
    public int deleteDispatchOrderByIds(Long[] orderIds)
    {
        return tzDispatchOrderMapper.deleteDispatchOrderByIds(orderIds);
    }

    @Override
    @Transactional
    public int confirmOrder(Long orderId, String operator)
    {
        TzDispatchOrder order = requireOrder(orderId);
        if (!STATUS_PENDING.equals(order.getStatus()))
        {
            throw new ServiceException(String.format("调度单[%d]当前状态为[%s]，仅待确认状态可确认", orderId, statusName(order.getStatus())));
        }
        TzDispatchOrder update = new TzDispatchOrder();
        update.setOrderId(orderId);
        update.setStatus(STATUS_CONFIRMED);
        update.setUpdateBy(operator);
        update.setUpdateTime(DateUtils.getNowDate());
        return tzDispatchOrderMapper.updateDispatchOrder(update);
    }

    @Override
    @Transactional
    public int rejectOrder(Long orderId, String remark, String operator)
    {
        if (StringUtils.isEmpty(remark))
        {
            throw new ServiceException("驳回原因不能为空");
        }
        TzDispatchOrder order = requireOrder(orderId);
        if (!STATUS_PENDING.equals(order.getStatus()))
        {
            throw new ServiceException(String.format("调度单[%d]当前状态为[%s]，仅待确认状态可驳回", orderId, statusName(order.getStatus())));
        }
        TzDispatchOrder update = new TzDispatchOrder();
        update.setOrderId(orderId);
        update.setStatus(STATUS_REJECTED);
        update.setRemark(remark);
        update.setUpdateBy(operator);
        update.setUpdateTime(DateUtils.getNowDate());
        return tzDispatchOrderMapper.updateDispatchOrder(update);
    }

    @Override
    @Transactional
    public int doneOrder(Long orderId, BigDecimal actualTons, String operator)
    {
        TzDispatchOrder order = requireOrder(orderId);
        if (!STATUS_CONFIRMED.equals(order.getStatus()))
        {
            throw new ServiceException(String.format("调度单[%d]当前状态为[%s]，仅已确认状态可完成", orderId, statusName(order.getStatus())));
        }
        // 实收缺省取台账汇总；无台账时保持空值（区别于"实收为0"）
        if (actualTons == null)
        {
            actualTons = tzDispatchOrderMapper.sumActualTonsByOrderId(orderId);
        }
        TzDispatchOrder update = new TzDispatchOrder();
        update.setOrderId(orderId);
        update.setStatus(STATUS_DONE);
        update.setActualTons(actualTons);
        update.setUpdateBy(operator);
        update.setUpdateTime(DateUtils.getNowDate());
        return tzDispatchOrderMapper.updateDispatchOrder(update);
    }

    /**
     * 按ID取调度单，不存在时抛异常
     */
    private TzDispatchOrder requireOrder(Long orderId)
    {
        TzDispatchOrder order = tzDispatchOrderMapper.selectDispatchOrderById(orderId);
        if (order == null)
        {
            throw new ServiceException(String.format("调度单[%d]不存在", orderId));
        }
        return order;
    }

    /**
     * 状态码转名称（错误提示用）
     */
    private String statusName(String status)
    {
        switch (status == null ? "" : status)
        {
            case STATUS_PENDING:
                return "待确认";
            case STATUS_CONFIRMED:
                return "已确认";
            case STATUS_REJECTED:
                return "已驳回";
            case STATUS_DONE:
                return "已完成";
            default:
                return "未知(" + status + ")";
        }
    }
}
