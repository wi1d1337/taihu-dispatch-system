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
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.taihu.domain.TzWorker;
import com.ruoyi.taihu.service.ITzWorkerService;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 作业人员 管理（片长/打捞员，绑定分区；H5 接单选人也走 /list）
 *
 * @author taihu
 */
@RestController
@RequestMapping("/taihu/workers")
public class TzWorkerController extends BaseController
{
    @Autowired
    private ITzWorkerService tzWorkerService;

    /**
     * 查询作业人员列表（role/zone 过滤，联表 zone_name）
     */
    @PreAuthorize("@ss.hasPermi('taihu:worker:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzWorker tzWorker)
    {
        startPage();
        List<TzWorker> list = tzWorkerService.selectWorkerList(tzWorker);
        return getDataTable(list);
    }

    /**
     * 导出作业人员列表
     */
    @PreAuthorize("@ss.hasPermi('taihu:worker:list')")
    @Log(title = "作业人员", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TzWorker tzWorker)
    {
        List<TzWorker> list = tzWorkerService.selectWorkerList(tzWorker);
        ExcelUtil<TzWorker> util = new ExcelUtil<TzWorker>(TzWorker.class);
        util.exportExcel(response, list, "作业人员数据");
    }

    /**
     * 获取作业人员详细信息
     */
    @PreAuthorize("@ss.hasPermi('taihu:worker:query')")
    @GetMapping(value = "/{workerId}")
    public AjaxResult getInfo(@PathVariable("workerId") Long workerId)
    {
        return success(tzWorkerService.selectWorkerById(workerId));
    }

    /**
     * 新增作业人员
     */
    @PreAuthorize("@ss.hasPermi('taihu:worker:add')")
    @Log(title = "作业人员", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody TzWorker tzWorker)
    {
        tzWorker.setCreateBy(getUsername());
        return toAjax(tzWorkerService.insertWorker(tzWorker));
    }

    /**
     * 修改作业人员
     */
    @PreAuthorize("@ss.hasPermi('taihu:worker:edit')")
    @Log(title = "作业人员", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody TzWorker tzWorker)
    {
        tzWorker.setUpdateBy(getUsername());
        return toAjax(tzWorkerService.updateWorker(tzWorker));
    }

    /**
     * 删除作业人员
     */
    @PreAuthorize("@ss.hasPermi('taihu:worker:remove')")
    @Log(title = "作业人员", businessType = BusinessType.DELETE)
    @DeleteMapping("/{workerIds}")
    public AjaxResult remove(@PathVariable Long[] workerIds)
    {
        return toAjax(tzWorkerService.deleteWorkerByIds(workerIds));
    }
}
