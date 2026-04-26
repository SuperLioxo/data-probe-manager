package com.lixin.probe.service.impl;

import com.lixin.probe.dto.FilterResult;
import com.lixin.probe.entity.QualityRule;
import com.lixin.probe.service.QualityFilterEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class QualityFilterEngineImpl implements QualityFilterEngine {

    @Override
    public FilterResult filter(List<Map<String, Object>> rows, List<QualityRule> rules) {
        FilterResult result = new FilterResult();
        if (rules == null || rules.isEmpty()) {
            for (Map<String, Object> row : rows) {
                result.addPassed(row);
            }
            return result;
        }

        for (Map<String, Object> row : rows) {
            List<String> violations = new ArrayList<>();
            for (QualityRule rule : rules) {
                if (!Boolean.TRUE.equals(rule.getEnabled())) continue;
                String violation = checkRule(row, rule);
                if (violation != null) {
                    violations.add(violation);
                }
            }
            if (violations.isEmpty()) {
                result.addPassed(row);
            } else {
                result.addFailed(row, String.join("; ", violations), String.join("; ", violations));
            }
        }
        return result;
    }

    @Override
    public boolean validateRow(Map<String, Object> row, List<QualityRule> rules) {
        if (rules == null || rules.isEmpty()) return true;
        for (QualityRule rule : rules) {
            if (!Boolean.TRUE.equals(rule.getEnabled())) continue;
            if (checkRule(row, rule) != null) return false;
        }
        return true;
    }

    private String checkRule(Map<String, Object> row, QualityRule rule) {
        String ruleType = rule.getRuleType();
        String columnName = rule.getColumnName();

        if (columnName == null || columnName.isEmpty()) return null;
        Object value = row.get(columnName);

        return switch (ruleType) {
            case "NOT_NULL" -> (value == null || value.toString().isEmpty())
                    ? columnName + " 不能为空" : null;
            case "REGEX", "FORMAT" -> {
                if (value == null) yield null;
                String pattern = rule.getRuleParams();
                if (pattern == null || pattern.isEmpty()) yield null;
                yield Pattern.matches(pattern, value.toString())
                        ? null : columnName + " 格式不匹配: " + pattern;
            }
            case "RANGE" -> {
                if (value == null || !(value instanceof Number)) yield null;
                double num = ((Number) value).doubleValue();
                String expr = rule.getRuleParams();
                if (expr == null) yield null;
                String[] parts = expr.split(",");
                if (parts.length == 2) {
                    double min = Double.parseDouble(parts[0].trim());
                    double max = Double.parseDouble(parts[1].trim());
                    yield (num >= min && num <= max)
                            ? null : columnName + " 值 " + num + " 超出范围 [" + min + "," + max + "]";
                }
                yield null;
            }
            case "LENGTH" -> {
                if (value == null) yield null;
                int len = value.toString().length();
                String expr = rule.getRuleParams();
                if (expr == null) yield null;
                try {
                    int maxLen = Integer.parseInt(expr.trim());
                    yield len <= maxLen ? null : columnName + " 长度 " + len + " 超过限制 " + maxLen;
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case "COMPLETENESS" -> {
                long nullCount = row.values().stream().filter(Objects::isNull).count();
                double threshold = 0.5;
                String expr = rule.getRuleParams();
                if (expr != null) {
                    try { threshold = Double.parseDouble(expr.trim()); } catch (Exception ignored) {}
                }
                double nullRatio = (double) nullCount / row.size();
                yield nullRatio <= threshold ? null : "数据完整性不足: " + (nullRatio * 100) + "% 字段为空";
            }
            default -> null;
        };
    }
}
