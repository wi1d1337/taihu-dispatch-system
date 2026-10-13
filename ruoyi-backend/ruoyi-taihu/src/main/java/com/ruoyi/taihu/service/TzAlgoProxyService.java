package com.ruoyi.taihu.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * algo 算法服务代理（v2：研判/参数/自动调参/看板/日报/管线）
 *
 * 地址来自配置 taihu.algo-base（默认 http://algo:9100，可由环境变量 ALGO_BASE 覆盖）。
 * algo 无鉴权，仅 compose 内网可达；对外统一走若依 JWT/权限体系。
 * 参数修改（PUT /params/{key}）由 algo 落库+版本递增+写 tz_param_log 审计。
 *
 * @author taihu
 */
@Service
public class TzAlgoProxyService
{
    private static final String API = "/api/v1";

    @Value("${taihu.algo-base:http://algo:9100}")
    private String algoBase;

    @Autowired
    @Qualifier("taihuRestTemplate")
    private RestTemplate restTemplate;

    /**
     * 分区 GeoJSON（工作台地图底图，properties 含当日研判等级）
     */
    public JSONObject getZonesGeojson()
    {
        return get("/zones/geojson");
    }

    /**
     * 今日研判：同比外推定级+basis 明细 与 概率预报(prob/q25/q50/q75) 并列
     */
    public JSONObject assessToday()
    {
        return get("/assess/today");
    }

    /**
     * 研判回验：预测等级 vs 次日实际，输出命中/误报/漏报
     */
    public JSONObject assessAccuracy(Integer days)
    {
        return get("/assess/accuracy", param("days", days));
    }

    /**
     * 参数注册表读取
     */
    public JSONObject getParams()
    {
        return get("/params");
    }

    /**
     * 参数修改：body {value, operator} → algo 写库+版本递增+tz_param_log(trigger=1)
     */
    public JSONObject putParam(String key, JSONObject body)
    {
        return put("/params/" + key, body);
    }

    /**
     * 自动调参历史：参数值与命中率随时间曲线（含每次调整证据）
     */
    public JSONObject autotuneHistory()
    {
        return get("/autotune/history");
    }

    /**
     * 立即自动调参：回测→有界候选搜索→按 mode 生效
     */
    public JSONObject autotuneRun()
    {
        return post("/autotune/run");
    }

    /**
     * 考核看板：响应时长分布/办结率/超时督办次数/日生日清率/各分区吨数
     */
    public JSONObject getDashboard(Integer days)
    {
        return get("/dashboard/eval", param("days", days));
    }

    /**
     * 日报数据：当日工单汇总+吨数+未办结清单
     */
    public JSONObject dailyReport(String date)
    {
        return get("/daily/report", param("date", date));
    }

    /**
     * 跑当日管线：ingest→assess→auto-dispatch→report→autotune
     */
    public JSONObject runPipeline(String date)
    {
        return post("/pipeline/run-daily", param("date", date));
    }

    /**
     * 重置并生成演示数据（两年合成史/人员/船/站/分区/历史工单与研判/今日自动建单）
     */
    public JSONObject seed(Integer days)
    {
        return post("/seed", param("days", days));
    }

    // ------------------------------------------------------------------
    // 请求工具
    // ------------------------------------------------------------------

    /**
     * GET 请求并解析为 JSONObject
     */
    private JSONObject get(String path, String... query)
    {
        String url = buildUrl(path, query);
        try
        {
            return JSONObject.parseObject(restTemplate.getForObject(url, String.class));
        }
        catch (RestClientException e)
        {
            throw unreachable(e);
        }
    }

    /**
     * POST 请求（无 body，仅 query 参数）并解析为 JSONObject
     */
    private JSONObject post(String path, String... query)
    {
        String url = buildUrl(path, query);
        try
        {
            return JSONObject.parseObject(restTemplate.postForObject(url, null, String.class));
        }
        catch (RestClientException e)
        {
            throw unreachable(e);
        }
    }

    /**
     * PUT 请求（JSON body）并解析为 JSONObject
     */
    private JSONObject put(String path, JSONObject body)
    {
        String url = buildUrl(path);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try
        {
            String response = restTemplate.exchange(url, HttpMethod.PUT,
                    new HttpEntity<>(body == null ? new JSONObject() : body, headers), String.class).getBody();
            return StringUtils.isEmpty(response) ? new JSONObject() : JSONObject.parseObject(response);
        }
        catch (RestClientException e)
        {
            throw unreachable(e);
        }
    }

    /**
     * algo 不可达统一口径
     */
    private ServiceException unreachable(RestClientException e)
    {
        return new ServiceException("算法服务不可达或响应异常：" + e.getMessage());
    }

    /**
     * 拼接 algo 完整地址与查询参数（跳过空值）。
     * Spring 7 注意：UriComponentsBuilder 只用 fromUriString（fromHttpUrl 已移除）。
     */
    private String buildUrl(String path, String... query)
    {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(StringUtils.stripEnd(algoBase, "/") + API + path);
        for (int i = 0; query != null && i + 1 < query.length; i += 2)
        {
            if (StringUtils.isNotEmpty(query[i + 1]))
            {
                builder.queryParam(query[i], query[i + 1]);
            }
        }
        return builder.build().encode().toUriString();
    }

    /**
     * k/v 转扁平参数数组
     */
    private String[] param(String key, Object value)
    {
        return new String[] { key, value == null ? null : String.valueOf(value) };
    }
}
