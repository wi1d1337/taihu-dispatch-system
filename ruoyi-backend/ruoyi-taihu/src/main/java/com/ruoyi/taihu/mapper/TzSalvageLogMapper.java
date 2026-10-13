package com.ruoyi.taihu.mapper;

import java.util.List;
import com.ruoyi.taihu.domain.TzSalvageLog;

/**
 * 打捞台账 数据层
 *
 * @author taihu
 */
public interface TzSalvageLogMapper
{
    /**
     * 查询打捞台账列表（联表带出船名/分区名）
     *
     * @param tzSalvageLog 打捞台账
     * @return 打捞台账集合
     */
    public List<TzSalvageLog> selectSalvageLogList(TzSalvageLog tzSalvageLog);

    /**
     * 根据ID查询打捞台账
     *
     * @param logId 台账ID
     * @return 打捞台账
     */
    public TzSalvageLog selectSalvageLogById(Long logId);

    /**
     * 新增打捞台账
     *
     * @param tzSalvageLog 打捞台账
     * @return 结果
     */
    public int insertSalvageLog(TzSalvageLog tzSalvageLog);

    /**
     * 修改打捞台账
     *
     * @param tzSalvageLog 打捞台账
     * @return 结果
     */
    public int updateSalvageLog(TzSalvageLog tzSalvageLog);

    /**
     * 删除打捞台账
     *
     * @param logId 台账ID
     * @return 结果
     */
    public int deleteSalvageLogById(Long logId);

    /**
     * 批量删除打捞台账
     *
     * @param logIds 需要删除的数据ID
     * @return 结果
     */
    public int deleteSalvageLogByIds(Long[] logIds);
}
