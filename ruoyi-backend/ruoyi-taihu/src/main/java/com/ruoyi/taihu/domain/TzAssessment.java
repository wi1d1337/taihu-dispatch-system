package com.ruoyi.taihu.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;

/**
 * 研判记录对象 tz_assessment（algo 写入，Java 侧查询）
 *
 * @author taihu
 */
public class TzAssessment implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 研判ID */
    private Long assessmentId;

    /** 研判日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "研判日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date assessDate;

    /** 分区ID */
    @Excel(name = "分区ID")
    private Long zoneId;

    /** 等级（0无需干预 1关注 2较大 3重大） */
    @Excel(name = "等级", readConverterExp = "0=无需干预,1=关注,2=较大,3=重大")
    private String level;

    /** 同比外推预测Chl-a（ug/L） */
    @Excel(name = "预测Chl-a")
    private BigDecimal predicted;

    /** 去年同期值 */
    @Excel(name = "去年同期值")
    private BigDecimal yoy;

    /** 近7日均值 */
    @Excel(name = "近7日均值")
    private BigDecimal recent7;

    /** 近7日趋势% */
    @Excel(name = "近7日趋势%")
    private BigDecimal trend;

    /** 依据明细 JSON */
    private String basis;

    /** 置信度 */
    @Excel(name = "置信度")
    private BigDecimal confidence;

    /** 模型版本 */
    private String modelVersion;

    /** 参数版本 */
    private Integer paramVersion;

    /** 记录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 分区名（联表） */
    @Excel(name = "分区名")
    private String zoneName;

    /** 查询：起始日期（yyyy-MM-dd） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private String beginDate;

    /** 查询：截止日期（yyyy-MM-dd） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private String endDate;

    public void setAssessmentId(Long assessmentId)
    {
        this.assessmentId = assessmentId;
    }

    public Long getAssessmentId()
    {
        return assessmentId;
    }

    public void setAssessDate(Date assessDate)
    {
        this.assessDate = assessDate;
    }

    public Date getAssessDate()
    {
        return assessDate;
    }

    public void setZoneId(Long zoneId)
    {
        this.zoneId = zoneId;
    }

    public Long getZoneId()
    {
        return zoneId;
    }

    public void setLevel(String level)
    {
        this.level = level;
    }

    public String getLevel()
    {
        return level;
    }

    public void setPredicted(BigDecimal predicted)
    {
        this.predicted = predicted;
    }

    public BigDecimal getPredicted()
    {
        return predicted;
    }

    public void setYoy(BigDecimal yoy)
    {
        this.yoy = yoy;
    }

    public BigDecimal getYoy()
    {
        return yoy;
    }

    public void setRecent7(BigDecimal recent7)
    {
        this.recent7 = recent7;
    }

    public BigDecimal getRecent7()
    {
        return recent7;
    }

    public void setTrend(BigDecimal trend)
    {
        this.trend = trend;
    }

    public BigDecimal getTrend()
    {
        return trend;
    }

    public void setBasis(String basis)
    {
        this.basis = basis;
    }

    public String getBasis()
    {
        return basis;
    }

    public void setConfidence(BigDecimal confidence)
    {
        this.confidence = confidence;
    }

    public BigDecimal getConfidence()
    {
        return confidence;
    }

    public void setModelVersion(String modelVersion)
    {
        this.modelVersion = modelVersion;
    }

    public String getModelVersion()
    {
        return modelVersion;
    }

    public void setParamVersion(Integer paramVersion)
    {
        this.paramVersion = paramVersion;
    }

    public Integer getParamVersion()
    {
        return paramVersion;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setZoneName(String zoneName)
    {
        this.zoneName = zoneName;
    }

    public String getZoneName()
    {
        return zoneName;
    }

    public void setBeginDate(String beginDate)
    {
        this.beginDate = beginDate;
    }

    public String getBeginDate()
    {
        return beginDate;
    }

    public void setEndDate(String endDate)
    {
        this.endDate = endDate;
    }

    public String getEndDate()
    {
        return endDate;
    }
}
