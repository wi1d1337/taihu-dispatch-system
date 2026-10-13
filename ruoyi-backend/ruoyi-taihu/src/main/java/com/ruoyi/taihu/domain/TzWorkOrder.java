package com.ruoyi.taihu.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 打捞工单对象 tz_work_order（两级派发：算法/调度 → 片长 → 船/人）
 *
 * 状态机：0待接单 1已接单 2进行中 3待核查 4已办结 5已退回(整改重报回3) 6已撤销
 *
 * @author taihu
 */
public class TzWorkOrder extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态常量：待接单 */
    public static final String STATUS_PENDING = "0";
    /** 状态常量：已接单 */
    public static final String STATUS_ACCEPTED = "1";
    /** 状态常量：进行中 */
    public static final String STATUS_WORKING = "2";
    /** 状态常量：待核查 */
    public static final String STATUS_SUBMITTED = "3";
    /** 状态常量：已办结 */
    public static final String STATUS_DONE = "4";
    /** 状态常量：已退回 */
    public static final String STATUS_REJECTED = "5";
    /** 状态常量：已撤销 */
    public static final String STATUS_REVOKED = "6";

    /** 工单ID */
    private Long orderId;

    /** 工单号 TZ20261014-0001 */
    @Excel(name = "工单号")
    private String orderNo;

    /** 分区ID */
    @Excel(name = "分区ID")
    @NotNull(message = "分区不能为空")
    private Long zoneId;

    /** 藻情等级（1关注 2较大 3重大） */
    @Excel(name = "藻情等级", readConverterExp = "1=关注,2=较大,3=重大")
    @NotBlank(message = "藻情等级不能为空")
    private String level;

    /** 来源（1算法自动 2人工创建） */
    @Excel(name = "来源", readConverterExp = "1=算法自动,2=人工创建")
    private String source;

    /** 来源研判记录ID */
    private Long assessmentId;

    /** 状态（0待接单 1已接单 2进行中 3待核查 4已办结 5已退回 6已撤销） */
    @Excel(name = "状态", readConverterExp = "0=待接单,1=已接单,2=进行中,3=待核查,4=已办结,5=已退回,6=已撤销")
    private String status;

    /** 片长（首派对象） */
    private Long leaderId;

    /** 船只（片长接单时指定） */
    private Long boatId;

    /** 打捞员ID逗号分隔 */
    private String crewIds;

    /** 处置时限 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "处置时限", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date deadline;

    /** 派单时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "派单时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date dispatchedAt;

    /** 接单时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "接单时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date acceptedAt;

    /** 到场时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "到场时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date arrivedAt;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "提交时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date submittedAt;

    /** 核查时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "核查时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date checkedAt;

    /** 藻水湿重吨（完成时报） */
    @Excel(name = "藻水湿重吨")
    private BigDecimal actualTons;

    /** 现场照片URL逗号分隔 */
    private String photoUrls;

    /** 到场经度 */
    private BigDecimal arriveLng;

    /** 到场纬度 */
    private BigDecimal arriveLat;

    /** CP-SAT推荐派船方案 JSON [{boat_id,zone_id,tons}] */
    private String suggestBoats;

    /** 撤销原因 */
    private String revokeReason;

    /** 核查意见 */
    private String checkComment;

    /** 模型版本 */
    private String modelVersion;

    /** 参数版本 */
    private Integer paramVersion;

    // ---- 联表展示字段 ----

    /** 分区名（联表 tz_zone） */
    @Excel(name = "分区名")
    private String zoneName;

    /** 片长姓名（联表 tz_worker） */
    @Excel(name = "片长")
    private String leaderName;

    /** 船名（联表 tz_boat） */
    @Excel(name = "船只")
    private String boatName;

    // ---- 查询辅助参数（非表字段） ----

    /** 查询：工单日期（yyyy-MM-dd，匹配 create_time 日期） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private String orderDate;

    /** 查询：起始日期（yyyy-MM-dd） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private String beginDate;

    /** 查询：截止日期（yyyy-MM-dd） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private String endDate;

    /** 查询：打捞员ID（H5 我的工单，find_in_set 匹配 crew_ids） */
    private Long crewWorkerId;

    /** 状态中文标签（展示用，非表字段） */
    public String getStatusLabel()
    {
        if (status == null)
        {
            return "";
        }
        switch (status)
        {
            case STATUS_PENDING: return "待接单";
            case STATUS_ACCEPTED: return "已接单";
            case STATUS_WORKING: return "进行中";
            case STATUS_SUBMITTED: return "待核查";
            case STATUS_DONE: return "已办结";
            case STATUS_REJECTED: return "已退回";
            case STATUS_REVOKED: return "已撤销";
            default: return status;
        }
    }

    public void setOrderId(Long orderId)
    {
        this.orderId = orderId;
    }

    public Long getOrderId()
    {
        return orderId;
    }

    public void setOrderNo(String orderNo)
    {
        this.orderNo = orderNo;
    }

    public String getOrderNo()
    {
        return orderNo;
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

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getSource()
    {
        return source;
    }

    public void setAssessmentId(Long assessmentId)
    {
        this.assessmentId = assessmentId;
    }

    public Long getAssessmentId()
    {
        return assessmentId;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setLeaderId(Long leaderId)
    {
        this.leaderId = leaderId;
    }

    public Long getLeaderId()
    {
        return leaderId;
    }

    public void setBoatId(Long boatId)
    {
        this.boatId = boatId;
    }

    public Long getBoatId()
    {
        return boatId;
    }

    public void setCrewIds(String crewIds)
    {
        this.crewIds = crewIds;
    }

    public String getCrewIds()
    {
        return crewIds;
    }

    public void setDeadline(Date deadline)
    {
        this.deadline = deadline;
    }

    public Date getDeadline()
    {
        return deadline;
    }

    public void setDispatchedAt(Date dispatchedAt)
    {
        this.dispatchedAt = dispatchedAt;
    }

    public Date getDispatchedAt()
    {
        return dispatchedAt;
    }

    public void setAcceptedAt(Date acceptedAt)
    {
        this.acceptedAt = acceptedAt;
    }

    public Date getAcceptedAt()
    {
        return acceptedAt;
    }

    public void setArrivedAt(Date arrivedAt)
    {
        this.arrivedAt = arrivedAt;
    }

    public Date getArrivedAt()
    {
        return arrivedAt;
    }

    public void setSubmittedAt(Date submittedAt)
    {
        this.submittedAt = submittedAt;
    }

    public Date getSubmittedAt()
    {
        return submittedAt;
    }

    public void setCheckedAt(Date checkedAt)
    {
        this.checkedAt = checkedAt;
    }

    public Date getCheckedAt()
    {
        return checkedAt;
    }

    public void setActualTons(BigDecimal actualTons)
    {
        this.actualTons = actualTons;
    }

    public BigDecimal getActualTons()
    {
        return actualTons;
    }

    public void setPhotoUrls(String photoUrls)
    {
        this.photoUrls = photoUrls;
    }

    public String getPhotoUrls()
    {
        return photoUrls;
    }

    public void setArriveLng(BigDecimal arriveLng)
    {
        this.arriveLng = arriveLng;
    }

    public BigDecimal getArriveLng()
    {
        return arriveLng;
    }

    public void setArriveLat(BigDecimal arriveLat)
    {
        this.arriveLat = arriveLat;
    }

    public BigDecimal getArriveLat()
    {
        return arriveLat;
    }

    public void setSuggestBoats(String suggestBoats)
    {
        this.suggestBoats = suggestBoats;
    }

    public String getSuggestBoats()
    {
        return suggestBoats;
    }

    public void setRevokeReason(String revokeReason)
    {
        this.revokeReason = revokeReason;
    }

    public String getRevokeReason()
    {
        return revokeReason;
    }

    public void setCheckComment(String checkComment)
    {
        this.checkComment = checkComment;
    }

    public String getCheckComment()
    {
        return checkComment;
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

    public void setZoneName(String zoneName)
    {
        this.zoneName = zoneName;
    }

    public String getZoneName()
    {
        return zoneName;
    }

    public void setLeaderName(String leaderName)
    {
        this.leaderName = leaderName;
    }

    public String getLeaderName()
    {
        return leaderName;
    }

    public void setBoatName(String boatName)
    {
        this.boatName = boatName;
    }

    public String getBoatName()
    {
        return boatName;
    }

    public void setOrderDate(String orderDate)
    {
        this.orderDate = orderDate;
    }

    public String getOrderDate()
    {
        return orderDate;
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

    public void setCrewWorkerId(Long crewWorkerId)
    {
        this.crewWorkerId = crewWorkerId;
    }

    public Long getCrewWorkerId()
    {
        return crewWorkerId;
    }
}
