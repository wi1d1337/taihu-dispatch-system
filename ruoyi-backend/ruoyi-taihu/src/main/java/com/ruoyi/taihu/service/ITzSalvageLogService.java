package com.ruoyi.taihu.service;

import java.util.List;
import com.ruoyi.taihu.domain.TzSalvageLog;

/**
 * 打捞台账 服务层
 *
 * @author taihu
 */
public interface ITzSalvageLogService
{
    /**
     * 查询打捞台账列表
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
     * 新增打捞台账；带 orderId 时自动从调度单带出船/分区/日期/半天
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
     * 批量删除打捞台账
     *
     * @param logIds 需要删除的台账ID
     * @return 结果
     */
    public int deleteSalvageLogByIds(Long[] logIds);
}
