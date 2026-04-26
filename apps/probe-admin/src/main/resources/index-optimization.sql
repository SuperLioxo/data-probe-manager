-- =====================================
-- 数据库性能索引优化脚本
-- 创建日期: 2026-03-11
-- 作者: Claude Code
-- 版本: 1.0
-- 说明: 添加缺失的索引以优化查询性能
-- =====================================

-- =====================================
-- 1. RBAC权限管理索引优化
-- =====================================

-- sys_role_permission表：为permission_id添加索引
-- 用于PermissionServiceImpl.getPermissionsByRoleId()的反向查询
CREATE INDEX IF NOT EXISTS idx_role_permission_permission_id
ON sys_role_permission(permission_id);

-- sys_user_role表：为role_id添加索引（已存在user_id索引）
-- 用于RoleService.getRolesByUserId()的反向查询
CREATE INDEX IF NOT EXISTS idx_user_role_role_id
ON sys_user_role(role_id);

-- =====================================
-- 2. 探针管理索引优化
-- =====================================

-- probe表：为常用查询条件添加复合索引
-- 用于ProbeController的列表查询（status + create_time）
CREATE INDEX IF NOT EXISTS idx_probe_status_time
ON probe(status, create_time DESC);

-- probe表：为类型查询添加索引
-- 用于ProbeController.listByType()方法
CREATE INDEX IF NOT EXISTS idx_probe_type
ON probe(type);

-- probe表：为分组查询优化
CREATE INDEX IF NOT EXISTS idx_probe_group_status
ON probe(group_id, status);

-- =====================================
-- 3. 文件管理索引优化
-- =====================================

-- file_metadata表：优化父子目录查询
-- 已有单列索引，添加复合索引用于深度查询
CREATE INDEX IF NOT EXISTS idx_file_metadata_probe_deleted_type
ON file_metadata(probe_id, is_deleted, file_type);

-- file_metadata表：优化文件大小范围查询
-- 用于文件大小统计
CREATE INDEX IF NOT EXISTS idx_file_metadata_size_deleted
ON file_metadata(file_size, is_deleted);

-- =====================================
-- 5. 审计日志索引优化
-- =====================================

-- sys_audit_log表：添加用户模块时间复合索引
-- 用于审计日志的多条件查询
CREATE INDEX IF NOT EXISTS idx_audit_log_user_time
ON sys_audit_log(user_id, create_time DESC);

-- sys_audit_log表：添加操作模块时间复合索引
CREATE INDEX IF NOT EXISTS idx_audit_log_operation_time
ON sys_audit_log(operation, create_time DESC);

-- =====================================
-- 6. 监控数据索引优化
-- =====================================

-- metric_data表：添加探针指标时间复合索引
-- 用于时间序列数据查询
CREATE INDEX IF NOT EXISTS idx_metric_data_probe_metric_time
ON metric_data(probe_key, metric_name, collect_time DESC);

-- metric_data表：添加探针时间复合索引（用于范围查询）
CREATE INDEX IF NOT EXISTS idx_metric_data_probe_time
ON metric_data(probe_id, collect_time DESC);

-- database_performance表：添加类型时间复合索引
CREATE INDEX IF NOT EXISTS idx_db_performance_type_time
ON database_performance(database_type, timestamp DESC);

-- =====================================
-- 7. 探针分组索引优化
-- =====================================

-- probe_group_relation表：添加探针ID索引
CREATE INDEX IF NOT EXISTS idx_probe_group_relation_probe_id
ON probe_group_relation(probe_id);

-- =====================================
-- 索引统计信息
-- =====================================

-- 查看索引使用情况（需要超级用户权限）
-- SELECT * FROM pg_stat_user_indexes WHERE schemaname = 'public';

-- 查看表大小
-- SELECT
--     tablename,
--     pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
-- FROM pg_tables
-- WHERE schemaname = 'public'
-- ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

COMMENT ON SCHEMA public IS '所有索引已优化完成';
