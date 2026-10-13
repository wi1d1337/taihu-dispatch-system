package com.ruoyi.taihu.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.taihu.domain.TzBoat;
import com.ruoyi.taihu.mapper.TzBoatMapper;
import com.ruoyi.taihu.service.ITzBoatService;

/**
 * 打捞船只 服务层实现
 *
 * @author taihu
 */
@Service
public class TzBoatServiceImpl implements ITzBoatService
{
    @Autowired
    private TzBoatMapper tzBoatMapper;

    @Override
    public List<TzBoat> selectBoatList(TzBoat tzBoat)
    {
        return tzBoatMapper.selectBoatList(tzBoat);
    }

    @Override
    public TzBoat selectBoatById(Long boatId)
    {
        return tzBoatMapper.selectBoatById(boatId);
    }

    @Override
    public int insertBoat(TzBoat tzBoat)
    {
        return tzBoatMapper.insertBoat(tzBoat);
    }

    @Override
    public int updateBoat(TzBoat tzBoat)
    {
        return tzBoatMapper.updateBoat(tzBoat);
    }

    @Override
    public int deleteBoatByIds(Long[] boatIds)
    {
        return tzBoatMapper.deleteBoatByIds(boatIds);
    }
}
