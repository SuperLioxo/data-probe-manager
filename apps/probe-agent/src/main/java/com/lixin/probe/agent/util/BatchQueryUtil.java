package com.lixin.probe.agent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 批量查询工具类
 * 提供并行查询、批量查询等优化功能
 *
 * @author probe-agent
 * @since 1.0.0
 */
public class BatchQueryUtil {

    private static final Logger log = LoggerFactory.getLogger(BatchQueryUtil.class);
    /**
     * 线程池（用于并行查询）
     */
    private static final ExecutorService queryExecutor = Executors.newFixedThreadPool(
        Math.min(Runtime.getRuntime().availableProcessors(), 8),
        r -> {
            Thread thread = new Thread(r, "batch-query-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        }
    );

    /**
     * 批量执行SQL查询（串行）
     *
     * @param connection 数据库连接
     * @param sqls       SQL语句列表
     * @return 查询结果列表
     */
    public static List<List<Object[]>> batchQuery(Connection connection, List<String> sqls) {
        List<List<Object[]>> results = new ArrayList<>();

        for (String sql : sqls) {
            try {
                List<Object[]> rows = query(connection, sql);
                results.add(rows);
            } catch (Exception e) {
                log.error("批量查询失败: {}", sql, e);
                results.add(new ArrayList<>());
            }
        }

        return results;
    }

    /**
     * 批量执行SQL查询（并行）
     *
     * @param connection 数据库连接
     * @param sqls       SQL语句列表
     * @return 查询结果列表（CompletableFuture）
     */
    public static CompletableFuture<List<List<Object[]>>> batchQueryAsync(Connection connection, List<String> sqls) {
        List<CompletableFuture<List<Object[]>>> futures = new ArrayList<>();

        for (String sql : sqls) {
            CompletableFuture<List<Object[]>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return query(connection, sql);
                } catch (Exception e) {
                    log.error("并行查询失败: {}", sql, e);
                    return new ArrayList<>();
                }
            }, queryExecutor);
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<List<Object[]>> results = new ArrayList<>();
                for (CompletableFuture<List<Object[]>> future : futures) {
                    results.add(future.join());
                }
                return results;
            });
    }

    /**
     * 执行单个SQL查询
     *
     * @param connection 数据库连接
     * @param sql        SQL语句
     * @return 查询结果（每行数据为一个Object数组）
     */
    public static List<Object[]> query(Connection connection, String sql) throws SQLException {
        List<Object[]> results = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            int columnCount = rs.getMetaData().getColumnCount();

            while (rs.next()) {
                Object[] row = new Object[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    row[i] = rs.getObject(i + 1);
                }
                results.add(row);
            }
        }

        return results;
    }

    /**
     * 执行单个SQL查询（使用回调处理结果）
     *
     * @param connection 数据库连接
     * @param sql        SQL语句
     * @param handler    结果处理器
     * @param <T>        返回类型
     * @return 处理后的结果列表
     */
    public static <T> List<T> query(Connection connection, String sql, ResultSetHandler<T> handler) throws SQLException {
        List<T> results = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                try {
                    T result = handler.handle(rs);
                    if (result != null) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    log.error("处理结果集失败: {}", sql, e);
                }
            }
        }

        return results;
    }

    /**
     * 批量执行SQL更新（INSERT/UPDATE/DELETE）
     *
     * @param connection 数据库连接
     * @param sqls       SQL语句列表
     * @return 每条SQL影响的行数
     */
    public static List<Integer> batchUpdate(Connection connection, List<String> sqls) {
        List<Integer> results = new ArrayList<>();

        for (String sql : sqls) {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                int rows = stmt.executeUpdate();
                results.add(rows);
            } catch (Exception e) {
                log.error("批量更新失败: {}", sql, e);
                results.add(-1);
            }
        }

        return results;
    }

    /**
     * 执行分页查询
     *
     * @param connection 数据库连接
     * @param sql        SQL语句（不带LIMIT/OFFSET）
     * @param page       页码（从1开始）
     * @param size       每页大小
     * @return 分页结果
     */
    public static PageResult<Object[]> queryByPage(Connection connection, String sql, int page, int size) throws SQLException {
        // 查询总数
        String countSql = "SELECT COUNT(*) FROM (" + sql + ") AS temp";
        int total = 0;

        try (PreparedStatement stmt = connection.prepareStatement(countSql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                total = rs.getInt(1);
            }
        }

        // 计算偏移量
        int offset = (page - 1) * size;

        // 查询数据
        String dataSql = sql + " LIMIT " + size + " OFFSET " + offset;
        List<Object[]> data = query(connection, dataSql);

        return new PageResult<>(data, total, page, size);
    }

    /**
     * 关闭线程池
     */
    public static void shutdown() {
        if (queryExecutor != null && !queryExecutor.isShutdown()) {
            queryExecutor.shutdown();
        }
    }

    /**
     * 结果集处理器函数式接口
     */
    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }

    /**
     * 分页结果
     */
    public static class PageResult<T> {
        private final List<T> records;
        private final int total;
        private final int page;
        private final int size;
        private final int totalPages;

        public PageResult(List<T> records, int total, int page, int size) {
            this.records = records;
            this.total = total;
            this.page = page;
            this.size = size;
            this.totalPages = (int) Math.ceil((double) total / size);
        }

        public List<T> getRecords() {
            return records;
        }

        public int getTotal() {
            return total;
        }

        public int getPage() {
            return page;
        }

        public int getSize() {
            return size;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public boolean hasPrevious() {
            return page > 1;
        }

        public boolean hasNext() {
            return page < totalPages;
        }
    }
}
