-- H2 Database 简化初始化脚本
-- 移除复杂的迁移脚本，只保留核心表结构

-- 创建探针表
CREATE TABLE IF NOT EXISTS probe (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    probe_key VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'offline',
    host_ip VARCHAR(50),
    port INTEGER,
    version VARCHAR(50),
    config TEXT,
    collect_interval INTEGER DEFAULT 60,
    running_status VARCHAR(20) DEFAULT 'stopped',
    running_status_updated_time TIMESTAMP,
    last_heartbeat TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建指标数据表
CREATE TABLE IF NOT EXISTS metric_data (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    probe_id BIGINT,
    probe_key VARCHAR(255),
    metric_name VARCHAR(255),
    metric_value DECIMAL(20,6),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id VARCHAR(100),
    username VARCHAR(100),
    operation VARCHAR(50),
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    details TEXT,
    ip_address VARCHAR(50),
    user_agent VARCHAR(255),
    status VARCHAR(20),
    error_message TEXT,
    level VARCHAR(20) DEFAULT 'INFO',
    business_id BIGINT,
    business_type VARCHAR(100),
    response_message TEXT,
    is_exception BOOLEAN DEFAULT FALSE,
    exception_message TEXT,
    is_archived BOOLEAN DEFAULT FALSE,
    archive_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建文件探针表
CREATE TABLE IF NOT EXISTS file_probe (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    probe_key VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) DEFAULT 'FILE',
    status VARCHAR(32) NOT NULL,
    host_ip VARCHAR(64) NOT NULL,
    port INTEGER,
    version VARCHAR(32),
    scan_path TEXT NOT NULL,
    file_extensions TEXT,
    ignore_paths TEXT,
    scan_interval INTEGER DEFAULT 300,
    max_depth INTEGER DEFAULT 10,
    total_file_count BIGINT DEFAULT 0,
    total_directory_count BIGINT DEFAULT 0,
    total_size BIGINT DEFAULT 0,
    last_scan_time TIMESTAMP,
    last_heartbeat TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建数据库元数据表
CREATE TABLE IF NOT EXISTS database_metadata (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    database_type VARCHAR(50),
    database_name VARCHAR(255),
    version VARCHAR(50),
    charset VARCHAR(50),
    collation VARCHAR(50),
    url VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建表信息表
CREATE TABLE IF NOT EXISTS table_info (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    engine VARCHAR(50),
    row_count BIGINT,
    data_size BIGINT,
    index_size BIGINT,
    total_size BIGINT,
    create_time_str VARCHAR(50),
    update_time_str VARCHAR(50),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建列信息表
CREATE TABLE IF NOT EXISTS column_info (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    column_name VARCHAR(255) NOT NULL,
    data_type VARCHAR(100),
    column_type VARCHAR(255),
    is_nullable BOOLEAN,
    key_type VARCHAR(20),
    default_value VARCHAR(255),
    extra VARCHAR(255),
    comment TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_probe_probe_key ON probe(probe_key);
CREATE INDEX IF NOT EXISTS idx_probe_type ON probe(type);
CREATE INDEX IF NOT EXISTS idx_probe_status ON probe(status);
CREATE INDEX IF NOT EXISTS idx_probe_running_status ON probe(running_status);
CREATE INDEX IF NOT EXISTS idx_metric_probe_key ON metric_data(probe_key);
CREATE INDEX IF NOT EXISTS idx_metric_timestamp ON metric_data(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_log(username);
CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_log(create_time);
CREATE INDEX IF NOT EXISTS idx_audit_level ON audit_log(level);
CREATE INDEX IF NOT EXISTS idx_db_metadata_probe_key ON database_metadata(probe_key);
CREATE INDEX IF NOT EXISTS idx_table_info_probe_key ON table_info(probe_key);
CREATE INDEX IF NOT EXISTS idx_column_info_probe_key ON column_info(probe_key);
CREATE INDEX IF NOT EXISTS idx_column_info_table_name ON column_info(table_name);