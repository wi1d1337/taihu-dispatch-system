package com.ruoyi.taihu.service;

import java.util.List;
import com.ruoyi.taihu.domain.TzWorker;

/**
 * 作业人员 服务层
 *
 * @author taihu
 */
public interface ITzWorkerService
{
    /**
     * 查询作业人员列表
     */
    public List<TzWorker> selectWorkerList(TzWorker tzWorker);

    /**
     * 根据ID查询作业人员
     */
    public TzWorker selectWorkerById(Long workerId);

    /**
     * 根据登录名查询在岗作业人员（H5 账号映射）
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
     * 批量删除作业人员
     */
    public int deleteWorkerByIds(Long[] workerIds);

    /**
     * 同分区候选片长（督办自动改派轮转）
     */
    public TzWorker selectCandidateLeader(Long zoneId, Long excludeLeaderId);
}
