package com.ruoyi.taihu.controller;

import java.math.BigDecimal;
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
import com.ruoyi.taihu.domain.TzDispatchOrder;
import com.ruoyi.taihu.service.ITzDispatchOrderService;

/**
 * 调度单 管理
 *
 * 状态机接口：PUT /{id}/confirm（0→1）、PUT /{id}/reject（0→2，remark 必填）、PUT /{id}/done（1→3，actualTons 选填）
 *
 * @author taihu
 */
@RestController
@RequestMapping("/taihu/orders")
public class TzDispatchOrderController extends BaseController
{
    @Autowired
    private ITzDispatchOrderService tzDispatchOrderService;

    /**
     * 查询调度单列表（date/status/halfDay 过滤，联表返回 boat_name/zone_name/station_name）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzDispatchOrder tzDispatchOrder)
    {
        startPage();
        List<TzDispatchOrder> list = tzDispatchOrderService.selectDispatchOrderList(tzDispatchOrder);
        return getDataTable(list);
    }

    /**
     * 获取调度单详细信息
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:query')")
    @GetMapping(value = "/{orderId}")
    public AjaxResult getInfo(@PathVariable("orderId") Long orderId)
    {
        return success(tzDispatchOrderService.selectDispatchOrderById(orderId));
    }

    /**
     * 新增调度单
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:add')")
    @Log(title = "调度单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzDispatchOrder tzDispatchOrder)
    {
        tzDispatchOrder.setCreateBy(getUsername());
        return toAjax(tzDispatchOrderService.insertDispatchOrder(tzDispatchOrder));
    }

    /**
     * 修改调度单
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:edit')")
    @Log(title = "调度单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzDispatchOrder tzDispatchOrder)
    {
        tzDispatchOrder.setUpdateBy(getUsername());
        return toAjax(tzDispatchOrderService.updateDispatchOrder(tzDispatchOrder));
    }

    /**
     * 删除调度单
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:remove')")
    @Log(title = "调度单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orderIds}")
    public AjaxResult remove(@PathVariable Long[] orderIds)
    {
        return toAjax(tzDispatchOrderService.deleteDispatchOrderByIds(orderIds));
    }

    /**
     * 确认调度单（0待确认 → 1已确认）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:confirm')")
    @Log(title = "调度单-确认", businessType = BusinessType.UPDATE)
    @PutMapping("/{orderId}/confirm")
    public AjaxResult confirm(@PathVariable("orderId") Long orderId)
    {
        return toAjax(tzDispatchOrderService.confirmOrder(orderId, getUsername()));
    }

    /**
     * 驳回调度单（0待确认 → 2已驳回，body.remark 必填）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:reject')")
    @Log(title = "调度单-驳回", businessType = BusinessType.UPDATE)
    @PutMapping("/{orderId}/reject")
    public AjaxResult reject(@PathVariable("orderId") Long orderId, @RequestBody Map<String, String> body)
    {
        String remark = body == null ? null : body.get("remark");
        return toAjax(tzDispatchOrderService.rejectOrder(orderId, remark, getUsername()));
    }

    /**
     * 完成调度单（1已确认 → 3已完成；body.actualTons 选填，缺省取台账汇总）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:done')")
    @Log(title = "调度单-完成", businessType = BusinessType.UPDATE)
    @PutMapping("/{orderId}/done")
    public AjaxResult done(@PathVariable("orderId") Long orderId, @RequestBody(required = false) Map<String, Object> body)
    {
        BigDecimal actualTons = null;
        if (body != null && body.get("actualTons") != null)
        {
            try
            {
                actualTons = new BigDecimal(String.valueOf(body.get("actualTons")));
            }
            catch (NumberFormatException e)
            {
                throw new ServiceException("actualTons 必须为数值");
            }
        }
        return toAjax(tzDispatchOrderService.doneOrder(orderId, actualTons, getUsername()));
    }
}
