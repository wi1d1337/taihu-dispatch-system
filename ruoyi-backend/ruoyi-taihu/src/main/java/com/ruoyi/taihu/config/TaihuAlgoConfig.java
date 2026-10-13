package com.ruoyi.taihu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * taihu 模块配置：调用 algo 算法服务的 RestTemplate
 *
 * @author taihu
 */
@Configuration
public class TaihuAlgoConfig
{
    /** 连接超时（毫秒） */
    private static final int CONNECT_TIMEOUT = 5_000;

    /** 读超时（毫秒）：管线 run-daily 含 CP-SAT 求解（演示口径超时 30s），放宽到 180s */
    private static final int READ_TIMEOUT = 180_000;

    @Bean("taihuRestTemplate")
    public RestTemplate taihuRestTemplate()
    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return new RestTemplate(factory);
    }
}
