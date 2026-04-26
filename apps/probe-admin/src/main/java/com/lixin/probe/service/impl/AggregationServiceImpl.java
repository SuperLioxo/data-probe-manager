package com.lixin.probe.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lixin.probe.service.AggregationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class AggregationServiceImpl implements AggregationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.password:}")
    private String defaultDbPassword;

    @Value("${spring.datasource.username:probe_user}")
    private String defaultDbUsername;

    @Override
    public void registerDataSource(String sourceId, String sourceName, String sourceType,
                                   String databaseType, String host, Integer port,
                                   String databaseName, String agentCode) {
        String sql = """
            INSERT INTO aggregation.data_source_registry (source_id, source_name, source_type, database_type, host, port, database_name, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (source_id) DO UPDATE SET
                source_name = EXCLUDED.source_name,
                source_type = EXCLUDED.source_type,
                database_type = EXCLUDED.database_type,
                host = EXCLUDED.host,
                port = EXCLUDED.port,
                database_name = EXCLUDED.database_name,
                updated_at = NOW()
            """;
        try {
            jdbcTemplate.update(sql, sourceName, sourceName, sourceType, databaseType,
                    host, port, databaseName);
            log.info("[汇聚] 注册数据源: name={}", sourceName);
        } catch (Exception e) {
            log.error("[汇聚] 注册数据源失败: {}", e.getMessage());
        }
    }

    @Override
    public void syncMetadataToAggregation(String sourceId, String databaseName, String tableName,
                                          Long rowCount, Long dataSize, List<Map<String, Object>> columns) {
        String tableSql = """
            INSERT INTO aggregation.table_metadata (source_id, table_name, row_count, column_count, schema_name, updated_at)
            VALUES (?, ?, ?, ?, ?, NOW())
            ON CONFLICT DO NOTHING
            """;
        try {
            Integer columnCount = columns != null ? columns.size() : null;
            jdbcTemplate.update(tableSql, sourceId, tableName, rowCount, columnCount, databaseName);
            log.debug("[汇聚] 同步表元数据: source={}, table={}", sourceId, tableName);

            // 自动关联：将元数据同步到连接同一数据库的其他数据源
            propagateMetadataToSameDatabase(sourceId, tableName, rowCount, columnCount, databaseName);
        } catch (Exception e) {
            log.error("[汇聚] 同步表元数据失败: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> queryAggregatedData(String sourceId, String tableName, int pageNum, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        int offset = (pageNum - 1) * pageSize;
        try {
            // 根据数据源信息构建查询
            Map<String, Object> sourceInfo = getDataSourceInfo(sourceId);
            if (sourceInfo == null) {
                log.warn("[汇聚] 数据源不存在: {}", sourceId);
                result.put("total", 0);
                result.put("records", Collections.emptyList());
                return result;
            }

            String databaseType = (String) sourceInfo.get("database_type");
            String schemaName = (String) sourceInfo.get("database_name");
            JdbcTemplate sourceJdbc = buildSourceJdbcTemplate(sourceInfo);

            String qualifiedTable = sanitize(tableName);
            // 对于 PostgreSQL，使用 schema.table 格式如果指定了 schema
            if ("postgresql".equalsIgnoreCase(databaseType) && schemaName != null) {
                // 检查表是否在目标数据库的 public schema 中
                qualifiedTable = sanitize(tableName);
            }

            String countSql = "SELECT COUNT(*) FROM " + qualifiedTable;
            Long total = sourceJdbc.queryForObject(countSql, Long.class);

            String dataSql = "SELECT * FROM " + qualifiedTable + " LIMIT ? OFFSET ?";
            List<Map<String, Object>> rows = sourceJdbc.queryForList(dataSql, pageSize, offset);

            result.put("total", total);
            result.put("records", rows);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);

            // 清理动态数据源连接
            closeDataSource(sourceJdbc);
        } catch (Exception e) {
            log.warn("[汇聚] 查询汇聚数据失败: sourceId={}, table={}, error={}", sourceId, tableName, e.getMessage(), e);
            result.put("total", 0);
            result.put("records", Collections.emptyList());
        }
        return result;
    }

    @Override
    public Map<String, Object> getAggregationStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            Long sourceCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM aggregation.data_source_registry", Long.class);
            Long tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM aggregation.table_metadata", Long.class);
            Long fileCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM aggregation.file_registry", Long.class);
            Long badRecordCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM aggregation.quality_bad_records", Long.class);
            stats.put("dataSourceCount", sourceCount);
            stats.put("tableCount", tableCount);
            stats.put("fileCount", fileCount);
            stats.put("badRecordCount", badRecordCount);
        } catch (Exception e) {
            log.warn("[汇聚] 获取汇聚统计失败: {}", e.getMessage());
            stats.put("dataSourceCount", 0);
            stats.put("tableCount", 0);
            stats.put("fileCount", 0);
            stats.put("badRecordCount", 0);
        }
        return stats;
    }

    @Override
    public void recordBadRecord(Long syncTaskId, Long sourceId, String tableName,
                                Map<String, Object> rowData, List<String> violatedRules, String reason) {
        String sql = """
            INSERT INTO aggregation.quality_bad_records (sync_task_id, source_id, table_name, row_data, violated_rules, rejection_reason, detected_at)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, NOW())
            """;
        try {
            jdbcTemplate.update(sql, syncTaskId, sourceId, tableName,
                    JSON.toJSONString(rowData), JSON.toJSONString(violatedRules), reason);
        } catch (Exception e) {
            log.error("[汇聚] 记录不合格数据失败: {}", e.getMessage());
        }
    }

    @Override
    public void registerFile(Long sourceId, String fileName, String filePath, Long fileSize,
                             String fileExtension, String fileMd5, String storagePath,
                             String aggregationTable, String uploadedBy) {
        String sql = """
            INSERT INTO aggregation.file_registry (source_id, file_name, file_path, file_size, file_extension, file_md5, storage_path, aggregation_table, uploaded_by, uploaded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """;
        try {
            jdbcTemplate.update(sql, sourceId, fileName, filePath, fileSize,
                    fileExtension, fileMd5, storagePath, aggregationTable, uploadedBy);
        } catch (Exception e) {
            log.error("[汇聚] 注册文件失败: {}", e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getAggregatedDataSources() {
        try {
            return jdbcTemplate.queryForList("SELECT * FROM aggregation.data_source_registry ORDER BY registered_at DESC");
        } catch (Exception e) {
            log.warn("[汇聚] 获取数据源列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> getAggregatedTables(String sourceId) {
        try {
            if (sourceId != null) {
                // 先尝试直接查询
                List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                        "SELECT * FROM aggregation.table_metadata WHERE source_id = ? ORDER BY table_name", sourceId);
                // 如果没有元数据，尝试复用同库数据源的元数据
                if (tables.isEmpty()) {
                    ensureMetadataPropagated(sourceId);
                    tables = jdbcTemplate.queryForList(
                            "SELECT * FROM aggregation.table_metadata WHERE source_id = ? ORDER BY table_name", sourceId);
                }
                return tables;
            }
            return jdbcTemplate.queryForList("SELECT * FROM aggregation.table_metadata ORDER BY table_name");
        } catch (Exception e) {
            log.warn("[汇聚] 获取表列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 确保数据源有元数据：如果没有，从同库数据源复制
     */
    private void ensureMetadataPropagated(String sourceId) {
        try {
            String sourceName = getSourceName(sourceId);
            String copySql = "INSERT INTO aggregation.table_metadata (source_id, table_name, table_comment, row_count, column_count, schema_name, registered_at, updated_at) " +
                    "SELECT ?, t.table_name, ?, t.row_count, t.column_count, t.schema_name, NOW(), NOW() " +
                    "FROM aggregation.table_metadata t " +
                    "JOIN aggregation.data_source_registry src ON src.source_id = t.source_id " +
                    "JOIN aggregation.data_source_registry dst ON dst.source_id = ? " +
                    "WHERE t.source_id != ? " +
                    "AND src.host = dst.host AND src.port = dst.port AND src.database_name = dst.database_name " +
                    "AND NOT EXISTS (SELECT 1 FROM aggregation.table_metadata m WHERE m.source_id = ? AND m.table_name = t.table_name)";
            int rows = jdbcTemplate.update(copySql, sourceId, sourceName, sourceId, sourceId, sourceId);
            if (rows > 0) {
                log.info("[汇聚] 自动关联: 为 {} 复制了 {} 张表的元数据", sourceId, rows);
            }
        } catch (Exception e) {
            log.warn("[汇聚] 自动关联元数据失败: {}", e.getMessage());
        }
    }

    private String getSourceName(String sourceId) {
        try {
            List<Map<String, Object>> sources = jdbcTemplate.queryForList(
                    "SELECT source_name FROM aggregation.data_source_registry WHERE source_id = ?", sourceId);
            return sources.isEmpty() ? "" : (String) sources.get(0).get("source_name");
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public List<Map<String, Object>> getBadRecords(Long syncTaskId, String tableName, int pageNum, int pageSize) {
        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM aggregation.quality_bad_records WHERE 1=1");
            List<Object> params = new ArrayList<>();
            if (syncTaskId != null) {
                sql.append(" AND sync_task_id = ?");
                params.add(syncTaskId);
            }
            if (tableName != null && !tableName.isEmpty()) {
                sql.append(" AND table_name = ?");
                params.add(tableName);
            }
            sql.append(" ORDER BY detected_at DESC LIMIT ? OFFSET ?");
            params.add(pageSize);
            params.add((pageNum - 1) * pageSize);
            return jdbcTemplate.queryForList(sql.toString(), params.toArray());
        } catch (Exception e) {
            log.warn("[汇聚] 获取不合格记录失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 自动关联：当某个数据源同步了表元数据后，
     * 将相同的元数据也写入连接同一数据库（同host+port+database_name）的其他数据源
     */
    private void propagateMetadataToSameDatabase(String sourceId, String tableName,
                                                  Long rowCount, Integer columnCount, String schemaName) {
        try {
            String sql = """
                INSERT INTO aggregation.table_metadata (source_id, table_name, row_count, column_count, schema_name, registered_at, updated_at)
                SELECT r.source_id, ?, ?, ?, ?, NOW(), NOW()
                FROM aggregation.data_source_registry r
                JOIN aggregation.data_source_registry src ON src.source_id = ?
                WHERE r.source_id != ?
                  AND r.host = src.host
                  AND r.port = src.port
                  AND r.database_name = src.database_name
                  AND NOT EXISTS (
                      SELECT 1 FROM aggregation.table_metadata t
                      WHERE t.source_id = r.source_id AND t.table_name = ?
                  )
                """;
            int rows = jdbcTemplate.update(sql, tableName, rowCount, columnCount, schemaName,
                    sourceId, sourceId, tableName);
            if (rows > 0) {
                log.info("[汇聚] 自动关联表元数据到{}个同库数据源: table={}", rows, tableName);
            }
        } catch (Exception e) {
            log.warn("[汇聚] 自动关联表元数据失败: {}", e.getMessage());
        }
    }

    private String sanitize(String identifier) {
        if (identifier == null) return "";
        return identifier.replaceAll("[^a-zA-Z0-9_]", "");
    }

    private Map<String, Object> getDataSourceInfo(String sourceId) {
        try {
            List<Map<String, Object>> sources = jdbcTemplate.queryForList(
                    "SELECT * FROM aggregation.data_source_registry WHERE source_id = ?", sourceId);
            return sources.isEmpty() ? null : sources.get(0);
        } catch (Exception e) {
            log.error("[汇聚] 查询数据源信息失败: {}", e.getMessage());
            return null;
        }
    }

    private JdbcTemplate buildSourceJdbcTemplate(Map<String, Object> sourceInfo) {
        String databaseType = (String) sourceInfo.get("database_type");
        String host = (String) sourceInfo.get("host");
        Integer port = sourceInfo.get("port") != null ? ((Number) sourceInfo.get("port")).intValue() : 5432;
        String databaseName = (String) sourceInfo.get("database_name");

        String jdbcUrl;
        String driverClassName;
        String username;
        String password;

        if ("mysql".equalsIgnoreCase(databaseType)) {
            driverClassName = "com.mysql.cj.jdbc.Driver";
            // 强制使用 IPv4 地址，避免 localhost 被 Java 解析为 IPv6
            String mysqlHost = "localhost".equals(host) || "127.0.0.1".equals(host) ? "127.0.0.1" : host;
            jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true", mysqlHost, port, databaseName);
            username = "root";
            password = "root";
        } else {
            driverClassName = "org.postgresql.Driver";
            jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
            username = defaultDbUsername;
            password = defaultDbPassword;
        }

        DriverManagerDataSource ds = new DriverManagerDataSource(jdbcUrl, username, password);
        ds.setDriverClassName(driverClassName);
        return new JdbcTemplate(ds);
    }

    private void closeDataSource(JdbcTemplate jdbc) {
        try {
            if (jdbc.getDataSource() instanceof DriverManagerDataSource ds) {
                // DriverManagerDataSource 无需显式关闭
            }
        } catch (Exception ignored) {
        }
    }
}
