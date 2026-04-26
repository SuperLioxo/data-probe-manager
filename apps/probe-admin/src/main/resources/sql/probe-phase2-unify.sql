-- Phase 2: 数据迁移 - 将 file_probe 和 database_probe 数据迁入 probe 表
-- 执行前请确保 Phase 1 已完成（probe 表已有 running_status, agent_code 列）

-- ============================================
-- 1. 迁移 file_probe → probe（幂等）
-- ============================================
INSERT INTO probe (probe_key, name, type, status, host_ip, port, version,
    config, collect_interval, last_heartbeat, create_time, update_time)
SELECT
    fp.probe_key,
    fp.name,
    'FILE',
    fp.status,
    fp.host_ip,
    fp.port,
    fp.version,
    json_build_object(
        'scanPath', fp.scan_path,
        'fileExtensions', fp.file_extensions,
        'ignorePaths', fp.ignore_paths,
        'scanInterval', fp.scan_interval,
        'maxDepth', fp.max_depth
    )::text,
    fp.scan_interval,
    fp.last_heartbeat,
    fp.create_time,
    fp.update_time
FROM file_probe fp
WHERE NOT EXISTS (
    SELECT 1 FROM probe p WHERE p.probe_key = fp.probe_key AND p.type = 'FILE'
);

-- ============================================
-- 2. 迁移 database_probe → probe（幂等）
-- ============================================
INSERT INTO probe (probe_key, name, type, status, host_ip, port, version,
    config, collect_interval, last_heartbeat, create_time, update_time)
SELECT
    dp.probe_key,
    dp.name,
    'DATABASE',
    dp.status,
    COALESCE(dp.host_ip, ''),
    COALESCE(dp.port, 0),
    dp.version,
    json_build_object(
        'databaseType', dp.database_type,
        'databaseHost', dp.database_host,
        'databasePort', dp.database_port,
        'databaseName', dp.database_name,
        'username', dp.username,
        'password', dp.password,
        'schemas', dp.schemas,
        'currentConnectionId', dp.current_connection_id,
        'connectionPool', dp.connection_pool
    )::text,
    dp.collect_interval,
    dp.last_heartbeat,
    dp.create_time,
    dp.update_time
FROM database_probe dp
WHERE NOT EXISTS (
    SELECT 1 FROM probe p WHERE p.probe_key = dp.probe_key AND p.type = 'DATABASE'
);
