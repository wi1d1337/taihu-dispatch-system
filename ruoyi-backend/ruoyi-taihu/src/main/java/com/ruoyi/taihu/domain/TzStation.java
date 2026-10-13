package com.ruoyi.taihu.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 藻水分离站对象 tz_station
 *
 * @author taihu
 */
public class TzStation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 分离站ID */
    private Long stationId;

    /** 站名 */
    @NotBlank(message = "站名不能为空")
    @Excel(name = "站名")
    private String stationName;

    /** 日处理能力（吨/日） */
    @Excel(name = "日处理能力")
    private BigDecimal capacityTDay;

    /** 状态（0可用 1停用） */
    @Excel(name = "状态", readConverterExp = "0=可用,1=停用")
    private String status;

    public void setStationId(Long stationId)
    {
        this.stationId = stationId;
    }

    public Long getStationId()
    {
        return stationId;
    }

    public void setStationName(String stationName)
    {
        this.stationName = stationName;
    }

    public String getStationName()
    {
        return stationName;
    }

    public void setCapacityTDay(BigDecimal capacityTDay)
    {
        this.capacityTDay = capacityTDay;
    }

    public BigDecimal getCapacityTDay()
    {
        return capacityTDay;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }
}
