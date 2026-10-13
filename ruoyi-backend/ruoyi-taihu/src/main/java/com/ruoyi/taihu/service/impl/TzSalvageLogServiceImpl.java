package com.ruoyi.taihu.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.taihu.domain.TzDispatchOrder;
import com.ruoyi.taihu.domain.TzSalvageLog;
import com.ruoyi.taihu.mapper.TzDispatchOrderMapper;
import com.ruoyi.taihu.mapper.TzSalvageLogMapper;
import com.ruoyi.taihu.service.ITzSalvageLogService;

/**
 * 打捞台账 服务层实现
 *
 * 台账是全系统唯一人工录入点：带 orderId 时自动从调度单带出
 * 船只/分区/日期/半天，现场只补实收数。
 *
 * @author taihu
 */
@Service
public class TzSalvageLogServiceImpl implements ITzSalvageLogService
{
    @Autowired
    private TzSalvageLogMapper tzSalvageLogMapper;

    @Autowired
    private TzDispatchOrderMapper tzDispatchOrderMapper;

    @Override
    public List<TzSalvageLog> selectSalvageLogList(TzSalvageLog tzSalvageLog)
    {
        return tzSalvageLogMapper.selectSalvageLogList(tzSalvageLog);
    }

    @Override
    public TzSalvageLog selectSalvageLogById(Long logId)
    {
        return tzSalvageLogMapper.selectSalvageLogById(logId);
    }

    @Override
    public int insertSalvageLog(TzSalvageLog tzSalvageLog)
    {
        // 带 orderId：校验调度单存在，并强制以调度单口径带出船/分区/日期/半天
        if (tzSalvageLog.getOrderId() != null)
        {
            TzDispatchOrder order = tzDispatchOrderMapper.selectDispatchOrderById(tzSalvageLog.getOrderId());
            if (order == null)
            {
                throw new ServiceException(String.format("关联调度单[%d]不存在", tzSalvageLog.getOrderId()));
            }
            tzSalvageLog.setBoatId(order.getBoatId());
            tzSalvageLog.setZoneId(order.getZoneId());
            tzSalvageLog.setLogDate(order.getOrderDate());
            tzSalvageLog.setHalfDay(order.getHalfDay());
        }
        // 完整性兜底：带出后仍缺关键字段（计划外作业未填）则拒绝
        if (tzSalvageLog.getBoatId() == null || tzSalvageLog.getZoneId() == null || tzSalvageLog.getLogDate() == null)
        {
            throw new ServiceException("船只、分区、作业日期不能为空（计划外作业请直接填写，或关联有效的调度单）");
        }
        tzSalvageLog.setCreateTime(DateUtils.getNowDate());
        return tzSalvageLogMapper.insertSalvageLog(tzSalvageLog);
    }

    @Override
    public int updateSalvageLog(TzSalvageLog tzSalvageLog)
    {
        tzSalvageLog.setUpdateTime(DateUtils.getNowDate());
        return tzSalvageLogMapper.updateSalvageLog(tzSalvageLog);
    }

    @Override
    public int deleteSalvageLogByIds(Long[] logIds)
    {
        return tzSalvageLogMapper.deleteSalvageLogByIds(logIds);
    }
}
