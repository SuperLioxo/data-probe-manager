-- 质量增强 (PostgreSQL)

-- 质量巡检调度记录表
CREATE TABLE IF NOT EXISTS quality_scan_log (
    id BIGSERIAL PRIMARY KEY,
    scan_type VARCHAR(30) NOT NULL,
    rules_checked INT DEFAULT 0,
    violations_found INT DEFAULT 0,
    violations_fixed INT DEFAULT 0,
    duration_ms INT DEFAULT 0,
    scan_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_scan_log_time ON quality_scan_log(scan_time);
