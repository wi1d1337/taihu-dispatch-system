package com.ruoyi.taihu.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;

/**
 * 算法参数注册表对象 tz_algo_param（读写走 algo API+审计；Java 侧直读供督办阈值等）
 *
 * @author taihu
 */
public class TzAlgoParam implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 参数ID */
    private Long paramId;

    /** 参数键（区分大小写） */
    @Excel(name = "参数键")
    private String paramKey;

    /** 当前值 */
    @Excel(name = "当前值")
    private BigDecimal paramValue;

    /** 下限 */
    @Excel(name = "下限")
    private BigDecimal minValue;

    /** 上限 */
    @Excel(name = "上限")
    private BigDecimal maxValue;

    /** 单位 */
    @Excel(name = "单位")
    private String unit;

    /** 业务含义 */
    @Excel(name = "业务含义")
    private String meaning;

    /** 更新人 */
    @Excel(name = "更新人")
    private String updatedBy;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "更新时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** 版本（每次修改+1） */
    @Excel(name = "版本")
    private Integer version;

    public void setParamId(Long paramId)
    {
        this.paramId = paramId;
    }

    public Long getParamId()
    {
        return paramId;
    }

    public void setParamKey(String paramKey)
    {
        this.paramKey = paramKey;
    }

    public String getParamKey()
    {
        return paramKey;
    }

    public void setParamValue(BigDecimal paramValue)
    {
        this.paramValue = paramValue;
    }

    public BigDecimal getParamValue()
    {
        return paramValue;
    }

    public void setMinValue(BigDecimal minValue)
    {
        this.minValue = minValue;
    }

    public BigDecimal getMinValue()
    {
        return minValue;
    }

    public void setMaxValue(BigDecimal maxValue)
    {
        this.maxValue = maxValue;
    }

    public BigDecimal getMaxValue()
    {
        return maxValue;
    }

    public void setUnit(String unit)
    {
        this.unit = unit;
    }

    public String getUnit()
    {
        return unit;
    }

    public void setMeaning(String meaning)
    {
        this.meaning = meaning;
    }

    public String getMeaning()
    {
        return meaning;
    }

    public void setUpdatedBy(String updatedBy)
    {
        this.updatedBy = updatedBy;
    }

    public String getUpdatedBy()
    {
        return updatedBy;
    }

    public void setUpdatedAt(Date updatedAt)
    {
        this.updatedAt = updatedAt;
    }

    public Date getUpdatedAt()
    {
        return updatedAt;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public Integer getVersion()
    {
        return version;
    }
}
