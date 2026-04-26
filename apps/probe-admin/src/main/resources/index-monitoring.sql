-- =====================================
-- 数据库索引性能监控脚本
-- 创建日期: 2026-03-11
-- 作者: Claude Code
-- 版本: 1.0
-- 说明: 监控索引使用情况和性能
-- =====================================

-- =====================================
-- 1. 索引使用情况统计
-- =====================================

-- 查看所有索引的使用频率
SELECT
    schemaname AS schema_name,
    tablename AS table_name,
    indexname AS index_name,
    idx_scan AS index_scans,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched,
    pg_size_pretty(pg_relation_size(quote_ident(schemaname)||'.'||quote_ident(tablename))) AS table_size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan ASC;

-- 查找未使用的索引（扫描次数为0）
-- 注意：PRIMARY KEY和UNIQUE约束的索引即使扫描为0也不应该删除
SELECT
    schemaname AS schema_name,
    tablename AS table_name,
    indexname AS index_name,
    idx_scan AS index_scans,
    pg_size_pretty(pg_relation_size(quote_ident(schemaname)||'.'||quote_ident(tablename))) AS table_size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
  AND idx_scan = 0
  AND indexname NOT LIKE '%_pkey'
  AND indexname NOT LIKE '%_key%'
ORDER BY pg_relation_size(quote_ident(schemaname)||'.'||quote_ident(tablename)) DESC;

-- =====================================
-- 2. 表大小和索引大小统计
-- =====================================

-- 查看表及其索引的大小
SELECT
    t.tablename AS table_name,
    pg_size_pretty(pg_total_relation_size(quote_ident(t.tablename))) AS total_size,
    pg_size_pretty(pg_relation_size(quote_ident(t.tablename))) AS table_size,
    pg_size_pretty(pg_indexes_size(quote_ident(t.tablename))) AS indexes_size,
    CASE
        WHEN pg_total_relation_size(quote_ident(t.tablename)) > 0 THEN
            ROUND(100 * pg_indexes_size(quote_ident(t.tablename))::NUMERIC /
                  pg_total_relation_size(quote_ident(t.tablename)), 2)
        ELSE 0
    END AS index_ratio_pct
FROM pg_tables t
WHERE t.schemaname = 'public'
ORDER BY pg_total_relation_size(quote_ident(t.tablename)) DESC;

-- =====================================
-- 3. 查询性能分析
-- =====================================

-- 查看慢查询（需要pg_stat_statements扩展）
-- 如果未安装，运行: CREATE EXTENSION pg_stat_statements;
-- SELECT
--     calls,
--     total_exec_time / 1000 AS total_time_seconds,
--     mean_exec_time AS avg_time_ms,
--     max_exec_time AS max_time_ms,
--     query
-- FROM pg_stat_statements
-- ORDER BY mean_exec_time DESC
-- LIMIT 20;

-- =====================================
-- 4. 表扫描统计
-- =====================================

-- 查看表的顺序扫描 vs 索引扫描
SELECT
    schemaname AS schema_name,
    tablename AS table_name,
    seq_scan AS sequential_scans,
    seq_tup_read AS seq_tuples_read,
    idx_scan AS index_scans,
    idx_tup_fetch AS idx_tuples_fetched,
    CASE
        WHEN (seq_scan + idx_scan) > 0 THEN
            ROUND(100 * idx_scan::NUMERIC / (seq_scan + idx_scan), 2)
        ELSE 0
    END AS index_scan_ratio_pct
FROM pg_stat_user_tables
WHERE schemaname = 'public'
ORDER BY seq_scan DESC;

-- 查找高顺序扫描的表（可能需要优化索引）
SELECT
    schemaname AS schema_name,
    tablename AS table_name,
    seq_scan AS sequential_scans,
    seq_tup_read AS seq_tuples_read,
    pg_size_pretty(pg_relation_size(quote_ident(schemaname)||'.'||quote_ident(tablename))) AS table_size
FROM pg_stat_user_tables
WHERE schemaname = 'public'
  AND seq_scan > 1000
ORDER BY seq_scan DESC;

-- =====================================
-- 5. 索引效率分析
-- =====================================

-- 查看索引的读取效率
SELECT
    schemaname AS schema_name,
    tablename AS table_name,
    indexname AS index_name,
    idx_scan AS index_scans,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched,
    CASE
        WHEN idx_scan > 0 THEN
            ROUND(100 * idx_tup_fetch::NUMERIC / idx_tup_read, 2)
        ELSE 0
    END AS fetch_efficiency_pct
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
  AND idx_scan > 0
ORDER BY idx_scan DESC;

-- =====================================
-- 6. 缓存命中率
-- =====================================

-- 查看数据库缓存命中率
SELECT
    round(sum(blks_hit)::NUMERIC / (sum(blks_hit) + sum(blks_read) + 1) * 100, 2) AS cache_hit_ratio_pct
FROM pg_stat_database
WHERE datname = current_database();

-- 查看表级别的缓存命中率
SELECT
    schemaname AS schema_name,
    tablename AS table_name,
    heap_blks_read AS heap_disk_reads,
    heap_blks_hit AS heap_cache_hits,
    CASE
        WHEN (heap_blks_hit + heap_blks_read) > 0 THEN
            ROUND(100 * heap_blks_hit::NUMERIC / (heap_blks_hit + heap_blks_read), 2)
        ELSE 0
    END AS cache_hit_ratio_pct
FROM pg_statio_user_tables
WHERE schemaname = 'public'
ORDER BY (heap_blks_hit + heap_blks_read) DESC;

-- =====================================
-- 7. 索引建议
-- =====================================

-- 查找可能缺失索引的外键
SELECT
    tc.table_name,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND NOT EXISTS (
      SELECT 1
      FROM pg_indexes
      WHERE schemaname = 'public'
        AND tablename = tc.table_name
        AND indexdef LIKE '%' || kcu.column_name || '%'
  )
ORDER BY tc.table_name, kcu.column_name;

COMMENT ON SCHEMA public IS '索引性能监控脚本已就绪';
