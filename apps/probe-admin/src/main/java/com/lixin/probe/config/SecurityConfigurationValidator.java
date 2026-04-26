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
 * 安全配置验证器
 * <p>
 * 在应用启动时验证关键的安全配置，确保生产环境的安全性。
 * 如果检测到不安全的配置，应用将拒绝启动并显示清晰的错误信息。
 * </p>
 *
 * <p>验证规则:</p>
 * <ul>
 *   <li>JWT_SECRET: 必须 >= 64 字符 (HS256 算法要求)</li>
 *   <li>META_ENCRYPTION_KEY: 必须 >= 32 字符 (AES-256 要求)</li>
 *   <li>FILE_ENCRYPTION_KEY: 必须 >= 32 字符 (AES-256 要求)</li>
 *   <li>UNIFIED_PROBE_KEY: 必须 >= 16 字符</li>
 * </ul>
 *
 * @author Claude Code
 * @date 2026-03-20
 * @version 1.0
 */
@Component
@Profile("!test")  // 在测试环境中禁用，避免测试启动失败
public class SecurityConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfigurationValidator.class);

    /**
     * JWT 密钥最小长度 (HS256)
     */
    private static final int MIN_JWT_SECRET_LENGTH = 64;

    /**
     * AES-256 加密密钥最小长度 (32 字节 = 44 base64 字符)
     */
    private static final int MIN_ENCRYPTION_KEY_LENGTH = 32;

    /**
     * 探针认证密钥最小长度
     */
    private static final int MIN_PROBE_KEY_LENGTH = 16;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${META_ENCRYPTION_KEY:dev-meta-encryption-key-for-development-only-32chars-min}")
    private String metaEncryptionKey;

    @Value("${FILE_ENCRYPTION_KEY:dev-file-encryption-key-for-development-only-32chars-min}")
    private String fileEncryptionKey;

    @Value("${UNIFIED_PROBE_KEY:dev-unified-probe-key-for-development-16chars}")
    private String unifiedProbeKey;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    /**
     * 在应用启动后立即执行安全配置验证
     *
     * @throws IllegalStateException 如果安全配置不符合要求
     */
    @PostConstruct
    public void validateSecurityConfiguration() {
        log.info("========================================");
        log.info("开始验证安全配置...");
        log.info("当前环境: {}", activeProfile);
        log.info("========================================");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 验证 JWT 密钥
        validateJwtSecret(errors, warnings);

        // 验证加密密钥
        validateEncryptionKeys(errors, warnings);

        // 验证探针密钥
        validateProbeKey(errors, warnings);

        // 如果有错误，抛出异常阻止应用启动
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

        // 输出警告信息
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
     * 验证 JWT 密钥配置
     */
    private void validateJwtSecret(List<String> errors, List<String> warnings) {
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            errors.add("❌ JWT_SECRET 未设置");
            return;
        }

        // 开发环境：降低密钥长度要求（32字符即可满足HS256算法）
        int minRequiredLength = "dev".equals(activeProfile) ? 32 : MIN_JWT_SECRET_LENGTH;

        if (jwtSecret.length() < minRequiredLength) {
            errors.add(String.format(
                    "❌ JWT_SECRET 长度不足: 当前 %d 字符，至少需要 %d 字符",
                    jwtSecret.length(), minRequiredLength
            ));
            return;
        }

        // 检查是否使用了默认/弱密钥
        if (isWeakKey(jwtSecret)) {
            String message = "JWT_SECRET 使用了弱密钥或默认值，请使用强随机密钥";
            if ("dev".equals(activeProfile)) {
                // 开发环境：只警告，不阻止启动
                warnings.add(message);
                log.warn("⚠️  开发环境使用固定密钥: {}", message);
            } else {
                // 生产环境：阻止启动
                errors.add("❌ " + message);
                return;
            }
        }

        log.info("✅ JWT_SECRET: {} 字符 (符合{})", jwtSecret.length(),
                 "dev".equals(activeProfile) ? "开发环境要求" : "要求");
    }

    /**
     * 验证加密密钥配置
     */
    private void validateEncryptionKeys(List<String> errors, List<String> warnings) {
        // 开发环境：对密钥长度要求更宽松
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
                warnings.add(message);
                log.info("✅ META_ENCRYPTION_KEY: {} 字符 (开发环境)", metaEncryptionKey.length());
            } else {
                errors.add("❌ " + message);
            }
        } else {
            log.info("✅ META_ENCRYPTION_KEY: {} 字符 (符合要求)", metaEncryptionKey.length());
        }

        // 验证文件加密密钥
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
     * 验证探针密钥配置
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
     * 检查是否为弱密钥或默认密钥
     *
     * @param key 待检查的密钥
     * @return true 如果是弱密钥
     */
    private boolean isWeakKey(String key) {
        if (key == null) {
            return true;
        }

        // 检查常见的弱密钥模式
        String[] weakPatterns = {
                "password", "secret", "key", "test", "demo",
                "admin", "root", "default", "changeme",
                "your-secret-key", "your-key-here", "unified-probe-key"
        };

        String lowerKey = key.toLowerCase();
        for (String pattern : weakPatterns) {
            if (lowerKey.contains(pattern)) {
                return true;
            }
        }

        return false;
    }
}