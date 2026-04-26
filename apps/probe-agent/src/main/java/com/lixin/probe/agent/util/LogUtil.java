package com.lixin.probe.agent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 日志工具类
 * 提供统一的日志记录和性能监控功能
 *
 * @author probe-agent
 * @ @since 1.0.0
 */
public class LogUtil {

    private static final Logger log = LoggerFactory.getLogger(LogUtil.class);
    /**
     * 记录方法执行耗时
     *
     * @param operation 操作名称
     * @param supplier  要执行的操作
     * @param <T>      返回类型
     * @return 操作结果
     */
    public static <T> T logTime(String operation, Supplier<T> supplier) {
        long startTime = System.currentTimeMillis();
        log.debug("开始执行: {}", operation);

        try {
            T result = supplier.get();
            long duration = System.currentTimeMillis() - startTime;
            log.info("执行完成: {} | 耗时: {} ms", operation, duration);
            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("执行失败: {} | 耗时: {} ms", operation, duration, e);
            throw e;
        }
    }

    /**
     * 记录方法执行耗时（异步）
     *
     * @param operation 操作名称
     * @param supplier  要执行的操作
     * @param <T>      返回类型
     * @return 操作结果
     */
    public static <T> T logTimeAsync(String operation, Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> logTime(operation, supplier)).join();
    }

    /**
     * 记录数据库操作
     *
     * @param operation 操作名称
     * @param database  数据库信息
     * @param supplier  要执行的操作
     * @param <T>      返回类型
     * @return 操作结果
     */
    public static <T> T logDatabaseOperation(String operation, String database, Supplier<T> supplier) {
        log.info("数据库操作: [{}] {}", database, operation);

        try {
            T result = supplier.get();
            log.info("数据库操作成功: [{}] {}", database, operation);
            return result;

        } catch (Exception e) {
            log.error("数据库操作失败: [{}] {} | 错误: {}", database, operation, e.getMessage());
            throw new RuntimeException("数据库操作失败: " + operation, e);
        }
    }

    /**
     * 记录插件操作
     *
     * @param pluginId  插件ID
     * @param operation 操作名称
     * @param supplier  要执行的操作
     * @param <T>      返回类型
     * @return 操作结果
     */
    public static <T> T logPluginOperation(String pluginId, String operation, Supplier<T> supplier) {
        log.info("插件操作: [{}] {}", pluginId, operation);

        try {
            T result = supplier.get();
            log.info("插件操作成功: [{}] {}", pluginId, operation);
            return result;

        } catch (Exception e) {
            log.error("插件操作失败: [{}] {} | 错误: {}", pluginId, operation, e.getMessage());
            throw new RuntimeException("插件操作失败: " + operation, e);
        }
    }

    /**
     * 记录集合操作
     *
     * @param collection 集合
     * @return 集合的字符串表示
     */
    public static String collectionToString(Collection<?> collection) {
        if (collection == null) {
            return "null";
        }
        return String.format("[%s] (size: %d)",
                collection.getClass().getSimpleName(),
                collection.size());
    }

    /**
     * 记录Map操作
     *
     * @param map Map对象
     * @return Map的字符串表示
     */
    public static String mapToString(Map<?, ?> map) {
        if (map == null) {
            return "null";
        }
        return String.format("{size: %d}", map.size());
    }

    /**
     * 格式化字节大小
     *
     * @param bytes 字节数
     * @return 格式化后的大小字符串
     */
    public static String formatBytes(long bytes) {
        if (bytes == 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int unitIndex = 0;
        double size = bytes;

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return String.format("%.2f %s", size, units[unitIndex]);
    }

    /**
     * 格式化时间长度
     *
     * @param millis 毫秒数
     * @return 格式化后的时间字符串
     */
    public static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }

        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + " s";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            long remainingSeconds = seconds % 60;
            return String.format("%d min %d s", minutes, remainingSeconds);
        }

        long hours = minutes / 60;
        if (hours < 24) {
            long remainingMinutes = minutes % 60;
            return String.format("%d h %d min", hours, remainingMinutes);
        }

        long days = hours / 24;
        long remainingHours = hours % 24;
        return String.format("%d d %d h", days, remainingHours);
    }

    /**
     * 截断过长的字符串
     *
     * @param str       字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */
    public static String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    /**
     * 脱敏敏感信息
     *
     * @param str 原始字符串
     * @return 脱敏后的字符串
     */
    public static String mask(String str) {
        if (str == null) {
            return null;
        }

        if (str.length() <= 4) {
            return "****";
        }

        // 保留前2位和后2位，中间用*代替
        return str.substring(0, 2) + "****" + str.substring(str.length() - 2);
    }

    /**
     * 脱敏URL中的密码
     *
     * @param url URL字符串
     * @return 脱敏后的URL
     */
    public static String maskUrlPassword(String url) {
        if (url == null) {
            return null;
        }

        // 匹配 jdbc:xxx://username:password@host:port/db
        return url.replaceAll("://([^:]+):([^@]+)@", "://$1:****@");
    }

    /**
     * 安全地关闭资源
     *
     * @param closeable 可关闭的资源
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("关闭资源失败", e);
            }
        }
    }

    /**
     * 安全地关闭资源并记录错误
     *
     * @param closeable 可关闭的资源
     * @param resourceName 资源名称（用于日志）
     */
    public static void closeQuietly(AutoCloseable closeable, String resourceName) {
        if (closeable != null) {
            try {
                closeable.close();
                log.debug("资源已关闭: {}", resourceName);
            } catch (Exception e) {
                log.warn("关闭资源失败: {} | {}", resourceName, e.getMessage());
            }
        }
    }

    /**
     * 检查字符串是否为空或空白
     *
     * @param str 字符串
     * @return true=为空或空白, false=不为空
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 检查字符串是否不为空
     *
     * @param str 字符串
     * @return true=不为空, false=为空
     */
    public static boolean isNotBlank(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * 执行操作并忽略异常
     *
     * @param runnable 要执行的操作
     * @param errorMsg  失败时的错误消息
     */
    public static void runQuietly(Runnable runnable, String errorMsg) {
        try {
            runnable.run();
        } catch (Exception e) {
            log.debug("{}: {}", errorMsg, e.getMessage());
        }
    }

    /**
     * 检查对象是否为null
     *
     * @param obj     对象
     * @param objName 对象名称（用于日志）
     * @throws IllegalArgumentException 如果对象为null
     */
    public static <T> T requireNonNull(T obj, String objName) {
        if (obj == null) {
            throw new IllegalArgumentException(objName + " 不能为 null");
        }
        return obj;
    }

    /**
     * 创建带描述的值对象
     *
     * @param value 值
     * @param desc  描述
     * @return 包含value和desc的Map
     */
    public static Map<String, Object> descValue(Object value, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("desc", desc);
        return m;
    }
}
