package com.lixin.probe.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 安全工具类
 * 提供常用的安全相关方法
 */
public class SecurityUtil {

    private static final Logger log = LoggerFactory.getLogger(SecurityUtil.class);
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /**
     * 密码加密
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    public static String encryptPassword(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    /**
     * 验证密码
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean matchesPassword(String rawPassword, String encodedPassword) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(encodedPassword)) {
            return false;
        }
        return PASSWORD_ENCODER.matches(rawPassword, encodedPassword);
    }

    /**
     * 脱敏处理 - 隐藏敏感信息
     * @param content 原始内容
     * @param keepStart 保留开头字符数
     * @param keepEnd 保留结尾字符数
     * @return 脱敏后的内容
     */
    public static String mask(String content, int keepStart, int keepEnd) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        int length = content.length();
        if (length <= keepStart + keepEnd) {
            return content;
        }

        String start = content.substring(0, keepStart);
        String end = content.substring(length - keepEnd);
        int maskLength = length - keepStart - keepEnd;

        return start + "*".repeat(maskLength) + end;
    }

    /**
     * 手机号脱敏（保留前3位和后4位）
     */
    public static String maskPhone(String phone) {
        return mask(phone, 3, 4);
    }

    /**
     * 邮箱脱敏（保留前2位和@之后的内容）
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        int atIndex = email.indexOf("@");
        String prefix = email.substring(0, Math.min(2, atIndex));
        String suffix = email.substring(atIndex);
        int maskLength = Math.max(0, atIndex - 2);

        return prefix + "*".repeat(maskLength) + suffix;
    }

    /**
     * Token脱敏（只显示前8位和后4位）
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

    /**
     * 检查字符串是否包含SQL注入特征
     */
    public static boolean containsSqlInjection(String str) {
        if (str == null) {
            return false;
        }

        String[] sqlKeywords = {
            "select", "insert", "update", "delete", "drop", "truncate",
            "union", "exec", "execute", "script", "javascript", "alert",
            "'--", "/*", "*/", ";", "--", "xp_", "sp_",
            " or ", " and ", " xor ", "/*!", "||", "&&", "--"
        };

        // 检查SQL注入模式
        String lowerStr = str.toLowerCase();
        for (String keyword : sqlKeywords) {
            if (lowerStr.contains(keyword)) {
                log.warn("检测到可能的SQL注入: {}", str);
                return true;
            }
        }

        // 检查单引号注入模式 (如 '1'='1)
        if (lowerStr.matches(".*'\\s*=\\s*'.*")) {
            log.warn("检测到可能的SQL注入: {}", str);
            return true;
        }

        return false;
    }

    /**
     * 检查字符串是否包含XSS攻击特征
     */
    public static boolean containsXss(String str) {
        if (str == null) {
            return false;
        }

        String[] xssPatterns = {
            "<script", "</script", "javascript:", "onerror=", "onload=",
            "onclick=", "onmouseover=", "onfocus=", "onblur=", "eval("
        };

        String lowerStr = str.toLowerCase();
        for (String pattern : xssPatterns) {
            if (lowerStr.contains(pattern)) {
                log.warn("检测到可能的XSS攻击: {}", str);
                return true;
            }
        }

        return false;
    }

    /**
     * 获取客户端IP地址
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 处理多个IP的情况
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 检查是否为安全的文件名
     */
    public static boolean isSafeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        // 检查路径遍历攻击
        if (filename.contains("../") || filename.contains("..\\")) {
            return false;
        }

        // 检查绝对路径
        if (filename.startsWith("/") || filename.contains(":")) {
            return false;
        }

        // 检查特殊字符
        String[] unsafeChars = {"..", "~", "\0"};
        for (String ch : unsafeChars) {
            if (filename.contains(ch)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 生成随机密钥
     * @param length 密钥长度
     * @return 随机密钥
     */
    public static String generateSecretKey(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder key = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * chars.length());
            key.append(chars.charAt(index));
        }

        return key.toString();
    }

    /**
     * 验证文件路径是否安全，防止路径遍历攻击
     * @param filePath 要验证的文件路径
     * @param allowedBaseDir 允许的基础目录
     * @return 是否安全
     */
    public static boolean isSafeFilePath(String filePath, String allowedBaseDir) {
        if (filePath == null || filePath.isEmpty()) {
            log.warn("文件路径为空");
            return false;
        }

        try {
            Path path = Paths.get(filePath).normalize();
            Path baseDir = Paths.get(allowedBaseDir).normalize();

            // 检查规范化后的路径是否以基础目录开头
            if (!path.startsWith(baseDir)) {
                log.warn("文件路径超出允许的目录: filePath={}, baseDir={}", path, baseDir);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("验证文件路径失败: filePath={}", filePath, e);
            return false;
        }
    }

    /**
     * 验证文件路径是否在允许的插件目录中
     * @param filePath 文件路径
     * @param allowedDirectories 允许的目录列表
     * @return 是否安全
     */
    public static boolean isSafePluginPath(String filePath, String... allowedDirectories) {
        if (filePath == null || filePath.isEmpty()) {
            log.warn("文件路径为空");
            return false;
        }

        if (allowedDirectories == null || allowedDirectories.length == 0) {
            log.warn("没有配置允许的目录");
            return false;
        }

        try {
            Path path = Paths.get(filePath).normalize();

            // 检查是否在任何一个允许的目录中
            for (String allowedDir : allowedDirectories) {
                Path baseDir = Paths.get(allowedDir).normalize();
                if (path.startsWith(baseDir)) {
                    return true;
                }
            }

            log.warn("文件路径不在任何允许的目录中: filePath={}, allowedDirs={}",
                    path, String.join(",", allowedDirectories));
            return false;

        } catch (Exception e) {
            log.error("验证文件路径失败: filePath={}", filePath, e);
            return false;
        }
    }
}
