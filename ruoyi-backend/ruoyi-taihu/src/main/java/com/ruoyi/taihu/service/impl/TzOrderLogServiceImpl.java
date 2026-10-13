package com.ruoyi.taihu.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.taihu.domain.TzOrderLog;
import com.ruoyi.taihu.mapper.TzOrderLogMapper;
import com.ruoyi.taihu.service.ITzOrderLogService;

/**
 * 工单流转日志 服务实现
 *
 * @author taihu
 */
@Service
public class TzOrderLogServiceImpl implements ITzOrderLogService
{
    @Autowired
    private TzOrderLogMapper tzOrderLogMapper;

    @Override
    public int insertLog(TzOrderLog tzOrderLog)
    {
        return tzOrderLogMapper.insertOrderLog(tzOrderLog);
    }

    @Override
    public List<TzOrderLog> selectLogsByOrderId(Long orderId)
    {
        return tzOrderLogMapper.selectLogsByOrderId(orderId);
    }

    @Override
    public Date selectLastActionTime(Long orderId, List<String> actions)
    {
        return tzOrderLogMapper.selectLastActionTime(orderId, actions);
    }
}
