-- 创建数据库元数据表
CREATE TABLE IF NOT EXISTS database_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    database_type VARCHAR(50),
    database_name VARCHAR(255),
    "version" VARCHAR(50),
    charset VARCHAR(50),
    "collation" VARCHAR(50),
    url VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建表信息表
CREATE TABLE IF NOT EXISTS table_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
CREATE INDEX IF NOT EXISTS idx_db_metadata_probe_key ON database_metadata(probe_key);
CREATE INDEX IF NOT EXISTS idx_table_info_probe_key ON table_info(probe_key);
CREATE INDEX IF NOT EXISTS idx_column_info_probe_key ON column_info(probe_key);
CREATE INDEX IF NOT EXISTS idx_column_info_table_name ON column_info(table_name);
