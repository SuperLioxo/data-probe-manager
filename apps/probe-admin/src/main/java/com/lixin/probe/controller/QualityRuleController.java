package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.QualityReport;
import com.lixin.probe.entity.QualityRule;
import com.lixin.probe.service.AggregationService;
import com.lixin.probe.service.QualityRuleService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quality-rules")
public class QualityRuleController {

    @Autowired
    private QualityRuleService qualityRuleService;

    @Autowired(required = false)
    private AggregationService aggregationService;

    @GetMapping
    public Result<Page<QualityRule>> list(
            @RequestParam(required = false) String probeKey,
            @RequestParam(required = false) String tableName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return Result.success(qualityRuleService.getRules(probeKey, tableName, pageNum, pageSize));
        } catch (Exception e) {
            return Result.error("查询规则失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<QualityRule> getById(@PathVariable Long id) {
        try {
            return Result.success(qualityRuleService.getRule(id));
        } catch (Exception e) {
            return Result.error("查询规则失败: " + e.getMessage());
        }
    }

    @PostMapping
    public Result<QualityRule> create(@RequestBody QualityRule rule) {
        try {
            return Result.success(qualityRuleService.createRule(rule));
        } catch (Exception e) {
            return Result.error("创建规则失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<QualityRule> update(@PathVariable Long id, @RequestBody QualityRule rule) {
        try {
            rule.setId(id);
            return Result.success(qualityRuleService.updateRule(rule));
        } catch (Exception e) {
            return Result.error("更新规则失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            qualityRuleService.deleteRule(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error("删除规则失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/check")
    public Result<List<QualityReport>> check(@PathVariable Long id) {
        try {
            return Result.success(qualityRuleService.checkRule(id));
        } catch (Exception e) {
            return Result.error("执行质量检查失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics(
            @RequestParam(required = false) String probeKey) {
        try {
            return Result.success(qualityRuleService.getQualityStatistics(probeKey));
        } catch (Exception e) {
            return Result.error("查询统计失败: " + e.getMessage());
        }
    }

    @GetMapping("/reports")
    public Result<Page<QualityReport>> reports(
            @RequestParam(required = false) String probeKey,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return Result.success(qualityRuleService.getReports(probeKey, tableName, ruleId, pageNum, pageSize));
        } catch (Exception e) {
            return Result.error("查询报告失败: " + e.getMessage());
        }
    }

    /**
     * 查询不合格记录（从汇聚库）
     */
    @GetMapping("/bad-records")
    public Result<List<Map<String, Object>>> badRecords(
            @RequestParam(required = false) Long syncTaskId,
            @RequestParam(required = false) String tableName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (aggregationService == null) {
            return Result.error("汇聚服务未启用");
        }
        try {
            List<Map<String, Object>> records = aggregationService.getBadRecords(syncTaskId, tableName, pageNum, pageSize);
            return Result.success(records);
        } catch (Exception e) {
            return Result.error("查询不合格记录失败: " + e.getMessage());
        }
    }

    /**
     * 导出不合格记录为CSV
     */
    @GetMapping("/bad-records/export")
    public void exportBadRecords(HttpServletResponse response,
                                 @RequestParam(required = false) Long syncTaskId,
                                 @RequestParam(required = false) String tableName) throws Exception {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=bad_records.csv");
        if (aggregationService == null) return;
        List<Map<String, Object>> records = aggregationService.getBadRecords(syncTaskId, tableName, 1, 10000);
        StringBuilder sb = new StringBuilder("\uFEFF"); // BOM for Excel
        if (!records.isEmpty()) {
            sb.append(String.join(",", records.get(0).keySet())).append("\n");
            for (Map<String, Object> row : records) {
                sb.append(row.values().stream()
                        .map(v -> "\"" + (v != null ? v.toString().replace("\"", "\"\"") : "") + "\"")
                        .reduce((a, b) -> a + "," + b).orElse("")).append("\n");
            }
        }
        response.getWriter().write(sb.toString());
    }
}
