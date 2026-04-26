package com.lixin.probe.util;

import java.util.UUID;

/**
 * 字符串生成工具类
 * 提供随机字符串生成功能
 *
 * @author Claude Code
 * @date 2026-03-12
 * @version 1.0
 */
public final class StringGenerator {

    private static final String ALPHABETIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String NUMERIC = "0123456789";

    private StringGenerator() {
        // 工具类不允许实例化
    }

    /**
     * 生成UUID（去除横线）
     *
     * @return UUID字符串
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成带横线的UUID
     *
     * @return UUID字符串
     */
    public static String uuidWithHyphens() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成随机数字字符串
     *
     * @param length 长度
     * @return 随机数字字符串
     */
    public static String randomNumeric(int length) {
        if (length <= 0) {
            return StringUtils.EMPTY;
        }
        return randomString(length, NUMERIC);
    }

    /**
     * 生成随机字母字符串
     *
     * @param length 长度
     * @return 随机字母字符串
     */
    public static String randomAlphabetic(int length) {
        if (length <= 0) {
            return StringUtils.EMPTY;
        }
        return randomString(length, ALPHABETIC);
    }

    /**
     * 生成随机字母数字字符串
     *
     * @param length 长度
     * @return 随机字母数字字符串
     */
    public static String randomAlphanumeric(int length) {
        if (length <= 0) {
            return StringUtils.EMPTY;
        }
        return randomString(length, ALPHANUMERIC);
    }

    /**
     * 生成随机字符串
     *
     * @param length 长度
     * @param charset 字符集
     * @return 随机字符串
     */
    public static String randomString(int length, String charset) {
        if (length <= 0 || charset == null || charset.isEmpty()) {
            return StringUtils.EMPTY;
        }

        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * charset.length());
            result.append(charset.charAt(index));
        }
        return result.toString();
    }

    /**
     * 生成随机十六进制字符串
     *
     * @param length 长度
     * @return 随机十六进制字符串
     */
    public static String randomHex(int length) {
        return randomString(length, "0123456789abcdef");
    }

    /**
     * 生成随机Base64字符串
     *
     * @param length 字节长度
     * @return 随机Base64字符串
     */
    public static String randomBase64(int length) {
        byte[] bytes = new byte[length];
        new java.util.Random().nextBytes(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }
}
