package com.ruoyi.taihu.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.taihu.domain.TzWorkOrder;
import com.ruoyi.taihu.service.ITzOrderLogService;
import com.ruoyi.taihu.service.ITzWorkOrderService;

/**
 * 打捞工单 管理（管理台）
 *
 * 状态机接口：PUT /{id}/revoke（0/1→6，reason 必填）、PUT /{id}/reassign（→0 改派片长）、
 * PUT /{id}/check（3→4/5，body.pass+checkComment）、POST 人工创建（source=2）
 *
 * @author taihu
 */
@RestController
@RequestMapping("/taihu/orders")
public class TzWorkOrderController extends BaseController
{
    @Autowired
    private ITzWorkOrderService tzWorkOrderService;

    @Autowired
    private ITzOrderLogService tzOrderLogService;

    /**
     * 查询工单列表（date/zone/level/status 过滤，联表 zone_name/leader_name/boat_name）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzWorkOrder tzWorkOrder)
    {
        startPage();
        List<TzWorkOrder> list = tzWorkOrderService.selectWorkOrderList(tzWorkOrder);
        return getDataTable(list);
    }

    /**
     * 获取工单详细信息
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:query')")
    @GetMapping(value = "/{orderId}")
    public AjaxResult getInfo(@PathVariable("orderId") Long orderId)
    {
        return success(tzWorkOrderService.selectWorkOrderById(orderId));
    }

    /**
     * 获取工单流转时间线（详情抽屉用）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:query')")
    @GetMapping(value = "/{orderId}/logs")
    public AjaxResult logs(@PathVariable("orderId") Long orderId)
    {
        return success(tzOrderLogService.selectLogsByOrderId(orderId));
    }

    /**
     * 人工创建工单（source=2，状态置 0 派给片长）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:add')")
    @Log(title = "打捞工单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzWorkOrder tzWorkOrder)
    {
        tzWorkOrder.setCreateBy(getUsername());
        return toAjax(tzWorkOrderService.insertWorkOrder(tzWorkOrder));
    }

    /**
     * 撤销工单（status∈{0,1}→6，body.revokeReason 必填）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:revoke')")
    @Log(title = "工单-撤销", businessType = BusinessType.UPDATE)
    @PutMapping("/{orderId}/revoke")
    public AjaxResult revoke(@PathVariable("orderId") Long orderId, @RequestBody Map<String, String> body)
    {
        String reason = body == null ? null : body.get("revokeReason");
        return toAjax(tzWorkOrderService.revokeOrder(orderId, reason, getUsername()));
    }

    /**
     * 改派片长（status∈{0,1}→0，body.leaderId 必填，清船只/打捞员）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:reassign')")
    @Log(title = "工单-改派", businessType = BusinessType.UPDATE)
    @PutMapping("/{orderId}/reassign")
    public AjaxResult reassign(@PathVariable("orderId") Long orderId, @RequestBody Map<String, Object> body)
    {
        Long leaderId = parseLong(body == null ? null : body.get("leaderId"), "leaderId");
        String note = body == null ? null : String.valueOf(body.get("note"));
        return toAjax(tzWorkOrderService.reassignOrder(orderId, leaderId, getUsername(), note));
    }

    /**
     * 核查工单（status=3：body.pass=true→4 / false→5+checkComment 必填）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:check')")
    @Log(title = "工单-核查", businessType = BusinessType.UPDATE)
    @PutMapping("/{orderId}/check")
    public AjaxResult check(@PathVariable("orderId") Long orderId, @RequestBody Map<String, Object> body)
    {
        if (body == null || body.get("pass") == null)
        {
            throw new ServiceException("核查结论 pass 必填（true 办结 / false 退回）");
        }
        boolean pass = Boolean.parseBoolean(String.valueOf(body.get("pass")));
        String checkComment = body.get("checkComment") == null ? null : String.valueOf(body.get("checkComment"));
        return toAjax(tzWorkOrderService.checkOrder(orderId, pass, checkComment, getUsername()));
    }

    /**
     * 删除工单（仅终态 4/6 可删，连带流转日志）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:remove')")
    @Log(title = "打捞工单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orderIds}")
    public AjaxResult remove(@PathVariable Long[] orderIds)
    {
        return toAjax(tzWorkOrderService.deleteWorkOrderByIds(orderIds));
    }

    /**
     * body 数值参数解析
     */
    private Long parseLong(Object value, String field)
    {
        if (value == null || StringUtils.isEmpty(String.valueOf(value)))
        {
            throw new ServiceException(field + " 必填");
        }
        try
        {
            return Long.parseLong(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException(field + " 必须为数值");
        }
    }
}
