package com.ruoyi.taihu.service;

import java.util.List;
import com.ruoyi.taihu.domain.TzBoat;

/**
 * 打捞船只 服务层
 *
 * @author taihu
 */
public interface ITzBoatService
{
    /**
     * 查询打捞船只列表
     *
     * @param tzBoat 打捞船只
     * @return 打捞船只集合
     */
    public List<TzBoat> selectBoatList(TzBoat tzBoat);

    /**
     * 根据ID查询打捞船只
     *
     * @param boatId 船只ID
     * @return 打捞船只
     */
    public TzBoat selectBoatById(Long boatId);

    /**
     * 新增打捞船只
     *
     * @param tzBoat 打捞船只
     * @return 结果
     */
    public int insertBoat(TzBoat tzBoat);

    /**
     * 修改打捞船只
     *
     * @param tzBoat 打捞船只
     * @return 结果
     */
    public int updateBoat(TzBoat tzBoat);

    /**
     * 批量删除打捞船只
     *
     * @param boatIds 需要删除的船只ID
     * @return 结果
     */
    public int deleteBoatByIds(Long[] boatIds);
}
