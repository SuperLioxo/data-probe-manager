-- CDC增强：为 change_log 表添加行级变更字段
-- 执行前请备份数据库

-- 添加行级变更前后数据
ALTER TABLE change_log ADD COLUMN IF NOT EXISTS before_data TEXT;
ALTER TABLE change_log ADD COLUMN IF NOT EXISTS after_data TEXT;

-- 添加CDC操作类型（INSERT/UPDATE/DELETE）
ALTER TABLE change_log ADD COLUMN IF NOT EXISTS operation VARCHAR(20);

-- 添加binlog位置信息
ALTER TABLE change_log ADD COLUMN IF NOT EXISTS cdc_position VARCHAR(200);

-- 为CDC查询添加索引
CREATE INDEX IF NOT EXISTS idx_change_log_operation ON change_log(operation);
CREATE INDEX IF NOT EXISTS idx_change_log_detected_time ON change_log(detected_time);
