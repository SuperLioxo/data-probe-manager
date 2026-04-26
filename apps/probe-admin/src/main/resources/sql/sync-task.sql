-- 数据自动同步相关表 (PostgreSQL)

-- 同步任务配置表
CREATE TABLE IF NOT EXISTS sync_task (
    id BIGSERIAL PRIMARY KEY,
    task_name VARCHAR(200) NOT NULL,
    source_probe_key VARCHAR(255) NOT NULL,
    source_table_name VARCHAR(255),
    target_type VARCHAR(50) NOT NULL,
    target_config TEXT NOT NULL,
    sync_mode VARCHAR(20) DEFAULT 'INCREMENTAL',
    cron_expression VARCHAR(100),
    conflict_strategy VARCHAR(20) DEFAULT 'UPSERT',
    last_sync_position TEXT,
    last_sync_time TIMESTAMP,
    last_sync_status VARCHAR(20),
    next_sync_time TIMESTAMP,
    enabled BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sync_task_probe ON sync_task(source_probe_key);
CREATE INDEX IF NOT EXISTS idx_sync_task_status ON sync_task(last_sync_status);

-- 同步执行日志表
CREATE TABLE IF NOT EXISTS sync_log (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    sync_mode VARCHAR(20),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(20),
    rows_processed BIGINT DEFAULT 0,
    rows_failed BIGINT DEFAULT 0,
    error_message TEXT,
    start_position TEXT,
    end_position TEXT
);
CREATE INDEX IF NOT EXISTS idx_sync_log_task_time ON sync_log(task_id, start_time);
