package com.lixin.probe.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全配置验证器 —— 应用启动时的安全基线检查
 *
 * <p>本组件在Spring容器初始化完成后自动执行，对系统中所有关键的安全配置进行合规性检查。
 * 如果检测到不安全的配置（如密钥未设置、长度不足、使用了弱密钥），应用将拒绝启动，
 * 从而防止因配置不当导致的安全漏洞。</p>
 *
 * <h3>验证项一览：</h3>
 * <table border="1">
 *   <tr><th>配置项</th><th>环境变量</th><th>最小长度</th><th>用途</th></tr>
 *   <tr><td>jwt.secret</td><td>JWT_SECRET</td><td>64字符（生产）/ 32字符（开发）</td><td>JWT令牌签名密钥</td></tr>
 *   <tr><td>META_ENCRYPTION_KEY</td><td>META_ENCRYPTION_KEY</td><td>32字符</td><td>元数据加密密钥（AES-256）</td></tr>
 *   <tr><td>FILE_ENCRYPTION_KEY</td><td>FILE_ENCRYPTION_KEY</td><td>32字符</td><td>文件加密密钥（AES-256）</td></tr>
 *   <tr><td>UNIFIED_PROBE_KEY</td><td>UNIFIED_PROBE_KEY</td><td>16字符（仅生产环境必须）</td><td>探针认证密钥</td></tr>
 * </table>
 *
 * <h3>环境区分策略：</h3>
 * <ul>
 *   <li><b>开发环境（dev）</b>：降低密钥长度要求，允许使用默认值（仅警告），方便本地开发调试</li>
 *   <li><b>生产环境（prod/production）</b>：严格执行所有验证规则，不通过则阻止应用启动</li>
 *   <li><b>测试环境（test）</b>：通过 @Profile("!test") 注解完全跳过验证，避免影响单元测试</li>
 * </ul>
 *
 * <h3>密钥生成推荐命令：</h3>
 * <pre>
 *   # JWT签名密钥（64字符以上）
 *   openssl rand -base64 64
 *
 *   # AES-256加密密钥（32字节 = 44 base64字符）
 *   openssl rand -base64 32
 *
 *   # 探针认证密钥
 *   openssl rand -hex 32
 * </pre>
 *
 * @see JwtUtil JwtUtil中使用了jwt.secret配置
 * @see com.lixin.probe.util.CryptoUtil CryptoUtil中使用了加密密钥配置
 */
@Component
@Profile("!test")  // 在测试环境中禁用，避免测试启动失败
public class SecurityConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfigurationValidator.class);

    /**
     * JWT密钥最小长度（生产环境）
     * HS256算法要求密钥至少256位（32字节），这里要求64字符以确保足够的安全余量
     */
    private static final int MIN_JWT_SECRET_LENGTH = 64;

    /**
     * AES-256加密密钥最小长度
     * AES-256算法要求密钥恰好32字节，Base64编码后约44字符
     */
    private static final int MIN_ENCRYPTION_KEY_LENGTH = 32;

    /**
     * 探针认证密钥最小长度
     * 探针使用该密钥与Agent进行双向认证
     */
    private static final int MIN_PROBE_KEY_LENGTH = 16;

    /** JWT签名密钥，从配置文件或环境变量JWT_SECRET读取 */
    @Value("${jwt.secret:}")
    private String jwtSecret;

    /** 元数据加密密钥，用于加密存储敏感的元数据信息（如数据库连接密码） */
    @Value("${META_ENCRYPTION_KEY:dev-meta-encryption-key-for-development-only-32chars-min}")
    private String metaEncryptionKey;

    /** 文件加密密钥，用于加密上传的敏感文件 */
    @Value("${FILE_ENCRYPTION_KEY:dev-file-encryption-key-for-development-only-32chars-min}")
    private String fileEncryptionKey;

    /** 统一探针认证密钥，Agent探针使用该密钥与服务端进行身份验证 */
    @Value("${UNIFIED_PROBE_KEY:dev-unified-probe-key-for-development-16chars}")
    private String unifiedProbeKey;

    /** 当前激活的Spring Profile，用于区分开发和生产环境的验证策略 */
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    /**
     * 应用启动后执行安全配置验证
     *
     * <p>该方法在Spring Bean初始化完成后由容器自动调用（@PostConstruct注解）。
     * 按顺序验证JWT密钥、加密密钥和探针密钥。</p>
     *
     * <p>验证结果处理：</p>
     * <ul>
     *   <li><b>错误（errors）</b>：抛出IllegalStateException，阻止应用启动。
     *       应用日志中会显示详细的错误信息和密钥生成命令。</li>
     *   <li><b>警告（warnings）</b>：仅记录日志，不阻止启动。通常出现在开发环境
     *       使用了弱密钥或默认密钥时。</li>
     * </ul>
     *
     * @throws IllegalStateException 如果安全配置不符合要求，阻止应用启动
     */
    @PostConstruct
    public void validateSecurityConfiguration() {
        log.info("========================================");
        log.info("开始验证安全配置...");
        log.info("当前环境: {}", activeProfile);
        log.info("========================================");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 第一项：验证 JWT 签名密钥
        validateJwtSecret(errors, warnings);

        // 第二项：验证元数据和文件加密密钥
        validateEncryptionKeys(errors, warnings);

        // 第三项：验证探针认证密钥
        validateProbeKey(errors, warnings);

        // 如果存在任何错误，抛出异常阻止应用启动
        // 同时在日志中输出密钥生成的推荐命令，方便运维人员操作
        if (!errors.isEmpty()) {
            String errorMessage = "\n\n========================================\n" +
                    "🚨 安全配置验证失败\n" +
                    "========================================\n" +
                    String.join("\n", errors) +
                    "\n\n生成安全密钥的命令:\n" +
                    "  JWT Secret:        openssl rand -base64 64\n" +
                    "  加密密钥 (32字节):  openssl rand -base64 32\n" +
                    "  探针密钥:          openssl rand -hex 32\n" +
                    "\n========================================\n";

            log.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        // 输出警告信息（不阻止启动，但需要关注）
        if (!warnings.isEmpty()) {
            log.warn("========================================");
            log.warn("⚠️  安全配置警告:");
            for (String warning : warnings) {
                log.warn("  - {}", warning);
            }
            log.warn("========================================");
        }

        log.info("✅ 安全配置验证通过");
        log.info("========================================");
    }

    /**
     * 验证JWT签名密钥配置
     *
     * <p>JWT密钥是整个认证体系的核心，如果密钥泄露或太短，攻击者可以伪造Token，
     * 从而冒充任意用户访问系统。因此对密钥的验证最为严格。</p>
     *
     * <p>验证逻辑：</p>
     * <ol>
     *   <li>密钥不能为空</li>
     *   <li>开发环境至少32字符，生产环境至少64字符</li>
     *   <li>不能包含常见的弱密钥模式（如"password"、"secret"等）</li>
     * </ol>
     *
     * @param errors   错误列表，验证不通过时添加错误信息（会导致启动失败）
     * @param warnings 警告列表，弱密钥在开发环境下添加警告（不会导致启动失败）
     */
    private void validateJwtSecret(List<String> errors, List<String> warnings) {
        // 检查密钥是否为空
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            errors.add("❌ JWT_SECRET 未设置");
            return;
        }

        // 根据环境确定最小长度要求
        // 开发环境：32字符即可满足HS256算法基本要求
        // 生产环境：64字符，提供更大的密钥空间
        int minRequiredLength = "dev".equals(activeProfile) ? 32 : MIN_JWT_SECRET_LENGTH;

        // 检查密钥长度是否达标
        if (jwtSecret.length() < minRequiredLength) {
            errors.add(String.format(
                    "❌ JWT_SECRET 长度不足: 当前 %d 字符，至少需要 %d 字符",
                    jwtSecret.length(), minRequiredLength
            ));
            return;
        }

        // 检查是否使用了弱密钥（如包含"password"、"secret"等常见词汇）
        // 弱密钥容易被字典攻击破解
        if (isWeakKey(jwtSecret)) {
            String message = "JWT_SECRET 使用了弱密钥或默认值，请使用强随机密钥";
            if ("dev".equals(activeProfile)) {
                // 开发环境：只记录警告，不阻止启动
                warnings.add(message);
                log.warn("⚠️  开发环境使用固定密钥: {}", message);
            } else {
                // 生产环境：严格要求，阻止启动
                errors.add("❌ " + message);
                return;
            }
        }

        log.info("✅ JWT_SECRET: {} 字符 (符合{})", jwtSecret.length(),
                 "dev".equals(activeProfile) ? "开发环境要求" : "要求");
    }

    /**
     * 验证加密密钥配置（元数据加密密钥 + 文件加密密钥）
     *
     * <p>两把加密密钥均用于AES-256加密算法：</p>
     * <ul>
     *   <li>META_ENCRYPTION_KEY —— 加密存储在数据库中的敏感元数据（如数据库连接密码、API密钥等）</li>
     *   <li>FILE_ENCRYPTION_KEY —— 加密上传到服务器的敏感文件</li>
     * </ul>
     *
     * @param errors   错误列表
     * @param warnings 警告列表
     */
    private void validateEncryptionKeys(List<String> errors, List<String> warnings) {
        // 开发环境对密钥长度要求更宽松（30字符即可，方便使用默认开发密钥）
        int minRequiredLength = "dev".equals(activeProfile) ? 30 : MIN_ENCRYPTION_KEY_LENGTH;

        // 验证元数据加密密钥
        if (metaEncryptionKey == null || metaEncryptionKey.isEmpty()) {
            errors.add("❌ META_ENCRYPTION_KEY 未设置");
        } else if (metaEncryptionKey.length() < minRequiredLength) {
            errors.add(String.format(
                    "❌ META_ENCRYPTION_KEY 长度不足: 当前 %d 字符，至少需要 %d 字符",
                    metaEncryptionKey.length(), minRequiredLength
            ));
        } else if (isWeakKey(metaEncryptionKey)) {
            String message = "META_ENCRYPTION_KEY 使用了弱密钥，请使用强随机密钥";
            if ("dev".equals(activeProfile)) {
                // 开发环境仅警告
                warnings.add(message);
                log.info("✅ META_ENCRYPTION_KEY: {} 字符 (开发环境)", metaEncryptionKey.length());
            } else {
                // 生产环境阻止启动
                errors.add("❌ " + message);
            }
        } else {
            log.info("✅ META_ENCRYPTION_KEY: {} 字符 (符合要求)", metaEncryptionKey.length());
        }

        // 验证文件加密密钥（逻辑与元数据加密密钥验证相同）
        if (fileEncryptionKey == null || fileEncryptionKey.isEmpty()) {
            errors.add("❌ FILE_ENCRYPTION_KEY 未设置");
        } else if (fileEncryptionKey.length() < minRequiredLength) {
            errors.add(String.format(
                    "❌ FILE_ENCRYPTION_KEY 长度不足: 当前 %d 字符，至少需要 %d 字符",
                    fileEncryptionKey.length(), minRequiredLength
            ));
        } else if (isWeakKey(fileEncryptionKey)) {
            String message = "FILE_ENCRYPTION_KEY 使用了弱密钥，请使用强随机密钥";
            if ("dev".equals(activeProfile)) {
                warnings.add(message);
                log.info("✅ FILE_ENCRYPTION_KEY: {} 字符 (开发环境)", fileEncryptionKey.length());
            } else {
                errors.add("❌ " + message);
            }
        } else {
            log.info("✅ FILE_ENCRYPTION_KEY: {} 字符 (符合要求)", fileEncryptionKey.length());
        }
    }

    /**
     * 验证探针认证密钥配置
     *
     * <p>探针密钥用于Agent探针与管理端之间的双向认证。Agent在每次上报数据或同步配置时
     * 需要携带该密钥以证明身份。</p>
     *
     * <p>环境策略：</p>
     * <ul>
     *   <li>开发环境：探针密钥可选，未设置时不影响开发</li>
     *   <li>生产环境：必须设置，且长度至少16字符，否则拒绝启动</li>
     * </ul>
     *
     * @param errors   错误列表
     * @param warnings 警告列表
     */
    private void validateProbeKey(List<String> errors, List<String> warnings) {
        // 探针密钥在开发环境可以为空，但在生产环境必须设置
        if ("prod".equals(activeProfile) || "production".equals(activeProfile)) {
            if (unifiedProbeKey == null || unifiedProbeKey.isEmpty()) {
                errors.add("❌ UNIFIED_PROBE_KEY 未设置 (生产环境必须设置)");
                return;
            }

            if (unifiedProbeKey.length() < MIN_PROBE_KEY_LENGTH) {
                errors.add(String.format(
                        "❌ UNIFIED_PROBE_KEY 长度不足: 当前 %d 字符，至少需要 %d 字符",
                        unifiedProbeKey.length(), MIN_PROBE_KEY_LENGTH
                ));
                return;
            }

            if (isWeakKey(unifiedProbeKey)) {
                warnings.add("UNIFIED_PROBE_KEY 使用了弱密钥，请使用强随机密钥");
            } else {
                log.info("✅ UNIFIED_PROBE_KEY: {} 字符 (符合要求)", unifiedProbeKey.length());
            }
        } else {
            log.info("ℹ️  UNIFIED_PROBE_KEY: 开发环境未设置 (可选)");
        }
    }

    /**
     * 弱密钥检测
     *
     * <p>通过检查密钥中是否包含常见的弱密钥模式来判断密钥安全性。
     * 这些模式包括常见的测试用词、默认值占位符等。</p>
     *
     * <p>检测模式列表：</p>
     * <ul>
     *   <li>常见弱密码词：password、secret、key、test、demo、admin、root、default、changeme</li>
     *   <li>配置模板占位符：your-secret-key、your-key-here、unified-probe-key</li>
     * </ul>
     *
     * <p>注意：这是一种启发式检测，无法检测所有弱密钥，但能捕获最常见的配置错误。</p>
     *
     * @param key 待检查的密钥字符串
     * @return true表示检测到弱密钥模式，false表示未检测到
     */
    private boolean isWeakKey(String key) {
        if (key == null) {
            return true;
        }

        // 常见弱密钥模式列表
        // 包含了常见的测试用词、默认占位符、以及容易被猜到的词汇
        String[] weakPatterns = {
                "password", "secret", "key", "test", "demo",
                "admin", "root", "default", "changeme",
                "your-secret-key", "your-key-here", "unified-probe-key"
        };

        // 不区分大小写进行匹配
        String lowerKey = key.toLowerCase();
        for (String pattern : weakPatterns) {
            if (lowerKey.contains(pattern)) {
                return true;
            }
        }

        return false;
    }
}
