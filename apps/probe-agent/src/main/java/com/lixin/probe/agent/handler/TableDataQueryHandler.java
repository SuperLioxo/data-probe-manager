package com.lixin.probe.agent.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.config.DatabaseConfigManager;
import com.lixin.probe.agent.config.DatabaseConnectionConfig;
import com.lixin.probe.agent.plugin.api.DatabasePlugin;
import com.lixin.probe.agent.plugin.loader.SpiPluginLoader;
import com.lixin.probe.agent.pojo.request.ProbeRequest;
import com.lixin.probe.agent.pojo.request.TableDataQueryConfig;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

/**
 * 表数据查询处理器
 *
 * 处理通过WebSocket发送的表数据查询请求
 */
@Component
public class TableDataQueryHandler {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(TableDataQueryHandler.class);

    private final SpiPluginLoader pluginLoader;
    private final DatabaseConfigManager databaseConfigManager;
    private final ObjectMapper objectMapper;

    @Autowired
    public TableDataQueryHandler(SpiPluginLoader pluginLoader,
                                  DatabaseConfigManager databaseConfigManager) {
        this.pluginLoader = pluginLoader;
        this.databaseConfigManager = databaseConfigManager;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 处理表数据查询请求
     *
     * @param request 探针请求
     * @return 表数据查询结果
     */
    public Map<String, Object> handleQuery(ProbeRequest request) {
        log.info("========== [TableDataQueryHandler] 开始处理表数据查询 ==========");
        log.info("probeKey: {}", request.getCode());

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 从params中获取查询配置
            Object paramsObj = request.getParams();
            if (paramsObj == null) {
                log.error("params为空");
                return buildErrorResponse("查询参数为空");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) paramsObj;

            // 2. 解析TableDataQueryConfig
            TableDataQueryConfig queryConfig = parseQueryConfig(params);
            if (queryConfig == null) {
                log.error("解析查询配置失败");
                return buildErrorResponse("解析查询配置失败");
            }

            log.info("查询配置: databaseName={}, tableName={}, pageNum={}, pageSize={}",
                    queryConfig.getDatabaseName(), queryConfig.getTableName(),
                    queryConfig.getPageNum(), queryConfig.getPageSize());

            // 3. 获取数据库连接
            // 需要从databaseConfigManager中找到对应databaseName的连接配置
            Object connectionConfig = findDatabaseConfig(queryConfig.getDatabaseName());
            if (connectionConfig == null) {
                log.error("未找到数据库配置: databaseName={}", queryConfig.getDatabaseName());
                return buildErrorResponse("未找到数据库配置: " + queryConfig.getDatabaseName());
            }

            // 4. 构建插件配置并执行查询
            Map<String, Object> pluginConfig = buildPluginConfig(connectionConfig);
            String dbType = (String) pluginConfig.get("type");

            com.lixin.probe.agent.plugin.api.DatabasePlugin plugin = pluginLoader.getPlugin(dbType);
            if (plugin == null) {
                log.error("未找到数据库插件: {}", dbType);
                return buildErrorResponse("不支持的数据库类型: " + dbType);
            }

            // 5. 执行查询
            try (Connection conn = plugin.getConnection(pluginConfig)) {
                if (conn == null || conn.isClosed()) {
                    log.error("数据库连接失败");
                    return buildErrorResponse("数据库连接失败");
                }

                log.info("数据库连接成功，开始查询表数据");
                result = executeQuery(conn, queryConfig, queryConfig.getDatabaseName());
                log.info("查询完成: 总行数={}", result.get("total"));

            } catch (SQLException e) {
                log.error("SQL执行失败", e);
                return buildErrorResponse("SQL执行失败: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("处理表数据查询失败", e);
            return buildErrorResponse("查询失败: " + e.getMessage());
        }

        log.info("========== [TableDataQueryHandler] 处理完成 ==========");
        return result;
    }

    /**
     * 解析查询配置
     */
    private TableDataQueryConfig parseQueryConfig(Map<String, Object> params) {
        try {
            // 尝试直接从params中获取字段
            String databaseName = params.get("databaseName") != null ? params.get("databaseName").toString() : null;
            String tableName = params.get("tableName") != null ? params.get("tableName").toString() : null;
            Integer pageNum = params.get("pageNum") != null ? Integer.parseInt(params.get("pageNum").toString()) : 1;
            Integer pageSize = params.get("pageSize") != null ? Integer.parseInt(params.get("pageSize").toString()) : 50;

            // 解析过滤器
            @SuppressWarnings("unchecked")
            Map<String, Object> filters = params.get("filters") != null ?
                (Map<String, Object>) params.get("filters") : null;

            // 解析游标分页参数
            String cursor = params.get("cursor") != null ? params.get("cursor").toString() : null;
            String orderByColumn = params.get("orderByColumn") != null ? params.get("orderByColumn").toString() : null;
            boolean useCursorPagination = params.get("useCursorPagination") != null ?
                Boolean.parseBoolean(params.get("useCursorPagination").toString()) : false;

            if (databaseName != null && tableName != null) {
                TableDataQueryConfig config = new TableDataQueryConfig(databaseName, tableName, pageNum, pageSize);
                config.setFilters(filters);
                config.setCursor(cursor);
                config.setOrderByColumn(orderByColumn);
                config.setUseCursorPagination(useCursorPagination);
                return config;
            }

            // 尝试从JSON字符串解析
            String configJson = (String) params.get("config");
            if (configJson != null) {
                return objectMapper.readValue(configJson, TableDataQueryConfig.class);
            }

            log.error("无法从params中提取查询配置: {}", params);
            return null;

        } catch (Exception e) {
            log.error("解析查询配置异常", e);
            return null;
        }
    }

    /**
     * 查找数据库配置
     */
    private Object findDatabaseConfig(String databaseName) {
        try {
            log.info("🔍 [findDatabaseConfig] 开始查找数据库配置: databaseName='{}'", databaseName);

            if (databaseConfigManager == null) {
                log.warn("❌ DatabaseConfigManager未初始化");
                return null;
            }

            List<DatabaseConnectionConfig> databases = databaseConfigManager.getDatabases();
            if (databases == null) {
                log.warn("❌ 数据库配置列表为null");
                return null;
            }

            log.info("📋 [findDatabaseConfig] 可用的数据库配置数量: {}", databases.size());

            for (int i = 0; i < databases.size(); i++) {
                DatabaseConnectionConfig db = databases.get(i);
                String dbName = db.getDatabaseName();
                String instanceId = db.getInstanceId();

                log.info("  [{}] 检查配置: instanceId='{}', databaseName='{}'", i, instanceId, dbName);

                if (databaseName.equals(dbName)) {
                    log.info("✅ [findDatabaseConfig] 找到匹配的数据库配置:");
                    log.info("  - instanceId: {}", db.getInstanceId());
                    log.info("  - databaseName: {}", db.getDatabaseName());
                    log.info("  - databaseType: {}", db.getDatabaseType());
                    log.info("  - host: {}", db.getHost());
                    log.info("  - port: {}", db.getPort());
                    return db;
                }
            }

            log.warn("❌ [findDatabaseConfig] 未找到匹配的数据库配置，databaseName='{}'", databaseName);
            return null;

        } catch (Exception e) {
            log.error("❌ [findDatabaseConfig] 查找数据库配置失败", e);
            return null;
        }
    }

    /**
     * 构建插件配置
     */
    private Map<String, Object> buildPluginConfig(Object connectionConfig) {
        Map<String, Object> config = new HashMap<>();

        if (connectionConfig instanceof DatabaseConnectionConfig) {
            DatabaseConnectionConfig conn = (DatabaseConnectionConfig) connectionConfig;

            log.info("🔧 [buildPluginConfig] 构建插件配置:");
            log.info("  - instanceId: {}", conn.getInstanceId());
            log.info("  - databaseType: {}", conn.getDatabaseType());
            log.info("  - host: {}", conn.getHost());
            log.info("  - port: {}", conn.getPort());
            log.info("  - databaseName: {}", conn.getDatabaseName());
            log.info("  - username: {}", conn.getUsername());
            log.info("  - schemas: {}", conn.getSchemas());

            // Defensive check: ensure databaseName is not null
            String dbName = conn.getDatabaseName();
            if (dbName == null || dbName.trim().isEmpty()) {
                log.error("❌ [buildPluginConfig] databaseName为null或空! instanceId={}", conn.getInstanceId());
                throw new IllegalArgumentException("数据库名称不能为空: instanceId=" + conn.getInstanceId());
            }

            config.put("type", conn.getDatabaseType());
            config.put("host", conn.getHost());
            config.put("port", conn.getPort());
            config.put("name", dbName);  // For plugins that use "name"
            config.put("databaseName", dbName);  // For plugins that use "databaseName"
            config.put("username", conn.getUsername());
            config.put("password", conn.getPassword());
            config.put("schemas", conn.getSchemas() != null ? conn.getSchemas() : Arrays.asList("public"));

            log.info("✅ [buildPluginConfig] 插件配置构建完成，databaseName={}", dbName);
        } else {
            // Fallback to Map-based config
            Map<String, Object> conn = (Map<String, Object>) connectionConfig;

            log.info("🔧 [buildPluginConfig] 使用Map配置:");
            log.info("  - config: {}", conn);

            // Defensive check: ensure databaseName is not null
            String dbName = conn.get("databaseName") != null ? conn.get("databaseName").toString() : null;
            if (dbName == null || dbName.trim().isEmpty()) {
                log.error("❌ [buildPluginConfig] Map中databaseName为null或空! config={}", conn);
                throw new IllegalArgumentException("Map中数据库名称不能为空");
            }

            config.put("type", conn.get("databaseType"));
            config.put("host", conn.get("host"));
            config.put("port", conn.get("port"));
            config.put("name", dbName);  // For plugins that use "name"
            config.put("databaseName", dbName);  // For plugins that use "databaseName"
            config.put("username", conn.get("username"));
            config.put("password", conn.get("password"));

            @SuppressWarnings("unchecked")
            List<String> schemas = (List<String>) conn.get("schemas");
            config.put("schemas", schemas != null ? schemas : Arrays.asList("public"));

            log.info("✅ [buildPluginConfig] Map插件配置构建完成，databaseName={}", dbName);
        }

        return config;
    }

    /**
     * 执行查询（支持游标分页和传统分页）
     */
    private Map<String, Object> executeQuery(Connection conn, TableDataQueryConfig queryConfig, String databaseName) throws SQLException {
        Map<String, Object> result = new HashMap<>();

        String tableName = queryConfig.getTableName();
        Integer pageSize = queryConfig.getPageSize();
        Map<String, Object> filters = queryConfig.getFilters();
        boolean useCursor = queryConfig.isUseCursorPagination();

        log.info("执行查询: databaseName={}, tableName={}, pageSize={}, useCursor={}, cursor={}",
                databaseName, tableName, pageSize, useCursor, queryConfig.getCursor());

        // 1. 获取表结构（列信息），传入 databaseName 作为 catalog 避免跨库列混入
        List<Map<String, Object>> columns = getTableColumns(conn, tableName, databaseName);
        result.put("columns", columns);

        if (columns.isEmpty()) {
            log.warn("表不存在或无法访问: {}", tableName);
            result.put("rows", List.of());
            result.put("total", 0L);
            result.put("hasMore", false);
            return result;
        }

        // 2. 检测排序列（时间戳或主键）
        String orderByColumn = queryConfig.getOrderByColumn();
        if (orderByColumn == null && useCursor) {
            // 首次请求，自动检测时间戳列
            orderByColumn = detectTimestampColumn(columns);
            if (orderByColumn == null) {
                // 没有时间戳列，使用主键
                orderByColumn = getPrimaryKeyColumn(conn, tableName);
            }
        } else if (orderByColumn != null) {
            log.info("使用指定的排序列: {}", orderByColumn);
        }

        // 3. 构建WHERE条件
        StringBuilder whereClause = new StringBuilder();
        List<Object> filterValues = new ArrayList<>();

        if (filters != null && !filters.isEmpty()) {
            log.info("应用过滤器: {}", filters);
            whereClause.append(" WHERE ");

            boolean firstCondition = true;
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                String columnName = entry.getKey();
                Object filterValue = entry.getValue();

                // 验证列名是否存在
                boolean columnExists = columns.stream()
                    .anyMatch(col -> columnName.equals(col.get("name")));

                if (!columnExists) {
                    log.warn("列 '{}' 不存在于表中，跳过此过滤条件", columnName);
                    continue;
                }

                if (!firstCondition) {
                    whereClause.append(" AND ");
                }

                whereClause.append("\"").append(columnName).append("\"::text LIKE ?");
                filterValues.add("%" + filterValue + "%");
                firstCondition = false;

                log.info("  添加过滤条件: {} LIKE {}", columnName, filterValue);
            }

            if (firstCondition) {
                whereClause.setLength(0);
            }
        }

        // 4. 游标分页：添加游标条件
        String cursor = queryConfig.getCursor();
        if (useCursor && cursor != null && !cursor.isEmpty() && orderByColumn != null) {
            log.info("使用游标分页: cursor={}, orderByColumn={}", cursor, orderByColumn);

            if (whereClause.length() == 0) {
                whereClause.append(" WHERE ");
            } else {
                whereClause.append(" AND ");
            }

            // 游标条件: order_by_column < cursor_value （降序，新数据在前）
            // 先用占位符，后面会根据数据库类型替换引号
            whereClause.append("\"").append(orderByColumn).append("\" < ?");
            filterValues.add(cursor);
        }

        // 5. 构建查询SQL
        // 根据数据库类型确定引号样式
        String quoteStart = "\"";
        String quoteEnd = "\"";

        // 检测数据库类型并使用相应的引号
        try {
            String dbUrl = conn.getMetaData().getURL();
            if (dbUrl != null && dbUrl.contains("mysql")) {
                quoteStart = "`";
                quoteEnd = "`";
            }
        } catch (SQLException e) {
            log.debug("无法检测数据库类型，使用默认双引号");
        }

        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            Map<String, Object> col = columns.get(i);
            sql.append(quoteStart).append(col.get("name")).append(quoteEnd);
        }
        sql.append(" FROM ").append(quoteStart).append(tableName).append(quoteEnd);

        // 添加WHERE条件
        if (whereClause.length() > 0) {
            // 更新WHERE条件中的引号
            String whereClauseStr = whereClause.toString();
            if (quoteStart.equals("`")) {
                // MySQL：将双引号替换为反引号
                whereClauseStr = whereClauseStr.replace("\"", "`");
            }
            sql.append(whereClauseStr);
        }

        // 添加ORDER BY（降序）
        if (orderByColumn != null) {
            sql.append(" ORDER BY ").append(quoteStart).append(orderByColumn).append(quoteEnd).append(" DESC");
            result.put("orderByColumn", orderByColumn);
        }

        // 添加LIMIT（游标分页不需要OFFSET）
        sql.append(" LIMIT ").append(pageSize + 1);  // 多取一条判断是否有更多数据

        log.info("SQL: {}", sql);

        // 6. 执行查询
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            // 设置参数（过滤条件 + 游标）
            for (int i = 0; i < filterValues.size(); i++) {
                pstmt.setString(i + 1, filterValues.get(i).toString());
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    rows.add(row);
                }
            }
        }

        // 7. 判断是否有更多数据
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows.remove(rows.size() - 1);  // 移除多取的那一条
        }
        result.put("rows", rows);
        result.put("hasMore", hasMore);

        // 8. 生成下一个游标
        if (hasMore && !rows.isEmpty() && orderByColumn != null) {
            Map<String, Object> lastRow = rows.get(rows.size() - 1);
            Object lastValue = lastRow.get(orderByColumn);

            if (lastValue != null) {
                String nextCursor = String.valueOf(lastValue);
                result.put("nextCursor", nextCursor);
                log.info("生成下一个游标: {} = {}", orderByColumn, nextCursor);
            }
        }

        // 9. 查询总数（仅用于显示，游标分页不依赖总数）
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ")
            .append(quoteStart).append(tableName).append(quoteEnd);
        if (whereClause.length() > 0) {
            // 移除游标条件，只统计过滤条件
            String whereStr = whereClause.toString();
            // 根据数据库类型调整引号
            if (quoteStart.equals("`")) {
                whereStr = whereStr.replace("\"", "`");
                // 查找并移除游标条件（MySQL格式）
                int cursorIndex = whereStr.indexOf(" AND `" + orderByColumn + "` <");
                if (cursorIndex > 0) {
                    countSql.append(whereStr.substring(0, cursorIndex));
                } else {
                    countSql.append(whereStr);
                }
            } else {
                // PostgreSQL等其他数据库保持原逻辑
                int cursorIndex = whereStr.indexOf(" AND \"" + orderByColumn + "\" <");
                if (cursorIndex > 0) {
                    countSql.append(whereStr.substring(0, cursorIndex));
                } else {
                    countSql.append(whereStr);
                }
            }
        }

        log.info("Count SQL: {}", countSql);
        try (PreparedStatement countPstmt = conn.prepareStatement(countSql.toString())) {

            // 设置过滤参数（不包括游标）
            int paramCount = filterValues.size() - (useCursor && cursor != null ? 1 : 0);
            for (int i = 0; i < paramCount; i++) {
                countPstmt.setString(i + 1, filterValues.get(i).toString());
            }

            try (ResultSet countRs = countPstmt.executeQuery()) {
                if (countRs.next()) {
                    result.put("total", countRs.getLong(1));
                }
            }
        }

        result.put("pageSize", pageSize);
        result.put("useCursorPagination", useCursor);

        return result;
    }

    /**
     * 获取表的列信息
     */
    private List<Map<String, Object>> getTableColumns(Connection conn, String tableName, String databaseName) throws SQLException {
        List<Map<String, Object>> columns = new java.util.ArrayList<>();

        DatabaseMetaData metaData = conn.getMetaData();
        // 传入 databaseName 作为 catalog，避免 MySQL 返回其他库的同名表列
        try (ResultSet rs = metaData.getColumns(databaseName, null, tableName, null)) {
            while (rs.next()) {
                Map<String, Object> column = new HashMap<>();
                column.put("name", rs.getString("COLUMN_NAME"));
                column.put("type", rs.getString("TYPE_NAME"));
                column.put("size", rs.getInt("COLUMN_SIZE"));
                column.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                columns.add(column);
            }
        }

        return columns;
    }

    /**
     * 检测表中的时间戳列（用于游标分页）
     * 优先级: created_at > updated_at > create_time > update_time > timestamp
     */
    private String detectTimestampColumn(List<Map<String, Object>> columns) {
        // 常见的时间戳列名（按优先级排序）
        String[] timestampCandidates = {
            "created_at", "created", "create_time",
            "updated_at", "updated", "update_time",
            "timestamp", "ts", "date"
        };

        // 不区分大小写查找
        for (String candidate : timestampCandidates) {
            for (Map<String, Object> column : columns) {
                String columnName = ((String) column.get("name")).toLowerCase();
                String columnType = ((String) column.get("type")).toLowerCase();

                if (columnName.equals(candidate) &&
                    (columnType.contains("timestamp") ||
                     columnType.contains("datetime") ||
                     columnType.contains("date"))) {
                    log.info("✓ 检测到时间戳列: {} (类型: {})", column.get("name"), column.get("type"));
                    return (String) column.get("name");
                }
            }
        }

        log.warn("⚠️  未检测到时间戳列，将使用主键游标分页");
        return null;
    }

    /**
     * 获取表的主键列名
     */
    private String getPrimaryKeyColumn(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getPrimaryKeys(null, null, tableName)) {
            if (rs.next()) {
                String pkColumn = rs.getString("COLUMN_NAME");
                log.info("✓ 检测到主键列: {}", pkColumn);
                return pkColumn;
            }
        }
        log.warn("⚠️  未检测到主键列");
        return null;
    }

    /**
     * 构建错误响应
     */
    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", message);
        result.put("columns", List.of());
        result.put("rows", List.of());
        result.put("total", 0L);
        result.put("pageNum", 1);
        result.put("pageSize", 50);
        return result;
    }
}
