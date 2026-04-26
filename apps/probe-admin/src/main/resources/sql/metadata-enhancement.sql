-- 元数据增强：索引、外键、约束

-- 索引信息表
CREATE TABLE IF NOT EXISTS index_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    database_name VARCHAR(255),
    table_name VARCHAR(255) NOT NULL,
    index_name VARCHAR(255) NOT NULL,
    column_names TEXT,
    index_type VARCHAR(50),
    is_unique BOOLEAN DEFAULT FALSE,
    is_primary BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_probe_table (probe_key, table_name)
);

-- 外键关系表
CREATE TABLE IF NOT EXISTS foreign_key_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    database_name VARCHAR(255),
    table_name VARCHAR(255) NOT NULL,
    constraint_name VARCHAR(255),
    column_name VARCHAR(255) NOT NULL,
    ref_table VARCHAR(255) NOT NULL,
    ref_column VARCHAR(255) NOT NULL,
    update_rule VARCHAR(20),
    delete_rule VARCHAR(20),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_probe_table (probe_key, table_name)
);

-- 约束信息表
CREATE TABLE IF NOT EXISTS constraint_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    database_name VARCHAR(255),
    table_name VARCHAR(255) NOT NULL,
    constraint_name VARCHAR(255) NOT NULL,
    constraint_type VARCHAR(50) NOT NULL,
    column_name VARCHAR(255),
    check_clause TEXT,
    default_value TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_probe_table (probe_key, table_name)
);
