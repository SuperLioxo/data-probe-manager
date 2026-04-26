package com.lixin.probe.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * 字符串工具类
 * 提供常用的字符串处理方法
 *
 * <p><strong>重要提示:</strong> 此类正在重构中，部分方法已迁移到专职工具类：</p>
 * <ul>
 *   <li>字符串验证方法已迁移到 {@link StringValidator}</li>
 *   <li>大小写转换方法已迁移到 {@link StringConverter}</li>
 *   <li>编码解码方法已迁移到 {@link StringEncoder}</li>
 *   <li>数据脱敏方法已迁移到 {@link StringMasker}</li>
 *   <li>随机生成方法已迁移到 {@link StringGenerator}</li>
 * </ul>
 *
 * <p>请在新代码中直接使用这些专职工具类，已迁移的方法将逐步标记为 @Deprecated</p>
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (重构中)
 */
public class StringUtils {

    /**
     * 空字符串
     */
    public static final String EMPTY = "";

    /**
     * 逗号分隔符
     */
    public static final String COMMA = ",";

    /**
     * 下划线
     */
    public static final String UNDERSCORE = "_";

    /**
     * 连字符
     */
    public static final String HYPHEN = "-";

    /**
     * 点号
     */
    public static final String DOT = ".";

    /**
     * 斜杠
     */
    public static final String SLASH = "/";

    /**
     * 冒号
     */
    public static final String COLON = ":";

    /**
     * 分号
     */
    public static final String SEMICOLON = ";";

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

    private StringUtils() {
        // 工具类不允许实例化
    }

    /**
     * 判断字符串是否为空
     *
     * @param str 字符串
     * @return true如果为null或空字符串
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否不为空
     *
     * @param str 字符串
     * @return true如果不为null且不是空字符串
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 判断字符串是否为空白（null、空或只有空格）
     *
     * @param str 字符串
     * @return true如果为空白
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 判断字符串是否不为空白
     *
     * @param str 字符串
     * @return true如果不为空白
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 判断字符串数组是否有非空元素
     *
     * @param strs 字符串数组
     * @return true如果至少有一个非空元素
     */
    public static boolean isAnyNotEmpty(String... strs) {
        if (strs == null || strs.length == 0) {
            return false;
        }
        for (String str : strs) {
            if (isNotEmpty(str)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断所有字符串是否都非空
     *
     * @param strs 字符串数组
     * @return true如果所有字符串都非空
     */
    public static boolean isAllNotEmpty(String... strs) {
        if (strs == null || strs.length == 0) {
            return false;
        }
        for (String str : strs) {
            if (isEmpty(str)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 默认字符串处理
     * 如果字符串为空，返回默认值
     *
     * @param str 字符串
     * @param defaultValue 默认值
     * @return 字符串或默认值
     */
    public static String defaultIfEmpty(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }

    /**
     * 默认字符串处理（空白）
     * 如果字符串为空白，返回默认值
     *
     * @param str 字符串
     * @param defaultValue 默认值
     * @return 字符串或默认值
     */
    public static String defaultIfBlank(String str, String defaultValue) {
        return isBlank(str) ? defaultValue : str;
    }

    /**
     * 去除字符串首尾空格
     * 如果为null返回空字符串
     *
     * @param str 字符串
     * @return 去除空格后的字符串
     */
    public static String trim(String str) {
        return str == null ? EMPTY : str.trim();
    }

    /**
     * 去除字符串中所有空格
     *
     * @param str 字符串
     * @return 去除所有空格后的字符串
     */
    public static String removeAllSpaces(String str) {
        if (str == null) {
            return EMPTY;
        }
        return str.replace(" ", "");
    }

    /**
     * 截取字符串
     * 如果字符串长度超过最大长度，截取并添加省略号
     *
     * @param str 字符串
     * @param maxLength 最大长度
     * @return 截取后的字符串
     */
    public static String truncate(String str, int maxLength) {
        if (str == null) {
            return EMPTY;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    /**
     * 连接字符串数组
     *
     * @param strings 字符串数组
     * @param delimiter 分隔符
     * @return 连接后的字符串
     */
    public static String join(String[] strings, String delimiter) {
        if (strings == null || strings.length == 0) {
            return EMPTY;
        }
        return String.join(delimiter, strings);
    }

    /**
     * 连接字符串集合
     *
     * @param strings 字符串集合
     * @param delimiter 分隔符
     * @return 连接后的字符串
     */
    public static String join(Collection<String> strings, String delimiter) {
        if (strings == null || strings.isEmpty()) {
            return EMPTY;
        }
        return String.join(delimiter, strings);
    }

    /**
     * 分割字符串
     *
     * @param str 字符串
     * @param delimiter 分隔符
     * @return 分割后的数组
     */
    public static String[] split(String str, String delimiter) {
        if (str == null || str.isEmpty()) {
            return new String[0];
        }
        return str.split(delimiter);
    }

    /**
     * 转换为驼峰命名
     * 例如：hello_world -> helloWorld
     *
     * @param str 字符串
     * @return 驼峰命名的字符串
     * @deprecated 请使用 {@link StringConverter#toCamelCase(String)} 替代
     */
    @Deprecated
    public static String toCamelCase(String str) {
        if (isBlank(str)) {
            return EMPTY;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    /**
     * 转换为蛇形命名
     * 例如：helloWorld -> hello_world
     *
     * @param str 字符串
     * @return 蛇形命名的字符串
     * @deprecated 请使用 {@link StringConverter#toSnakeCase(String)} 替代
     */
    @Deprecated
    public static String toSnakeCase(String str) {
        if (isBlank(str)) {
            return EMPTY;
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * 验证是否为有效邮箱
     *
     * @param email 邮箱地址
     * @return true如果是有效邮箱
     * @deprecated 请使用 {@link StringValidator#isValidEmail(String)} 替代
     */
    @Deprecated
    public static boolean isValidEmail(String email) {
        if (isBlank(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 验证是否为有效手机号（中国大陆）
     *
     * @param phone 手机号
     * @return true如果是有效手机号
     * @deprecated 请使用 {@link StringValidator#isValidPhone(String)} 替代
     */
    @Deprecated
    public static boolean isValidPhone(String phone) {
        if (isBlank(phone)) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * 验证是否为有效IPv4地址
     *
     * @param ip IP地址
     * @return true如果是有效IPv4
     * @deprecated 请使用 {@link StringValidator#isValidIPv4(String)} 替代
     */
    @Deprecated
    public static boolean isValidIPv4(String ip) {
        if (isBlank(ip)) {
            return false;
        }
        return IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * Base64编码
     *
     * @param str 字符串
     * @return Base64编码后的字符串
     * @deprecated 请使用 {@link StringEncoder#base64Encode(String)} 替代
     */
    @Deprecated
    public static String base64Encode(String str) {
        if (str == null) {
            return EMPTY;
        }
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64解码
     *
     * @param str Base64编码的字符串
     * @return 解码后的字符串
     * @deprecated 请使用 {@link StringEncoder#base64Decode(String)} 替代
     */
    @Deprecated
    public static String base64Decode(String str) {
        if (str == null) {
            return EMPTY;
        }
        byte[] decoded = Base64.getDecoder().decode(str);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    /**
     * 比较两个字符串是否相等（忽略大小写）
     *
     * @param str1 字符串1
     * @param str2 字符串2
     * @return true如果相等
     * @deprecated 请使用 {@link StringConverter#equalsIgnoreCase(String, String)} 替代
     */
    @Deprecated
    public static boolean equalsIgnoreCase(String str1, String str2) {
        if (str1 == null && str2 == null) {
            return true;
        }
        if (str1 == null || str2 == null) {
            return false;
        }
        return str1.equalsIgnoreCase(str2);
    }

    /**
     * 判断字符串是否以指定前缀开头（忽略大小写）
     *
     * @param str 字符串
     * @param prefix 前缀
     * @return true如果以指定前缀开头
     * @deprecated 请使用 {@link StringConverter#startsWithIgnoreCase(String, String)} 替代
     */
    @Deprecated
    public static boolean startsWithIgnoreCase(String str, String prefix) {
        if (str == null || prefix == null) {
            return false;
        }
        return str.toLowerCase().startsWith(prefix.toLowerCase());
    }

    /**
     * 判断字符串是否以指定后缀结尾（忽略大小写）
     *
     * @param str 字符串
     * @param suffix 后缀
     * @return true如果以指定后缀结尾
     * @deprecated 请使用 {@link StringConverter#endsWithIgnoreCase(String, String)} 替代
     */
    @Deprecated
    public static boolean endsWithIgnoreCase(String str, String suffix) {
        if (str == null || suffix == null) {
            return false;
        }
        return str.toLowerCase().endsWith(suffix.toLowerCase());
    }

    /**
     * 字符串包含判断（忽略大小写）
     *
     * @param str 字符串
     * @param searchStr 搜索字符串
     * @return true如果包含
     * @deprecated 请使用 {@link StringConverter#containsIgnoreCase(String, String)} 替代
     */
    @Deprecated
    public static boolean containsIgnoreCase(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return false;
        }
        return str.toLowerCase().contains(searchStr.toLowerCase());
    }

    /**
     * 重复字符串
     *
     * @param str 字符串
     * @param count 重复次数
     * @return 重复后的字符串
     */
    public static String repeat(String str, int count) {
        if (str == null || count <= 0) {
            return EMPTY;
        }
        return str.repeat(count);
    }

    /**
     * 隐藏字符串中间部分
     * 例如：13812345678 -> 138****5678
     *
     * @param str 字符串
     * @param keepStart 保留开头字符数
     * @param keepEnd 保留结尾字符数
     * @param maskChar 掩码字符
     * @return 隐藏后的字符串
     * @deprecated 请使用 {@link StringMasker#mask(String, int, int, char)} 替代
     */
    @Deprecated
    public static String mask(String str, int keepStart, int keepEnd, char maskChar) {
        if (str == null) {
            return EMPTY;
        }
        if (str.length() <= keepStart + keepEnd) {
            return str;
        }

        int maskLength = str.length() - keepStart - keepEnd;
        String mask = repeat(String.valueOf(maskChar), maskLength);

        return str.substring(0, keepStart) + mask + str.substring(str.length() - keepEnd);
    }

    /**
     * 隐藏手机号中间4位
     *
     * @param phone 手机号
     * @return 隐藏后的手机号
     * @deprecated 请使用 {@link StringMasker#maskPhone(String)} 替代
     */
    @Deprecated
    public static String maskPhone(String phone) {
        return mask(phone, 3, 4, '*');
    }

    /**
     * 生成UUID（去除横线）
     *
     * @return UUID字符串
     * @deprecated 请使用 {@link StringGenerator#uuid()} 替代
     */
    @Deprecated
    public static String uuid() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成随机数字字符串
     *
     * @param length 长度
     * @return 随机数字字符串
     * @deprecated 请使用 {@link StringGenerator#randomNumeric(int)} 替代
     */
    @Deprecated
    public static String randomNumeric(int length) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append((int) (Math.random() * 10));
        }
        return result.toString();
    }

    /**
     * 生成随机字母字符串
     *
     * @param length 长度
     * @return 随机字母字符串
     * @deprecated 请使用 {@link StringGenerator#randomAlphabetic(int)} 替代
     */
    @Deprecated
    public static String randomAlphabetic(int length) {
        StringBuilder result = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < length; i++) {
            result.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return result.toString();
    }

    /**
     * 生成随机字母数字字符串
     *
     * @param length 长度
     * @return 随机字母数字字符串
     * @deprecated 请使用 {@link StringGenerator#randomAlphanumeric(int)} 替代
     */
    @Deprecated
    public static String randomAlphanumeric(int length) {
        StringBuilder result = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < length; i++) {
            result.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return result.toString();
    }
}
