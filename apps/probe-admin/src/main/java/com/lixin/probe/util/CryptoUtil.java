package com.lixin.probe.util;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * 加密解密工具类
 * 升级到 AES-256，添加 HMAC 签名
 *
 * @version 3.0 - 安全增强版
 */
@Component
public class CryptoUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CryptoUtil.class);

    // 算法常量
    public static final String SHA_256 = "SHA-256";
    public static final String AES = "AES";
    public static final String HMAC_SHA256 = "HmacSHA256";

    // AES 算法模式（CBC 模式 + PKCS5Padding 填充）
    private static final String AES_CBC_PKCS5PADDING = "AES/CBC/PKCS5Padding";

    // 密钥长度常量（升级到 AES-256）
    private static final int AES_256_KEY_LENGTH = 32;  // 256位 = 32字节
    private static final int IV_LENGTH = 16;           // 128位 IV

    // PBKDF2 密钥派生参数
    private static final int PBKDF2_ITERATIONS = 10000;
    private static final int PBKDF2_KEY_LENGTH = 32;  // AES-256

    /**
     * 使用 AES-256 加密（推荐方法）
     *
     * @param text 明文
     * @param key  密钥（任意长度，会自动派生为 32 字节）
     * @return     格式：base64(IV):base64(encrypted)
     */
    public static String encryptAES256(String text, String key) {
        // 验证密钥
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("密钥不能为空");
        }

        try {
            // 1. 使用 PBKDF2 派生 32 字节密钥（AES-256）
            byte[] derivedKey = deriveKey(key, PBKDF2_KEY_LENGTH);

            // 2. 生成随机 IV
            byte[] ivBytes = generateRandomIV();

            // 3. 初始化加密器
            SecretKeySpec secretKeySpec = new SecretKeySpec(derivedKey, AES);
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(AES_CBC_PKCS5PADDING);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);

            // 4. 加密
            byte[] encryptedBytes = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));

            // 5. Base64 编码
            String ivBase64 = Base64.getEncoder().encodeToString(ivBytes);
            String encryptedBase64 = Base64.getEncoder().encodeToString(encryptedBytes);

            log.debug("AES-256 加密成功，明文长度: {}, 密文长度: {}", text.length(), encryptedBase64.length());

            return ivBase64 + ":" + encryptedBase64;
        } catch (Exception e) {
            log.error("AES-256 加密失败", e);
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 使用 AES-256 解密（推荐方法）
     *
     * @param cipherText 格式：base64(IV):base64(encrypted)
     * @param key        密钥
     * @return           明文
     */
    public static String decryptAES256(String cipherText, String key) {
        try {
            // 1. 验证格式
            if (cipherText == null || !cipherText.contains(":")) {
                throw new IllegalArgumentException("密文格式错误");
            }

            // 2. 分割 IV 和密文
            String[] parts = cipherText.split(":", 2);
            byte[] ivBytes = Base64.getDecoder().decode(parts[0]);
            byte[] encryptedBytes = Base64.getDecoder().decode(parts[1]);

            // 3. 使用 PBKDF2 派生密钥
            byte[] derivedKey = deriveKey(key, PBKDF2_KEY_LENGTH);

            // 4. 初始化解密器
            SecretKeySpec secretKeySpec = new SecretKeySpec(derivedKey, AES);
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance(AES_CBC_PKCS5PADDING);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);

            // 5. 解密
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            String plaintext = new String(decryptedBytes, StandardCharsets.UTF_8);

            log.debug("AES-256 解密成功，密文长度: {}, 明文长度: {}", cipherText.length(), plaintext.length());

            return plaintext;
        } catch (Exception e) {
            log.error("AES-256 解密失败，密文: {}", cipherText.substring(0, Math.min(20, cipherText.length())), e);
            throw new RuntimeException("解密失败", e);
        }
    }

    /**
     * 生成 HMAC-SHA256 签名（用于消息完整性验证）
     *
     * @param data 原始数据
     * @param key  密钥
     * @return     Base64 编码的签名
     */
    public static String signHMAC(String data, String key) {
        try {
            byte[] derivedKey = deriveKey(key, PBKDF2_KEY_LENGTH);
            SecretKeySpec secretKeySpec = new SecretKeySpec(derivedKey, HMAC_SHA256);

            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hmac);
        } catch (Exception e) {
            log.error("HMAC 签名失败", e);
            throw new RuntimeException("签名失败", e);
        }
    }

    /**
     * 验证 HMAC 签名
     *
     * @param data      原始数据
     * @param key       密钥
     * @param signature 签名
     * @return          是否验证通过
     */
    public static boolean verifyHMAC(String data, String key, String signature) {
        try {
            String expectedSignature = signHMAC(data, key);
            return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("HMAC 验证失败", e);
            return false;
        }
    }

    /**
     * 使用 PBKDF2 派生密钥（更安全的密钥派生）
     *
     * @param password  原始密码/密钥
     * @param keyLength 派生的密钥长度（字节）
     * @return          派生的密钥
     */
    private static byte[] deriveKey(String password, int keyLength) {
        try {
            // 使用固定盐值（在生产环境中应该使用随机盐值并存储）
            byte[] salt = "probe-meta-salt-2026".getBytes(StandardCharsets.UTF_8);

            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, keyLength * 8);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            byte[] derivedKey = factory.generateSecret(spec).getEncoded();
            log.trace("密钥派生成功，长度: {} 字节", derivedKey.length);

            return derivedKey;
        } catch (Exception e) {
            log.error("密钥派生失败", e);
            throw new RuntimeException("密钥派生失败", e);
        }
    }

    /**
     * 生成随机 IV
     *
     * @return 16 字节的随机 IV
     */
    private static byte[] generateRandomIV() {
        byte[] ivBytes = new byte[IV_LENGTH];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(ivBytes);
        return ivBytes;
    }

    /**
     * 加密（兼容旧版本接口）
     *
     * @param algo 算法类型 (AES, SHA_256)
     * @param text 明文
     * @param key  密钥
     * @return     密文
     */
    public static String encrypt(String algo, String text, String key) throws Exception {
        if (AES.equalsIgnoreCase(algo)) {
            // 升级到 AES-256
            return encryptAES256(text, key);
        } else if (SHA_256.equalsIgnoreCase(algo)) {
            return SHA_256(text);
        } else {
            throw new IllegalArgumentException("不支持的加密算法：" + algo);
        }
    }

    /**
     * 解密（兼容旧版本接口）
     *
     * @param algo 算法类型 (AES)
     * @param text 密文
     * @param key  密钥
     * @return     明文
     */
    public static String decrypt(String algo, String text, String key) throws Exception {
        if (AES.equalsIgnoreCase(algo)) {
            // 升级到 AES-256
            return decryptAES256(text, key);
        } else {
            throw new IllegalArgumentException("不支持的解密算法：" + algo);
        }
    }

    /**
     * AES加密/解密（兼容旧版本接口，已废弃）
     *
     * @deprecated 请使用 encryptAES256() 和 decryptAES256()
     */
    @Deprecated
    public static String AES(int mode, String text, String key) throws Exception {
        log.warn("使用了已废弃的 AES() 方法，请升级到 encryptAES256/decryptAES256");

        if (mode == Cipher.ENCRYPT_MODE) {
            return encryptAES256(text, key);
        } else if (mode == Cipher.DECRYPT_MODE) {
            return decryptAES256(text, key);
        }
        throw new IllegalArgumentException("不支持的模式：" + mode);
    }

    /**
     * SHA-256哈希
     *
     * @param input 输入字符串
     * @return      十六进制的 SHA-256 哈希值（64个字符）
     */
    public static String SHA_256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(SHA_256);
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hashBytes);
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return      十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 验证密钥长度是否有效（增强版）
     *
     * @param key 密钥
     * @return    是否有效
     */
    public static boolean isValidAesKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        // 现在支持任意长度，会自动派生为 32 字节
        return key.length() >= 8;  // 最少 8 个字符
    }
}
