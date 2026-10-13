package com.ruoyi.taihu.mapper;

import java.util.List;
import com.ruoyi.taihu.domain.TzAlgoParam;

/**
 * 算法参数注册表 数据层（对外修改走 algo API+审计；本层供 Java 直读，如督办阈值）
 *
 * @author taihu
 */
public interface TzAlgoParamMapper
{
    /**
     * 查询参数列表
     */
    public List<TzAlgoParam> selectParamList(TzAlgoParam tzAlgoParam);

    /**
     * 根据参数键查询
     */
    public TzAlgoParam selectParamByKey(String paramKey);
}
