package com.ruoyi.taihu.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.taihu.service.TzAlgoProxyService;

/**
 * algo 算法服务代理
 *
 * 前端不直连 algo，统一走若依后端代理（JWT 鉴权 + 权限字符）。
 * 地址由 taihu.algo-base 配置（默认 http://algo:9100，环境变量 ALGO_BASE 覆盖）。
 *
 * @author taihu
 */
@RestController
@RequestMapping("/taihu")
public class TzAlgoController extends BaseController
{
    @Autowired
    private TzAlgoProxyService tzAlgoProxyService;

    /**
     * 分区 GeoJSON（工作台零瓦片地图数据源）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:list')")
    @GetMapping("/geojson")
    public AjaxResult geojson()
    {
        return success(tzAlgoProxyService.getZonesGeojson());
    }

    /**
     * 运行看板（algo /dashboard/eval 与 /history/series 合并返回）
     *
     * @param days 统计窗口天数，缺省 30（history 缺省 90，以 algo 实现为准）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:list')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard(@RequestParam(value = "days", required = false) Integer days)
    {
        return success(tzAlgoProxyService.getDashboard(days));
    }

    /**
     * 跑当日管线 ingest→forecast→solve→report（生成/刷新今日调度单）
     *
     * @param date YYYY-MM-DD，缺省今天
     */
    @PreAuthorize("@ss.hasPermi('taihu:pipeline:run')")
    @Log(title = "太湖管线运行", businessType = BusinessType.OTHER)
    @PostMapping("/pipeline/run")
    public AjaxResult runPipeline(@RequestParam(value = "date", required = false) String date)
    {
        return success(tzAlgoProxyService.runPipeline(date));
    }

    /**
     * 重置并生成演示数据（分区/船/站/历史台账/预报/今日调度单）
     *
     * @param days 生成历史天数，缺省 120
     */
    @PreAuthorize("@ss.hasPermi('taihu:pipeline:run')")
    @Log(title = "太湖演示数据重置", businessType = BusinessType.OTHER)
    @PostMapping("/seed")
    public AjaxResult seed(@RequestParam(value = "days", required = false) Integer days)
    {
        return success(tzAlgoProxyService.seed(days));
    }
}
