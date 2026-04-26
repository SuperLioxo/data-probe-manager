-- 补全缺失的数据库表
-- 包含：agent, settings, dead_letter_task, probe_group, database_connection, file_metadata, file_scan_history

-- ============================================
-- 1. Agent 注册表
-- ============================================
CREATE TABLE IF NOT EXISTS agent (
    id BIGSERIAL PRIMARY KEY,
    agent_code VARCHAR(100) UNIQUE NOT NULL,
    agent_name VARCHAR(200),
    host_ip VARCHAR(64),
    port INTEGER,
    status VARCHAR(32) NOT NULL DEFAULT 'offline',
    version VARCHAR(32),
    description TEXT,
    last_heartbeat TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_code ON agent(agent_code);
CREATE INDEX IF NOT EXISTS idx_agent_status ON agent(status);

-- ============================================
-- 2. 系统设置表
-- ============================================
CREATE TABLE IF NOT EXISTS settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(200) UNIQUE NOT NULL,
    setting_value TEXT,
    description VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_settings_key ON settings(setting_key);

-- ============================================
-- 3. 死信任务表
-- ============================================
CREATE TABLE IF NOT EXISTS dead_letter_task (
    id BIGSERIAL PRIMARY KEY,
    original_task_id BIGINT,
    task_name VARCHAR(200),
    source_probe_key VARCHAR(255),
    source_table_name VARCHAR(255),
    target_type VARCHAR(50),
    target_config TEXT,
    sync_mode VARCHAR(20),
    failure_reason TEXT,
    failure_stack TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    status VARCHAR(32) DEFAULT 'PENDING',
    next_retry_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_retry_time TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dead_letter_status ON dead_letter_task(status);
CREATE INDEX IF NOT EXISTS idx_dead_letter_probe ON dead_letter_task(source_probe_key);
CREATE INDEX IF NOT EXISTS idx_dead_letter_time ON dead_letter_task(create_time);

-- ============================================
-- 4. 探针分组表
-- ============================================
CREATE TABLE IF NOT EXISTS probe_group (
    id BIGSERIAL PRIMARY KEY,
    group_name VARCHAR(200) NOT NULL,
    group_code VARCHAR(200) UNIQUE NOT NULL,
    parent_id BIGINT DEFAULT 0,
    path VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    description TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_probe_group_parent ON probe_group(parent_id);

-- 探针-分组关联表（与 ProbeGroupRelation 实体对应）
CREATE TABLE IF NOT EXISTS probe_group_relation (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL,
    probe_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pgr_group ON probe_group_relation(group_id);
CREATE INDEX IF NOT EXISTS idx_pgr_probe ON probe_group_relation(probe_id);

-- ============================================
-- 5. 数据库连接配置表
-- ============================================
-- 注意：列名与 DatabaseConnection 实体字段对应
CREATE TABLE IF NOT EXISTS database_connection (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200),
    database_type VARCHAR(50) NOT NULL,
    database_host VARCHAR(200) NOT NULL,
    database_port INTEGER NOT NULL,
    database_name VARCHAR(200),
    username VARCHAR(200),
    password VARCHAR(500),
    schemas TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_db_conn_type ON database_connection(database_type);

-- ============================================
-- 6. 文件元数据表
-- ============================================
CREATE TABLE IF NOT EXISTS file_metadata (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT DEFAULT 0,
    file_extension VARCHAR(20),
    file_md5 VARCHAR(64),
    is_directory BOOLEAN DEFAULT FALSE,
    parent_path TEXT,
    last_modified TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_file_meta_probe ON file_metadata(probe_key);
CREATE INDEX IF NOT EXISTS idx_file_meta_path ON file_metadata(file_path);
CREATE INDEX IF NOT EXISTS idx_file_meta_ext ON file_metadata(file_extension);

-- ============================================
-- 7. 文件扫描历史表
-- ============================================
CREATE TABLE IF NOT EXISTS file_scan_history (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    scan_path TEXT,
    total_files INTEGER DEFAULT 0,
    total_directories INTEGER DEFAULT 0,
    total_size BIGINT DEFAULT 0,
    new_files INTEGER DEFAULT 0,
    modified_files INTEGER DEFAULT 0,
    deleted_files INTEGER DEFAULT 0,
    duration_ms BIGINT DEFAULT 0,
    status VARCHAR(32) DEFAULT 'COMPLETED',
    error_message TEXT,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_scan_history_probe ON file_scan_history(probe_key);
CREATE INDEX IF NOT EXISTS idx_scan_history_time ON file_scan_history(create_time);

-- ============================================
-- 8. 补充 probe 表缺失的字段
-- ============================================
ALTER TABLE probe ADD COLUMN IF NOT EXISTS running_status VARCHAR(32) DEFAULT 'stopped';
ALTER TABLE probe ADD COLUMN IF NOT EXISTS running_status_updated_time TIMESTAMP;
ALTER TABLE probe ADD COLUMN IF NOT EXISTS agent_code VARCHAR(64);

-- ============================================
-- 9. 补充 sync_task 表缺失的字段
-- ============================================
ALTER TABLE sync_task ADD COLUMN IF NOT EXISTS quality_check_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE sync_task ADD COLUMN IF NOT EXISTS realtime_sync_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE sync_task ADD COLUMN IF NOT EXISTS realtime_sync_config TEXT;

-- ============================================
-- 10. 补充 change_log 表缺失的字段
-- ============================================
ALTER TABLE change_log ADD COLUMN IF NOT EXISTS before_data TEXT;
ALTER TABLE change_log ADD COLUMN IF NOT EXISTS after_data TEXT;
ALTER TABLE change_log ADD COLUMN IF NOT EXISTS operation VARCHAR(20);
ALTER TABLE change_log ADD COLUMN IF NOT EXISTS cdc_position VARCHAR(200);
