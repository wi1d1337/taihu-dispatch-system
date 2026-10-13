package com.ruoyi.taihu.domain;

import java.math.BigDecimal;
import java.util.Date;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 打捞台账对象 tz_salvage_log
 *
 * 全系统唯一人工录入点：带 orderId 时船/分区/日期/半天自动从调度单带出，现场只补实收数。
 *
 * @author taihu
 */
public class TzSalvageLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 台账ID */
    private Long logId;

    /** 关联调度单ID（可空=计划外作业） */
    private Long orderId;

    /** 船只ID（带 orderId 时由服务层从调度单带出，计划外作业必填） */
    private Long boatId;

    /** 分区ID（带 orderId 时由服务层从调度单带出） */
    private Long zoneId;

    /** 作业日期（带 orderId 时由服务层从调度单带出） */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Excel(name = "作业日期", width = 12, dateFormat = "yyyy-MM-dd")
    private Date logDate;

    /** 半天（0上午 1下午） */
    @Excel(name = "半天", readConverterExp = "0=上午,1=下午")
    private String halfDay;

    /** 实收吨数（必填） */
    @NotNull(message = "实收吨数不能为空")
    @Excel(name = "实收吨数")
    private BigDecimal actualTons;

    /** 油耗（L） */
    @Excel(name = "油耗")
    private BigDecimal fuelLiters;

    /** 作业人数 */
    @Excel(name = "作业人数")
    private Long crew;

    /** 照片URL */
    private String photoUrl;

    /** 经度 */
    private BigDecimal lng;

    /** 纬度 */
    private BigDecimal lat;

    // ---- 以下为联表展示字段（非表字段） ----

    /** 船名（联表） */
    @Excel(name = "船名")
    private String boatName;

    /** 分区名（联表） */
    @Excel(name = "分区")
    private String zoneName;

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

    public void setLogDate(Date logDate)
    {
        this.logDate = logDate;
    }

    public Date getLogDate()
    {
        return logDate;
    }

    public void setHalfDay(String halfDay)
    {
        this.halfDay = halfDay;
    }

    public String getHalfDay()
    {
        return halfDay;
    }

    public void setActualTons(BigDecimal actualTons)
    {
        this.actualTons = actualTons;
    }

    public BigDecimal getActualTons()
    {
        return actualTons;
    }

    public void setFuelLiters(BigDecimal fuelLiters)
    {
        this.fuelLiters = fuelLiters;
    }

    public BigDecimal getFuelLiters()
    {
        return fuelLiters;
    }

    public void setCrew(Long crew)
    {
        this.crew = crew;
    }

    public Long getCrew()
    {
        return crew;
    }

    public void setPhotoUrl(String photoUrl)
    {
        this.photoUrl = photoUrl;
    }

    public String getPhotoUrl()
    {
        return photoUrl;
    }

    public void setLng(BigDecimal lng)
    {
        this.lng = lng;
    }

    public BigDecimal getLng()
    {
        return lng;
    }

    public void setLat(BigDecimal lat)
    {
        this.lat = lat;
    }

    public BigDecimal getLat()
    {
        return lat;
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
}
