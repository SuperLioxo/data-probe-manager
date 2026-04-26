-- 数据变化探测相关表 (PostgreSQL)

-- 数据快照表
CREATE TABLE IF NOT EXISTS data_snapshot (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    database_name VARCHAR(255),
    table_name VARCHAR(255) NOT NULL,
    row_count BIGINT,
    data_size BIGINT,
    index_size BIGINT,
    data_checksum VARCHAR(64),
    max_update_time VARCHAR(100),
    snapshot_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_snapshot_probe_table ON data_snapshot(probe_key, database_name, table_name, snapshot_time);

-- 变化日志表
CREATE TABLE IF NOT EXISTS change_log (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    database_name VARCHAR(255),
    table_name VARCHAR(255) NOT NULL,
    change_type VARCHAR(30) NOT NULL,
    change_detail TEXT,
    affected_rows BIGINT DEFAULT 0,
    snapshot_before_id BIGINT,
    snapshot_after_id BIGINT,
    detected_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_changelog_probe_time ON change_log(probe_key, detected_time);
CREATE INDEX IF NOT EXISTS idx_changelog_type ON change_log(change_type);
