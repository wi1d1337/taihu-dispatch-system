package com.ruoyi.taihu.service;

import java.util.List;
import com.ruoyi.taihu.domain.TzStation;

/**
 * 藻水分离站 服务层
 *
 * @author taihu
 */
public interface ITzStationService
{
    /**
     * 查询藻水分离站列表
     *
     * @param tzStation 藻水分离站
     * @return 藻水分离站集合
     */
    public List<TzStation> selectStationList(TzStation tzStation);

    /**
     * 根据ID查询藻水分离站
     *
     * @param stationId 分离站ID
     * @return 藻水分离站
     */
    public TzStation selectStationById(Long stationId);

    /**
     * 新增藻水分离站
     *
     * @param tzStation 藻水分离站
     * @return 结果
     */
    public int insertStation(TzStation tzStation);

    /**
     * 修改藻水分离站
     *
     * @param tzStation 藻水分离站
     * @return 结果
     */
    public int updateStation(TzStation tzStation);

    /**
     * 批量删除藻水分离站
     *
     * @param stationIds 需要删除的分离站ID
     * @return 结果
     */
    public int deleteStationByIds(Long[] stationIds);
}
