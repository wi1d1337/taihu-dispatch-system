package com.ruoyi.taihu.mapper;

import java.util.List;
import com.ruoyi.taihu.domain.TzAssessment;

/**
 * 研判记录 数据层（algo 写入，Java 查询）
 *
 * @author taihu
 */
public interface TzAssessmentMapper
{
    /**
     * 查询研判记录列表（date/zone/level 过滤，联表 zone_name）
     */
    public List<TzAssessment> selectAssessmentList(TzAssessment tzAssessment);

    /**
     * 根据ID查询研判记录
     */
    public TzAssessment selectAssessmentById(Long assessmentId);
}
