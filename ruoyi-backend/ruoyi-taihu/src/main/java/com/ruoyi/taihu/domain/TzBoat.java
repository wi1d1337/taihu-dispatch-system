package com.ruoyi.taihu.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 打捞船只对象 tz_boat
 *
 * @author taihu
 */
public class TzBoat extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 船只ID */
    private Long boatId;

    /** 船名 */
    @NotBlank(message = "船名不能为空")
    @Excel(name = "船名")
    private String boatName;

    /** 类型（0打捞 1机动 2小型） */
    @Excel(name = "船只类型", readConverterExp = "0=打捞船,1=机动船,2=小型船")
    private String boatType;

    /** 日处理能力（吨/日） */
    @Excel(name = "日处理能力")
    private BigDecimal capacityT;

    /** 航速（km/h） */
    @Excel(name = "航速")
    private BigDecimal speedKmh;

    /** 油耗（L/km） */
    @Excel(name = "油耗")
    private BigDecimal fuelLkm;

    /** 状态（0可用 1维护） */
    @Excel(name = "状态", readConverterExp = "0=可用,1=维护")
    private String status;

    /** 母港 */
    @Excel(name = "母港")
    private String homePort;

    public void setBoatId(Long boatId)
    {
        this.boatId = boatId;
    }

    public Long getBoatId()
    {
        return boatId;
    }

    public void setBoatName(String boatName)
    {
        this.boatName = boatName;
    }

    public String getBoatName()
    {
        return boatName;
    }

    public void setBoatType(String boatType)
    {
        this.boatType = boatType;
    }

    public String getBoatType()
    {
        return boatType;
    }

    public void setCapacityT(BigDecimal capacityT)
    {
        this.capacityT = capacityT;
    }

    public BigDecimal getCapacityT()
    {
        return capacityT;
    }

    public void setSpeedKmh(BigDecimal speedKmh)
    {
        this.speedKmh = speedKmh;
    }

    public BigDecimal getSpeedKmh()
    {
        return speedKmh;
    }

    public void setFuelLkm(BigDecimal fuelLkm)
    {
        this.fuelLkm = fuelLkm;
    }

    public BigDecimal getFuelLkm()
    {
        return fuelLkm;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setHomePort(String homePort)
    {
        this.homePort = homePort;
    }

    public String getHomePort()
    {
        return homePort;
    }
}
