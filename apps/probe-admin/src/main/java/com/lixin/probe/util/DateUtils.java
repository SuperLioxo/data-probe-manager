package com.lixin.probe.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 日期时间工具类
 * 提供常用的日期时间处理方法
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 1.0
 */
public class DateUtils {

    /**
     * 默认日期时间格式
     */
    public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 日期格式
     */
    public static final String DATE_FORMAT = "yyyy-MM-dd";

    /**
     * 时间格式
     */
    public static final String TIME_FORMAT = "HH:mm:ss";

    /**
     * ISO格式
     */
    public static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    /**
     * 默认格式化器
     */
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_DATETIME_FORMAT);

    /**
     * 日期格式化器（用于格式化日期部分）
     */
    public static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern(DATE_FORMAT));
    }

    /**
     * 时间格式化器（用于格式化时间部分）
     */
    public static String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern(TIME_FORMAT));
    }

    /**
     * ISO格式化器（用于格式化为ISO格式）
     */
    public static String formatISO(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern(ISO_FORMAT));
    }

    private DateUtils() {
        // 工具类不允许实例化
    }

    /**
     * 格式化当前时间
     *
     * @return 格式化后的时间字符串
     */
    public static String formatNow() {
        return LocalDateTime.now().format(DEFAULT_FORMATTER);
    }

    /**
     * 格式化日期时间
     *
     * @param dateTime 日期时间
     * @return 格式化后的字符串
     */
    public static String format(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DEFAULT_FORMATTER);
    }

    /**
     * 使用指定格式格式化日期时间
     *
     * @param dateTime 日期时间
     * @param pattern 格式模式
     * @return 格式化后的字符串
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 解析日期时间字符串
     *
     * @param dateTimeStr 日期时间字符串
     * @return LocalDateTime对象
     */
    public static LocalDateTime parse(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateTimeStr, DEFAULT_FORMATTER);
    }

    /**
     * 解析日期时间字符串
     *
     * @param dateTimeStr 日期时间字符串
     * @param pattern 格式模式
     * @return LocalDateTime对象
     */
    public static LocalDateTime parse(String dateTimeStr, String pattern) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 获取当前时间
     *
     * @return 当前LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * 获取N分钟前的时间
     *
     * @param minutes 分钟数
     * @return N分钟前的时间
     */
    public static LocalDateTime minutesAgo(int minutes) {
        return LocalDateTime.now().minus(minutes, ChronoUnit.MINUTES);
    }

    /**
     * 获取N小时前的时间
     *
     * @param hours 小时数
     * @return N小时前的时间
     */
    public static LocalDateTime hoursAgo(int hours) {
        return LocalDateTime.now().minus(hours, ChronoUnit.HOURS);
    }

    /**
     * 获取N天前的时间
     *
     * @param days 天数
     * @return N天前的时间
     */
    public static LocalDateTime daysAgo(int days) {
        return LocalDateTime.now().minus(days, ChronoUnit.DAYS);
    }

    /**
     * 获取今天开始时间（00:00:00）
     *
     * @return 今天的开始时间
     */
    public static LocalDateTime startOfDay() {
        return LocalDateTime.now().toLocalDate().atStartOfDay();
    }

    /**
     * 获取今天结束时间（23:59:59）
     *
     * @return 今天的结束时间
     */
    public static LocalDateTime endOfDay() {
        return LocalDateTime.now().toLocalDate().atTime(23, 59, 59);
    }

    /**
     * 获取本周开始时间
     *
     * @return 本周一的00:00:00
     */
    public static LocalDateTime startOfWeek() {
        LocalDateTime now = LocalDateTime.now();
        return now.toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .atStartOfDay();
    }

    /**
     * 获取本月开始时间
     *
     * @return 本月1号的00:00:00
     */
    public static LocalDateTime startOfMonth() {
        return LocalDateTime.now().toLocalDate()
                .withDayOfMonth(1)
                .atStartOfDay();
    }

    /**
     * 计算两个时间之间的分钟数
     *
     * @param start 开始时间
     * @param end 结束时间
     * @return 分钟数差值
     */
    public static long minutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.MINUTES.between(start, end);
    }

    /**
     * 计算两个时间之间的小时数
     *
     * @param start 开始时间
     * @param end 结束时间
     * @return 小时数差值
     */
    public static long hoursBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.HOURS.between(start, end);
    }

    /**
     * 计算两个时间之间的天数
     *
     * @param start 开始时间
     * @param end 结束时间
     * @return 天数差值
     */
    public static long daysBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * 检查时间是否在指定范围内
     *
     * @param dateTime 检查的时间
     * @param start 范围开始时间
     * @param end 范围结束时间
     * @return true如果在范围内
     */
    public static boolean isBetween(LocalDateTime dateTime, LocalDateTime start, LocalDateTime end) {
        if (dateTime == null || start == null || end == null) {
            return false;
        }
        return !dateTime.isBefore(start) && !dateTime.isAfter(end);
    }

    /**
     * 判断时间是否过期（晚于当前时间）
     *
     * @param dateTime 检查的时间
     * @return true如果已过期
     */
    public static boolean isExpired(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        return dateTime.isBefore(LocalDateTime.now());
    }

    /**
     * 判断时间是否在未来
     *
     * @param dateTime 检查的时间
     * @return true如果在将来
     */
    public static boolean isFuture(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        return dateTime.isAfter(LocalDateTime.now());
    }

    /**
     * 判断时间是否在过去
     *
     * @param dateTime 检查的时间
     * @return true如果在过去
     */
    public static boolean isPast(LocalDateTime dateTime) {
        if (dateTime == null) {
            return false;
        }
        return dateTime.isBefore(LocalDateTime.now());
    }

    /**
     * 获取友好的时间描述
     *
     * @param dateTime 时间
     * @return 友好描述（如："刚刚"、"5分钟前"、"1小时前"）
     */
    public static String getFriendlyDescription(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }

        long seconds = ChronoUnit.SECONDS.between(dateTime, LocalDateTime.now());

        if (seconds < 60) {
            return "刚刚";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "分钟前";
        }

        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "小时前";
        }

        long days = hours / 24;
        if (days < 30) {
            return days + "天前";
        }

        long months = days / 30;
        if (months < 12) {
            return months + "月前";
        }

        long years = months / 12;
        return years + "年前";
    }

    /**
     * 将时间戳转换为LocalDateTime
     *
     * @param timestamp 时间戳（毫秒）
     * @return LocalDateTime对象
     */
    public static LocalDateTime fromTimestamp(long timestamp) {
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault()
        );
    }

    /**
     * 将LocalDateTime转换为时间戳
     *
     * @param dateTime LocalDateTime对象
     * @return 时间戳（毫秒）
     */
    public static long toTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0;
        }
        return dateTime.atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }
}
