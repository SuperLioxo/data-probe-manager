package com.lixin.probe.util;

import java.util.regex.Pattern;

/**
 * 字符串验证工具类
 * 提供字符串格式验证功能
 *
 * @author Claude Code
 * @date 2026-03-12
 * @version 1.0
 */
public final class StringValidator {

    /**
     * 邮箱正则表达式
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    /**
     * 手机号正则表达式（中国大陆）
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^1[3-9]\\d{9}$"
    );

    /**
     * IPv4正则表达式
     */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    );

    private StringValidator() {
        // 工具类不允许实例化
    }

    /**
     * 验证是否为有效邮箱
     *
     * @param email 邮箱地址
     * @return true如果是有效邮箱
     */
    public static boolean isValidEmail(String email) {
        if (StringUtils.isBlank(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 验证是否为有效手机号（中国大陆）
     *
     * @param phone 手机号
     * @return true如果是有效手机号
     */
    public static boolean isValidPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * 验证是否为有效IPv4地址
     *
     * @param ip IP地址
     * @return true如果是有效IPv4
     */
    public static boolean isValidIPv4(String ip) {
        if (StringUtils.isBlank(ip)) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * 验证字符串是否为空
     *
     * @param str 字符串
     * @return true如果为null或空字符串
     */
    public static boolean isEmpty(String str) {
        return StringUtils.isEmpty(str);
    }

    /**
     * 验证字符串是否不为空
     *
     * @param str 字符串
     * @return true如果不为null且不是空字符串
     */
    public static boolean isNotEmpty(String str) {
        return StringUtils.isNotEmpty(str);
    }

    /**
     * 验证字符串是否为空白（null、空或只有空格）
     *
     * @param str 字符串
     * @return true如果为空白
     */
    public static boolean isBlank(String str) {
        return StringUtils.isBlank(str);
    }

    /**
     * 验证字符串是否不为空白
     *
     * @param str 字符串
     * @return true如果不为空白
     */
    public static boolean isNotBlank(String str) {
        return StringUtils.isNotBlank(str);
    }
}
