-- Phase 1: 稳定当前状态
-- 给 probe 表添加 running_status、agent_code 列
-- 创建缺失的 database_probe 建表 SQL

-- 1. 给 probe 表添加新列（均可空，不破坏现有功能）
ALTER TABLE probe ADD COLUMN IF NOT EXISTS running_status VARCHAR(32);
ALTER TABLE probe ADD COLUMN IF NOT EXISTS agent_code VARCHAR(64);

-- 2. 创建 database_probe 表（当前缺失）
CREATE TABLE IF NOT EXISTS database_probe (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL DEFAULT 'DATABASE',
    status VARCHAR(32) NOT NULL DEFAULT 'offline',
    host_ip VARCHAR(64),
    port INTEGER,
    version VARCHAR(32),
    database_type VARCHAR(32),
    database_host VARCHAR(128),
    database_port INTEGER,
    database_name VARCHAR(128),
    username VARCHAR(128),
    password VARCHAR(500),
    schemas TEXT,
    collect_interval INTEGER DEFAULT 60,
    current_connection_id BIGINT,
    connection_pool VARCHAR(255),
    last_collect_time TIMESTAMP,
    last_heartbeat TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_database_probe_key ON database_probe(probe_key);
CREATE INDEX IF NOT EXISTS idx_database_probe_type ON database_probe(database_type);
