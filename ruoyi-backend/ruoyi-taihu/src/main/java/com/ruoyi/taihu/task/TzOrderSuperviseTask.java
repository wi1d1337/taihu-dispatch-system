package com.ruoyi.taihu.task;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.taihu.domain.TzOrderLog;
import com.ruoyi.taihu.domain.TzWorkOrder;
import com.ruoyi.taihu.domain.TzWorker;
import com.ruoyi.taihu.mapper.TzOrderLogMapper;
import com.ruoyi.taihu.service.ITzAlgoParamService;
import com.ruoyi.taihu.service.ITzWorkOrderService;
import com.ruoyi.taihu.service.ITzWorkerService;

/**
 * 工单超时督办计时器（契约 §4：每 10 分钟扫描，不是状态，是计时器动作）
 *
 * 规则：
 * 1) status∈{0,1,2,3} 且超 supervise.remind_h 未动作 → 写「催办」日志（状态不变）；
 *    动作基线：0=派单时间、1=接单时间、2=到场时间、3=提交时间（缺省创建时间）。
 * 2) 催办后超 supervise.escalate_h 仍无动作：
 *    - status=0 → 同分区轮转改派其他片长（无则写「升级督办」）；改派重置基线，重新计催办；
 *    - status=2/3 → 写「升级督办」；status=1 契约未定，不升级。
 * 3) 阈值从 tz_algo_param 直读库（缺省 remind 2h / escalate 6h）。
 * 4) 每单独立 try/catch，单条异常不影响整批。
 *
 * @author taihu
 */
@Component
public class TzOrderSuperviseTask
{
    private static final Logger log = LoggerFactory.getLogger(TzOrderSuperviseTask.class);

    /** 督办系统操作人标识（写 tz_order_log.operator） */
    private static final String OPERATOR = "督办系统";

    /** 催办阈值参数键（小时） */
    private static final String KEY_REMIND_H = "supervise.remind_h";

    /** 升级阈值参数键（小时） */
    private static final String KEY_ESCALATE_H = "supervise.escalate_h";

    @Autowired
    private ITzWorkOrderService tzWorkOrderService;

    @Autowired
    private ITzWorkerService tzWorkerService;

    @Autowired
    private ITzAlgoParamService tzAlgoParamService;

    @Autowired
    private TzOrderLogMapper tzOrderLogMapper;

    /**
     * 每 10 分钟扫描一次
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void supervise()
    {
        long remindMs = hoursToMillis(tzAlgoParamService.getDecimalValue(KEY_REMIND_H, BigDecimal.valueOf(2)));
        long escalateMs = hoursToMillis(tzAlgoParamService.getDecimalValue(KEY_ESCALATE_H, BigDecimal.valueOf(6)));
        List<TzWorkOrder> actives = tzWorkOrderService.selectActiveOrders();
        int reminded = 0, escalated = 0, reassigned = 0;
        for (TzWorkOrder order : actives)
        {
            try
            {
                int result = superviseOrder(order, remindMs, escalateMs);
                if (result == 1)
                {
                    reminded++;
                }
                else if (result == 2)
                {
                    escalated++;
                }
                else if (result == 3)
                {
                    reassigned++;
                }
            }
            catch (Exception e)
            {
                log.error("工单[{}]督办处理异常：{}", order.getOrderNo(), e.getMessage(), e);
            }
        }
        if (reminded + escalated + reassigned > 0)
        {
            log.info("督办扫描完成：催办 {} 单，升级督办 {} 单，自动改派 {} 单", reminded, escalated, reassigned);
        }
    }

    /**
     * 单工单督办：返回 0=无动作 1=催办 2=升级督办 3=自动改派
     */
    private int superviseOrder(TzWorkOrder order, long remindMs, long escalateMs)
    {
        Date now = DateUtils.getNowDate();
        Date baseline = actionBaseline(order);
        Date lastRemind = staleSafe(tzOrderLogMapper.selectLastActionTime(order.getOrderId(),
                Arrays.asList(TzOrderLog.ACTION_REMIND)), baseline);
        Date lastEscalate = staleSafe(tzOrderLogMapper.selectLastActionTime(order.getOrderId(),
                Arrays.asList(TzOrderLog.ACTION_ESCALATE)), baseline);

        // 阶段一：未催办且超 remind 阈值 → 催办（状态不变）
        if (lastRemind == null)
        {
            if (now.getTime() - baseline.getTime() > remindMs)
            {
                insertLog(order, TzOrderLog.ACTION_REMIND,
                        "超 " + msToHoursText(remindMs) + " 小时未动作，系统催办");
                return 1;
            }
            return 0;
        }

        // 阶段二：已催办且超 escalate 阈值仍无动作
        if (now.getTime() - lastRemind.getTime() <= escalateMs)
        {
            return 0;
        }
        // 本轮催办后已升级过 → 不重复
        if (lastEscalate != null && lastEscalate.after(lastRemind))
        {
            return 0;
        }
        String status = order.getStatus();
        if (TzWorkOrder.STATUS_PENDING.equals(status))
        {
            // 待接单超时：同分区轮转改派其他片长
            TzWorker candidate = tzWorkerService.selectCandidateLeader(order.getZoneId(), order.getLeaderId());
            if (candidate != null)
            {
                tzWorkOrderService.reassignOrder(order.getOrderId(), candidate.getWorkerId(), OPERATOR,
                        "催办超时未接单，督办系统自动改派");
                return 3;
            }
            insertLog(order, TzOrderLog.ACTION_ESCALATE,
                    "催办超时未接单且同分区无其他在岗片长，升级督办（片长[" + order.getLeaderId() + "]）");
            return 2;
        }
        if (TzWorkOrder.STATUS_WORKING.equals(status) || TzWorkOrder.STATUS_SUBMITTED.equals(status))
        {
            insertLog(order, TzOrderLog.ACTION_ESCALATE,
                    "催办超时仍未" + (TzWorkOrder.STATUS_WORKING.equals(status) ? "提交完成" : "核查办结") + "，升级督办");
            return 2;
        }
        // status=1 已接单：契约未定义升级动作，仅维持催办记录
        return 0;
    }

    /**
     * 动作基线：0=派单 1=接单 2=到场 3=提交（缺省创建时间）
     */
    private Date actionBaseline(TzWorkOrder order)
    {
        switch (order.getStatus())
        {
            case TzWorkOrder.STATUS_PENDING: return nvl(order.getDispatchedAt(), order.getCreateTime());
            case TzWorkOrder.STATUS_ACCEPTED: return nvl(order.getAcceptedAt(), order.getCreateTime());
            case TzWorkOrder.STATUS_WORKING: return nvl(order.getArrivedAt(), order.getCreateTime());
            case TzWorkOrder.STATUS_SUBMITTED: return nvl(order.getSubmittedAt(), order.getCreateTime());
            default: return nvl(order.getCreateTime(), new Date());
        }
    }

    /**
     * 基线之前的动作视为上一轮周期残留（如改派前的催办），作废
     */
    private Date staleSafe(Date actionTime, Date baseline)
    {
        if (actionTime == null || (baseline != null && actionTime.before(baseline)))
        {
            return null;
        }
        return actionTime;
    }

    /**
     * 写督办日志（状态不变）
     */
    private void insertLog(TzWorkOrder order, String action, String note)
    {
        TzOrderLog logEntry = new TzOrderLog();
        logEntry.setOrderId(order.getOrderId());
        logEntry.setAction(action);
        logEntry.setOperator(OPERATOR);
        logEntry.setFromStatus(order.getStatus());
        logEntry.setToStatus(order.getStatus());
        logEntry.setNote(note);
        tzOrderLogMapper.insertOrderLog(logEntry);
        log.info("工单[{}] {}：{}", order.getOrderNo(), action, note);
    }

    private Date nvl(Date value, Date fallback)
    {
        return value == null ? fallback : value;
    }

    private long hoursToMillis(BigDecimal hours)
    {
        return (long) (hours.doubleValue() * 3600_000L);
    }

    private String msToHoursText(long ms)
    {
        return BigDecimal.valueOf(ms).divide(BigDecimal.valueOf(3600_000L)).stripTrailingZeros().toPlainString();
    }
}
