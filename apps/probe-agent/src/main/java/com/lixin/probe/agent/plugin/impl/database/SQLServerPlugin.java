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

/**
 * SQL Server 数据库插件
 * 支持 SQL Server 2016, 2017, 2019, 2022 版本
 *
 * @author probe-agent
 * @since 1.0.0
 */
public class SQLServerPlugin implements DatabasePlugin {

    private static final Logger log = LoggerFactory.getLogger(SQLServerPlugin.class);
    @Override
    public String getPluginId() {
        return "sqlserver-database-plugin";
    }

    @Override
    public String getName() {
        return "SQL Server Database Plugin";
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
        return "SQL Server 数据库探针插件，支持元数据查询、数据量统计";
    }

    @Override
    public String getDbType() {
        return "sqlserver";
    }

    @Override
    public String getVersionRange() {
        return "2016,2017,2019,2022";
    }

    @Override
    public int getDefaultPort() {
        return 1433;
    }

    @Override
    public boolean isVersionSupported(String version) {
        if (version == null || version.isEmpty()) {
            return true;
        }
        return "2016".equals(version) || "2017".equals(version)
                || "2019".equals(version) || "2022".equals(version);
    }

    @Override
    public String getDriverClass() {
        return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    }

    @Override
    public String buildUrl(Map<String, Object> params) {
        String host = (String) params.get("host");
        Integer port = (Integer) params.get("port");
        String name = (String) params.get("databaseName");
        return String.format("jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false;trustServerCertificate=true",
                host, port, name);
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
                                    .comment(tableSet.getString("REMARKS"))
                                    .columnCount(columns.size())
                                    .columns(columns)
                                    .build());
                        }
                    }
                }
            }

            // 构建元数据响应
            ProbeResponse.Metadata.Database database = ProbeResponse.Metadata.Database.builder()
                    .type(getDbType())
                    .version((String) config.get("version"))
                    .host((String) config.get("host"))
                    .port((Integer) config.get("port"))
                    .username((String) config.get("username"))
                    .password(null)
                    .name(dbName)
                    .schemas(schemas)
                    .tableCount(tables.size())
                    .columnCount(tables.values().stream().mapToInt(ProbeResponse.Metadata.Table::getColumnCount).sum())
                    .tables(tables)
                    .build();

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

                        // 获取列数
                        int columnCount = getColumnCount(metaData, dbName, schema, tableName);

                        String fullTableName = (schemaName == null || schemaName.isEmpty())
                                ? tableName
                                : schemaName + "." + tableName;

                        tables.put(fullTableName, ProbeResponse.DataSize.Table
                                .builder()
                                .name(tableName)
                                .columnCount(columnCount)
                                .storage(storage)
                                .rowCount(rowCount)
                                .build());
                    }
                }
            }

            ProbeResponse.DataSize.Database database = ProbeResponse.DataSize.Database.builder()
                    .type(getDbType())
                    .version((String) config.get("version"))
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
        // SQL Server 使用方括号转义表名和列名
        return "[" + fullTableName.replace(".", "].[") + "]";
    }

    @Override
    public String getCountSql(boolean isPrecise, Map<String, Object> params) {
        String tableName = escapeTableName((String) params.get("table"));
        if (isPrecise) {
            return String.format("SELECT COUNT(*) FROM %s", tableName);
        }
        // 粗略模式：使用 sys.dm_db_partition_stats 估算
        String database = escapeSqlIdentifier((String) params.get("database"));
        String table = escapeSqlIdentifier((String) params.get("table"));
        return String.format("SELECT SUM(row_count) FROM sys.dm_db_partition_stats " +
                "WHERE object_id = OBJECT_ID('%s.%s')",
                database, table);
    }

    @Override
    public String getStorageSql(Map<String, Object> params) {
        // SQL Server 使用 sys.allocation_units 查询（页面数 * 8KB = 字节数）
        String database = escapeSqlIdentifier((String) params.get("database"));
        String table = escapeSqlIdentifier((String) params.get("table"));
        return String.format("SELECT SUM(a.total_pages) * 8 FROM sys.allocation_units a " +
                "INNER JOIN sys.partitions p ON a.container_id = p.partition_id " +
                "INNER JOIN sys.tables t ON p.object_id = t.object_id " +
                "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id " +
                "WHERE s.name + '.' + t.name = '%s.%s'",
                database, table);
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
