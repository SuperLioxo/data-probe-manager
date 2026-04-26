-- 数据库创建脚本
CREATE DATABASE probe_db;

-- 用户创建
CREATE USER probe_user WITH PASSWORD 'probe_pass';

-- 授权
GRANT ALL PRIVILEGES ON DATABASE probe_db TO probe_user;

-- 连接到数据库
\c probe_db

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(64),
    email VARCHAR(128),
    phone VARCHAR(32),
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入默认管理员用户 (密码: admin123, 使用BCrypt加密)
INSERT INTO sys_user (username, password, real_name, email, phone, status)
VALUES ('admin', '$2a$10$.kjwFErV.AUKB1GJ5WcEDO8eKWs2d9fEDu2rDo7YBXwT0TIqBOeqK', '李鑫', 'lixin@example.com', '13888888888', 1)
ON CONFLICT (username) DO NOTHING;

-- 探针表
CREATE TABLE IF NOT EXISTS probe (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    host_ip VARCHAR(64) NOT NULL,
    port INT,
    version VARCHAR(32),
    config TEXT,
    cpu_limit DECIMAL(5,2),
    memory_limit BIGINT,
    collect_interval INT DEFAULT 60,
    last_heartbeat TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 指标数据表
CREATE TABLE IF NOT EXISTS metric_data (
    id BIGSERIAL PRIMARY KEY,
    probe_id BIGINT NOT NULL,
    probe_key VARCHAR(64) NOT NULL,
    metric_name VARCHAR(64) NOT NULL,
    metric_value DECIMAL(18,4) NOT NULL,
    unit VARCHAR(32),
    tags TEXT,
    collect_time TIMESTAMP NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_metric_data_probe_key ON metric_data(probe_key);
CREATE INDEX IF NOT EXISTS idx_metric_data_collect_time ON metric_data(collect_time);
CREATE INDEX IF NOT EXISTS idx_metric_data_metric_name ON metric_data(metric_name);

-- 告警规则表
CREATE TABLE IF NOT EXISTS alert_rule (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    probe_id BIGINT DEFAULT 0,
    metric_name VARCHAR(64) NOT NULL,
    threshold DECIMAL(18,4) NOT NULL,
    operator VARCHAR(16) NOT NULL,
    duration INT DEFAULT 0,
    level VARCHAR(16) NOT NULL,
    cooldown_seconds INT DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 告警记录表 - 更新为匹配Java实体类
CREATE TABLE IF NOT EXISTS alert (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT,
    probe_id BIGINT,
    probe_name VARCHAR(128),
    severity VARCHAR(32),
    alert_type VARCHAR(64),
    message TEXT,
    current_value DECIMAL(18,4),
    threshold DECIMAL(18,4),
    status VARCHAR(32),
    triggered_at TIMESTAMP,
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolution TEXT,
    is_read BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_alert_status ON alert(status);
CREATE INDEX IF NOT EXISTS idx_alert_triggered_at ON alert(triggered_at);
CREATE INDEX IF NOT EXISTS idx_alert_severity ON alert(severity);
CREATE INDEX IF NOT EXISTS idx_alert_probe_id ON alert(probe_id);

-- 注释
COMMENT ON TABLE probe IS '探针表';
COMMENT ON TABLE metric_data IS '指标数据表';
COMMENT ON TABLE alert_rule IS '告警规则表';
COMMENT ON TABLE alert IS '告警记录表';

-- 插入测试数据 - 探针
INSERT INTO probe (probe_key, name, type, status, host_ip, port, version, collect_interval, last_heartbeat)
VALUES
    ('probe-system-001', '系统探针-01', 'SYSTEM', 'ONLINE', '192.168.1.100', 9999, '1.0.0', 60, CURRENT_TIMESTAMP)
    -- 应用探针已废弃，网络探针已并入系统探针，移除冗余测试数据
ON CONFLICT (probe_key) DO NOTHING;

-- 插入测试数据 - 告警
INSERT INTO alert (rule_id, probe_id, probe_name, severity, alert_type, message, current_value, threshold, status, triggered_at, is_read)
VALUES
    (1, 1, '系统探针-01', 'CRITICAL', 'CPU使用率过高', 'CPU使用率超过90%阈值', 95.5, 90.0, 'OPEN', CURRENT_TIMESTAMP - INTERVAL '2 hours', FALSE),
    (2, 1, '系统探针-01', 'MAJOR', '内存使用率过高', '内存使用率超过85%阈值', 88.3, 85.0, 'ACKNOWLEDGED', CURRENT_TIMESTAMP - INTERVAL '3 hours', TRUE),
    (3, 1, '系统探针-01', 'MINOR', '磁盘使用率过高', '磁盘使用率超过80%阈值', 82.1, 80.0, 'RESOLVED', CURRENT_TIMESTAMP - INTERVAL '1 day', TRUE)
ON CONFLICT DO NOTHING;

-- 系统设置表
CREATE TABLE IF NOT EXISTS settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(128) UNIQUE NOT NULL,
    setting_value TEXT,
    description VARCHAR(256),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE settings IS '系统设置表';

-- =====================================
-- RBAC权限管理表
-- =====================================

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) UNIQUE NOT NULL,
    description VARCHAR(256),
    status INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGSERIAL PRIMARY KEY,
    permission_name VARCHAR(128) NOT NULL,
    permission_code VARCHAR(128) UNIQUE NOT NULL,
    resource_type VARCHAR(32),
    resource_identifier VARCHAR(256),
    action VARCHAR(32),
    description VARCHAR(256),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, role_id)
);

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (role_id, permission_id)
);

-- RBAC索引
CREATE INDEX IF NOT EXISTS idx_user_role_user_id ON sys_user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_role_permission_role_id ON sys_role_permission(role_id);

COMMENT ON TABLE sys_role IS '角色表';
COMMENT ON TABLE sys_permission IS '权限表';
COMMENT ON TABLE sys_user_role IS '用户角色关联表';
COMMENT ON TABLE sys_role_permission IS '角色权限关联表';

-- =====================================
-- 探针分组管理表
-- =====================================

-- 探针分组表
CREATE TABLE IF NOT EXISTS probe_group (
    id BIGSERIAL PRIMARY KEY,
    group_name VARCHAR(128) NOT NULL,
    group_code VARCHAR(64) UNIQUE NOT NULL,
    parent_id BIGINT DEFAULT 0,
    path VARCHAR(512),
    description VARCHAR(256),
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 探针分组关联表
CREATE TABLE IF NOT EXISTS probe_group_relation (
    id BIGSERIAL PRIMARY KEY,
    probe_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (probe_id, group_id)
);

-- 分组管理索引
CREATE INDEX IF NOT EXISTS idx_probe_group_parent_id ON probe_group(parent_id);
CREATE INDEX IF NOT EXISTS idx_probe_group_relation_group_id ON probe_group_relation(group_id);

COMMENT ON TABLE probe_group IS '探针分组表';
COMMENT ON TABLE probe_group_relation IS '探针分组关联表';

-- =====================================
-- 审计日志表
-- =====================================

-- 审计日志表
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    operation VARCHAR(64),
    module VARCHAR(64),
    description TEXT,
    method VARCHAR(128),
    request_url VARCHAR(512),
    request_params TEXT,
    response_code INT,
    execution_time BIGINT,
    ip_address VARCHAR(64),
    user_agent TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 审计日志索引
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON sys_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_operation ON sys_audit_log(operation);
CREATE INDEX IF NOT EXISTS idx_audit_log_module ON sys_audit_log(module);
CREATE INDEX IF NOT EXISTS idx_audit_log_create_time ON sys_audit_log(create_time);

COMMENT ON TABLE sys_audit_log IS '审计日志表';

-- =====================================
-- 初始化数据
-- =====================================

-- 初始化角色
INSERT INTO sys_role (role_name, role_code, description) VALUES
('超级管理员', 'ROLE_ADMIN', '拥有系统所有权限'),
('管理员', 'ROLE_MANAGER', '拥有探针管理和配置权限'),
('操作员', 'ROLE_OPERATOR', '拥有查看和操作探针权限'),
('查看者', 'ROLE_VIEWER', '仅拥有查看权限')
ON CONFLICT (role_code) DO NOTHING;

-- 初始化权限
INSERT INTO sys_permission (permission_name, permission_code, resource_type, resource_identifier, action) VALUES
('探针列表查询', 'probe:list', 'API', '/api/probes', 'GET'),
('探针创建', 'probe:create', 'API', '/api/probes', 'POST'),
('探针更新', 'probe:update', 'API', '/api/probes/*', 'PUT'),
('探针删除', 'probe:delete', 'API', '/api/probes/*', 'DELETE'),
('告警规则查询', 'alert:list', 'API', '/api/alert-rules', 'GET'),
('告警规则创建', 'alert:create', 'API', '/api/alert-rules', 'POST'),
('告警规则更新', 'alert:update', 'API', '/api/alert-rules/*', 'PUT'),
('告警规则删除', 'alert:delete', 'API', '/api/alert-rules/*', 'DELETE'),
('告警确认', 'alert:confirm', 'API', '/api/alerts/*/confirm', 'POST'),
('告警解决', 'alert:resolve', 'API', '/api/alerts/*/resolve', 'PUT')
ON CONFLICT (permission_code) DO NOTHING;

-- 关联超级管理员的所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r, sys_permission p
WHERE r.role_code = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- 扩展probe表支持分组
ALTER TABLE probe ADD COLUMN IF NOT EXISTS group_id BIGINT DEFAULT 0;
ALTER TABLE probe ADD COLUMN IF NOT EXISTS group_path VARCHAR(512);
CREATE INDEX IF NOT EXISTS idx_probe_group_id ON probe(group_id);

COMMENT ON TABLE settings IS '系统设置表';

-- =====================================
-- 文件管理功能表
-- =====================================

-- 文件探针表
CREATE TABLE IF NOT EXISTS file_probe (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) DEFAULT 'FILE',
    status VARCHAR(32) NOT NULL,
    host_ip VARCHAR(64) NOT NULL,
    port INT,
    version VARCHAR(32),

    -- 文件探针配置
    scan_path TEXT NOT NULL,              -- 扫描路径（JSON数组）
    file_extensions TEXT,                 -- 文件类型过滤（逗号分隔）
    ignore_paths TEXT,                    -- 忽略路径（JSON数组）
    scan_interval INT DEFAULT 300,        -- 扫描间隔（秒）
    max_depth INT DEFAULT 10,             -- 最大递归深度

    -- 统计信息
    total_file_count BIGINT DEFAULT 0,
    total_directory_count BIGINT DEFAULT 0,
    total_size BIGINT DEFAULT 0,
    last_scan_time TIMESTAMP,

    last_heartbeat TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE file_probe IS '文件探针表';
COMMENT ON COLUMN file_probe.scan_path IS '扫描路径配置，JSON格式：["/data/logs", "/var/app"]';
COMMENT ON COLUMN file_probe.file_extensions IS '文件扩展名过滤：.log,.txt,.csv或空表示全部';
COMMENT ON COLUMN file_probe.ignore_paths IS '忽略路径配置：["/tmp", "*.bak"]';

CREATE INDEX idx_file_probe_key ON file_probe(probe_key);
CREATE INDEX idx_file_probe_status ON file_probe(status);

-- 文件元数据表
CREATE TABLE IF NOT EXISTS file_metadata (
    id BIGSERIAL PRIMARY KEY,
    probe_id BIGINT NOT NULL,
    probe_key VARCHAR(64) NOT NULL,

    -- 文件基本信息
    file_name VARCHAR(512) NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    file_extension VARCHAR(64),
    file_md5 VARCHAR(32),
    file_type VARCHAR(32),                -- FILE, DIRECTORY

    -- 层级关系
    parent_path TEXT,                     -- 父目录路径
    depth INT DEFAULT 0,                  -- 目录深度

    -- 时间信息
    last_modified BIGINT NOT NULL,        -- 文件修改时间（Unix毫秒时间戳）
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 状态
    is_deleted BOOLEAN DEFAULT FALSE,     -- 软删除标记

    CONSTRAINT fk_file_probe FOREIGN KEY (probe_id)
        REFERENCES file_probe(id) ON DELETE CASCADE
);

COMMENT ON TABLE file_metadata IS '文件元数据表';
COMMENT ON COLUMN file_metadata.last_modified IS '文件最后修改时间（毫秒时间戳）';
COMMENT ON COLUMN file_metadata.depth IS '相对于根路径的目录深度';

-- 索引
CREATE INDEX idx_file_metadata_probe_id ON file_metadata(probe_id);
CREATE INDEX idx_file_metadata_probe_key ON file_metadata(probe_key);
CREATE INDEX idx_file_metadata_path ON file_metadata(file_path);
CREATE INDEX idx_file_metadata_parent ON file_metadata(parent_path);
CREATE INDEX idx_file_metadata_extension ON file_metadata(file_extension);
CREATE INDEX idx_file_metadata_type ON file_metadata(file_type);
CREATE INDEX idx_file_metadata_size ON file_metadata(file_size);
CREATE INDEX idx_file_metadata_modified ON file_metadata(last_modified);
CREATE INDEX idx_file_metadata_deleted ON file_metadata(is_deleted);

-- 复合索引（用于常见查询）
CREATE INDEX idx_file_metadata_probe_type ON file_metadata(probe_id, file_type);
CREATE INDEX idx_file_metadata_probe_parent ON file_metadata(probe_id, parent_path);

-- 文件扫描历史记录表
CREATE TABLE IF NOT EXISTS file_scan_history (
    id BIGSERIAL PRIMARY KEY,
    probe_id BIGINT NOT NULL,
    probe_key VARCHAR(64) NOT NULL,

    -- 扫描结果统计
    scan_start_time TIMESTAMP NOT NULL,
    scan_end_time TIMESTAMP NOT NULL,
    scan_duration INT NOT NULL,           -- 扫描耗时（毫秒）

    file_count BIGINT NOT NULL,
    directory_count BIGINT NOT NULL,
    total_size BIGINT NOT NULL,
    new_file_count INT DEFAULT 0,
    modified_file_count INT DEFAULT 0,
    deleted_file_count INT DEFAULT 0,

    scan_status VARCHAR(32) NOT NULL,     -- SUCCESS, FAILED, PARTIAL
    error_message TEXT,

    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE file_scan_history IS '文件扫描历史记录表';
COMMENT ON COLUMN file_scan_history.scan_status IS '扫描状态：SUCCESS-成功, FAILED-失败, PARTIAL-部分成功';

CREATE INDEX idx_file_scan_probe_time ON file_scan_history(probe_id, scan_start_time DESC);

-- =====================================
-- 数据库性能监控功能表
-- =====================================

-- 数据库性能监控表
CREATE TABLE IF NOT EXISTS database_performance (
    id BIGSERIAL PRIMARY KEY,
    probe_id BIGINT NOT NULL,
    probe_key VARCHAR(64) NOT NULL,

    -- 数据库信息
    database_type VARCHAR(32) NOT NULL,       -- MySQL, PostgreSQL, Oracle, etc.
    host VARCHAR(128),
    port INT,
    database_name VARCHAR(128),

    -- 连接信息
    connection_count INT DEFAULT 0,
    active_connections INT DEFAULT 0,
    max_connections INT DEFAULT 0,
    connection_usage DECIMAL(5,2),           -- 连接使用率百分比

    -- 查询性能
    avg_query_time BIGINT DEFAULT 0,          -- 平均查询耗时(ms)
    slow_query_count BIGINT DEFAULT 0,
    qps DECIMAL(10,2),                        -- 每秒查询数
    tps DECIMAL(10,2),                        -- 每秒事务数

    -- 缓存性能
    cache_hit_rate DECIMAL(5,2),             -- 缓存命中率百分比

    -- 数据采集时间
    timestamp BIGINT NOT NULL,

    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    -- 注意：此表不需要外键约束，因为probeKey由meta探针通过WebSocket上报
);

COMMENT ON TABLE database_performance IS '数据库性能监控表';
COMMENT ON COLUMN database_performance.connection_usage IS '连接使用率百分比';
COMMENT ON COLUMN database_performance.avg_query_time IS '平均查询耗时(毫秒)';
COMMENT ON COLUMN database_performance.qps IS '每秒查询数';
COMMENT ON COLUMN database_performance.timestamp IS '数据采集时间戳(毫秒)';

-- 索引
CREATE INDEX idx_db_performance_probe ON database_performance(probe_id);
CREATE INDEX idx_db_performance_probe_key ON database_performance(probe_key);
CREATE INDEX idx_db_performance_type ON database_performance(database_type);
CREATE INDEX idx_db_performance_time ON database_performance(timestamp DESC);
CREATE INDEX idx_db_performance_create_time ON database_performance(create_time DESC);

-- =====================================
-- 统一探针支持
-- =====================================

-- 扩展probe表以支持统一探针
ALTER TABLE probe ADD COLUMN IF NOT EXISTS capabilities TEXT;
ALTER TABLE probe ADD COLUMN IF NOT EXISTS protocols TEXT;
ALTER TABLE probe ADD COLUMN IF NOT EXISTS description TEXT;

COMMENT ON COLUMN probe.capabilities IS '探针能力列表（JSON格式），用于UNIFIED类型: ["system.cpu", "database.metadata"]';
COMMENT ON COLUMN probe.protocols IS '支持的协议列表（JSON格式），用于UNIFIED类型: ["UDP", "WEBSOCKET"]';
COMMENT ON COLUMN probe.description IS '探针描述';

