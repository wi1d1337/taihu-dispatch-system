package com.ruoyi.taihu.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 工单流转日志对象 tz_order_log（督办动作也记这：催办/改派/升级督办）
 *
 * @author taihu
 */
public class TzOrderLog implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 动作常量：派单（自动/人工/自动派单） */
    public static final String ACTION_DISPATCH = "派单";
    /** 动作常量：接单 */
    public static final String ACTION_ACCEPT = "接单";
    /** 动作常量：派船（片长确认船只+打捞员） */
    public static final String ACTION_ASSIGN_BOAT = "派船";
    /** 动作常量：到场 */
    public static final String ACTION_ARRIVE = "到场";
    /** 动作常量：提交 */
    public static final String ACTION_SUBMIT = "提交";
    /** 动作常量：核查 */
    public static final String ACTION_CHECK = "核查";
    /** 动作常量：办结 */
    public static final String ACTION_DONE = "办结";
    /** 动作常量：退回 */
    public static final String ACTION_REJECT = "退回";
    /** 动作常量：撤销 */
    public static final String ACTION_REVOKE = "撤销";
    /** 动作常量：催办（督办计时器） */
    public static final String ACTION_REMIND = "催办";
    /** 动作常量：改派（人工或督办自动） */
    public static final String ACTION_REASSIGN = "改派";
    /** 动作常量：升级督办 */
    public static final String ACTION_ESCALATE = "升级督办";

    /** 日志ID */
    private Long logId;

    /** 工单ID */
    private Long orderId;

    /** 动作 */
    private String action;

    /** 操作人（系统动作为 督办系统） */
    private String operator;

    /** 原状态 */
    private String fromStatus;

    /** 新状态 */
    private String toStatus;

    /** 说明 */
    private String note;

    /** 记录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 操作人姓名（联表 tz_worker，展示用） */
    private String operatorName;

    public void setLogId(Long logId)
    {
        this.logId = logId;
    }

    public Long getLogId()
    {
        return logId;
    }

    public void setOrderId(Long orderId)
    {
        this.orderId = orderId;
    }

    public Long getOrderId()
    {
        return orderId;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public String getAction()
    {
        return action;
    }

    public void setOperator(String operator)
    {
        this.operator = operator;
    }

    public String getOperator()
    {
        return operator;
    }

    public void setFromStatus(String fromStatus)
    {
        this.fromStatus = fromStatus;
    }

    public String getFromStatus()
    {
        return fromStatus;
    }

    public void setToStatus(String toStatus)
    {
        this.toStatus = toStatus;
    }

    public String getToStatus()
    {
        return toStatus;
    }

    public void setNote(String note)
    {
        this.note = note;
    }

    public String getNote()
    {
        return note;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setOperatorName(String operatorName)
    {
        this.operatorName = operatorName;
    }

    public String getOperatorName()
    {
        return operatorName;
    }
}
