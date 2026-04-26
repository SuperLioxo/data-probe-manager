package com.lixin.probe.util;

/**
 * 字符串转换工具类
 * 提供字符串格式转换功能
 *
 * @author Claude Code
 * @date 2026-03-12
 * @version 1.0
 */
public final class StringConverter {

    private StringConverter() {
        // 工具类不允许实例化
    }

    /**
     * 转换为驼峰命名
     * 例如：hello_world -> helloWorld
     *
     * @param str 字符串
     * @return 驼峰命名的字符串
     */
    public static String toCamelCase(String str) {
        if (StringUtils.isBlank(str)) {
            return StringUtils.EMPTY;
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
     */
    public static String toSnakeCase(String str) {
        if (StringUtils.isBlank(str)) {
            return StringUtils.EMPTY;
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
     * 比较两个字符串是否相等（忽略大小写）
     *
     * @param str1 字符串1
     * @param str2 字符串2
     * @return true如果相等
     */
    public static boolean equalsIgnoreCase(String str1, String str2) {
        return StringUtils.equalsIgnoreCase(str1, str2);
    }

    /**
     * 判断字符串是否以指定前缀开头（忽略大小写）
     *
     * @param str 字符串
     * @param prefix 前缀
     * @return true如果以指定前缀开头
     */
    public static boolean startsWithIgnoreCase(String str, String prefix) {
        return StringUtils.startsWithIgnoreCase(str, prefix);
    }

    /**
     * 判断字符串是否以指定后缀结尾（忽略大小写）
     *
     * @param str 字符串
     * @param suffix 后缀
     * @return true如果以指定后缀结尾
     */
    public static boolean endsWithIgnoreCase(String str, String suffix) {
        return StringUtils.endsWithIgnoreCase(str, suffix);
    }

    /**
     * 字符串包含判断（忽略大小写）
     *
     * @param str 字符串
     * @param searchStr 搜索字符串
     * @return true如果包含
     */
    public static boolean containsIgnoreCase(String str, String searchStr) {
        return StringUtils.containsIgnoreCase(str, searchStr);
    }
}
