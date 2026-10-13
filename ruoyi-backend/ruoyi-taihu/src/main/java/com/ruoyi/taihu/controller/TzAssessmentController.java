package com.ruoyi.taihu.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.taihu.domain.TzAssessment;
import com.ruoyi.taihu.service.ITzAssessmentService;

/**
 * 研判记录 查询（研判面板历史；今日研判与回验准确度走 /taihu/assess/* 代理 algo）
 *
 * @author taihu
 */
@RestController
@RequestMapping("/taihu/assessments")
public class TzAssessmentController extends BaseController
{
    @Autowired
    private ITzAssessmentService tzAssessmentService;

    /**
     * 查询研判记录列表（date/zone/level 过滤，联表 zone_name）
     */
    @PreAuthorize("@ss.hasPermi('taihu:assess:list')")
    @GetMapping("/list")
    public TableDataInfo list(TzAssessment tzAssessment)
    {
        startPage();
        List<TzAssessment> list = tzAssessmentService.selectAssessmentList(tzAssessment);
        return getDataTable(list);
    }

    /**
     * 获取研判记录详细信息（basis 依据明细）
     */
    @PreAuthorize("@ss.hasPermi('taihu:assess:list')")
    @GetMapping(value = "/{assessmentId}")
    public AjaxResult getInfo(@PathVariable("assessmentId") Long assessmentId)
    {
        return success(tzAssessmentService.selectAssessmentById(assessmentId));
    }
}
