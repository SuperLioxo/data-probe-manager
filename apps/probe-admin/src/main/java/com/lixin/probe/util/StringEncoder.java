package com.lixin.probe.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 字符串编码工具类
 * 提供字符串编码解码功能
 *
 * @author Claude Code
 * @date 2026-03-12
 * @version 1.0
 */
public final class StringEncoder {

    private StringEncoder() {
        // 工具类不允许实例化
    }

    /**
     * Base64编码
     *
     * @param str 字符串
     * @return Base64编码后的字符串
     */
    public static String base64Encode(String str) {
        if (str == null) {
            return StringUtils.EMPTY;
        }
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64解码
     *
     * @param str Base64编码的字符串
     * @return 解码后的字符串
     */
    public static String base64Decode(String str) {
        if (str == null) {
            return StringUtils.EMPTY;
        }
        byte[] decoded = Base64.getDecoder().decode(str);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    /**
     * URL安全的Base64编码
     *
     * @param str 字符串
     * @return URL安全的Base64编码后的字符串
     */
    public static String base64UrlEncode(String str) {
        if (str == null) {
            return StringUtils.EMPTY;
        }
        return Base64.getUrlEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * URL安全的Base64解码
     *
     * @param str URL安全的Base64编码的字符串
     * @return 解码后的字符串
     */
    public static String base64UrlDecode(String str) {
        if (str == null) {
            return StringUtils.EMPTY;
        }
        byte[] decoded = Base64.getUrlDecoder().decode(str);
        return new String(decoded, StandardCharsets.UTF_8);
    }
}
