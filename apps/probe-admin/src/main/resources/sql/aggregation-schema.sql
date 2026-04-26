-- 汇聚数据库模式
-- 管理端独立的汇聚 schema，前端仅从此 schema 读取数据
-- 执行前请备份数据库

-- 创建汇聚 Schema
CREATE SCHEMA IF NOT EXISTS aggregation;

-- 数据源注册表：记录所有已汇聚的数据源
CREATE TABLE IF NOT EXISTS aggregation.data_source_registry (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL,
    source_name VARCHAR(200) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    database_type VARCHAR(50),
    host VARCHAR(200),
    port INTEGER,
    database_name VARCHAR(200),
    agent_code VARCHAR(100),
    status VARCHAR(20) DEFAULT 'active',
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 聚合表元数据：来自所有数据源的表结构信息
CREATE TABLE IF NOT EXISTS aggregation.table_metadata (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL,
    database_name VARCHAR(200),
    table_name VARCHAR(200) NOT NULL,
    row_count BIGINT,
    data_size BIGINT,
    column_count INTEGER,
    table_comment TEXT,
    synced_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 聚合列元数据：来自所有数据源的字段信息
CREATE TABLE IF NOT EXISTS aggregation.column_metadata (
    id BIGSERIAL PRIMARY KEY,
    table_metadata_id BIGINT NOT NULL REFERENCES aggregation.table_metadata(id),
    column_name VARCHAR(200) NOT NULL,
    column_type VARCHAR(100),
    column_length INTEGER,
    is_nullable BOOLEAN DEFAULT TRUE,
    is_primary_key BOOLEAN DEFAULT FALSE,
    column_comment TEXT,
    ordinal_position INTEGER
);

-- 文件注册表：所有已上传/已同步的文件
CREATE TABLE IF NOT EXISTS aggregation.file_registry (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT,
    file_name VARCHAR(500) NOT NULL,
    file_path TEXT,
    file_size BIGINT,
    file_extension VARCHAR(20),
    file_md5 VARCHAR(64),
    storage_path TEXT,
    aggregation_table VARCHAR(200),
    status VARCHAR(20) DEFAULT 'active',
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(100)
);

-- 质量不合格记录：被过滤掉的数据及原因
CREATE TABLE IF NOT EXISTS aggregation.quality_bad_records (
    id BIGSERIAL PRIMARY KEY,
    sync_task_id BIGINT,
    source_id BIGINT,
    table_name VARCHAR(200),
    row_data JSONB,
    violated_rules JSONB,
    rejection_reason TEXT,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_agg_ds_source_id ON aggregation.data_source_registry(source_id);
CREATE INDEX IF NOT EXISTS idx_agg_table_source ON aggregation.table_metadata(source_id);
CREATE INDEX IF NOT EXISTS idx_agg_table_name ON aggregation.table_metadata(table_name);
CREATE INDEX IF NOT EXISTS idx_agg_col_table ON aggregation.column_metadata(table_metadata_id);
CREATE INDEX IF NOT EXISTS idx_agg_file_source ON aggregation.file_registry(source_id);
CREATE INDEX IF NOT EXISTS idx_agg_bad_sync ON aggregation.quality_bad_records(sync_task_id);
CREATE INDEX IF NOT EXISTS idx_agg_bad_table ON aggregation.quality_bad_records(table_name);
