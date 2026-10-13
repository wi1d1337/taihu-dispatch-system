package com.ruoyi.taihu.controller;

import java.util.List;
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
import com.ruoyi.taihu.domain.TzSalvageLog;
import com.ruoyi.taihu.service.ITzSalvageLogService;

/**
 * 打捞台账 管理
 *
 * 全系统唯一人工录入点；POST 带 orderId 时自动带出船/分区/日期/半天。
 *
 * @author taihu
 */
@RestController
@RequestMapping("/taihu/logs")
public class TzSalvageLogController extends BaseController
{
    @Autowired
    private ITzSalvageLogService tzSalvageLogService;

    /**
     * 查询台账列表
     */
    @PreAuthorize("@ss.hasPermi('taihu:log:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzSalvageLog tzSalvageLog)
    {
        startPage();
        List<TzSalvageLog> list = tzSalvageLogService.selectSalvageLogList(tzSalvageLog);
        return getDataTable(list);
    }

    /**
     * 获取台账详细信息
     */
    @PreAuthorize("@ss.hasPermi('taihu:log:query')")
    @GetMapping(value = "/{logId}")
    public AjaxResult getInfo(@PathVariable("logId") Long logId)
    {
        return success(tzSalvageLogService.selectSalvageLogById(logId));
    }

    /**
     * 新增台账（带 orderId 时自动带出船/分区/日期/半天，并校验调度单存在）
     */
    @PreAuthorize("@ss.hasPermi('taihu:log:add')")
    @Log(title = "打捞台账", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzSalvageLog tzSalvageLog)
    {
        tzSalvageLog.setCreateBy(getUsername());
        return toAjax(tzSalvageLogService.insertSalvageLog(tzSalvageLog));
    }

    /**
     * 修改台账
     */
    @PreAuthorize("@ss.hasPermi('taihu:log:edit')")
    @Log(title = "打捞台账", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzSalvageLog tzSalvageLog)
    {
        tzSalvageLog.setUpdateBy(getUsername());
        return toAjax(tzSalvageLogService.updateSalvageLog(tzSalvageLog));
    }

    /**
     * 删除台账
     */
    @PreAuthorize("@ss.hasPermi('taihu:log:remove')")
    @Log(title = "打捞台账", businessType = BusinessType.DELETE)
    @DeleteMapping("/{logIds}")
    public AjaxResult remove(@PathVariable Long[] logIds)
    {
        return toAjax(tzSalvageLogService.deleteSalvageLogByIds(logIds));
    }
}
