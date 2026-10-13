package com.ruoyi.taihu.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * algo 算法服务代理
 *
 * 地址来自配置 taihu.algo-base（默认 http://algo:9100，可由环境变量 ALGO_BASE 覆盖）。
 * algo 无鉴权，仅 compose 内网可达；对外统一走若依 JWT/权限体系。
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
     * 分区 GeoJSON（工作台地图底图数据）
     */
    public JSONObject getZonesGeojson()
    {
        return get("/zones/geojson");
    }

    /**
     * 运行看板：/dashboard/eval 与 /history/series 合并返回
     */
    public JSONObject getDashboard(Integer days)
    {
        JSONObject eval = get("/dashboard/eval", param("days", days));
        JSONObject history = get("/history/series", param("days", days));
        JSONObject result = new JSONObject();
        result.put("eval", eval);
        result.put("history", history);
        return result;
    }

    /**
     * 跑当日管线（ingest→forecast→solve→report）
     *
     * @param date YYYY-MM-DD，空则 algo 缺省取今天
     */
    public JSONObject runPipeline(String date)
    {
        return post("/pipeline/run-daily", param("date", date));
    }

    /**
     * 重置并生成演示数据
     *
     * @param days 生成历史天数，空则 algo 缺省 120
     */
    public JSONObject seed(Integer days)
    {
        return post("/seed", param("days", days));
    }

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
            throw new ServiceException("算法服务不可达或响应异常：" + e.getMessage());
        }
    }

    /**
     * POST 请求并解析为 JSONObject
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
            throw new ServiceException("算法服务不可达或响应异常：" + e.getMessage());
        }
    }

    /**
     * 拼接 algo 完整地址与查询参数（跳过空值）
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
