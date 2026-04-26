package com.lixin.probe.service;

import com.lixin.probe.dto.FilterResult;
import com.lixin.probe.entity.QualityRule;

import java.util.List;
import java.util.Map;

/**
 * 数据质量过滤引擎
 * 在同步流程中对数据执行质量校验，过滤不合格数据
 */
public interface QualityFilterEngine {

    /**
     * 对一批数据行执行质量过滤
     * @param rows 待过滤的数据行
     * @param rules 质量规则列表
     * @return 过滤结果（通过行 + 不合格行）
     */
    FilterResult filter(List<Map<String, Object>> rows, List<QualityRule> rules);

    /**
     * 对单行数据执行质量校验
     * @param row 数据行
     * @param rules 质量规则列表
     * @return 是否通过校验
     */
    boolean validateRow(Map<String, Object> row, List<QualityRule> rules);
}
