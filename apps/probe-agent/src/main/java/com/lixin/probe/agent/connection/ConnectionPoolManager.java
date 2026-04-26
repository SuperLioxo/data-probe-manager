package com.lixin.probe.agent.connection;

import com.lixin.probe.agent.plugin.api.DatabasePlugin;
import com.lixin.probe.agent.plugin.loader.SpiPluginLoader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 数据库连接池管理器
 * 负责管理数据库连接的生命周期，包括创建、复用、监控和清理
 *
 * @author probe-agent
 * @since 1.0.0
 */
@Component
public class ConnectionPoolManager {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPoolManager.class);
    @Autowired
    private SpiPluginLoader pluginLoader;

    /**
     * 连接缓存：key=databaseKey, value=ConnectionHolder
     */
    private final Map<String, ConnectionHolder> connectionPool = new ConcurrentHashMap<>();

    /**
     * 连接配置缓存
     */
    private final Map<String, ConnectionConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 定时任务执行器
     */
    private ScheduledExecutorService scheduler;

    /**
     * 最大连接缓存时间（毫秒）
     */
    private static final long MAX_CONNECTION_AGE = 30 * 60 * 1000; // 30分钟

    /**
     * 空闲连接清理间隔（毫秒）
     */
    private static final long CLEANUP_INTERVAL = 5 * 60 * 1000; // 5分钟

    /**
     * 初始化连接池管理器
     */
    @PostConstruct
    public void init() {
        log.info("初始化数据库连接池管理器...");

        // 启动定时清理任务
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "connection-pool-cleaner");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleAtFixedRate(this::cleanupIdleConnections,
                CLEANUP_INTERVAL, CLEANUP_INTERVAL, TimeUnit.MILLISECONDS);

        log.info("数据库连接池管理器初始化完成");
    }

    /**
     * 销毁连接池管理器
     */
    @PreDestroy
    public void destroy() {
        log.info("销毁数据库连接池管理器...");

        // 关闭所有连接
        closeAll();

        // 停止定时任务
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("数据库连接池管理器已销毁");
    }

    /**
     * 获取或创建数据库连接
     *
     * @param databaseKey 数据库键（格式：type_name）
     * @param config      连接配置
     * @return 数据库连接
     */
    public Connection getConnection(String databaseKey, ConnectionConfig config) {
        ConnectionHolder holder = connectionPool.get(databaseKey);

        // 检查连接是否可用
        if (holder != null && holder.isValid()) {
            holder.updateLastUsed();
            log.debug("复用缓存的连接: {}", databaseKey);
            return holder.getConnection();
        }

        // 创建新连接
        return createConnection(databaseKey, config);
    }

    /**
     * 创建新的数据库连接
     */
    private Connection createConnection(String databaseKey, ConnectionConfig config) {
        try {
            DatabasePlugin plugin = pluginLoader.getPlugin(config.getType());
            if (plugin == null) {
                throw new SQLException("未找到数据库类型 [" + config.getType() + "] 的插件");
            }

            Map<String, Object> params = config.toMap();
            Connection conn = plugin.getConnection(params);

            if (conn == null || conn.isClosed()) {
                throw new SQLException("创建的连接为空或已关闭");
            }

            // 缓存连接
            ConnectionHolder holder = new ConnectionHolder(conn);
            connectionPool.put(databaseKey, holder);
            configCache.put(databaseKey, config);

            log.info("创建新的数据库连接: {} (总连接数: {})", databaseKey, connectionPool.size());
            return conn;

        } catch (Exception e) {
            log.error("创建数据库连接失败: {}", databaseKey, e);
            throw new RuntimeException("创建数据库连接失败", e);
        }
    }

    /**
     * 释放连接回连接池（不关闭，只是标记为可复用）
     *
     * @param databaseKey 数据库键
     */
    public void releaseConnection(String databaseKey) {
        ConnectionHolder holder = connectionPool.get(databaseKey);
        if (holder != null) {
            holder.updateLastUsed();
            log.debug("释放连接回连接池: {}", databaseKey);
        }
    }

    /**
     * 关闭指定数据库的连接
     *
     * @param databaseKey 数据库键
     */
    public void closeConnection(String databaseKey) {
        ConnectionHolder holder = connectionPool.remove(databaseKey);
        if (holder != null) {
            holder.close();
            log.info("关闭数据库连接: {}", databaseKey);
        }
    }

    /**
     * 关闭所有连接
     */
    public void closeAll() {
        log.info("关闭所有数据库连接...");

        int count = 0;
        for (Map.Entry<String, ConnectionHolder> entry : connectionPool.entrySet()) {
            entry.getValue().close();
            count++;
        }

        connectionPool.clear();
        configCache.clear();

        log.info("已关闭 {} 个数据库连接", count);
    }

    /**
     * 清理空闲连接
     */
    private void cleanupIdleConnections() {
        long now = System.currentTimeMillis();
        int cleanedCount = 0;

        // 清理超过最大存活时间的连接
        for (Map.Entry<String, ConnectionHolder> entry : connectionPool.entrySet()) {
            ConnectionHolder holder = entry.getValue();
            if (!holder.isValid() || (now - holder.getLastUsed() > MAX_CONNECTION_AGE)) {
                entry.getValue().close();
                connectionPool.remove(entry.getKey());
                configCache.remove(entry.getKey());
                cleanedCount++;
            }
        }

        if (cleanedCount > 0) {
            log.info("清理了 {} 个空闲连接 (剩余连接数: {})", cleanedCount, connectionPool.size());
        }
    }

    /**
     * 获取连接池统计信息
     *
     * @return 统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalConnections", connectionPool.size());
        stats.put("maxConnectionAge", MAX_CONNECTION_AGE);
        stats.put("cleanupInterval", CLEANUP_INTERVAL);

        // 按数据库类型统计
        Map<String, Integer> typeStats = new ConcurrentHashMap<>();
        for (ConnectionConfig config : configCache.values()) {
            typeStats.merge(config.getType(), 1, Integer::sum);
        }
        stats.put("connectionsByType", typeStats);

        return stats;
    }

    /**
     * 测试连接
     *
     * @param databaseKey 数据库键
     * @param config      连接配置
     * @return true=成功, false=失败
     */
    public boolean testConnection(String databaseKey, ConnectionConfig config) {
        Connection conn = null;
        try {
            DatabasePlugin plugin = pluginLoader.getPlugin(config.getType());
            if (plugin == null) {
                return false;
            }

            Map<String, Object> params = config.toMap();
            conn = plugin.getConnection(params);

            if (conn != null && !conn.isClosed()) {
                String testSql = plugin.getTestSql();
                if (testSql != null) {
                    try (java.sql.Statement stmt = conn.createStatement();
                         java.sql.ResultSet rs = stmt.executeQuery(testSql)) {
                        // 执行测试查询成功
                    }
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("测试连接失败: {}", databaseKey, e);
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.error("关闭测试连接失败", e);
                }
            }
        }
    }

    /**
     * 连接持有者
     */
    private static class ConnectionHolder {
        private final Connection connection;
        private final long createdTime;
        private volatile long lastUsedTime;

        public ConnectionHolder(Connection connection) {
            this.connection = connection;
            this.createdTime = System.currentTimeMillis();
            this.lastUsedTime = this.createdTime;
        }

        public Connection getConnection() {
            return connection;
        }

        public boolean isValid() {
            try {
                return connection != null && !connection.isClosed() && !connection.isReadOnly();
            } catch (SQLException e) {
                return false;
            }
        }

        public void close() {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                log.error("关闭连接失败", e);
            }
        }

        public long getLastUsed() {
            return lastUsedTime;
        }

        public void updateLastUsed() {
            this.lastUsedTime = System.currentTimeMillis();
        }

        public long getAge() {
            return System.currentTimeMillis() - createdTime;
        }
    }

    /**
     * 连接配置
     */
    public static class ConnectionConfig {
        private final String type;
        private final String host;
        private final Integer port;
        private final String name;
        private final String username;
        private final String password;

        public ConnectionConfig(String type, String host, Integer port, String name,
                               String username, String password) {
            this.type = type;
            this.host = host;
            this.port = port;
            this.name = name;
            this.username = username;
            this.password = password;
        }

        public String getType() {
            return type;
        }

        public String getHost() {
            return host;
        }

        public Integer getPort() {
            return port;
        }

        public String getName() {
            return name;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> params = new HashMap<>();
            params.put("host", host);
            params.put("port", port);
            params.put("name", name);
            params.put("username", username);
            params.put("password", password);
            return params;
        }
    }
}
