package com.ruoyi.taihu.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;

/**
 * 参数变更审计对象 tz_param_log（algo 侧写入，Java 侧只读查询）
 *
 * @author taihu
 */
public class TzParamLog implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long logId;

    /** 参数键 */
    @Excel(name = "参数键")
    private String paramKey;

    /** 原值 */
    @Excel(name = "原值")
    private BigDecimal oldValue;

    /** 新值 */
    @Excel(name = "新值")
    private BigDecimal newValue;

    /** 原版本 */
    @Excel(name = "原版本")
    private Integer oldVersion;

    /** 新版本 */
    @Excel(name = "新版本")
    private Integer newVersion;

    /** 触发（1人工 2自动调参 3自动建议待确认） */
    @Excel(name = "触发", readConverterExp = "1=人工,2=自动调参,3=自动建议待确认")
    private String trigger;

    /** 证据 JSON（窗口命中率/POD/FAR/MAE/候选对比/理由） */
    private String evidence;

    /** 操作人 */
    @Excel(name = "操作人")
    private String operator;

    /** 记录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "记录时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    public void setLogId(Long logId)
    {
        this.logId = logId;
    }

    public Long getLogId()
    {
        return logId;
    }

    public void setParamKey(String paramKey)
    {
        this.paramKey = paramKey;
    }

    public String getParamKey()
    {
        return paramKey;
    }

    public void setOldValue(BigDecimal oldValue)
    {
        this.oldValue = oldValue;
    }

    public BigDecimal getOldValue()
    {
        return oldValue;
    }

    public void setNewValue(BigDecimal newValue)
    {
        this.newValue = newValue;
    }

    public BigDecimal getNewValue()
    {
        return newValue;
    }

    public void setOldVersion(Integer oldVersion)
    {
        this.oldVersion = oldVersion;
    }

    public Integer getOldVersion()
    {
        return oldVersion;
    }

    public void setNewVersion(Integer newVersion)
    {
        this.newVersion = newVersion;
    }

    public Integer getNewVersion()
    {
        return newVersion;
    }

    public void setTrigger(String trigger)
    {
        this.trigger = trigger;
    }

    public String getTrigger()
    {
        return trigger;
    }

    public void setEvidence(String evidence)
    {
        this.evidence = evidence;
    }

    public String getEvidence()
    {
        return evidence;
    }

    public void setOperator(String operator)
    {
        this.operator = operator;
    }

    public String getOperator()
    {
        return operator;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }
}
