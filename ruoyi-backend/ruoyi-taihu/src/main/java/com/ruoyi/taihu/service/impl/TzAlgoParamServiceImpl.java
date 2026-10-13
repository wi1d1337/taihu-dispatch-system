package com.ruoyi.taihu.service.impl;

import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.taihu.domain.TzAlgoParam;
import com.ruoyi.taihu.mapper.TzAlgoParamMapper;
import com.ruoyi.taihu.service.ITzAlgoParamService;

/**
 * 算法参数注册表 服务实现（Java 直读库；对外修改走 algo 代理+tz_param_log 审计）
 *
 * @author taihu
 */
@Service
public class TzAlgoParamServiceImpl implements ITzAlgoParamService
{
    private static final Logger log = LoggerFactory.getLogger(TzAlgoParamServiceImpl.class);

    @Autowired
    private TzAlgoParamMapper tzAlgoParamMapper;

    @Override
    public List<TzAlgoParam> selectParamList(TzAlgoParam tzAlgoParam)
    {
        return tzAlgoParamMapper.selectParamList(tzAlgoParam);
    }

    @Override
    public TzAlgoParam selectParamByKey(String paramKey)
    {
        return tzAlgoParamMapper.selectParamByKey(paramKey);
    }

    @Override
    public BigDecimal getDecimalValue(String paramKey, BigDecimal defaultValue)
    {
        try
        {
            TzAlgoParam param = tzAlgoParamMapper.selectParamByKey(paramKey);
            if (param != null && param.getParamValue() != null)
            {
                return param.getParamValue();
            }
        }
        catch (Exception e)
        {
            log.warn("读取算法参数[{}]失败，使用缺省值 {}：{}", paramKey, defaultValue, e.getMessage());
        }
        return defaultValue;
    }
}
