package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.QualityReport;
import com.lixin.probe.entity.QualityRule;

import java.util.List;
import java.util.Map;

public interface QualityRuleService {

    Page<QualityRule> getRules(String probeKey, String tableName, int pageNum, int pageSize);

    List<QualityRule> getRulesByProbeKey(String probeKey);

    QualityRule createRule(QualityRule rule);

    QualityRule updateRule(QualityRule rule);

    void deleteRule(Long id);

    QualityRule getRule(Long id);

    List<QualityReport> checkRule(Long ruleId);

    List<QualityReport> checkAllEnabledRules(String probeKey);

    List<QualityReport> checkRuleWithSampleData(QualityRule rule, List<Map<String, Object>> sampleData);

    String generateFixSql(QualityRule rule);

    Map<String, Object> getQualityStatistics(String probeKey);

    Page<QualityReport> getReports(String probeKey, String tableName, Long ruleId, int pageNum, int pageSize);

    void scheduledQualityScan();
}
