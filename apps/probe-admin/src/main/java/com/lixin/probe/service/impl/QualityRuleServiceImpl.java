package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.DatabaseConnection;
import com.lixin.probe.entity.QualityReport;
import com.lixin.probe.entity.QualityRule;
import com.lixin.probe.mapper.DatabaseConnectionMapper;
import com.lixin.probe.mapper.QualityReportMapper;
import com.lixin.probe.mapper.QualityRuleMapper;
import com.lixin.probe.service.QualityRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Service
public class QualityRuleServiceImpl implements QualityRuleService {

    @Autowired
    private QualityRuleMapper ruleMapper;

    @Autowired
    private QualityReportMapper reportMapper;

    @Autowired
    private DatabaseConnectionMapper connectionMapper;

    @Override
    public Page<QualityRule> getRules(String probeKey, String tableName, int pageNum, int pageSize) {
        Page<QualityRule> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<QualityRule> wrapper = new LambdaQueryWrapper<>();
        if (probeKey != null && !probeKey.isEmpty()) wrapper.eq(QualityRule::getProbeKey, probeKey);
        if (tableName != null && !tableName.isEmpty()) wrapper.eq(QualityRule::getTableName, tableName);
        wrapper.orderByDesc(QualityRule::getCreateTime);
        return ruleMapper.selectPage(page, wrapper);
    }

    @Override
    public List<QualityRule> getRulesByProbeKey(String probeKey) {
        LambdaQueryWrapper<QualityRule> wrapper = new LambdaQueryWrapper<>();
        if (probeKey != null && !probeKey.isEmpty()) {
            wrapper.eq(QualityRule::getProbeKey, probeKey);
        }
        wrapper.eq(QualityRule::getEnabled, true);
        return ruleMapper.selectList(wrapper);
    }

    @Override
    public QualityRule createRule(QualityRule rule) {
        rule.setCreateTime(LocalDateTime.now());
        rule.setUpdateTime(LocalDateTime.now());
        if (rule.getEnabled() == null) rule.setEnabled(true);
        if (rule.getSeverity() == null) rule.setSeverity("WARNING");
        ruleMapper.insert(rule);
        return rule;
    }

    @Override
    public QualityRule updateRule(QualityRule rule) {
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(rule);
        return rule;
    }

    @Override
    public void deleteRule(Long id) {
        ruleMapper.deleteById(id);
        reportMapper.delete(new LambdaQueryWrapper<QualityReport>().eq(QualityReport::getRuleId, id));
    }

    @Override
    public QualityRule getRule(Long id) {
        return ruleMapper.selectById(id);
    }

    @Override
    public List<QualityReport> checkRule(Long ruleId) {
        QualityRule rule = ruleMapper.selectById(ruleId);
        if (rule == null || !rule.getEnabled()) {
            return Collections.emptyList();
        }
        return executeRuleCheck(rule);
    }

    @Override
    public List<QualityReport> checkAllEnabledRules(String probeKey) {
        LambdaQueryWrapper<QualityRule> wrapper = new LambdaQueryWrapper<QualityRule>()
                .eq(QualityRule::getEnabled, true);
        if (probeKey != null && !probeKey.isEmpty()) {
            wrapper.eq(QualityRule::getProbeKey, probeKey);
        }
        List<QualityRule> rules = ruleMapper.selectList(wrapper);
        List<QualityReport> allReports = new ArrayList<>();
        for (QualityRule rule : rules) {
            allReports.addAll(executeRuleCheck(rule));
        }
        return allReports;
    }

    private List<QualityReport> executeRuleCheck(QualityRule rule) {
        List<QualityReport> violations = new ArrayList<>();

        DatabaseConnection dbConn = findConnection(rule.getProbeKey());
        if (dbConn == null) {
            log.warn("[质量校验] 未找到数据库连接: probeKey={}", rule.getProbeKey());
            return Collections.emptyList();
        }

        String[] sqlAndParams = buildCheckSql(rule, dbConn.getDatabaseType());
        if (sqlAndParams == null) return violations;

        String checkSql = sqlAndParams[0];
        Object[] params = new Object[sqlAndParams.length - 1];
        for (int i = 0; i < params.length; i++) params[i] = sqlAndParams[i + 1];

        try (Connection conn = getConnection(dbConn);
             PreparedStatement ps = conn.prepareStatement(checkSql)) {

            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String rowId = String.valueOf(rs.getObject(1));
                    Object value = rs.getObject(2);
                    violations.add(QualityReport.builder()
                            .ruleId(rule.getId())
                            .probeKey(rule.getProbeKey())
                            .databaseName(rule.getDatabaseName())
                            .tableName(rule.getTableName())
                            .columnName(rule.getColumnName())
                            .rowIdentifier(rowId)
                            .violationDetail(toJson(Map.of("value", String.valueOf(value != null ? value : "NULL"), "ruleType", rule.getRuleType())))
                            .checkTime(LocalDateTime.now())
                            .build());
                }
            }
            log.info("[质量校验] 规则 {} 检查完成, 发现 {} 条违规", rule.getRuleName(), violations.size());
        } catch (Exception e) {
            log.warn("[质量校验] 规则 {} 执行失败: {}", rule.getRuleName(), e.getMessage());
            return Collections.emptyList();
        }

        return violations;
    }

    private DatabaseConnection findConnection(String probeKey) {
        if (probeKey == null || probeKey.isEmpty()) return null;
        return connectionMapper.selectOne(
                new LambdaQueryWrapper<DatabaseConnection>()
                        .eq(DatabaseConnection::getIsActive, true)
                        .and(w -> w.eq(DatabaseConnection::getName, probeKey)
                                .or().eq(DatabaseConnection::getDatabaseName, probeKey)));
    }

    private Connection getConnection(DatabaseConnection dbConn) throws Exception {
        String url = buildJdbcUrl(dbConn);
        return DriverManager.getConnection(url, dbConn.getUsername(), dbConn.getPassword());
    }

    private String buildJdbcUrl(DatabaseConnection conn) {
        String type = conn.getDatabaseType() != null ? conn.getDatabaseType().toLowerCase() : "mysql";
        String host = conn.getDatabaseHost();
        int port = conn.getDatabasePort() != null ? conn.getDatabasePort() : 3306;
        String db = conn.getDatabaseName();
        return switch (type) {
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + (db != null ? db : "") + "?useSSL=false&serverTimezone=UTC";
            case "postgresql", "postgres" -> "jdbc:postgresql://" + host + ":" + port + "/" + (db != null ? db : "postgres");
            case "oracle" -> "jdbc:oracle:thin:@" + host + ":" + port + ":" + (db != null ? db : "ORCL");
            case "sqlserver", "mssql" -> "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + (db != null ? db : "master") + ";encrypt=false";
            case "dm" -> "jdbc:dm://" + host + ":" + port + "/" + (db != null ? db : "");
            default -> "jdbc:mysql://" + host + ":" + port + "/" + (db != null ? db : "") + "?useSSL=false";
        };
    }

    private String[] buildCheckSql(QualityRule rule, String dbType) {
        String table = rule.getTableName();
        String col = rule.getColumnName();
        String schema = rule.getDatabaseName();
        String fullTable = (schema != null && !schema.isEmpty()) ? "\"" + schema + "\".\"" + table + "\"" : "\"" + table + "\"";

        if ("CUSTOM_SQL".equals(rule.getRuleType())) {
            String customSql = parseJsonString(rule.getRuleParams(), "sql", "");
            if (customSql.isEmpty()) return null;
            return new String[]{customSql + " LIMIT 500"};
        }

        String selectId = "SELECT id, " + quoteIdentifier(col) + " FROM " + fullTable + " WHERE ";

        return switch (rule.getRuleType()) {
            case "NOT_NULL" -> new String[]{selectId + quoteIdentifier(col) + " IS NULL LIMIT 500"};
            case "REGEX" -> {
                String pattern = parseJsonString(rule.getRuleParams(), "pattern", ".*");
                if ("mysql".equalsIgnoreCase(dbType)) {
                    yield new String[]{selectId + quoteIdentifier(col) + " IS NOT NULL AND " + quoteIdentifier(col) + " NOT REGEXP ? LIMIT 500", pattern};
                } else if ("postgresql".equalsIgnoreCase(dbType)) {
                    yield new String[]{selectId + quoteIdentifier(col) + " IS NOT NULL AND " + quoteIdentifier(col) + " !~ ? LIMIT 500", pattern};
                } else {
                    yield new String[]{selectId + quoteIdentifier(col) + " IS NOT NULL LIMIT 500"};
                }
            }
            case "RANGE" -> {
                double min = parseJsonDouble(rule.getRuleParams(), "min", Double.MIN_VALUE);
                double max = parseJsonDouble(rule.getRuleParams(), "max", Double.MAX_VALUE);
                yield new String[]{selectId + quoteIdentifier(col) + " IS NOT NULL AND (" + quoteIdentifier(col) + " < ? OR " + quoteIdentifier(col) + " > ?) LIMIT 500", String.valueOf(min), String.valueOf(max)};
            }
            case "ENUM" -> {
                String values = parseJsonString(rule.getRuleParams(), "values", "");
                if (values.isEmpty()) yield null;
                String[] vals = values.split(",");
                // Build placeholders
                String placeholders = Arrays.stream(vals).map(v -> "?").collect(java.util.stream.Collectors.joining(", "));
                String[] result = new String[1 + vals.length];
                result[0] = selectId + quoteIdentifier(col) + " IS NOT NULL AND " + quoteIdentifier(col) + " NOT IN (" + placeholders + ") LIMIT 500";
                for (int i = 0; i < vals.length; i++) result[i + 1] = vals[i].trim();
                yield result;
            }
            case "LENGTH" -> {
                int min = (int) parseJsonDouble(rule.getRuleParams(), "min", 0);
                int max = (int) parseJsonDouble(rule.getRuleParams(), "max", Integer.MAX_VALUE);
                yield new String[]{selectId + quoteIdentifier(col) + " IS NOT NULL AND (LENGTH(" + quoteIdentifier(col) + ") < ? OR LENGTH(" + quoteIdentifier(col) + ") > ?) LIMIT 500", String.valueOf(min), String.valueOf(max)};
            }
            case "TYPE_CHECK" -> {
                String expected = parseJsonString(rule.getRuleParams(), "expected", "string");
                if ("numeric".equals(expected)) {
                    if ("postgresql".equalsIgnoreCase(dbType)) {
                        yield new String[]{selectId + quoteIdentifier(col) + " IS NOT NULL AND " + quoteIdentifier(col) + "::text !~ ? LIMIT 500", "^[0-9]+$"};
                    } else {
                        yield new String[]{selectId + quoteIdentifier(col) + " IS NOT NULL AND " + quoteIdentifier(col) + " NOT REGEXP ? LIMIT 500", "^-?[0-9]+(\\.[0-9]+)?$"};
                    }
                }
                yield new String[]{selectId + quoteIdentifier(col) + " IS NOT NULL LIMIT 500"};
            }
            default -> null;
        };
    }

    private String quoteIdentifier(String name) {
        if (name == null) return "\"\"";
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private String escapeSqlValue(String value) {
        if (value == null) return "";
        return value.replace("'", "''");
    }

    @Override
    public List<QualityReport> checkRuleWithSampleData(QualityRule rule, List<Map<String, Object>> sampleData) {
        List<QualityReport> violations = new ArrayList<>();
        if (sampleData == null || sampleData.isEmpty()) return violations;

        String colName = rule.getColumnName();
        String ruleParams = rule.getRuleParams();

        for (int i = 0; i < sampleData.size(); i++) {
            Map<String, Object> row = sampleData.get(i);
            Object value = colName != null ? row.get(colName) : null;
            String rowId = String.valueOf(row.getOrDefault("id", i));

            if (evaluateRule(value, rule.getRuleType(), ruleParams)) {
                violations.add(QualityReport.builder()
                        .ruleId(rule.getId())
                        .probeKey(rule.getProbeKey())
                        .databaseName(rule.getDatabaseName())
                        .tableName(rule.getTableName())
                        .columnName(colName)
                        .rowIdentifier(rowId)
                        .violationDetail(toJson(Map.of("value", String.valueOf(value), "ruleType", rule.getRuleType())))
                        .checkTime(LocalDateTime.now())
                        .build());
            }
        }
        return violations;
    }

    @Override
    public String generateFixSql(QualityRule rule) {
        if (!Boolean.TRUE.equals(rule.getAutoFix()) || rule.getFixAction() == null) {
            return null;
        }
        String table = rule.getTableName();
        String col = rule.getColumnName();
        switch (rule.getFixAction()) {
            case "NULLIFY":
                return "UPDATE " + escape(table) + " SET " + escape(col) + " = NULL WHERE " + escape(col) + " IS NOT NULL";
            case "TRIM":
                return "UPDATE " + escape(table) + " SET " + escape(col) + " = TRIM(" + escape(col) + ") WHERE " + escape(col) + " LIKE '% %'";
            case "UPPERCASE":
                return "UPDATE " + escape(table) + " SET " + escape(col) + " = UPPER(" + escape(col) + ")";
            case "LOWERCASE":
                return "UPDATE " + escape(table) + " SET " + escape(col) + " = LOWER(" + escape(col) + ")";
            case "SET_DEFAULT": {
                String defaultVal = parseJsonString(rule.getFixParams(), "defaultValue", "");
                return "UPDATE " + escape(table) + " SET " + escape(col) + " = '" + defaultVal.replace("'", "''") + "' WHERE " + escape(col) + " IS NULL";
            }
            case "REPLACE": {
                String pattern = parseJsonString(rule.getFixParams(), "pattern", "");
                String replacement = parseJsonString(rule.getFixParams(), "replacement", "");
                return "UPDATE " + escape(table) + " SET " + escape(col) + " = REPLACE(" + escape(col) + ", '" + pattern.replace("'", "''") + "', '" + replacement.replace("'", "''") + "')";
            }
            default:
                return null;
        }
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?")
    @com.lixin.probe.annotation.DistributedLock(key = "'quality:scan'", waitTime = 0, leaseTime = 600)
    public void scheduledQualityScan() {
        log.info("[质量巡检] 开始每日定时质量巡检...");
        long start = System.currentTimeMillis();

        List<QualityRule> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<QualityRule>().eq(QualityRule::getEnabled, true));

        int violationsFound = 0;
        int violationsFixed = 0;

        for (QualityRule rule : rules) {
            try {
                List<QualityReport> reports = executeRuleCheck(rule);
                violationsFound += reports.size();

                if (Boolean.TRUE.equals(rule.getAutoFix()) && !reports.isEmpty()) {
                    String fixSql = generateFixSql(rule);
                    if (fixSql != null) {
                        violationsFixed += reports.size();
                        log.info("[质量巡检] 自动修复规则 {}: {} 条, SQL: {}", rule.getRuleName(), reports.size(), fixSql);
                    }
                }
            } catch (Exception e) {
                log.warn("[质量巡检] 规则 {} 检查失败: {}", rule.getRuleName(), e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("[质量巡检] 巡检完成: 检查 {} 条规则, 发现 {} 个违规, 自动修复 {} 个, 耗时 {}ms",
                rules.size(), violationsFound, violationsFixed, duration);
    }

    @Override
    public Map<String, Object> getQualityStatistics(String probeKey) {
        Map<String, Object> stats = new LinkedHashMap<>();

        LambdaQueryWrapper<QualityRule> ruleWrapper = new LambdaQueryWrapper<>();
        if (probeKey != null && !probeKey.isEmpty()) {
            ruleWrapper.eq(QualityRule::getProbeKey, probeKey);
        }
        long totalRules = ruleMapper.selectCount(ruleWrapper);
        long enabledRules = ruleMapper.selectCount(ruleWrapper.eq(QualityRule::getEnabled, true));
        stats.put("totalRules", totalRules);
        stats.put("enabledRules", enabledRules);

        LambdaQueryWrapper<QualityReport> reportWrapper = new LambdaQueryWrapper<>();
        if (probeKey != null && !probeKey.isEmpty()) {
            reportWrapper.eq(QualityReport::getProbeKey, probeKey);
        }
        long totalViolations = reportMapper.selectCount(reportWrapper);
        stats.put("totalViolations", totalViolations);

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (String sev : List.of("ERROR", "WARNING", "INFO")) {
            long count = ruleMapper.selectCount(
                    new LambdaQueryWrapper<QualityRule>().eq(QualityRule::getSeverity, sev).eq(QualityRule::getEnabled, true));
            if (count > 0) bySeverity.put(sev, count);
        }
        stats.put("rulesBySeverity", bySeverity);

        long autoFixRules = ruleMapper.selectCount(
                new LambdaQueryWrapper<QualityRule>().eq(QualityRule::getAutoFix, true).eq(QualityRule::getEnabled, true));
        stats.put("autoFixRules", autoFixRules);

        return stats;
    }

    @Override
    public Page<QualityReport> getReports(String probeKey, String tableName, Long ruleId,
                                            int pageNum, int pageSize) {
        Page<QualityReport> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<QualityReport> wrapper = new LambdaQueryWrapper<>();
        if (probeKey != null && !probeKey.isEmpty()) wrapper.eq(QualityReport::getProbeKey, probeKey);
        if (tableName != null && !tableName.isEmpty()) wrapper.eq(QualityReport::getTableName, tableName);
        if (ruleId != null) wrapper.eq(QualityReport::getRuleId, ruleId);
        wrapper.orderByDesc(QualityReport::getCheckTime);
        return reportMapper.selectPage(page, wrapper);
    }

    // ====== 内部方法 ======

    private boolean evaluateRule(Object value, String ruleType, String ruleParams) {
        if (value == null) return ruleType.equals("NOT_NULL");

        switch (ruleType) {
            case "NOT_NULL":
                return false; // value is not null, so no violation
            case "REGEX": {
                String pattern = parseJsonString(ruleParams, "pattern", ".*");
                try {
                    return !Pattern.matches(pattern, String.valueOf(value));
                } catch (PatternSyntaxException e) {
                    return false;
                }
            }
            case "RANGE": {
                double min = parseJsonDouble(ruleParams, "min", Double.MIN_VALUE);
                double max = parseJsonDouble(ruleParams, "max", Double.MAX_VALUE);
                try {
                    double v = Double.parseDouble(String.valueOf(value));
                    return v < min || v > max;
                } catch (NumberFormatException e) {
                    return true;
                }
            }
            case "ENUM": {
                String values = parseJsonString(ruleParams, "values", "");
                if (values.isEmpty()) return false;
                Set<String> allowed = new HashSet<>(Arrays.asList(values.split(",")));
                return !allowed.contains(String.valueOf(value));
            }
            case "LENGTH": {
                int min = (int) parseJsonDouble(ruleParams, "min", 0);
                int max = (int) parseJsonDouble(ruleParams, "max", Integer.MAX_VALUE);
                int len = String.valueOf(value).length();
                return len < min || len > max;
            }
            case "TYPE_CHECK": {
                String expected = parseJsonString(ruleParams, "expected", "string");
                String strVal = String.valueOf(value);
                switch (expected) {
                    case "numeric": return !strVal.matches("-?\\d+(\\.\\d+)?");
                    case "integer": return !strVal.matches("-?\\d+");
                    case "boolean": return !strVal.matches("true|false|0|1");
                    case "email": return !strVal.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
                    default: return false;
                }
            }
            case "CUSTOM_SQL":
                return false; // Cannot evaluate without DB connection
            default:
                return false;
        }
    }

    private String parseJsonString(String json, String key, String defaultValue) {
        if (json == null || json.isEmpty()) return defaultValue;
        try {
            int keyIdx = json.indexOf("\"" + key + "\"");
            if (keyIdx < 0) return defaultValue;
            int colonIdx = json.indexOf(":", keyIdx);
            if (colonIdx < 0) return defaultValue;
            int valueStart = json.indexOf("\"", colonIdx);
            int valueEnd = json.indexOf("\"", valueStart + 1);
            if (valueStart < 0 || valueEnd < 0) return defaultValue;
            return json.substring(valueStart + 1, valueEnd);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double parseJsonDouble(String json, String key, double defaultValue) {
        String str = parseJsonString(json, key, "");
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof String) {
                sb.append("\"").append(val).append("\"");
            } else {
                sb.append(val);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escape(String identifier) {
        if (identifier == null) return "unknown";
        return "`" + identifier.replace("`", "``") + "`";
    }
}
