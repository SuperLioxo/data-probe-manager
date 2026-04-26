-- 创建汇聚数据专用 schema（如不存在）
CREATE SCHEMA IF NOT EXISTS aggregated;

-- 汇聚数据源注册表
CREATE TABLE IF NOT EXISTS aggregation.data_source_registry (
    id BIGSERIAL PRIMARY KEY,
    source_id VARCHAR(255) UNIQUE NOT NULL,
    source_name VARCHAR(200),
    source_type VARCHAR(50),
    database_type VARCHAR(50),
    host VARCHAR(200),
    port INTEGER,
    database_name VARCHAR(200),
    description TEXT,
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 汇聚表元数据
CREATE TABLE IF NOT EXISTS aggregation.table_metadata (
    id BIGSERIAL PRIMARY KEY,
    source_id VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    table_comment TEXT,
    row_count BIGINT DEFAULT 0,
    column_count INTEGER DEFAULT 0,
    schema_name VARCHAR(200),
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文件注册表
CREATE TABLE IF NOT EXISTS aggregation.file_registry (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT,
    file_name VARCHAR(500) NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT DEFAULT 0,
    file_extension VARCHAR(20),
    file_md5 VARCHAR(64),
    storage_path TEXT,
    aggregation_table VARCHAR(255),
    uploaded_by VARCHAR(100),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 质量不合格记录
CREATE TABLE IF NOT EXISTS aggregation.quality_bad_records (
    id BIGSERIAL PRIMARY KEY,
    sync_task_id BIGINT,
    source_id BIGINT,
    table_name VARCHAR(255),
    row_data JSONB,
    violated_rules JSONB,
    rejection_reason TEXT,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
