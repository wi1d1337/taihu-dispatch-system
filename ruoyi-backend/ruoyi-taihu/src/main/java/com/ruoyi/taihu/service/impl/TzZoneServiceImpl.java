package com.ruoyi.taihu.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.taihu.domain.TzZone;
import com.ruoyi.taihu.mapper.TzZoneMapper;
import com.ruoyi.taihu.service.ITzZoneService;

/**
 * 打捞分区 服务层实现
 *
 * @author taihu
 */
@Service
public class TzZoneServiceImpl implements ITzZoneService
{
    @Autowired
    private TzZoneMapper tzZoneMapper;

    @Override
    public List<TzZone> selectZoneList(TzZone tzZone)
    {
        return tzZoneMapper.selectZoneList(tzZone);
    }

    @Override
    public TzZone selectZoneById(Long zoneId)
    {
        return tzZoneMapper.selectZoneById(zoneId);
    }

    @Override
    public int insertZone(TzZone tzZone)
    {
        return tzZoneMapper.insertZone(tzZone);
    }

    @Override
    public int updateZone(TzZone tzZone)
    {
        return tzZoneMapper.updateZone(tzZone);
    }

    @Override
    public int deleteZoneByIds(Long[] zoneIds)
    {
        return tzZoneMapper.deleteZoneByIds(zoneIds);
    }
}
