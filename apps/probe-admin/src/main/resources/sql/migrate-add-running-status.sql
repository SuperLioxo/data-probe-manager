-- 添加探针运行状态字段
-- 目的：区分探针的连接状态和运行状态
-- Date: 2026-03-22

-- 添加运行状态字段
ALTER TABLE probe ADD COLUMN IF NOT EXISTS running_status VARCHAR(20);

-- 添加运行状态更新时间字段
ALTER TABLE probe ADD COLUMN IF NOT EXISTS running_status_updated_time TIMESTAMP;

-- 设置默认值：所有现有探针默认为stopped状态
UPDATE probe SET running_status = 'stopped' WHERE running_status IS NULL;

-- 添加字段注释
COMMENT ON COLUMN probe.running_status IS '探针运行状态：running(运行中), stopped(已停止)';
COMMENT ON COLUMN probe.running_status_updated_time IS '运行状态最后更新时间';

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_probe_running_status ON probe(running_status);

-- 验证迁移
SELECT
    column_name,
    data_type,
    column_default,
    is_nullable
FROM information_schema.columns
WHERE table_name = 'probe'
  AND column_name IN ('running_status', 'running_status_updated_time')
ORDER BY ordinal_position;
