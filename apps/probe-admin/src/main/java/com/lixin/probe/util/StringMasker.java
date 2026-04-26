package com.lixin.probe.util;

/**
 * 字符串脱敏工具类
 * 提供数据脱敏功能
 *
 * @author Claude Code
 * @date 2026-03-12
 * @version 1.0
 */
public final class StringMasker {

    private StringMasker() {
        // 工具类不允许实例化
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
     */
    public static String mask(String str, int keepStart, int keepEnd, char maskChar) {
        if (str == null) {
            return StringUtils.EMPTY;
        }
        if (str.length() <= keepStart + keepEnd) {
            return str;
        }

        int maskLength = str.length() - keepStart - keepEnd;
        String mask = StringUtils.repeat(String.valueOf(maskChar), maskLength);

        return str.substring(0, keepStart) + mask + str.substring(str.length() - keepEnd);
    }

    /**
     * 隐藏手机号中间4位
     *
     * @param phone 手机号
     * @return 隐藏后的手机号
     */
    public static String maskPhone(String phone) {
        return mask(phone, 3, 4, '*');
    }

    /**
     * 隐藏邮箱地址
     * 例如：example@gmail.com -> e******@gmail.com
     *
     * @param email 邮箱地址
     * @return 隐藏后的邮箱
     */
    public static String maskEmail(String email) {
        if (StringUtils.isBlank(email) || !email.contains("@")) {
            return email;
        }

        int atIndex = email.indexOf('@');
        String prefix = email.substring(0, atIndex);
        String suffix = email.substring(atIndex);

        if (prefix.length() <= 2) {
            return email;
        }

        return prefix.charAt(0) + StringUtils.repeat("*", prefix.length() - 1) + suffix;
    }

    /**
     * 隐藏银行卡号
     * 例如：6222021234567890 -> 622202*******890
     *
     * @param cardNo 银行卡号
     * @return 隐藏后的卡号
     */
    public static String maskBankCard(String cardNo) {
        return mask(cardNo, 6, 4, '*');
    }

    /**
     * 隐藏身份证号
     * 例如：310101199001011234 -> 310101********1234
     *
     * @param idNo 身份证号
     * @return 隐藏后的身份证号
     */
    public static String maskIdCard(String idNo) {
        return mask(idNo, 6, 4, '*');
    }

    /**
     * Token脱敏
     * 显示前8位和后4位，中间用4个星号填充
     *
     * @param token Token字符串
     * @return 脱敏后的Token
     */
    public static String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return token;
        }

        int keepStart = 8;
        int keepEnd = 4;

        if (token.length() <= keepStart + keepEnd) {
            return token;
        }

        // 固定显示4个星号
        String start = token.substring(0, keepStart);
        String end = token.substring(token.length() - keepEnd);

        return start + "****" + end;
    }
}
