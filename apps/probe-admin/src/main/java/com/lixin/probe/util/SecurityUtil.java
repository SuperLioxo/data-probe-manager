package com.lixin.probe.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 安全工具类 —— 提供密码加密、数据脱敏、攻击检测等通用安全能力
 *
 * <p>本类是一个无状态的静态工具类，集成了系统各模块常用的安全相关方法。
 * 所有方法均为静态方法，可直接通过类名调用，无需实例化。</p>
 *
 * <h3>功能模块：</h3>
 * <ol>
 *   <li><b>密码管理</b> —— 基于BCrypt算法的密码加密和验证</li>
 *   <li><b>数据脱敏</b> —— 对手机号、邮箱、Token等敏感信息进行掩码处理</li>
 *   <li><b>攻击检测</b> —— SQL注入和XSS攻击特征检测</li>
 *   <li><b>客户端识别</b> —— 获取客户端真实IP地址（支持代理环境）</li>
 *   <li><b>文件安全</b> —— 文件名和文件路径安全性校验，防止路径遍历攻击</li>
 *   <li><b>密钥生成</b> —— 生成指定长度的随机密钥</li>
 * </ol>
 *
 * <h3>使用示例：</h3>
 * <pre>
 *   // 密码加密
 *   String encoded = SecurityUtil.encryptPassword("myPassword123");
 *
 *   // 密码验证
 *   boolean matches = SecurityUtil.matchesPassword("myPassword123", encoded);
 *
 *   // 数据脱敏
 *   String maskedPhone = SecurityUtil.maskPhone("13812345678");    // "138****5678"
 *   String maskedEmail = SecurityUtil.maskEmail("test@example.com"); // "te***@example.com"
 *   String maskedToken = SecurityUtil.maskToken("eyJhbGciOi...");   // "eyJhbGci****abcd"
 *
 *   // 攻击检测
 *   boolean hasSql = SecurityUtil.containsSqlInjection("1' OR '1'='1"); // true
 *   boolean hasXss = SecurityUtil.containsXss("&lt;script&gt;alert(1)&lt;/script&gt;");    // true
 *
 *   // 文件路径安全校验
 *   boolean safe = SecurityUtil.isSafeFilename("../../etc/passwd"); // false
 * </pre>
 *
 * @see BCryptPasswordEncoder 密码加密底层实现
 */
public class SecurityUtil {

    private static final Logger log = LoggerFactory.getLogger(SecurityUtil.class);

    /**
     * BCrypt密码编码器实例
     * BCrypt是一种自适应哈希算法，内置盐值，计算成本可调。
     * 相比MD5/SHA等简单哈希，BCrypt能有效抵抗暴力破解和彩虹表攻击。
     */
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /**
     * 密码加密 —— 使用BCrypt算法对明文密码进行单向哈希
     *
     * <p>BCrypt算法特点：</p>
     * <ul>
     *   <li>自动生成随机盐值，相同密码每次加密结果不同</li>
     *   <li>哈希强度可随硬件性能提升而调整</li>
     *   <li>加密结果中包含算法版本、成本因子、盐值和哈希值（如 $2a$10$...）</li>
     * </ul>
     *
     * @param rawPassword 原始明文密码
     * @return BCrypt加密后的密码哈希字符串
     * @throws IllegalArgumentException 如果密码为空或空白
     */
    public static String encryptPassword(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    /**
     * 密码验证 —— 将明文密码与加密后的密码哈希进行比对
     *
     * <p>用于用户登录时的密码校验。BCrypt的验证过程会从加密字符串中提取盐值，
     * 用相同的盐值对输入密码进行哈希，然后比较结果。</p>
     *
     * @param rawPassword      用户输入的明文密码
     * @param encodedPassword 数据库中存储的加密密码
     * @return true表示密码匹配，false表示不匹配或输入为空
     */
    public static boolean matchesPassword(String rawPassword, String encodedPassword) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(encodedPassword)) {
            return false;
        }
        return PASSWORD_ENCODER.matches(rawPassword, encodedPassword);
    }

    /**
     * 通用脱敏方法 —— 对字符串进行掩码处理
     *
     * <p>保留字符串的首尾部分，中间替换为星号。用于日志输出、API响应等场景，
     * 防止敏感信息泄露。</p>
     *
     * <p>示例：mask("ABCDEFGHIJ", 2, 3) -> "AB*****HIJ"</p>
     *
     * @param content  原始内容
     * @param keepStart 保留开头字符数
     * @param keepEnd   保留结尾字符数
     * @return 脱敏后的内容，如果内容长度不足则原样返回
     */
    public static String mask(String content, int keepStart, int keepEnd) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        int length = content.length();
        // 如果总长度不超过保留部分，不需要脱敏
        if (length <= keepStart + keepEnd) {
            return content;
        }

        String start = content.substring(0, keepStart);
        String end = content.substring(length - keepEnd);
        int maskLength = length - keepStart - keepEnd;

        return start + "*".repeat(maskLength) + end;
    }

    /**
     * 手机号脱敏 —— 保留前3位和后4位
     *
     * <p>示例：maskPhone("13812345678") -> "138****5678"</p>
     *
     * @param phone 原始手机号
     * @return 脱敏后的手机号
     */
    public static String maskPhone(String phone) {
        return mask(phone, 3, 4);
    }

    /**
     * 邮箱脱敏 —— 保留前2位和@之后的内容
     *
     * <p>示例：maskEmail("testuser@example.com") -> "te******@example.com"</p>
     *
     * @param email 原始邮箱地址
     * @return 脱敏后的邮箱地址
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        int atIndex = email.indexOf("@");
        // 保留@前的前2个字符（不足2个则全保留）
        String prefix = email.substring(0, Math.min(2, atIndex));
        // 保留@及@之后的部分
        String suffix = email.substring(atIndex);
        // 中间部分替换为星号
        int maskLength = Math.max(0, atIndex - 2);

        return prefix + "*".repeat(maskLength) + suffix;
    }

    /**
     * Token脱敏 —— 只显示前8位和后4位，中间固定为4个星号
     *
     * <p>用于日志输出场景，既能区分不同的Token，又不会泄露完整内容。</p>
     * <p>示例：maskToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.abc123") -> "eyJhbGci****c123"</p>
     *
     * @param token 原始Token字符串
     * @return 脱敏后的Token字符串
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

        // 固定显示4个星号（不随Token长度变化）
        String start = token.substring(0, keepStart);
        String end = token.substring(token.length() - keepEnd);

        return start + "****" + end;
    }

    /**
     * SQL注入特征检测
     *
     * <p>通过匹配常见的SQL注入攻击模式来检测输入字符串是否可能包含恶意SQL代码。
     * 这是一种辅助性的安全措施，不能替代参数化查询等根本性防御手段。</p>
     *
     * <p>检测模式包括：</p>
     * <ul>
     *   <li>SQL关键字：SELECT、INSERT、UPDATE、DELETE、DROP、UNION等</li>
     *   <li>SQL特殊符号：单引号、注释符（--、斜杠星号）、分号等</li>
     *   <li>逻辑操作符注入：OR、AND、XOR等</li>
     *   <li>引号等值注入模式：如 '1'='1</li>
     * </ul>
     *
     * <p>注意：此方法可能产生误报（如合法输入中包含SQL关键字），建议仅用于日志告警，
     * 不要直接拦截请求。</p>
     *
     * @param str 待检测的字符串
     * @return true表示检测到可能的SQL注入特征
     */
    public static boolean containsSqlInjection(String str) {
        if (str == null) {
            return false;
        }

        // SQL注入常见关键字和特殊符号列表
        String[] sqlKeywords = {
            "select", "insert", "update", "delete", "drop", "truncate",
            "union", "exec", "execute", "script", "javascript", "alert",
            "'--", "/*", "*/", ";", "--", "xp_", "sp_",
            " or ", " and ", " xor ", "/*!", "||", "&&", "--"
        };

        // 逐个匹配SQL注入模式（不区分大小写）
        String lowerStr = str.toLowerCase();
        for (String keyword : sqlKeywords) {
            if (lowerStr.contains(keyword)) {
                log.warn("检测到可能的SQL注入: {}", str);
                return true;
            }
        }

        // 检查单引号注入模式 (如 '1'='1)，这是经典的SQL注入手法
        if (lowerStr.matches(".*'\\s*=\\s*'.*")) {
            log.warn("检测到可能的SQL注入: {}", str);
            return true;
        }

        return false;
    }

    /**
     * XSS攻击特征检测
     *
     * <p>通过匹配常见的跨站脚本攻击（Cross-Site Scripting）模式来检测输入字符串
     * 是否可能包含恶意脚本代码。</p>
     *
     * <p>检测模式包括：</p>
     * <ul>
     *   <li>HTML脚本标签：&lt;script&gt;、&lt;/script&gt;</li>
     *   <li>JavaScript协议：javascript:</li>
     *   <li>事件处理属性：onerror=、onload=、onclick=、onmouseover=等</li>
     *   <li>代码执行函数：eval()</li>
     * </ul>
     *
     * @param str 待检测的字符串
     * @return true表示检测到可能的XSS攻击特征
     */
    public static boolean containsXss(String str) {
        if (str == null) {
            return false;
        }

        // XSS攻击常见的标签、协议和事件处理属性
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
     * 获取客户端真实IP地址
     *
     * <p>在反向代理（如Nginx、负载均衡器）环境下，直接通过request.getRemoteAddr()
     * 获取的是代理服务器的IP地址。此方法按照优先级从多个请求头中提取客户端的真实IP。</p>
     *
     * <p>优先级顺序：</p>
     * <ol>
     *   <li><b>X-Forwarded-For</b> —— 标准代理头，格式为"客户端IP, 代理1IP, 代理2IP"</li>
     *   <li><b>X-Real-IP</b> —— Nginx常用的自定义头，直接存储客户端IP</li>
     *   <li><b>remoteAddr</b> —— 无代理时的直连客户端IP</li>
     * </ol>
     *
     * @param request HTTP请求对象
     * @return 客户端真实IP地址
     */
    public static String getClientIp(HttpServletRequest request) {
        // 优先级1：X-Forwarded-For（多层代理时包含多个IP，取第一个）
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // 优先级2：X-Real-IP（Nginx代理设置）
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // 优先级3：直接获取（无代理环境）
            ip = request.getRemoteAddr();
        }

        // X-Forwarded-For格式为"IP1, IP2, IP3"，取第一个（即真实客户端IP）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 检查文件名是否安全
     *
     * <p>防止通过文件名进行路径遍历攻击（Directory Traversal / Path Traversal）。
     * 此方法用于文件上传场景，在处理用户提交的文件名之前进行安全检查。</p>
     *
     * <p>检查项：</p>
     * <ul>
     *   <li>路径遍历攻击：包含 "../" 或 "..\\" 的文件名</li>
     *   <li>绝对路径：以 "/" 开头或包含盘符冒号（如 "C:"）</li>
     *   <li>特殊字符：包含空字节（\0）、波浪号（~）等</li>
     * </ul>
     *
     * @param filename 待检查的文件名
     * @return true表示文件名安全，false表示文件名可能包含攻击
     */
    public static boolean isSafeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        // 检查路径遍历攻击（如 "../../../etc/passwd"）
        if (filename.contains("../") || filename.contains("..\\")) {
            return false;
        }

        // 检查绝对路径（如 "/etc/passwd" 或 "C:\Windows\system32"）
        if (filename.startsWith("/") || filename.contains(":")) {
            return false;
        }

        // 检查特殊字符（空字节注入等）
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
     *
     * <p>使用伪随机数生成指定长度的密钥字符串。字符集包含大小写字母、数字和特殊字符。</p>
     *
     * <p>注意：此方法使用Math.random()，适用于一般场景。对于高安全要求的密钥生成
     * （如加密密钥），建议使用 SecureRandom 或 openssl 命令行工具。</p>
     *
     * @param length 密钥长度
     * @return 随机密钥字符串
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
     * 验证文件路径是否安全 —— 防止路径遍历攻击
     *
     * <p>检查给定的文件路径是否在允许的基础目录范围内。
     * 通过路径规范化（normalize）消除 ".." 和 "." 的影响，然后检查规范化后的路径
     * 是否以允许的基础目录开头。</p>
     *
     * <p>示例：</p>
     * <ul>
     *   <li>isSafeFilePath("/app/uploads/file.txt", "/app/uploads") -> true</li>
     *   <li>isSafeFilePath("/app/uploads/../../etc/passwd", "/app/uploads") -> false</li>
     *   <li>isSafeFilePath("/etc/passwd", "/app/uploads") -> false</li>
     * </ul>
     *
     * @param filePath        要验证的文件路径
     * @param allowedBaseDir  允许的基础目录
     * @return true表示路径安全（在允许目录内），false表示路径不安全
     */
    public static boolean isSafeFilePath(String filePath, String allowedBaseDir) {
        if (filePath == null || filePath.isEmpty()) {
            log.warn("文件路径为空");
            return false;
        }

        try {
            // normalize()消除路径中的 ".." 和 "." ，获取真实路径
            Path path = Paths.get(filePath).normalize();
            Path baseDir = Paths.get(allowedBaseDir).normalize();

            // 检查规范化后的路径是否以基础目录开头
            // 如果不是，说明路径试图跳出允许的范围
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
     *
     * <p>与isSafeFilePath类似，但支持多个允许的目录。用于插件系统场景，
     * 插件可能分布在多个目录中，需要检查文件路径是否在任意一个允许的目录下。</p>
     *
     * @param filePath            文件路径
     * @param allowedDirectories  允许的目录列表（可变参数）
     * @return true表示路径在某个允许的目录中，false表示不在任何允许的目录中
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

            // 检查路径是否在任意一个允许的目录中
            for (String allowedDir : allowedDirectories) {
                Path baseDir = Paths.get(allowedDir).normalize();
                if (path.startsWith(baseDir)) {
                    return true;  // 路径在允许的目录范围内
                }
            }

            // 路径不在任何允许的目录中
            log.warn("文件路径不在任何允许的目录中: filePath={}, allowedDirs={}",
                    path, String.join(",", allowedDirectories));
            return false;

        } catch (Exception e) {
            log.error("验证文件路径失败: filePath={}", filePath, e);
            return false;
        }
    }
}
