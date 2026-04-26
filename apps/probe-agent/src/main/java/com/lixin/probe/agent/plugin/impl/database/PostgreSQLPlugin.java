package com.lixin.probe.agent.plugin.impl.database;

import com.lixin.probe.agent.plugin.api.DatabasePlugin;
import com.lixin.probe.agent.pojo.request.ProbeRequest;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * PostgreSQL 数据库插件
 * 支持 PostgreSQL 12, 13, 14, 15, 16 版本
 *
 * @author probe-agent
 * @since 1.0.0
 */
public class PostgreSQLPlugin implements DatabasePlugin {

    private static final Logger log = LoggerFactory.getLogger(PostgreSQLPlugin.class);
    @Override
    public String getPluginId() {
        return "postgresql-database-plugin";
    }

    @Override
    public String getName() {
        return "PostgreSQL Database Plugin";
    }

    @Override
    public String getType() {
        return "DATABASE";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "PostgreSQL 数据库探针插件，支持元数据查询、数据量统计";
    }

    @Override
    public String getDbType() {
        return "postgresql";
    }

    @Override
    public String getVersionRange() {
        return "12,13,14,15,16";
    }

    @Override
    public int getDefaultPort() {
        return 5432;
    }

    @Override
    public boolean isVersionSupported(String version) {
        if (version == null || version.isEmpty()) {
            return true;
        }
        return "12".equals(version) || "13".equals(version)
                || "14".equals(version) || "15".equals(version)
                || "16".equals(version);
    }

    @Override
    public String getDriverClass() {
        return "org.postgresql.Driver";
    }

    @Override
    public String buildUrl(Map<String, Object> params) {
        String host = (String) params.get("host");
        Integer port = (Integer) params.get("port");
        String name = (String) params.get("databaseName");
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, name);
    }

    @Override
    public Connection getConnection(Map<String, Object> params) throws Exception {
        String url = buildUrl(params);
        String username = (String) params.get("username");
        String password = (String) params.get("password");

        try {
            Class.forName(getDriverClass());
            Connection conn = DriverManager.getConnection(url, username, password);

            if (conn == null || conn.isClosed()) {
                throw new SQLException("创建的连接为空或已关闭");
            }

            log.info("{}数据库连接成功 | URL: {}", getDbType(), url);
            return conn;

        } catch (ClassNotFoundException e) {
            log.error("{}驱动加载失败 | 驱动类: {}", getDbType(), getDriverClass(), e);
            throw e;

        } catch (SQLException e) {
            log.error("{}数据库连接失败 | URL: {}", getDbType(), url, e);
            throw e;
        }
    }

    @Override
    public CompletableFuture<ProbeResponse.Metadata> getMetadata(Connection connection, ProbeRequest request) {
        try {
            Map<String, Object> config = new HashMap<>();
            if (request.getDatabase() != null) {
                config.put("name", request.getDatabase().getName());
                config.put("host", request.getDatabase().getHost());
                config.put("port", request.getDatabase().getPort());
                config.put("version", request.getDatabase().getType());
                config.put("schemas", request.getDatabase().getSchemas());
                config.put("ignore", Collections.emptyList());
            }

            String dbName = (String) config.get("name");
            List<String> schemas = (List<String>) config.getOrDefault("schemas", Collections.emptyList());
            java.sql.DatabaseMetaData metaData = connection.getMetaData();
            schemas = (schemas == null || schemas.isEmpty()) ? Collections.singletonList(null) : schemas;

            Map<String, ProbeResponse.Metadata.Table> tables = new LinkedHashMap<>();

            // 通用表查询逻辑
            for (String schema : schemas) {
                try (ResultSet tableSet = metaData.getTables(dbName, schema, null, new String[]{"TABLE"})) {
                    while (tableSet.next()) {
                        String tableName = tableSet.getString("TABLE_NAME");

                        try (ResultSet columnSet = metaData.getColumns(dbName, schema, tableName, null)) {
                            Map<String, ProbeResponse.Metadata.Column> columns = new LinkedHashMap<>();
                            while (columnSet.next()) {
                                ProbeResponse.Metadata.Column column = ProbeResponse.Metadata.Column.builder()
                                        .name(columnSet.getString("COLUMN_NAME"))
                                        .comment(columnSet.getString("REMARKS"))
                                        .type(columnSet.getString("TYPE_NAME"))
                                        .build();
                                columns.put(columnSet.getString("COLUMN_NAME"), column);
                            }

                            String fullTableName = (schema == null || schema.isEmpty())
                                    ? tableName
                                    : schema + "." + tableName;

                            tables.put(fullTableName, ProbeResponse.Metadata.Table
                                    .builder()
                                    .name(tableName)
                                    .databaseName(dbName)
                                    .comment(tableSet.getString("REMARKS"))
                                    .columnCount(columns.size())
                                    .columns(columns)
                                    .build());
                        }
                    }
                }
            }

            // 查询数据库版本、字符集和排序规则
            String dbVersion = null;
            String charset = null;
            String collation = null;

            // 查询PostgreSQL版本
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT version()")) {
                if (rs.next()) {
                    String fullVersion = rs.getString("version");
                    log.info("查询到PostgreSQL完整版本信息: {}", fullVersion);
                    // 解析版本号，例如 "PostgreSQL 16.13 on ..." -> "16.13"
                    if (fullVersion != null && fullVersion.startsWith("PostgreSQL ")) {
                        String[] parts = fullVersion.split(" ");
                        if (parts.length > 1) {
                            dbVersion = parts[1];  // 获取 "16.13" 部分
                        }
                    }
                    log.info("解析后的PostgreSQL版本: {}", dbVersion);
                }
            } catch (Exception e) {
                log.warn("查询数据库版本失败: {}", e.getMessage());
            }

            // 查询字符集和排序规则
            log.info("查询PostgreSQL数据库字符集和排序规则: datname={}", dbName);
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT encoding, datcollate FROM pg_database WHERE datname = '" + dbName + "'")) {
                if (rs.next()) {
                    int encodingCode = rs.getInt("encoding");
                    log.info("查询到PostgreSQL编码ID: {}", encodingCode);
                    // PostgreSQL encoding code to charset name mapping
                    // Source: PostgreSQL src/include/catalog/pg_encoding.h
                    String[] encodingNames = {
                        "SQL_ASCII",      // 0
                        "EUC_JP",         // 1
                        "EUC_CN",         // 2
                        "EUC_KR",         // 3
                        "EUC_TW",         // 4
                        "EUC_KR",         // 5 (MULE_INTERNAL for Korean)
                        "UTF8",           // 6  <-- This is the correct mapping!
                        "MULE_INTERNAL",  // 7
                        "LATIN1",         // 8
                        "LATIN2",         // 9
                        "LATIN3",         // 10
                        "LATIN4",         // 11
                        "LATIN5",         // 12
                        "LATIN6",         // 13
                        "LATIN7",         // 14
                        "LATIN8",         // 15
                        "LATIN9",         // 16
                        "LATIN10",        // 17
                        "SQL_ASCII",      // 18
                        "UTF8",           // 19 (alternate)
                        "MULE_INTERNAL"   // 20 (alternate)
                    };
                    if (encodingCode >= 0 && encodingCode < encodingNames.length) {
                        charset = encodingNames[encodingCode];
                    } else {
                        log.warn("编码ID {} 超出范围，使用默认UTF8", encodingCode);
                        charset = "UTF8";
                    }
                    collation = rs.getString("datcollate");
                    log.info("PostgreSQL数据库信息: encodingCode={}, charset={}, collation={}",
                        encodingCode, charset, collation);
                } else {
                    log.warn("未查询到数据库字符集信息，使用默认UTF8");
                    charset = "UTF8";
                    collation = "en_US.UTF-8";
                }
            } catch (Exception e) {
                log.warn("查询数据库字符集和排序规则失败，使用默认值: {}", e.getMessage());
                charset = "UTF8";
                collation = "en_US.UTF-8";
            }
            log.info("最终使用的charset={}, collation={}", charset, collation);

            // 构建元数据响应
            log.info("构建Database对象: type={}, version={}, charset={}, collation={}",
                getDbType(), dbVersion, charset, collation);
            ProbeResponse.Metadata.Database database = ProbeResponse.Metadata.Database.builder()
                    .probeKey(request.getDatabase() != null ? request.getDatabase().getProbeKey() : null)
                    .type(getDbType())
                    .version(dbVersion != null ? dbVersion : (String) config.get("version"))
                    .host((String) config.get("host"))
                    .port((Integer) config.get("port"))
                    .username((String) config.get("username"))
                    .password(null)
                    .name(dbName)
                    .charset(charset)
                    .collation(collation)
                    .schemas(schemas)
                    .tableCount(tables.size())
                    .columnCount(tables.values().stream().mapToInt(ProbeResponse.Metadata.Table::getColumnCount).sum())
                    .tables(tables)
                    .build();
            log.info("Database对象构建完成: charset={}, collation={}",
                database.getCharset(), database.getCollation());

            ProbeResponse.Metadata metadata = ProbeResponse.Metadata.builder()
                    .type(getDbType())
                    .databases(Map.of(dbName, database))
                    .build();

            return CompletableFuture.completedFuture(metadata);

        } catch (Exception e) {
            log.error("获取{}元数据失败", getDbType(), e);
            throw new CompletionException(e);
        }
    }

    @Override
    public CompletableFuture<ProbeResponse.DataSize> getDataSize(Connection connection, ProbeRequest request) {
        try {
            Map<String, Object> config = new HashMap<>();
            if (request.getDatabase() != null) {
                config.put("name", request.getDatabase().getName());
                config.put("host", request.getDatabase().getHost());
                config.put("port", request.getDatabase().getPort());
                config.put("schemas", request.getDatabase().getSchemas());
            }

            String dbName = (String) config.get("name");
            List<String> schemas = (List<String>) config.getOrDefault("schemas", Collections.emptyList());

            java.sql.DatabaseMetaData metaData = connection.getMetaData();
            schemas = (schemas == null || schemas.isEmpty()) ? Collections.singletonList(null) : schemas;

            // 查询PostgreSQL版本
            String dbVersion = null;
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT version()")) {
                if (rs.next()) {
                    String fullVersion = rs.getString("version");
                    log.info("查询到PostgreSQL完整版本信息: {}", fullVersion);
                    // 解析版本号，例如 "PostgreSQL 16.13 on ..." -> "16.13"
                    if (fullVersion != null && fullVersion.startsWith("PostgreSQL ")) {
                        String[] parts = fullVersion.split(" ");
                        if (parts.length > 1) {
                            dbVersion = parts[1];  // 获取 "16.13" 部分
                        }
                    }
                    log.info("解析后的PostgreSQL版本: {}", dbVersion);
                }
            } catch (Exception e) {
                log.warn("查询数据库版本失败: {}", e.getMessage());
            }

            Map<String, ProbeResponse.DataSize.Table> tables = new LinkedHashMap<>();

            for (String schema : schemas) {
                try (ResultSet tableSet = metaData.getTables(dbName, schema, null, new String[]{"TABLE"})) {
                    while (tableSet.next()) {
                        String tableName = tableSet.getString("TABLE_NAME");
                        String schemaName = tableSet.getString("TABLE_SCHEM");

                        Map<String, Object> params = new HashMap<>();
                        params.put("database", dbName);
                        params.put("table", tableName);
                        params.put("schema", schemaName);

                        // 查询行数
                        long rowCount = 0L;
                        String countSql = getCountSql(true, params);
                        try (Statement stmt = connection.createStatement();
                             ResultSet rs = stmt.executeQuery(countSql)) {
                            if (rs.next()) {
                                rowCount = rs.getLong(1);
                            }
                        }

                        // 查询存储大小
                        long storage = 0L;
                        String storageSql = getStorageSql(params);
                        try (Statement stmt = connection.createStatement();
                             ResultSet rs = stmt.executeQuery(storageSql)) {
                            if (rs.next()) {
                                storage = rs.getLong(1);
                            }
                        }

                        // 查询索引大小
                        long indexes = 0L;
                        String indexSql = getIndexSql(params);
                        try (Statement stmt = connection.createStatement();
                             ResultSet rs = stmt.executeQuery(indexSql)) {
                            if (rs.next()) {
                                indexes = rs.getLong(1);
                            }
                        }

                        // 获取列数
                        int columnCount = getColumnCount(metaData, dbName, schema, tableName);

                        String fullTableName = (schemaName == null || schemaName.isEmpty())
                                ? tableName
                                : schemaName + "." + tableName;

                        tables.put(fullTableName, ProbeResponse.DataSize.Table
                                .builder()
                                .name(tableName)
                                .databaseName(dbName)
                                .columnCount(columnCount)
                                .storage(storage)
                                .rowCount(rowCount)
                                .indexes(indexes)
                                .build());
                    }
                }
            }

            ProbeResponse.DataSize.Database database = ProbeResponse.DataSize.Database.builder()
                    .probeKey(request.getDatabase() != null ? request.getDatabase().getProbeKey() : null)
                    .type(getDbType())
                    .version(dbVersion != null ? dbVersion : "Unknown")
                    .host((String) config.get("host"))
                    .port((Integer) config.get("port"))
                    .username((String) config.get("username"))
                    .password(null)
                    .name(dbName)
                    .schemas(schemas)
                    .tableCount(tables.size())
                    .columnCount(tables.values().stream().mapToInt(ProbeResponse.DataSize.Table::getColumnCount).sum())
                    .storage(tables.values().stream().mapToLong(ProbeResponse.DataSize.Table::getStorage).sum())
                    .rowCount(tables.values().stream().mapToLong(ProbeResponse.DataSize.Table::getRowCount).sum())
                    .tables(tables)
                    .build();

            ProbeResponse.DataSize dataSize = ProbeResponse.DataSize.builder()
                    .databases(Map.of(dbName, database))
                    .build();

            return CompletableFuture.completedFuture(dataSize);

        } catch (Exception e) {
            log.error("获取{}数据量失败", getDbType(), e);
            throw new CompletionException(e);
        }
    }

    @Override
    public CompletableFuture<ProbeResponse.DataContent> getDataContent(Connection connection, ProbeRequest request) {
        // 默认实现：返回空数据内容
        return CompletableFuture.completedFuture(
            ProbeResponse.DataContent.builder()
                    .build()
        );
    }

    @Override
    public String escapeTableName(String fullTableName) {
        if (fullTableName == null || fullTableName.isEmpty()) {
            return "";
        }
        // PostgreSQL 使用双引号转义表名和列名
        return "\"" + fullTableName.replace(".", "\".\"") + "\"";
    }

    @Override
    public String getCountSql(boolean isPrecise, Map<String, Object> params) {
        String table = (String) params.get("table");
        String schema = (String) params.get("schema");

        if (isPrecise) {
            // 精确模式：使用完整的 schema.table 格式
            String effectiveSchema = (schema != null && !schema.isEmpty()) ? schema : "public";
            String fullTableName = effectiveSchema + "." + table;
            String escapedTableName = escapeTableName(fullTableName);
            return String.format("SELECT COUNT(*) FROM %s", escapedTableName);
        }
        // 粗略模式：使用 pg_class 系统表估算
        String escapedTable = escapeSqlIdentifier(table);
        return String.format("SELECT reltuples::bigint FROM pg_class WHERE relname = '%s'", escapedTable);
    }

    @Override
    public String getStorageSql(Map<String, Object> params) {
        // PostgreSQL 使用 pg_total_relation_size 函数
        // 需要构建完整的表名：schema.table
        String schema = (String) params.get("schema");
        String table = (String) params.get("table");

        // 如果 schema 为空或 null，使用 PostgreSQL 的默认 schema "public"
        String effectiveSchema = (schema != null && !schema.isEmpty()) ? schema : "public";

        // 使用 PostgreSQL 的 format 函数和 %I 占位符来正确转义标识符
        // format('%I.%I', schema, table) 会生成 "schema"."table" 格式
        // 然后使用 ::regclass 将其转换为 regclass 类型
        return String.format("SELECT pg_total_relation_size(format('%%I.%%I', '%s', '%s')::regclass) AS size",
                effectiveSchema, table);
    }

    public String getIndexSql(Map<String, Object> params) {
        // PostgreSQL 使用 pg_indexes_size 函数
        // 需要构建完整的表名：schema.table
        String schema = (String) params.get("schema");
        String table = (String) params.get("table");

        // 如果 schema 为空或 null，使用 PostgreSQL 的默认 schema "public"
        String effectiveSchema = (schema != null && !schema.isEmpty()) ? schema : "public";

        // 使用 PostgreSQL 的 format 函数和 %I 占位符来正确转义标识符
        // format('%I.%I', schema, table) 会生成 "schema"."table" 格式
        // 然后使用 ::regclass 将其转换为 regclass 类型
        return String.format("SELECT pg_indexes_size(format('%%I.%%I', '%s', '%s')::regclass) AS size",
                effectiveSchema, table);
    }

    @Override
    public String getTestSql() {
        return "SELECT 1";
    }

    // ====== 辅助方法 ======

    /**
     * 获取表的列数
     */
    protected int getColumnCount(java.sql.DatabaseMetaData metaData, String dbName, String schema, String tableName) throws SQLException {
        try (ResultSet columnSet = metaData.getColumns(dbName, schema, tableName, null)) {
            int count = 0;
            while (columnSet.next()) {
                count++;
            }
            return count;
        }
    }

    /**
     * 验证SQL标识符的安全性
     */
    protected boolean isValidSqlIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        String pattern = "^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$";
        return identifier.matches(pattern);
    }

    /**
     * 转义SQL标识符，防止SQL注入
     */
    protected String escapeSqlIdentifier(String identifier) {
        if (!isValidSqlIdentifier(identifier)) {
            throw new IllegalArgumentException("无效的SQL标识符: " + identifier);
        }
        return identifier;
    }
}
