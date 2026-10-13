package com.ruoyi.taihu.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.taihu.domain.TzWorker;

/**
 * 作业人员 数据层
 *
 * @author taihu
 */
public interface TzWorkerMapper
{
    /**
     * 查询作业人员列表（联表 zone_name）
     */
    public List<TzWorker> selectWorkerList(TzWorker tzWorker);

    /**
     * 根据ID查询作业人员
     */
    public TzWorker selectWorkerById(Long workerId);

    /**
     * 根据登录名查询作业人员（H5 账号映射）
     */
    public TzWorker selectWorkerByUsername(String username);

    /**
     * 新增作业人员
     */
    public int insertWorker(TzWorker tzWorker);

    /**
     * 修改作业人员
     */
    public int updateWorker(TzWorker tzWorker);

    /**
     * 删除作业人员
     */
    public int deleteWorkerById(Long workerId);

    /**
     * 批量删除作业人员
     */
    public int deleteWorkerByIds(Long[] workerIds);

    /**
     * 同分区候选片长（督办自动改派轮转：优先取 ID 大于当前片长的下一位，无则从头轮转）
     *
     * @param zoneId 分区
     * @param excludeLeaderId 当前片长（排除）
     */
    public TzWorker selectCandidateLeader(@Param("zoneId") Long zoneId, @Param("excludeLeaderId") Long excludeLeaderId);
}
