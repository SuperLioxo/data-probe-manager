package com.lixin.probe.agent.dto;

import java.io.Serializable;

/**
 * 质量规则 DTO - 从 Admin 同步到 Agent
 */
public class QualityRuleDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String ruleName;
    private String probeKey;
    private String databaseName;
    private String tableName;
    private String columnName;
    /** NOT_NULL, REGEX, RANGE, ENUM, LENGTH, TYPE_CHECK */
    private String ruleType;
    /** JSON格式参数 */
    private String ruleParams;
    private String severity;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getProbeKey() { return probeKey; }
    public void setProbeKey(String probeKey) { this.probeKey = probeKey; }
    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getRuleParams() { return ruleParams; }
    public void setRuleParams(String ruleParams) { this.ruleParams = ruleParams; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
}
