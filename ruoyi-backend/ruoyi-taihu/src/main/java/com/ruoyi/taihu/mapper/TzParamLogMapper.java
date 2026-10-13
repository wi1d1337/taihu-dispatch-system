package com.ruoyi.taihu.mapper;

import java.util.List;
import com.ruoyi.taihu.domain.TzParamLog;

/**
 * 参数变更审计 数据层（algo 侧写入，Java 只读查询）
 *
 * @author taihu
 */
public interface TzParamLogMapper
{
    /**
     * 查询参数变更审计列表（param_key 过滤，按时间倒序）
     */
    public List<TzParamLog> selectParamLogList(TzParamLog tzParamLog);
}
