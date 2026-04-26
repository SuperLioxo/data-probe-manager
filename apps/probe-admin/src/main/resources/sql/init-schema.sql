-- H2 Database 初始化脚本
-- 将PostgreSQL语法转换为H2兼容语法

-- 创建探针表
CREATE TABLE IF NOT EXISTS probe (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    probe_key VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'offline',
    host_ip VARCHAR(50),
    port INTEGER,
    version VARCHAR(50),
    config TEXT,
    collect_interval INTEGER DEFAULT 60,
    last_heartbeat TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建指标数据表
CREATE TABLE IF NOT EXISTS metric_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    probe_id BIGINT,
    probe_key VARCHAR(255),
    metric_name VARCHAR(255),
    value DECIMAL(20,6),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建审计日志表
-- 创建审计日志表（完整版）
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100),
    username VARCHAR(100),
    operation VARCHAR(50),
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    details TEXT,
    description TEXT,
    module VARCHAR(50),
    level VARCHAR(20),
    business_id BIGINT,
    business_type VARCHAR(50),
    ip_address VARCHAR(50),
    user_agent VARCHAR(255),
    status VARCHAR(20),
    error_message TEXT,
    response_message TEXT,
    is_exception BOOLEAN DEFAULT FALSE,
    exception_message TEXT,
    is_archived BOOLEAN DEFAULT FALSE,
    archive_time TIMESTAMP,
    request_url VARCHAR(512),
    request_method VARCHAR(10),
    method VARCHAR(255),
    request_params TEXT,
    response_code INT,
    execution_time BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入测试审计日志数据
INSERT INTO audit_log (
    user_id, username, operation, module, description, level,
    ip_address, user_agent, request_url, request_method,
    response_code, execution_time, is_exception, create_time
) VALUES
('1', 'admin', 'LOGIN', 'AUTH', '用户登录成功', 'INFO',
 '127.0.0.1', 'Mozilla/5.0', '/api/auth/login', 'POST',
 200, 156, FALSE, NOW()),
 
('1', 'admin', 'QUERY', 'PROBE', '查询探针列表', 'INFO',
 '127.0.0.1', 'Mozilla/5.0', '/api/probes', 'GET',
 200, 45, FALSE, NOW()),
 
('1', 'admin', 'CREATE', 'PROBE', '创建探针: 系统监控', 'INFO',
 '127.0.0.1', 'Mozilla/5.0', '/api/probes', 'POST',
 200, 123, FALSE, NOW()),
 
('1', 'admin', 'UPDATE', 'PROBE', '更新探针配置', 'INFO',
 '127.0.0.1', 'Mozilla/5.0', '/api/probes/1', 'PUT',
 200, 89, FALSE, NOW()),
 
('1', 'admin', 'DELETE', 'PROBE', '删除探针: 测试探针', 'WARN',
 '127.0.0.1', 'Mozilla/5.0', '/api/probes/1', 'DELETE',
 200, 67, FALSE, NOW()),
 
('1', 'admin', 'QUERY', 'PROBE', '查询探针详情', 'INFO',
 '127.0.0.1', 'Mozilla/5.0', '/api/probes/1', 'GET',
 200, 34, FALSE, NOW()),
 
('1', 'admin', 'CREATE', 'ALERT', '创建告警规则', 'INFO',
 '127.0.0.1', 'Mozilla/5.0', '/api/alerts', 'POST',
 200, 145, FALSE, NOW()),
 
('1', 'admin', 'UPDATE', 'ALERT', '更新告警规则', 'INFO',
 '127.0.0.1', 'Mozilla/5.0', '/api/alerts/1', 'PUT',
 200, 78, FALSE, NOW()),
 
('1', 'admin', 'LOGOUT', 'AUTH', '用户登出', 'INFO',
 '127.0.0.1', 'Mozilla/5.0', '/api/auth/logout', 'POST',
 200, 23, FALSE, NOW()),
 
('1', 'admin', 'PERMISSION_CHANGE', 'AUTH', '权限变更: 用户ID=2', 'CRITICAL',
 '127.0.0.1', 'Mozilla/5.0', '/api/users/2/permissions', 'PUT',
 200, 189, FALSE, NOW());

-- 创建文件探针表
CREATE TABLE IF NOT EXISTS file_probe (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    probe_key VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    scan_paths TEXT,
    file_extensions TEXT,
    recursive BOOLEAN DEFAULT TRUE,
    max_depth INTEGER DEFAULT 10,
    min_file_size BIGINT DEFAULT 0,
    max_file_size BIGINT DEFAULT 104857600,
    calculate_md5 BOOLEAN DEFAULT FALSE,
    include_hidden BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'offline',
    last_scan_time TIMESTAMP,
    file_count INTEGER DEFAULT 0,
    total_size BIGINT DEFAULT 0,
    host_ip VARCHAR(50),
    port INTEGER,
    collect_interval INTEGER DEFAULT 300,
    last_heartbeat TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建数据库元数据表
CREATE TABLE IF NOT EXISTS database_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    database_type VARCHAR(50),
    database_name VARCHAR(255),
    version VARCHAR(50),
    charset VARCHAR(50),
    collation VARCHAR(50),
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

-- 插入默认测试数据
INSERT INTO probe (probe_key, name, type, status, host_ip, port, collect_interval) VALUES
('AGENT-system-q4mdeo-5x6', '测试系统探针', 'SYSTEM', 'offline', '127.0.0.1', 9999, 10),
('AGENT-database-q421g8-o6z', '测试数据库探针', 'DATABASE', 'offline', '127.0.0.1', 5432, 60);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_probe_probe_key ON probe(probe_key);
CREATE INDEX IF NOT EXISTS idx_probe_type ON probe(type);
CREATE INDEX IF NOT EXISTS idx_probe_status ON probe(status);
CREATE INDEX IF NOT EXISTS idx_metric_probe_key ON metric_data(probe_key);
CREATE INDEX IF NOT EXISTS idx_metric_timestamp ON metric_data(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_log(username);
CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_log(create_time);
