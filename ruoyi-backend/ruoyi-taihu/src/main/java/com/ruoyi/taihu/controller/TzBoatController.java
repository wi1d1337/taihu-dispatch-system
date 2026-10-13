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
import com.ruoyi.taihu.domain.TzBoat;
import com.ruoyi.taihu.service.ITzBoatService;

/**
 * 打捞船只 管理
 *
 * @author taihu
 */
@RestController
@RequestMapping("/taihu/boats")
public class TzBoatController extends BaseController
{
    @Autowired
    private ITzBoatService tzBoatService;

    /**
     * 查询船只列表
     */
    @PreAuthorize("@ss.hasPermi('taihu:boat:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzBoat tzBoat)
    {
        startPage();
        List<TzBoat> list = tzBoatService.selectBoatList(tzBoat);
        return getDataTable(list);
    }

    /**
     * 获取船只详细信息
     */
    @PreAuthorize("@ss.hasPermi('taihu:boat:query')")
    @GetMapping(value = "/{boatId}")
    public AjaxResult getInfo(@PathVariable Long boatId)
    {
        return success(tzBoatService.selectBoatById(boatId));
    }

    /**
     * 新增船只
     */
    @PreAuthorize("@ss.hasPermi('taihu:boat:add')")
    @Log(title = "打捞船只", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzBoat tzBoat)
    {
        tzBoat.setCreateBy(getUsername());
        return toAjax(tzBoatService.insertBoat(tzBoat));
    }

    /**
     * 修改船只
     */
    @PreAuthorize("@ss.hasPermi('taihu:boat:edit')")
    @Log(title = "打捞船只", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzBoat tzBoat)
    {
        tzBoat.setUpdateBy(getUsername());
        return toAjax(tzBoatService.updateBoat(tzBoat));
    }

    /**
     * 删除船只
     */
    @PreAuthorize("@ss.hasPermi('taihu:boat:remove')")
    @Log(title = "打捞船只", businessType = BusinessType.DELETE)
    @DeleteMapping("/{boatIds}")
    public AjaxResult remove(@PathVariable Long[] boatIds)
    {
        return toAjax(tzBoatService.deleteBoatByIds(boatIds));
    }
}
