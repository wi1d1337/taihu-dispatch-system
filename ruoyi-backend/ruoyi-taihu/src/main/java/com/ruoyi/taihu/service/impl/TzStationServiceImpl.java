package com.ruoyi.taihu.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.taihu.domain.TzStation;
import com.ruoyi.taihu.mapper.TzStationMapper;
import com.ruoyi.taihu.service.ITzStationService;

/**
 * 藻水分离站 服务层实现
 *
 * @author taihu
 */
@Service
public class TzStationServiceImpl implements ITzStationService
{
    @Autowired
    private TzStationMapper tzStationMapper;

    @Override
    public List<TzStation> selectStationList(TzStation tzStation)
    {
        return tzStationMapper.selectStationList(tzStation);
    }

    @Override
    public TzStation selectStationById(Long stationId)
    {
        return tzStationMapper.selectStationById(stationId);
    }

    @Override
    public int insertStation(TzStation tzStation)
    {
        return tzStationMapper.insertStation(tzStation);
    }

    @Override
    public int updateStation(TzStation tzStation)
    {
        return tzStationMapper.updateStation(tzStation);
    }

    @Override
    public int deleteStationByIds(Long[] stationIds)
    {
        return tzStationMapper.deleteStationByIds(stationIds);
    }
}
