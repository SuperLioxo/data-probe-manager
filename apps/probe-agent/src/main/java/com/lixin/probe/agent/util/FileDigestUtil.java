package com.lixin.probe.agent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 文件摘要工具类
 * 提供文件 MD5、SHA-256 等哈希计算功能
 *
 * @author probe-agent
 * @since 1.0.0
 */
public class FileDigestUtil {

    private static final Logger log = LoggerFactory.getLogger(FileDigestUtil.class);
    private static final int BUFFER_SIZE = 8192; // 8KB 缓冲区
    private static final int MAX_FILE_SIZE_FOR_MD5 = 100 * 1024 * 1024; // 100MB 限制

    /**
     * 计算文件的 MD5 哈希值
     *
     * @param file 文件
     * @return MD5 哈希值（32位小写十六进制字符串），失败返回 null
     */
    public static String calculateMD5(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            log.warn("文件不存在或不是文件: {}", file);
            return null;
        }

        // 检查文件大小
        long fileSize = file.length();
        if (fileSize > MAX_FILE_SIZE_FOR_MD5) {
            log.warn("文件过大，跳过 MD5 计算: {} ({} bytes)", file.getAbsolutePath(), fileSize);
            return null;
        }

        FileInputStream fis = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            fis = new FileInputStream(file);

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                // 每 10MB 打印一次进度
                if (totalBytesRead % (10 * 1024 * 1024) == 0) {
                    log.debug("MD5 计算进度: {}/{} bytes", totalBytesRead, fileSize);
                }
            }

            byte[] digest = md.digest();
            return bytesToHex(digest);

        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 算法不可用", e);
            return null;
        } catch (IOException e) {
            log.error("读取文件失败，无法计算 MD5: {}", file.getAbsolutePath(), e);
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    log.error("关闭文件流失败", e);
                }
            }
        }
    }

    /**
     * 计算文件的 SHA-256 哈希值
     *
     * @param file 文件
     * @return SHA-256 哈希值（64位小写十六进制字符串），失败返回 null
     */
    public static String calculateSHA256(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            log.warn("文件不存在或不是文件: {}", file);
            return null;
        }

        FileInputStream fis = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            fis = new FileInputStream(file);

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }

            byte[] digest = md.digest();
            return bytesToHex(digest);

        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 算法不可用", e);
            return null;
        } catch (IOException e) {
            log.error("读取文件失败，无法计算 SHA-256: {}", file.getAbsolutePath(), e);
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    log.error("关闭文件流失败", e);
                }
            }
        }
    }

    /**
     * 字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串（小写）
     */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 快速计算小文件的 MD5（使用更小的缓冲区）
     *
     * @param file 文件
     * @return MD5 哈希值
     */
    public static String quickMD5(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }

        // 对于小文件（< 1MB），使用一次性读取
        if (file.length() < 1024 * 1024) {
            try {
                byte[] content = java.nio.file.Files.readAllBytes(file.toPath());
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(content);
                return bytesToHex(digest);
            } catch (Exception e) {
                log.error("快速 MD5 计算失败", e);
                return null;
            }
        }

        // 大文件使用标准方法
        return calculateMD5(file);
    }

    /**
     * 计算字符串的 MD5
     *
     * @param text 字符串
     * @return MD5 哈希值
     */
    public static String calculateMD5(String text) {
        if (text == null) {
            return null;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes("UTF-8"));
            return bytesToHex(digest);
        } catch (Exception e) {
            log.error("计算字符串 MD5 失败", e);
            return null;
        }
    }

    /**
     * 验证文件的 MD5 是否匹配
     *
     * @param file       文件
     * @param expectedMD5 期望的 MD5 值
     * @return true=匹配, false=不匹配
     */
    public static boolean verifyMD5(File file, String expectedMD5) {
        String actualMD5 = calculateMD5(file);
        if (actualMD5 == null) {
            return false;
        }
        return actualMD5.equalsIgnoreCase(expectedMD5);
    }
}
