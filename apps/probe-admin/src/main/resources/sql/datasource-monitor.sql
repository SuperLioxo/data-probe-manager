-- 数据源状态监测增强表

CREATE TABLE IF NOT EXISTS datasource_monitor_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_value DOUBLE,
    metric_unit VARCHAR(20),
    extra_info TEXT,
    collected_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_probe_metric (probe_key, metric_type, collected_time)
);
