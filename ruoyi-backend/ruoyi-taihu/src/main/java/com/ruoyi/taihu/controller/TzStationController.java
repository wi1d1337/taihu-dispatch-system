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
import com.ruoyi.taihu.domain.TzStation;
import com.ruoyi.taihu.service.ITzStationService;

/**
 * 藻水分离站 管理
 *
 * @author taihu
 */
@RestController
@RequestMapping("/taihu/stations")
public class TzStationController extends BaseController
{
    @Autowired
    private ITzStationService tzStationService;

    /**
     * 查询分离站列表
     */
    @PreAuthorize("@ss.hasPermi('taihu:station:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzStation tzStation)
    {
        startPage();
        List<TzStation> list = tzStationService.selectStationList(tzStation);
        return getDataTable(list);
    }

    /**
     * 获取分离站详细信息
     */
    @PreAuthorize("@ss.hasPermi('taihu:station:query')")
    @GetMapping(value = "/{stationId}")
    public AjaxResult getInfo(@PathVariable Long stationId)
    {
        return success(tzStationService.selectStationById(stationId));
    }

    /**
     * 新增分离站
     */
    @PreAuthorize("@ss.hasPermi('taihu:station:add')")
    @Log(title = "藻水分离站", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzStation tzStation)
    {
        tzStation.setCreateBy(getUsername());
        return toAjax(tzStationService.insertStation(tzStation));
    }

    /**
     * 修改分离站
     */
    @PreAuthorize("@ss.hasPermi('taihu:station:edit')")
    @Log(title = "藻水分离站", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzStation tzStation)
    {
        tzStation.setUpdateBy(getUsername());
        return toAjax(tzStationService.updateStation(tzStation));
    }

    /**
     * 删除分离站
     */
    @PreAuthorize("@ss.hasPermi('taihu:station:remove')")
    @Log(title = "藻水分离站", businessType = BusinessType.DELETE)
    @DeleteMapping("/{stationIds}")
    public AjaxResult remove(@PathVariable Long[] stationIds)
    {
        return toAjax(tzStationService.deleteStationByIds(stationIds));
    }
}
