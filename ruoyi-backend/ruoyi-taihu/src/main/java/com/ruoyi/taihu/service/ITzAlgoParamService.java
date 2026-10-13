package com.ruoyi.taihu.service;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.taihu.domain.TzAlgoParam;

/**
 * 算法参数注册表 服务层（Java 直读库，供督办阈值/处置时限等；对外修改走 algo 代理+审计）
 *
 * @author taihu
 */
public interface ITzAlgoParamService
{
    /**
     * 查询参数列表
     */
    public List<TzAlgoParam> selectParamList(TzAlgoParam tzAlgoParam);

    /**
     * 根据参数键查询
     */
    public TzAlgoParam selectParamByKey(String paramKey);

    /**
     * 取数值参数（直读库），缺失或非法时回退默认值
     *
     * @param paramKey 参数键
     * @param defaultValue 缺省值
     */
    public BigDecimal getDecimalValue(String paramKey, BigDecimal defaultValue);
}
