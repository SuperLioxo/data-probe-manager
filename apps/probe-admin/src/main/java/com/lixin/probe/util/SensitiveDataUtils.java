package com.lixin.probe.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具类
 * 用于防止敏感信息（密码、Token等）泄露到日志和错误消息中
 *
 * @author Claude Code
 * @date 2026-04-12
 */
public class SensitiveDataUtils {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataUtils.class);

    // 密码相关模式
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "(password|pwd|passwd|secret)\\s*[=:]\\s*['\"]?([^'\"\\s\\)\\}]+)['\"]?",
        Pattern.CASE_INSENSITIVE
    );

    // Token相关模式
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
        "(Bearer\\s+)[A-Za-z0-9\\-._~+/]+=*",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AUTH_TOKEN_PATTERN = Pattern.compile(
        "(token|access_token|refresh_token|api_key|apikey)\\s*[=:]\\s*['\"]?([^'\"\\s\\)\\}]+)['\"]?",
        Pattern.CASE_INSENSITIVE
    );

    // 连接字符串模式
    private static final Pattern CONNECTION_STRING_PATTERN = Pattern.compile(
        "([a-zA-Z]+://[^/\\s]+):([^@\\s]+)@",
        Pattern.CASE_INSENSITIVE
    );

    // IP地址模式（可选脱敏）
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
        "\\b(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\b"
    );

    // 邮箱模式
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+\\.[A-Z|a-z]{2,})\\b"
    );

    // 手机号模式（中国大陆）
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b(1[3-9]\\d)\\d{4}(\\d{4})\\b"
    );

    /**
     * 脱敏处理字符串
     *
     * @param input 原始字符串
     * @return 脱敏后的字符串
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String sanitized = input;

        try {
            // 脱敏密码
            sanitized = PASSWORD_PATTERN.matcher(sanitized).replaceAll("$1=***");

            // 脱敏Bearer Token
            sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("$1***");

            // 脱敏其他Token
            sanitized = AUTH_TOKEN_PATTERN.matcher(sanitized).replaceAll("$1=***");

            // 脱敏连接字符串中的密码
            sanitized = CONNECTION_STRING_PATTERN.matcher(sanitized).replaceAll("$1:***@");

            // 脱敏邮箱（保留首字母和域名）
            sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("$1***@$2");

            // 脱敏手机号（保留前3位和后4位）
            sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("$1******$2");

        } catch (Exception e) {
            log.warn("数据脱敏处理失败，返回原始字符串", e);
            return input;
        }

        return sanitized;
    }

    /**
     * 脱敏处理对象
     *
     * @param obj 原始对象
     * @return 脱敏后的字符串
     */
    public static String sanitize(Object obj) {
        if (obj == null) {
            return "null";
        }

        // 检查是否是敏感类（直接返回类名）
        if (obj.getClass().getName().contains("password") ||
            obj.getClass().getName().contains("token") ||
            obj.getClass().getName().contains("credential")) {
            return "[REDACTED " + obj.getClass().getSimpleName() + "]";
        }

        return sanitize(obj.toString());
    }

    /**
     * 脱敏处理日志消息
     * 支持参数化日志
     *
     * @param format 日志格式
     * @param args 参数列表
     * @return 脱敏后的日志消息
     */
    public static String sanitizeLogMessage(String format, Object... args) {
        if (format == null) {
            return "";
        }

        if (args == null || args.length == 0) {
            return format;
        }

        // 脱敏所有参数
        Object[] sanitizedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            sanitizedArgs[i] = sanitize(args[i]);
        }

        // 格式化日志
        try {
            return sanitize(String.format(format.replace("{}", "%s"), sanitizedArgs));
        } catch (Exception e) {
            log.warn("日志格式化失败", e);
            return format;
        }
    }

    /**
     * 脱敏IP地址（部分隐藏）
     *
     * @param ip IP地址
     * @return 脱敏后的IP地址
     */
    public static String maskIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }

        return IP_ADDRESS_PATTERN.matcher(ip).replaceAll("$1.$2.***.$4");
    }

    /**
     * 脱敏用户名（保留首字符）
     *
     * @param username 用户名
     * @return 脱敏后的用户名
     */
    public static String maskUsername(String username) {
        if (username == null || username.length() <= 1) {
            return username;
        }

        return username.charAt(0) + "***" +
               (username.length() > 4 ? username.substring(username.length() - 1) : "");
    }

    /**
     * 脱敏银行卡号（保留前4位和后4位）
     *
     * @param cardNumber 银行卡号
     * @return 脱敏后的银行卡号
     */
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) {
            return cardNumber;
        }

        int length = cardNumber.length();
        return cardNumber.substring(0, 4) +
               "*".repeat(length - 8) +
               cardNumber.substring(length - 4);
    }

    /**
     * 检查字符串是否包含敏感信息
     *
     * @param input 待检查的字符串
     * @return 是否包含敏感信息
     */
    public static boolean containsSensitiveData(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        return PASSWORD_PATTERN.matcher(input).find() ||
               BEARER_TOKEN_PATTERN.matcher(input).find() ||
               AUTH_TOKEN_PATTERN.matcher(input).find() ||
               CONNECTION_STRING_PATTERN.matcher(input).find();
    }

    /**
     * 创建安全的日志消息（自动脱敏）
     *
     * @param logger 日志记录器
     * @param level 日志级别
     * @param format 格式化字符串
     * @param args 参数
     */
    public static void logSafe(Logger logger, org.slf4j.event.Level level, String format, Object... args) {
        String sanitizedMessage = sanitizeLogMessage(format, args);

        switch (level) {
            case ERROR:
                logger.error(sanitizedMessage);
                break;
            case WARN:
                logger.warn(sanitizedMessage);
                break;
            case INFO:
                logger.info(sanitizedMessage);
                break;
            case DEBUG:
                logger.debug(sanitizedMessage);
                break;
            case TRACE:
                logger.trace(sanitizedMessage);
                break;
            default:
                logger.info(sanitizedMessage);
        }
    }
}
