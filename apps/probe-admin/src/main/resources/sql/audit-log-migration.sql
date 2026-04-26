-- 审计日志表结构更新脚本
-- 执行时间: 2026-03-22

-- 添加新字段到审计日志表
ALTER TABLE sys_audit_log
ADD COLUMN IF NOT EXISTS level VARCHAR(20) DEFAULT 'INFO',
ADD COLUMN IF NOT EXISTS business_id BIGINT,
ADD COLUMN IF NOT EXISTS business_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS response_message TEXT,
ADD COLUMN IF NOT EXISTS is_exception BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS exception_message TEXT,
ADD COLUMN IF NOT EXISTS is_archived BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS archive_time TIMESTAMP;

-- 添加索引以提升查询性能
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON sys_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_operation ON sys_audit_log(operation);
CREATE INDEX IF NOT EXISTS idx_audit_log_module ON sys_audit_log(module);
CREATE INDEX IF NOT EXISTS idx_audit_log_level ON sys_audit_log(level);
CREATE INDEX IF NOT EXISTS idx_audit_log_create_time ON sys_audit_log(create_time);
CREATE INDEX IF NOT EXISTS idx_audit_log_is_archived ON sys_audit_log(is_archived);
CREATE INDEX IF NOT EXISTS idx_audit_log_business ON sys_audit_log(business_type, business_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_time_range ON sys_audit_log(create_time, is_archived);

-- 添加注释
COMMENT ON COLUMN sys_audit_log.level IS '日志级别：INFO, WARN, ERROR, CRITICAL';
COMMENT ON COLUMN sys_audit_log.business_id IS '业务ID（关联的业务实体ID）';
COMMENT ON COLUMN sys_audit_log.business_type IS '业务类型（关联的业务实体类型）';
COMMENT ON COLUMN sys_audit_log.response_message IS '响应消息';
COMMENT ON COLUMN sys_audit_log.is_exception IS '是否异常';
COMMENT ON COLUMN sys_audit_log.exception_message IS '异常信息';
COMMENT ON COLUMN sys_audit_log.is_archived IS '是否已归档';
COMMENT ON COLUMN sys_audit_log.archive_time IS '归档时间';
