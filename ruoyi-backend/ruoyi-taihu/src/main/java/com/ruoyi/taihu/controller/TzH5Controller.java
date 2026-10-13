package com.ruoyi.taihu.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.service.SysLoginService;
import com.ruoyi.taihu.domain.TzWorkOrder;
import com.ruoyi.taihu.domain.TzWorker;
import com.ruoyi.taihu.service.ITzWorkerService;
import com.ruoyi.taihu.service.ITzWorkOrderService;

/**
 * H5 移动端接口（/taihu/h5/**，免动态菜单，仍走若依 JWT/token 体系）
 *
 * 演示账号：leader01 / worker01（admin123，角色 taihu_h5，关联 tz_worker.username）。
 * 身份判定：登录后 GET /h5/me 按映射角色渲染——片长见接单操作；打捞员见到场/提交。
 *
 * @author taihu
 */
@RestController
@RequestMapping("/taihu/h5")
public class TzH5Controller extends BaseController
{
    @Autowired
    private SysLoginService sysLoginService;

    @Autowired
    private ITzWorkerService tzWorkerService;

    @Autowired
    private ITzWorkOrderService tzWorkOrderService;

    /**
     * H5 登录：用户名密码（复用若依登录校验，含验证码开关/锁定/黑名单逻辑），返回 token
     *
     * body: { username, password, code?, uuid? }——若管理台开了验证码则须带 code/uuid
     */
    @Anonymous
    @PostMapping("/login")
    public AjaxResult login(@RequestBody Map<String, String> body)
    {
        if (body == null || StringUtils.isEmpty(body.get("username")) || StringUtils.isEmpty(body.get("password")))
        {
            throw new ServiceException("用户名与密码不能为空");
        }
        String token = sysLoginService.login(body.get("username"), body.get("password"),
                body.get("code"), body.get("uuid"));
        AjaxResult ajax = AjaxResult.success();
        ajax.put("token", token);
        // 顺手带回角色映射，前端登录后可直接分流
        TzWorker worker = tzWorkerService.selectWorkerByUsername(body.get("username"));
        ajax.put("workerRole", worker == null ? null : worker.getRole());
        return ajax;
    }

    /**
     * 当前用户信息：sys_user → tz_worker 映射（角色/姓名/分区/队伍）
     */
    @PreAuthorize("@ss.hasPermi('taihu:h5:me')")
    @GetMapping("/me")
    public AjaxResult me()
    {
        String username = getUsername();
        TzWorker worker = tzWorkerService.selectWorkerByUsername(username);
        if (worker == null)
        {
            throw new ServiceException("当前账号未关联在岗作业人员（tz_worker.username）");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("workerId", worker.getWorkerId());
        data.put("workerName", worker.getWorkerName());
        data.put("role", worker.getRole());
        data.put("roleLabel", worker.isLeader() ? "片长" : "打捞员");
        data.put("zoneId", worker.getZoneId());
        data.put("zoneName", worker.getZoneName());
        data.put("teamName", worker.getTeamName());
        data.put("username", worker.getUsername());
        data.put("permissions", getLoginUser().getPermissions());
        return success(data);
    }

    /**
     * 我的工单：片长=派给我的（leader_id）；打捞员=crew_ids 含我；status 可选筛选
     */
    @PreAuthorize("@ss.hasPermi('taihu:h5:order:list')")
    @GetMapping("/my/orders")
    public TableDataInfo myOrders(String status)
    {
        TzWorker worker = requireMyWorker();
        TzWorkOrder query = new TzWorkOrder();
        if (StringUtils.isNotEmpty(status))
        {
            query.setStatus(status);
        }
        if (worker.isLeader())
        {
            query.setLeaderId(worker.getWorkerId());
        }
        else
        {
            query.setCrewWorkerId(worker.getWorkerId());
        }
        startPage();
        List<TzWorkOrder> list = tzWorkOrderService.selectWorkOrderList(query);
        return getDataTable(list);
    }

    /**
     * 片长接单：body { boatId?, crewIds }——boatId 缺省取 suggest_boats 首条；crewIds 必填（打捞员ID逗号分隔）
     */
    @PreAuthorize("@ss.hasPermi('taihu:h5:order:accept')")
    @Log(title = "H5-接单", businessType = BusinessType.UPDATE)
    @PutMapping("/orders/{orderId}/accept")
    public AjaxResult accept(@PathVariable("orderId") Long orderId, @RequestBody Map<String, Object> body)
    {
        Long boatId = parseLongOrNull(body == null ? null : body.get("boatId"));
        String crewIds = body == null ? null : String.valueOf(body.get("crewIds"));
        return toAjax(tzWorkOrderService.acceptOrder(orderId, boatId, crewIds, getUsername()));
    }

    /**
     * 打捞员到场打卡：body { lng, lat } 必填 → status=2
     */
    @PreAuthorize("@ss.hasPermi('taihu:h5:order:arrive')")
    @Log(title = "H5-到场打卡", businessType = BusinessType.UPDATE)
    @PutMapping("/orders/{orderId}/arrive")
    public AjaxResult arrive(@PathVariable("orderId") Long orderId, @RequestBody Map<String, Object> body)
    {
        BigDecimal lng = parseDecimal(body == null ? null : body.get("lng"), "lng");
        BigDecimal lat = parseDecimal(body == null ? null : body.get("lat"), "lat");
        return toAjax(tzWorkOrderService.arriveOrder(orderId, lng, lat, getUsername()));
    }

    /**
     * 打捞员提交完成：body { actualTons 必填, photoUrls 选填 } → status=3
     */
    @PreAuthorize("@ss.hasPermi('taihu:h5:order:submit')")
    @Log(title = "H5-提交完成", businessType = BusinessType.UPDATE)
    @PutMapping("/orders/{orderId}/submit")
    public AjaxResult submit(@PathVariable("orderId") Long orderId, @RequestBody Map<String, Object> body)
    {
        BigDecimal actualTons = parseDecimal(body == null ? null : body.get("actualTons"), "actualTons");
        String photoUrls = body == null || body.get("photoUrls") == null ? null : String.valueOf(body.get("photoUrls"));
        return toAjax(tzWorkOrderService.submitOrder(orderId, actualTons, photoUrls, getUsername()));
    }

    /**
     * 当前登录账号必须映射在岗作业人员
     */
    private TzWorker requireMyWorker()
    {
        TzWorker worker = tzWorkerService.selectWorkerByUsername(getUsername());
        if (worker == null)
        {
            throw new ServiceException("当前账号未关联在岗作业人员（tz_worker.username）");
        }
        return worker;
    }

    /**
     * 可空数值解析
     */
    private Long parseLongOrNull(Object value)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value)))
        {
            return null;
        }
        try
        {
            return Long.parseLong(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("boatId 必须为数值");
        }
    }

    /**
     * 必填数值解析
     */
    private BigDecimal parseDecimal(Object value, String field)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value)))
        {
            throw new ServiceException(field + " 必填");
        }
        try
        {
            return new BigDecimal(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException(field + " 必须为数值");
        }
    }
}
