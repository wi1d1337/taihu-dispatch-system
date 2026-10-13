package com.ruoyi.taihu.service;

import java.util.List;
import com.ruoyi.taihu.domain.TzAssessment;

/**
 * 研判记录 服务层（algo 写入，Java 查询）
 *
 * @author taihu
 */
public interface ITzAssessmentService
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
