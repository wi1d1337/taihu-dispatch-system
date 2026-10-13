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
import com.ruoyi.taihu.domain.TzZone;
import com.ruoyi.taihu.service.ITzZoneService;

/**
 * 打捞分区 管理
 *
 * @author taihu
 */
@RestController
@RequestMapping("/taihu/zones")
public class TzZoneController extends BaseController
{
    @Autowired
    private ITzZoneService tzZoneService;

    /**
     * 查询分区列表（不分页参数时返回全量，供下拉/地图）
     */
    @PreAuthorize("@ss.hasPermi('taihu:zone:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzZone tzZone)
    {
        startPage();
        List<TzZone> list = tzZoneService.selectZoneList(tzZone);
        return getDataTable(list);
    }

    /**
     * 获取分区详细信息
     */
    @PreAuthorize("@ss.hasPermi('taihu:zone:query')")
    @GetMapping(value = "/{zoneId}")
    public AjaxResult getInfo(@PathVariable Long zoneId)
    {
        return success(tzZoneService.selectZoneById(zoneId));
    }

    /**
     * 新增分区
     */
    @PreAuthorize("@ss.hasPermi('taihu:zone:add')")
    @Log(title = "打捞分区", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzZone tzZone)
    {
        tzZone.setCreateBy(getUsername());
        return toAjax(tzZoneService.insertZone(tzZone));
    }

    /**
     * 修改分区
     */
    @PreAuthorize("@ss.hasPermi('taihu:zone:edit')")
    @Log(title = "打捞分区", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzZone tzZone)
    {
        tzZone.setUpdateBy(getUsername());
        return toAjax(tzZoneService.updateZone(tzZone));
    }

    /**
     * 删除分区
     */
    @PreAuthorize("@ss.hasPermi('taihu:zone:remove')")
    @Log(title = "打捞分区", businessType = BusinessType.DELETE)
    @DeleteMapping("/{zoneIds}")
    public AjaxResult remove(@PathVariable Long[] zoneIds)
    {
        return toAjax(tzZoneService.deleteZoneByIds(zoneIds));
    }
}
