package com.ruoyi.taihu.domain;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 调度单对象 tz_dispatch_order
 *
 * 状态机：0待确认 → 1已确认 → 3已完成；0 → 2已驳回
 *
 * @author taihu
 */
public class TzDispatchOrder extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 调度单ID */
    private Long orderId;

    /** 作业日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @NotNull(message = "作业日期不能为空")
    @Excel(name = "作业日期", width = 12, dateFormat = "yyyy-MM-dd")
    private Date orderDate;

    /** 半天（0上午 1下午） */
    @NotBlank(message = "上下午不能为空")
    @Excel(name = "半天", readConverterExp = "0=上午,1=下午")
    private String halfDay;

    /** 船只ID */
    @NotNull(message = "船只不能为空")
    private Long boatId;

    /** 分区ID */
    @NotNull(message = "分区不能为空")
    private Long zoneId;

    /** 分离站ID */
    private Long stationId;

    /** 建议预计收藻（吨） */
    @Excel(name = "计划收藻")
    private BigDecimal planTons;

    /** 实收（完成时回填） */
    @Excel(name = "实收")
    private BigDecimal actualTons;

    /** 状态（0待确认 1已确认 2已驳回 3已完成） */
    @Excel(name = "状态", readConverterExp = "0=待确认,1=已确认,2=已驳回,3=已完成")
    private String status;

    /** 求解器（CP_SAT/GREEDY） */
    private String solver;

    /** 目标函数值 */
    private BigDecimal objective;

    /** 模型版本 */
    private String modelVersion;

    /** 管线运行ID */
    private Long runId;

    // ---- 以下为联表展示字段（非表字段） ----

    /** 船名（联表） */
    @Excel(name = "船名")
    private String boatName;

    /** 分区名（联表） */
    @Excel(name = "分区")
    private String zoneName;

    /** 分离站名（联表） */
    @Excel(name = "分离站")
    private String stationName;

    public void setOrderId(Long orderId)
    {
        this.orderId = orderId;
    }

    public Long getOrderId()
    {
        return orderId;
    }

    public void setOrderDate(Date orderDate)
    {
        this.orderDate = orderDate;
    }

    public Date getOrderDate()
    {
        return orderDate;
    }

    public void setHalfDay(String halfDay)
    {
        this.halfDay = halfDay;
    }

    public String getHalfDay()
    {
        return halfDay;
    }

    public void setBoatId(Long boatId)
    {
        this.boatId = boatId;
    }

    public Long getBoatId()
    {
        return boatId;
    }

    public void setZoneId(Long zoneId)
    {
        this.zoneId = zoneId;
    }

    public Long getZoneId()
    {
        return zoneId;
    }

    public void setStationId(Long stationId)
    {
        this.stationId = stationId;
    }

    public Long getStationId()
    {
        return stationId;
    }

    public void setPlanTons(BigDecimal planTons)
    {
        this.planTons = planTons;
    }

    public BigDecimal getPlanTons()
    {
        return planTons;
    }

    public void setActualTons(BigDecimal actualTons)
    {
        this.actualTons = actualTons;
    }

    public BigDecimal getActualTons()
    {
        return actualTons;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setSolver(String solver)
    {
        this.solver = solver;
    }

    public String getSolver()
    {
        return solver;
    }

    public void setObjective(BigDecimal objective)
    {
        this.objective = objective;
    }

    public BigDecimal getObjective()
    {
        return objective;
    }

    public void setModelVersion(String modelVersion)
    {
        this.modelVersion = modelVersion;
    }

    public String getModelVersion()
    {
        return modelVersion;
    }

    public void setRunId(Long runId)
    {
        this.runId = runId;
    }

    public Long getRunId()
    {
        return runId;
    }

    public void setBoatName(String boatName)
    {
        this.boatName = boatName;
    }

    public String getBoatName()
    {
        return boatName;
    }

    public void setZoneName(String zoneName)
    {
        this.zoneName = zoneName;
    }

    public String getZoneName()
    {
        return zoneName;
    }

    public void setStationName(String stationName)
    {
        this.stationName = stationName;
    }

    public String getStationName()
    {
        return stationName;
    }
}
