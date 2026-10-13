package com.ruoyi.taihu.domain;

import java.math.BigDecimal;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 打捞分区对象 tz_zone
 *
 * @author taihu
 */
public class TzZone extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 分区ID */
    private Long zoneId;

    /** 分区名 */
    @Excel(name = "分区名")
    private String zoneName;

    /** 分区代码（MLW/ZSH/GH/HX/XBAY/NBAY） */
    @Excel(name = "分区代码")
    private String zoneCode;

    /** 面积（平方公里） */
    @Excel(name = "面积", readConverterExp = "平方公里")
    private BigDecimal areaKm2;

    /** 边界 GeoJSON 坐标数组 [[lng,lat],...] 的 JSON 串 */
    private String boundary;

    /** 72h 水华概率（0-1） */
    @Excel(name = "72h水华概率")
    private BigDecimal prob72h;

    /** 预计可收藻 q25（吨/日） */
    @Excel(name = "预计可收藻q25")
    private BigDecimal tonsQ25;

    /** 预计可收藻 q50（吨/日） */
    @Excel(name = "预计可收藻q50")
    private BigDecimal tonsQ50;

    /** 当前叶绿素a（ug/L） */
    @Excel(name = "当前叶绿素a")
    private BigDecimal chla;

    public void setZoneId(Long zoneId)
    {
        this.zoneId = zoneId;
    }

    public Long getZoneId()
    {
        return zoneId;
    }

    public void setZoneName(String zoneName)
    {
        this.zoneName = zoneName;
    }

    public String getZoneName()
    {
        return zoneName;
    }

    public void setZoneCode(String zoneCode)
    {
        this.zoneCode = zoneCode;
    }

    public String getZoneCode()
    {
        return zoneCode;
    }

    public void setAreaKm2(BigDecimal areaKm2)
    {
        this.areaKm2 = areaKm2;
    }

    public BigDecimal getAreaKm2()
    {
        return areaKm2;
    }

    public void setBoundary(String boundary)
    {
        this.boundary = boundary;
    }

    public String getBoundary()
    {
        return boundary;
    }

    public void setProb72h(BigDecimal prob72h)
    {
        this.prob72h = prob72h;
    }

    public BigDecimal getProb72h()
    {
        return prob72h;
    }

    public void setTonsQ25(BigDecimal tonsQ25)
    {
        this.tonsQ25 = tonsQ25;
    }

    public BigDecimal getTonsQ25()
    {
        return tonsQ25;
    }

    public void setTonsQ50(BigDecimal tonsQ50)
    {
        this.tonsQ50 = tonsQ50;
    }

    public BigDecimal getTonsQ50()
    {
        return tonsQ50;
    }

    public void setChla(BigDecimal chla)
    {
        this.chla = chla;
    }

    public BigDecimal getChla()
    {
        return chla;
    }
}
