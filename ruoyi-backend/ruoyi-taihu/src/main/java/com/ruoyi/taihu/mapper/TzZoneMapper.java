package com.ruoyi.taihu.mapper;

import java.util.List;
import com.ruoyi.taihu.domain.TzZone;

/**
 * 打捞分区 数据层
 *
 * @author taihu
 */
public interface TzZoneMapper
{
    /**
     * 查询打捞分区列表
     *
     * @param tzZone 打捞分区
     * @return 打捞分区集合
     */
    public List<TzZone> selectZoneList(TzZone tzZone);

    /**
     * 根据ID查询打捞分区
     *
     * @param zoneId 分区ID
     * @return 打捞分区
     */
    public TzZone selectZoneById(Long zoneId);

    /**
     * 新增打捞分区
     *
     * @param tzZone 打捞分区
     * @return 结果
     */
    public int insertZone(TzZone tzZone);

    /**
     * 修改打捞分区
     *
     * @param tzZone 打捞分区
     * @return 结果
     */
    public int updateZone(TzZone tzZone);

    /**
     * 删除打捞分区
     *
     * @param zoneId 分区ID
     * @return 结果
     */
    public int deleteZoneById(Long zoneId);

    /**
     * 批量删除打捞分区
     *
     * @param zoneIds 需要删除的数据ID
     * @return 结果
     */
    public int deleteZoneByIds(Long[] zoneIds);
}
