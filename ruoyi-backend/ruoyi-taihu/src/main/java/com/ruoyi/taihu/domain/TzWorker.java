package com.ruoyi.taihu.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 作业人员对象 tz_worker（片长/打捞员）
 *
 * @author taihu
 */
public class TzWorker extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 人员ID */
    private Long workerId;

    /** 姓名 */
    @Excel(name = "姓名")
    @NotBlank(message = "姓名不能为空")
    @Size(min = 0, max = 50, message = "姓名长度不能超过50个字符")
    private String workerName;

    /** 角色（1片长 2打捞员/船长） */
    @Excel(name = "角色", readConverterExp = "1=片长,2=打捞员")
    @NotBlank(message = "角色不能为空")
    private String role;

    /** 联系电话 */
    @Excel(name = "联系电话")
    private String phone;

    /** 负责分区（片长） */
    @Excel(name = "负责分区ID")
    private Long zoneId;

    /** 所属队伍/班组 */
    @Excel(name = "所属队伍")
    private String teamName;

    /** 关联sys_user登录名（H5登录） */
    @Excel(name = "登录名")
    private String username;

    /** 状态（0在岗 1停用） */
    @Excel(name = "状态", readConverterExp = "0=在岗,1=停用")
    private String status;

    /** 分区名（联表） */
    @Excel(name = "分区名")
    private String zoneName;

    public void setWorkerId(Long workerId)
    {
        this.workerId = workerId;
    }

    public Long getWorkerId()
    {
        return workerId;
    }

    public void setWorkerName(String workerName)
    {
        this.workerName = workerName;
    }

    public String getWorkerName()
    {
        return workerName;
    }

    public void setRole(String role)
    {
        this.role = role;
    }

    public String getRole()
    {
        return role;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setZoneId(Long zoneId)
    {
        this.zoneId = zoneId;
    }

    public Long getZoneId()
    {
        return zoneId;
    }

    public void setTeamName(String teamName)
    {
        this.teamName = teamName;
    }

    public String getTeamName()
    {
        return teamName;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getUsername()
    {
        return username;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setZoneName(String zoneName)
    {
        this.zoneName = zoneName;
    }

    public String getZoneName()
    {
        return zoneName;
    }

    /** 是否片长 */
    public boolean isLeader()
    {
        return "1".equals(role);
    }
}
