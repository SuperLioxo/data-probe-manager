-- 数据质量过滤相关表 (PostgreSQL)

-- 质量规则表
CREATE TABLE IF NOT EXISTS quality_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(200) NOT NULL,
    probe_key VARCHAR(255),
    database_name VARCHAR(255),
    table_name VARCHAR(255),
    column_name VARCHAR(255),
    rule_type VARCHAR(50) NOT NULL,
    rule_params TEXT NOT NULL,
    severity VARCHAR(20) DEFAULT 'WARNING',
    enabled BOOLEAN DEFAULT TRUE,
    auto_fix BOOLEAN DEFAULT FALSE,
    fix_action VARCHAR(50),
    fix_params TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_quality_rule_probe_table ON quality_rule(probe_key, table_name, column_name);

-- 质量检查报告表
CREATE TABLE IF NOT EXISTS quality_report (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT,
    probe_key VARCHAR(255),
    database_name VARCHAR(255),
    table_name VARCHAR(255),
    column_name VARCHAR(255),
    row_identifier VARCHAR(512),
    violation_detail TEXT,
    check_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_quality_report_rule ON quality_report(rule_id);
CREATE INDEX IF NOT EXISTS idx_quality_report_probe_time ON quality_report(probe_key, check_time);
