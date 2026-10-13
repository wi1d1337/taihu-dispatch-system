package com.ruoyi.taihu.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.taihu.domain.TzAssessment;
import com.ruoyi.taihu.mapper.TzAssessmentMapper;
import com.ruoyi.taihu.service.ITzAssessmentService;

/**
 * 研判记录 服务实现（algo 写入，Java 查询）
 *
 * @author taihu
 */
@Service
public class TzAssessmentServiceImpl implements ITzAssessmentService
{
    @Autowired
    private TzAssessmentMapper tzAssessmentMapper;

    @Override
    public List<TzAssessment> selectAssessmentList(TzAssessment tzAssessment)
    {
        return tzAssessmentMapper.selectAssessmentList(tzAssessment);
    }

    @Override
    public TzAssessment selectAssessmentById(Long assessmentId)
    {
        return tzAssessmentMapper.selectAssessmentById(assessmentId);
    }
}
