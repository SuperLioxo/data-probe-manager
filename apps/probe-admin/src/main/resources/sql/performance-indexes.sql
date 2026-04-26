-- 性能优化：补充缺失的索引和外键约束
-- 注意：先运行此脚本前确认表已创建

-- =====================================================
-- 1. sync_task 表优化索引
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_sync_task_probe_status ON sync_task(source_probe_key, last_sync_status);
CREATE INDEX IF NOT EXISTS idx_sync_task_enabled_cron ON sync_task(enabled, cron_expression);
CREATE INDEX IF NOT EXISTS idx_sync_task_next_sync ON sync_task(next_sync_time) WHERE enabled = true;

-- =====================================================
-- 2. sync_log 表优化索引
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_sync_log_task_time ON sync_log(task_id, start_time DESC);

-- =====================================================
-- 3. quality_rule 表优化索引
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_quality_rule_probe_table ON quality_rule(probe_key, table_name);
CREATE INDEX IF NOT EXISTS idx_quality_rule_type_enabled ON quality_rule(rule_type, enabled);

-- =====================================================
-- 4. quality_report 表优化索引
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_quality_report_rule ON quality_report(rule_id, check_time DESC);
CREATE INDEX IF NOT EXISTS idx_quality_report_probe_table ON quality_report(probe_key, table_name);

-- =====================================================
-- 5. change_log 表优化索引
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_change_log_probe_time ON change_log(probe_key, detected_time DESC);
CREATE INDEX IF NOT EXISTS idx_change_log_type ON change_log(change_type);

-- =====================================================
-- 6. data_snapshot 表优化索引
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_snapshot_probe_table_time ON data_snapshot(probe_key, table_name, snapshot_time DESC);

-- =====================================================
-- 7. change_alert_record 表优化索引
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_alert_record_probe_status ON change_alert_record(probe_key, status);

-- =====================================================
-- 8. quality_scan_log 表优化索引
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_scan_log_time ON quality_scan_log(scan_time DESC);

-- =====================================================
-- 9. file_metadata 表优化索引
-- =====================================================
CREATE INDEX IF NOT EXISTS idx_file_meta_probe ON file_metadata(probe_key, is_deleted, create_time DESC);

-- =====================================================
-- 10. 外键约束（可选，根据实际需要启用）
-- =====================================================
-- sync_log.task_id -> sync_task.id
ALTER TABLE sync_log ADD CONSTRAINT fk_sync_log_task
    FOREIGN KEY (task_id) REFERENCES sync_task(id) ON DELETE CASCADE;

-- quality_report.rule_id -> quality_rule.id
ALTER TABLE quality_report ADD CONSTRAINT fk_quality_report_rule
    FOREIGN KEY (rule_id) REFERENCES quality_rule(id) ON DELETE CASCADE;

-- data_snapshot 用于对比，不做硬外键
-- change_log 用于记录，不做硬外键
