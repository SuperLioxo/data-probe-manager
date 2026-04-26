-- 添加 database_name 字段到 column_info 表
-- 用于区分同一探针下的不同数据库实例

ALTER TABLE column_info ADD COLUMN IF NOT EXISTS database_name VARCHAR(255);

-- 为现有数据设置默认值（probe_db）
UPDATE column_info SET database_name = 'probe_db' WHERE database_name IS NULL;

-- 创建索引以优化查询
CREATE INDEX IF NOT EXISTS idx_column_info_database ON column_info(probe_key, database_name);
