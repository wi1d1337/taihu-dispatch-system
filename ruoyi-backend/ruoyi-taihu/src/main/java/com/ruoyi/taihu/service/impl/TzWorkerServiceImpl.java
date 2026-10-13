package com.ruoyi.taihu.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.taihu.domain.TzWorker;
import com.ruoyi.taihu.mapper.TzWorkerMapper;
import com.ruoyi.taihu.service.ITzWorkerService;

/**
 * 作业人员 服务实现
 *
 * @author taihu
 */
@Service
public class TzWorkerServiceImpl implements ITzWorkerService
{
    @Autowired
    private TzWorkerMapper tzWorkerMapper;

    @Override
    public List<TzWorker> selectWorkerList(TzWorker tzWorker)
    {
        return tzWorkerMapper.selectWorkerList(tzWorker);
    }

    @Override
    public TzWorker selectWorkerById(Long workerId)
    {
        return tzWorkerMapper.selectWorkerById(workerId);
    }

    @Override
    public TzWorker selectWorkerByUsername(String username)
    {
        return tzWorkerMapper.selectWorkerByUsername(username);
    }

    @Override
    public int insertWorker(TzWorker tzWorker)
    {
        validateRole(tzWorker.getRole());
        tzWorker.setCreateTime(DateUtils.getNowDate());
        return tzWorkerMapper.insertWorker(tzWorker);
    }

    @Override
    public int updateWorker(TzWorker tzWorker)
    {
        if (tzWorker.getWorkerId() == null)
        {
            throw new ServiceException("人员ID不能为空");
        }
        validateRole(tzWorker.getRole());
        tzWorker.setUpdateTime(DateUtils.getNowDate());
        return tzWorkerMapper.updateWorker(tzWorker);
    }

    @Override
    public int deleteWorkerByIds(Long[] workerIds)
    {
        return tzWorkerMapper.deleteWorkerByIds(workerIds);
    }

    @Override
    public TzWorker selectCandidateLeader(Long zoneId, Long excludeLeaderId)
    {
        return tzWorkerMapper.selectCandidateLeader(zoneId, excludeLeaderId);
    }

    /**
     * 角色合法性
     */
    private void validateRole(String role)
    {
        if (!"1".equals(role) && !"2".equals(role))
        {
            throw new ServiceException("人员角色必须为 1片长 / 2打捞员");
        }
    }
}
