package com.ruoyi.taihu.controller;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.taihu.service.TzAlgoProxyService;

/**
 * algo 算法服务代理（v2：研判/参数/自动调参/看板/日报/管线）
 *
 * 前端不直连 algo，统一走若依后端代理（JWT 鉴权 + 权限字符）。
 * PUT 参数与 autotune/run 挂 taihu:param:edit（"算法痕迹"审计由 algo 写 tz_param_log）。
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
     * 分区 GeoJSON（工作台地图数据源，properties 含当日研判等级）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:list')")
    @GetMapping("/geojson")
    public AjaxResult geojson()
    {
        return success(tzAlgoProxyService.getZonesGeojson());
    }

    /**
     * 今日研判（同比外推定级 + 概率预报并列）
     */
    @PreAuthorize("@ss.hasPermi('taihu:assess:list')")
    @GetMapping("/assess/today")
    public AjaxResult assessToday()
    {
        return success(tzAlgoProxyService.assessToday());
    }

    /**
     * 研判回验准确度（预测等级 vs 次日实际）
     */
    @PreAuthorize("@ss.hasPermi('taihu:assess:list')")
    @GetMapping("/assess/accuracy")
    public AjaxResult assessAccuracy(@RequestParam(value = "days", required = false) Integer days)
    {
        return success(tzAlgoProxyService.assessAccuracy(days));
    }

    /**
     * 算法参数表读取（key/当前值/区间/含义/更新人/版本）
     */
    @PreAuthorize("@ss.hasPermi('taihu:param:list')")
    @GetMapping("/params")
    public AjaxResult params()
    {
        return success(tzAlgoProxyService.getParams());
    }

    /**
     * 算法参数修改（body: value, operator）→ algo 落库+版本递增+tz_param_log 审计
     */
    @PreAuthorize("@ss.hasPermi('taihu:param:edit')")
    @Log(title = "算法参数修改", businessType = BusinessType.UPDATE)
    @PutMapping("/params/{key}")
    public AjaxResult putParam(@PathVariable("key") String key, @RequestBody Map<String, Object> body)
    {
        if (body == null || body.get("value") == null)
        {
            throw new ServiceException("参数 value 必填");
        }
        JSONObject payload = new JSONObject();
        try
        {
            payload.put("value", new BigDecimal(String.valueOf(body.get("value"))));
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("参数 value 必须为数值");
        }
        payload.put("operator", body.get("operator") == null ? getUsername() : String.valueOf(body.get("operator")));
        return success(tzAlgoProxyService.putParam(key, payload));
    }

    /**
     * 自动调参历史（参数值与命中率随时间曲线+调整证据）
     */
    @PreAuthorize("@ss.hasPermi('taihu:param:list')")
    @GetMapping("/autotune/history")
    public AjaxResult autotuneHistory()
    {
        return success(tzAlgoProxyService.autotuneHistory());
    }

    /**
     * 立即自动调参（回测→有界候选搜索→按 mode 生效，写审计）
     */
    @PreAuthorize("@ss.hasPermi('taihu:param:edit')")
    @Log(title = "立即自动调参", businessType = BusinessType.OTHER)
    @PostMapping("/autotune/run")
    public AjaxResult autotuneRun()
    {
        return success(tzAlgoProxyService.autotuneRun());
    }

    /**
     * 考核看板（响应时长分布/办结率/超时督办数/日生日清/分区吨数）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:list')")
    @GetMapping("/dashboard")
    public AjaxResult dashboard(@RequestParam(value = "days", required = false) Integer days)
    {
        return success(tzAlgoProxyService.getDashboard(days));
    }

    /**
     * 日报数据（当日工单汇总+吨数+未办结清单，对齐水利局逐日上报口径）
     */
    @PreAuthorize("@ss.hasPermi('taihu:order:list')")
    @GetMapping("/daily/report")
    public AjaxResult dailyReport(@RequestParam(value = "date", required = false) String date)
    {
        if (StringUtils.isEmpty(date))
        {
            date = null;
        }
        return success(tzAlgoProxyService.dailyReport(date));
    }

    /**
     * 跑当日管线 ingest→assess→auto-dispatch→report→autotune（生成/刷新今日研判与工单）
     */
    @PreAuthorize("@ss.hasPermi('taihu:pipeline:run')")
    @Log(title = "太湖管线运行", businessType = BusinessType.OTHER)
    @PostMapping("/pipeline/run")
    public AjaxResult runPipeline(@RequestParam(value = "date", required = false) String date)
    {
        return success(tzAlgoProxyService.runPipeline(date));
    }

    /**
     * 重置并生成演示数据（730 天合成史保证"去年同期"存在）
     */
    @PreAuthorize("@ss.hasPermi('taihu:pipeline:run')")
    @Log(title = "太湖演示数据重置", businessType = BusinessType.OTHER)
    @PostMapping("/seed")
    public AjaxResult seed(@RequestParam(value = "days", required = false) Integer days)
    {
        return success(tzAlgoProxyService.seed(days));
    }
}
