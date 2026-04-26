-- 同步任务增强：添加质量检查和实时同步字段
ALTER TABLE sync_task ADD COLUMN IF NOT EXISTS quality_check_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE sync_task ADD COLUMN IF NOT EXISTS realtime_sync_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE sync_task ADD COLUMN IF NOT EXISTS realtime_sync_config TEXT;
