-- 变化告警相关表 (PostgreSQL)

-- 变化告警配置表
CREATE TABLE IF NOT EXISTS change_alert_config (
    id BIGSERIAL PRIMARY KEY,
    alert_name VARCHAR(200) NOT NULL,
    probe_key VARCHAR(255),
    table_name VARCHAR(255),
    change_types VARCHAR(255),
    threshold_rows BIGINT DEFAULT 100,
    alert_level VARCHAR(20) DEFAULT 'WARNING',
    notify_channels VARCHAR(100) DEFAULT 'LOG',
    enabled BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_alert_config_probe ON change_alert_config(probe_key);

-- 变化告警记录表
CREATE TABLE IF NOT EXISTS change_alert_record (
    id BIGSERIAL PRIMARY KEY,
    alert_config_id BIGINT,
    probe_key VARCHAR(255) NOT NULL,
    table_name VARCHAR(255),
    change_type VARCHAR(30),
    change_detail TEXT,
    affected_rows BIGINT,
    alert_level VARCHAR(20),
    notify_channel VARCHAR(100),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_time TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_alert_record_time ON change_alert_record(probe_key, created_time);
CREATE INDEX IF NOT EXISTS idx_alert_record_status ON change_alert_record(status);
