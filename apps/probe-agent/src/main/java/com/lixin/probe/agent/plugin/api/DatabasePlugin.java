package com.lixin.probe.agent.plugin.api;

import com.lixin.probe.agent.pojo.request.ProbeRequest;
import com.lixin.probe.agent.pojo.response.ProbeResponse;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 数据库插件 SPI 接口
 * 所有数据库插件必须实现此接口
 *
 * @author probe-agent
 * @since 1.0.0
 */
public interface DatabasePlugin {

    // ====== 插件元数据 ======

    /**
     * 获取插件ID（唯一标识）
     *
     * @return 插件ID，如 "mysql-database-plugin"
     */
    String getPluginId();

    /**
     * 获取插件名称
     *
     * @return 插件名称，如 "MySQL Database Plugin"
     */
    String getName();

    /**
     * 获取插件类型
     *
     * @return 插件类型，如 "DATABASE"
     */
    String getType();

    /**
     * 获取插件版本
     *
     * @return 插件版本，如 "1.0.0"
     */
    String getVersion();

    /**
     * 获取插件描述
     *
     * @return 插件描述
     */
    String getDescription();

    /**
     * 获取支持的数据库类型
     *
     * @return 数据库类型，如 "mysql", "postgresql", "oracle"
     */
    String getDbType();

    /**
     * 获取支持的数据库版本范围
     *
     * @return 版本范围（如 "5.7,8.0" 或 "11g,12c,19c"）
     */
    String getVersionRange();

    /**
     * 获取默认端口
     *
     * @return 默认端口号
     */
    int getDefaultPort();

    /**
     * 检查是否支持指定版本
     *
     * @param version 数据库版本
     * @return 是否支持
     */
    boolean isVersionSupported(String version);

    // ====== 连接管理 ======

    /**
     * 构建JDBC连接URL
     *
     * @param params 连接参数（host, port, name等）
     * @return JDBC连接URL
     */
    String buildUrl(Map<String, Object> params);

    /**
     * 获取JDBC驱动类名
     *
     * @return 驱动类全限定名
     */
    String getDriverClass();

    /**
     * 获取数据库连接
     *
     * @param params 连接参数
     * @return 数据库连接
     * @throws Exception 连接失败时抛出异常
     */
    Connection getConnection(Map<String, Object> params) throws Exception;

    // ====== 数据探针功能 ======

    /**
     * 查询数据库元数据
     *
     * @param connection 数据库连接
     * @param request    探针请求参数
     * @return 元数据响应
     */
    CompletableFuture<ProbeResponse.Metadata> getMetadata(
        Connection connection,
        ProbeRequest request
    );

    /**
     * 查询数据量统计
     *
     * @param connection 数据库连接
     * @param request    探针请求参数
     * @return 数据量统计响应
     */
    CompletableFuture<ProbeResponse.DataSize> getDataSize(
        Connection connection,
        ProbeRequest request
    );

    /**
     * 执行SQL查询获取数据内容
     *
     * @param connection 数据库连接
     * @param request    探针请求参数
     * @return 数据内容响应
     */
    CompletableFuture<ProbeResponse.DataContent> getDataContent(
        Connection connection,
        ProbeRequest request
    );

    // ====== SQL构建辅助方法 ======

    /**
     * 表名转义（处理不同数据库的关键字和特殊字符）
     *
     * @param fullTableName 完整表名（可能包含schema）
     * @return 转义后的表名
     */
    String escapeTableName(String fullTableName);

    /**
     * 获取行数查询SQL
     *
     * @param isPrecise 是否精确统计（true=COUNT(*), false=估算）
     * @param params   查询参数（包含database, table, schema）
     * @return SQL语句
     */
    String getCountSql(boolean isPrecise, Map<String, Object> params);

    /**
     * 获取存储大小查询SQL
     *
     * @param params 查询参数（包含database, table, schema）
     * @return SQL语句
     */
    String getStorageSql(Map<String, Object> params);

    /**
     * 获取测试连接SQL
     *
     * @return 测试SQL语句
     */
    default String getTestSql() {
        return "SELECT 1";
    }

    /**
     * 导出表数据为 CSV 格式
     *
     * @param connection 数据库连接
     * @param fullTableName 完整表名（可能包含 schema.table）
     * @param whereClause   WHERE 过滤条件（可为 null）
     * @param columns       需要导出的列名列表（null=全部列）
     * @return CSV 格式字符串
     */
    default String exportDataAsCsv(Connection connection, String fullTableName,
                                   String whereClause, List<String> columns) {
        try {
            String escaped = escapeTableName(fullTableName);
            String colSelect = (columns != null && !columns.isEmpty())
                    ? columns.stream().map(this::escapeTableName).collect(java.util.stream.Collectors.joining(", "))
                    : "*";
            String sql = "SELECT " + colSelect + " FROM " + escaped;
            if (whereClause != null && !whereClause.trim().isEmpty()) {
                sql += " WHERE " + whereClause;
            }

            StringBuilder csv = new StringBuilder();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                // Header
                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) csv.append(",");
                    csv.append(meta.getColumnLabel(i));
                }
                csv.append("\n");

                // Rows
                while (rs.next()) {
                    for (int i = 1; i <= colCount; i++) {
                        if (i > 1) csv.append(",");
                        Object val = rs.getObject(i);
                        if (val != null) {
                            String s = val.toString();
                            if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
                                csv.append("\"").append(s.replace("\"", "\"\"")).append("\"");
                            } else {
                                csv.append(s);
                            }
                        }
                    }
                    csv.append("\n");
                }
            }
            return csv.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
