package com.lixin.probe.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lixin.probe.agent.dto.QualityRuleDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 质量校验器 - 应用质量规则到数据行
 */
@Component
public class QualityValidator {

    private static final Logger log = LoggerFactory.getLogger(QualityValidator.class);

    /**
     * 校验单行数据，返回不通过的规则列表
     */
    public List<QualityRuleDTO> validate(Map<String, Object> row, String databaseName, String tableName, List<QualityRuleDTO> rules) {
        List<QualityRuleDTO> violations = new ArrayList<>();
        for (QualityRuleDTO rule : rules) {
            if (!matchesScope(rule, databaseName, tableName)) continue;
            if (!checkRule(row, rule)) {
                violations.add(rule);
            }
        }
        return violations;
    }

    private boolean matchesScope(QualityRuleDTO rule, String databaseName, String tableName) {
        if (rule.getDatabaseName() != null && !rule.getDatabaseName().isEmpty()
                && !rule.getDatabaseName().equals(databaseName)) return false;
        if (rule.getTableName() != null && !rule.getTableName().isEmpty()
                && !rule.getTableName().equals(tableName)) return false;
        return true;
    }

    private boolean checkRule(Map<String, Object> row, QualityRuleDTO rule) {
        Object value = row.get(rule.getColumnName());
        JSONObject params = rule.getRuleParams() != null ? JSON.parseObject(rule.getRuleParams()) : new JSONObject();

        switch (rule.getRuleType()) {
            case "NOT_NULL":
                return value != null && !value.toString().isEmpty();
            case "REGEX":
                if (value == null) return true;
                String regex = params.getString("pattern");
                return regex == null || Pattern.matches(regex, value.toString());
            case "RANGE":
                return checkRange(value, params);
            case "ENUM":
                if (value == null) return true;
                String allowed = params.getString("values");
                if (allowed == null) return true;
                Set<String> values = new HashSet<>(Arrays.asList(allowed.split(",")));
                return values.contains(value.toString().trim());
            case "LENGTH":
                return checkLength(value, params);
            case "TYPE_CHECK":
                return checkType(value, params);
            default:
                return true;
        }
    }

    private boolean checkRange(Object value, JSONObject params) {
        if (value == null) return true;
        try {
            BigDecimal num = new BigDecimal(value.toString());
            if (params.containsKey("min")) {
                BigDecimal min = new BigDecimal(params.getString("min"));
                if (num.compareTo(min) < 0) return false;
            }
            if (params.containsKey("max")) {
                BigDecimal max = new BigDecimal(params.getString("max"));
                if (num.compareTo(max) > 0) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean checkLength(Object value, JSONObject params) {
        if (value == null) return true;
        int len = value.toString().length();
        Integer minLen = params.getInteger("minLength");
        Integer maxLen = params.getInteger("maxLength");
        if (minLen != null && len < minLen) return false;
        if (maxLen != null && len > maxLen) return false;
        return true;
    }

    private boolean checkType(Object value, JSONObject params) {
        if (value == null) return true;
        String expectedType = params.getString("type");
        if (expectedType == null) return true;
        String str = value.toString();
        switch (expectedType.toUpperCase()) {
            case "INTEGER":
                try { Long.parseLong(str); return true; } catch (NumberFormatException e) { return false; }
            case "DECIMAL":
                try { new BigDecimal(str); return true; } catch (NumberFormatException e) { return false; }
            case "BOOLEAN":
                return "true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str);
            case "DATE":
                return str.matches("\\d{4}-\\d{2}-\\d{2}.*");
            default:
                return true;
        }
    }
}
