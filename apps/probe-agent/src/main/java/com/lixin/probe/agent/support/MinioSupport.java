package com.lixin.probe.agent.support;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * MinIO 操作支持类
 * 提供文件上传、下载、删除、预签名URL生成等功能
 *
 * @author probe-agent
 * @since 1.0.0
 */
@Component
public class MinioSupport {

    private static final Logger log = LoggerFactory.getLogger(MinioSupport.class);
    @Autowired
    private io.minio.MinioClient minioClient;

    @Value("${probe.minio.endpoint:}")
    private String endpoint;

    @Value("${probe.minio.public-url:}")
    private String publicUrl;

    @Value("${probe.minio.bucket-name:probe-data}")
    private String defaultBucketName;

    @Value("${probe.minio.presigned.get-expiry-minutes:10}")
    private Integer defaultPresignedGetExpiryMinutes;

    @Value("${probe.minio.presigned.put-expiry-minutes:30}")
    private Integer defaultPresignedPutExpiryMinutes;

    /**
     * MinIO 是否可用
     */
    private volatile boolean available = false;

    /**
     * 初始化 MinIO 存储桶
     */
    @PostConstruct
    private void init() {
        try {
            // 如果 endpoint 为空，说明 MinIO 未配置，优雅降级
            if (!StringUtils.hasText(endpoint)) {
                log.warn("MinIO 未配置，文件上传功能将不可用，其他功能正常运行");
                this.available = false;
                return;
            }

            Assert.hasText(defaultBucketName, "minio.bucket-name 不能为空");
            Assert.isTrue(defaultPresignedGetExpiryMinutes != null && defaultPresignedGetExpiryMinutes > 0,
                    "minio.presigned.get-expiry-minutes 必须 > 0");
            Assert.isTrue(defaultPresignedPutExpiryMinutes != null && defaultPresignedPutExpiryMinutes > 0,
                    "minio.presigned.put-expiry-minutes 必须 > 0");

            if (createBucketIfNotExists(defaultBucketName)) {
                log.info("初始化 MinIO 成功 | endpoint: {}, bucket: {}", endpoint, defaultBucketName);
                this.available = true;
            } else {
                log.warn("初始化 MinIO 失败（MinIO不可用），文件上传功能将不可用，其他功能正常运行");
                this.available = false;
            }
        } catch (Exception e) {
            log.error("初始化 MinIO 失败，文件上传功能将不可用", e);
            this.available = false;
        }
    }

    /**
     * 检查 MinIO 是否可用
     *
     * @return true=可用, false=不可用
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * 创建存储桶（如果不存在）
     *
     * @param bucketName 存储桶名称
     * @return true=成功或已存在, false=失败
     */
    public boolean createBucketIfNotExists(String bucketName) {
        Assert.hasText(bucketName, "存储桶名称不能为空");
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("创建 MinIO 存储桶成功: {}", bucketName);
            }
            return true;
        } catch (Exception e) {
            log.error("创建 MinIO 存储桶失败: {}", bucketName, e);
            return false;
        }
    }

    /**
     * 上传文件
     *
     * @param file       文件
     * @param objectName 对象名称
     * @return true=成功, false=失败
     */
    public boolean upload(File file, String objectName) {
        Assert.notNull(file, "文件不能为空");
        Assert.hasText(objectName, "对象名称不能为空");
        return upload(file, defaultBucketName, objectName);
    }

    /**
     * 上传文件到指定存储桶
     *
     * @param file       文件
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return true=成功, false=失败
     */
    public boolean upload(File file, String bucketName, String objectName) {
        Assert.notNull(file, "文件不能为空");
        Assert.hasText(bucketName, "存储桶名称不能为空");
        Assert.hasText(objectName, "对象名称不能为空");
        validateObjectName(objectName);

        if (!file.exists() || !file.isFile()) {
            log.error("文件不存在或不是文件: {}", file.getAbsolutePath());
            return false;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            long fileSize = file.length();
            String contentType = Files.probeContentType(file.toPath());
            if (!StringUtils.hasText(contentType)) {
                contentType = "application/octet-stream";
            }

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(fis, fileSize, getPartSize(fileSize))
                    .contentType(contentType)
                    .build());
            log.info("上传文件成功 | bucket: {}, object: {}, size: {}", bucketName, objectName, fileSize);
            return true;
        } catch (Exception e) {
            log.error("上传文件失败 | bucket: {}, object: {}", bucketName, objectName, e);
            return false;
        }
    }

    /**
     * 上传输入流
     *
     * @param inputStream 输入流
     * @param size        文件大小
     * @param contentType 内容类型
     * @param objectName  对象名称
     * @return true=成功, false=失败
     */
    public boolean upload(InputStream inputStream, long size, String contentType, String objectName) {
        return upload(inputStream, size, contentType, defaultBucketName, objectName);
    }

    /**
     * 上传输入流到指定存储桶
     *
     * @param inputStream 输入流
     * @param size        文件大小
     * @param contentType 内容类型
     * @param bucketName  存储桶名称
     * @param objectName  对象名称
     * @return true=成功, false=失败
     */
    public boolean upload(InputStream inputStream, long size, String contentType, String bucketName, String objectName) {
        try {
            validateObjectName(objectName);
            if (!StringUtils.hasText(contentType)) {
                contentType = "application/octet-stream";
            }

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, size, getPartSize(size))
                    .contentType(contentType)
                    .build());
            return true;
        } catch (Exception e) {
            log.error("上传文件失败 | bucket: {}, object: {}", bucketName, objectName, e);
            return false;
        }
    }

    /**
     * 下载文件
     * 注意：调用方必须负责关闭返回的 InputStream
     *
     * @param objectName 对象名称
     * @return 文件流，失败返回 null
     */
    public InputStream download(String objectName) {
        return download(defaultBucketName, objectName);
    }

    /**
     * 从指定存储桶下载文件
     * 注意：调用方必须负责关闭返回的 InputStream
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 文件流，失败返回 null
     */
    public InputStream download(String bucketName, String objectName) {
        Assert.hasText(bucketName, "存储桶名称不能为空");
        Assert.hasText(objectName, "对象名称不能为空");
        validateObjectName(objectName);
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("下载文件失败 | bucket: {}, object: {}", bucketName, objectName, e);
            return null;
        }
    }

    /**
     * 安全下载文件（自动管理 InputStream 生命周期）
     *
     * @param objectName 对象名称
     * @param consumer   处理 InputStream 的回调
     * @return true=成功, false=失败
     */
    public boolean download(String objectName, Consumer<InputStream> consumer) {
        return download(defaultBucketName, objectName, consumer);
    }

    /**
     * 从指定存储桶安全下载文件（自动管理 InputStream 生命周期）
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @param consumer   处理 InputStream 的回调
     * @return true=成功, false=失败
     */
    public boolean download(String bucketName, String objectName, Consumer<InputStream> consumer) {
        Assert.hasText(bucketName, "存储桶名称不能为空");
        Assert.hasText(objectName, "对象名称不能为空");
        validateObjectName(objectName);
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build())) {
            consumer.accept(is);
            return true;
        } catch (Exception e) {
            log.error("下载文件失败 | bucket: {}, object: {}", bucketName, objectName, e);
            return false;
        }
    }

    /**
     * 删除文件
     *
     * @param objectName 对象名称
     * @return true=成功, false=失败
     */
    public boolean delete(String objectName) {
        return delete(defaultBucketName, objectName);
    }

    /**
     * 从指定存储桶删除文件
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return true=成功, false=失败
     */
    public boolean delete(String bucketName, String objectName) {
        Assert.hasText(bucketName, "存储桶名称不能为空");
        Assert.hasText(objectName, "对象名称不能为空");
        validateObjectName(objectName);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            log.info("删除文件成功 | bucket: {}, object: {}", bucketName, objectName);
            return true;
        } catch (Exception e) {
            log.error("删除文件失败 | bucket: {}, object: {}", bucketName, objectName, e);
            return false;
        }
    }

    /**
     * 生成 PUT 预签名 URL
     *
     * @param objectName 对象名称
     * @return PUT URL
     */
    public String generatePresignedPutUrl(String objectName) {
        return generatePresignedPutUrl(defaultBucketName, objectName, defaultPresignedPutExpiryMinutes);
    }

    /**
     * 生成 PUT 预签名 URL（指定过期时间）
     *
     * @param bucketName    存储桶名称
     * @param objectName    对象名称
     * @param expiryMinutes 过期时间（分钟）
     * @return PUT URL
     */
    public String generatePresignedPutUrl(String bucketName, String objectName, int expiryMinutes) {
        Assert.hasText(bucketName, "存储桶名称不能为空");
        Assert.hasText(objectName, "对象名称不能为空");
        Assert.isTrue(expiryMinutes > 0, "过期时间必须大于 0");
        validateObjectName(objectName);
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );
            // 替换 endpoint 为 publicUrl
            if (StringUtils.hasText(publicUrl)) {
                url = url.replace(endpoint, publicUrl);
            }
            return url;
        } catch (Exception e) {
            log.error("生成 PUT 预签名 URL 失败 | bucket: {}, object: {}", bucketName, objectName, e);
            return null;
        }
    }

    /**
     * 生成 GET 预签名 URL
     *
     * @param objectName 对象名称
     * @return GET URL
     */
    public String generatePresignedGetUrl(String objectName) {
        return generatePresignedGetUrl(defaultBucketName, objectName, null, defaultPresignedGetExpiryMinutes);
    }

    /**
     * 生成 GET 预签名 URL（带文件名）
     *
     * @param objectName 对象名称
     * @param fileName   原始文件名
     * @return GET URL
     */
    public String generatePresignedGetUrl(String objectName, String fileName) {
        return generatePresignedGetUrl(defaultBucketName, objectName, fileName, defaultPresignedGetExpiryMinutes);
    }

    /**
     * 生成 GET 预签名 URL（完整参数）
     *
     * @param bucketName    存储桶名称
     * @param objectName    对象名称
     * @param fileName      原始文件名
     * @param expiryMinutes 过期时间（分钟）
     * @return GET URL
     */
    public String generatePresignedGetUrl(String bucketName, String objectName, String fileName, int expiryMinutes) {
        Assert.hasText(bucketName, "存储桶名称不能为空");
        Assert.hasText(objectName, "对象名称不能为空");
        Assert.isTrue(expiryMinutes > 0, "过期时间必须大于 0");
        validateObjectName(objectName);
        try {
            GetPresignedObjectUrlArgs.Builder argsBuilder = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectName)
                    .expiry(expiryMinutes, TimeUnit.MINUTES);

            if (StringUtils.hasText(fileName)) {
                // 对原始文件名进行 URL 编码，防止中文/空格问题
                String encodedFilename = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                        .replace("+", "%20");
                argsBuilder.extraQueryParams(Map.of(
                        "response-content-disposition",
                        "attachment; filename=\"" + encodedFilename + "\""
                ));
            }

            String url = minioClient.getPresignedObjectUrl(argsBuilder.build());
            // 替换 endpoint 为 publicUrl
            if (StringUtils.hasText(publicUrl)) {
                url = url.replace(endpoint, publicUrl);
            }
            return url;
        } catch (Exception e) {
            log.error("生成 GET 预签名 URL 失败 | bucket: {}, object: {}", bucketName, objectName, e);
            return null;
        }
    }

    /**
     * 获取文件信息
     *
     * @param objectName 对象名称
     * @return 文件信息，不存在返回 null
     */
    public StatObjectResponse getStat(String objectName) {
        return getStat(defaultBucketName, objectName);
    }

    /**
     * 获取文件信息（指定存储桶）
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 文件信息，不存在返回 null
     */
    public StatObjectResponse getStat(String bucketName, String objectName) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (ErrorResponseException e) {
            String errorCode = e.errorResponse().code();
            if ("NoSuchKey".equals(errorCode) || "NotFound".equals(errorCode)) {
                return null;
            }
            log.error("获取文件信息失败 | bucket: {}, object: {}, errorCode: {}",
                    bucketName, objectName, errorCode, e);
            return null;
        } catch (Exception e) {
            log.error("获取文件信息失败 | bucket: {}, object: {}", bucketName, objectName, e);
            return null;
        }
    }

    /**
     * 检查文件是否存在
     *
     * @param objectName 对象名称
     * @return true=存在, false=不存在
     */
    public boolean exists(String objectName) {
        return getStat(objectName) != null;
    }

    /**
     * 检查文件是否存在（指定存储桶）
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return true=存在, false=不存在
     */
    public boolean exists(String bucketName, String objectName) {
        return getStat(bucketName, objectName) != null;
    }

    /**
     * 校验对象名称
     *
     * @param objectName 对象名称
     */
    private void validateObjectName(String objectName) {
        // 非空检查
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("对象名称不能为空");
        }

        // 路径穿越防护
        if (objectName.contains("..") || objectName.startsWith("/")) {
            throw new IllegalArgumentException("非法对象名称: " + objectName);
        }

        // 连续斜杠或反斜杠
        if (objectName.contains("//") || objectName.contains("\\")) {
            throw new IllegalArgumentException("对象名称不能包含连续斜杠或反斜杠");
        }

        if (objectName.startsWith(".") || objectName.endsWith(".")) {
            throw new IllegalArgumentException("对象名称不能以 '.' 开头或结尾");
        }
        if (objectName.startsWith(" ") || objectName.endsWith(" ")) {
            throw new IllegalArgumentException("对象名称不能以空格开头或结尾");
        }

        // 长度限制
        if (objectName.getBytes(StandardCharsets.UTF_8).length > 1024) {
            throw new IllegalArgumentException("对象名称长度不能超过 1024 字节（UTF-8）");
        }

        // 控制字符检查
        if (objectName.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("对象名称不能包含控制字符");
        }
    }

    /**
     * 计算分片大小
     *
     * @param fileSizeBytes 文件大小（字节）
     * @return 分片大小（字节）
     */
    private long getPartSize(long fileSizeBytes) {
        final long KB = 1024L;
        final long MB = 1024L * KB;
        final long GB = 1024L * MB;
        final long TB = 1024L * GB;

        if (fileSizeBytes <= 32 * MB) {
            // 不分片：fileSizeBytes <= 32MB
            return -1;
        } else if (fileSizeBytes <= 128 * MB) {
            // 分片16MB：32MB < fileSizeBytes <= 128MB
            return 16 * MB;
        } else if (fileSizeBytes <= 512 * MB) {
            // 分片32MB：128MB < fileSizeBytes <= 512MB
            return 32 * MB;
        } else if (fileSizeBytes <= 2 * GB) {
            // 分片64MB：512MB < fileSizeBytes <= 2GB
            return 64 * MB;
        } else if (fileSizeBytes <= 8 * GB) {
            // 分片128MB：2GB < fileSizeBytes <= 8GB
            return 128 * MB;
        } else if (fileSizeBytes <= 32 * GB) {
            // 分片256MB：8GB < fileSizeBytes <= 32GB
            return 256 * MB;
        } else if (fileSizeBytes <= 1 * TB) {
            // 分片512MB：32GB < fileSizeBytes <= 1TB
            return 512 * MB;
        } else {
            throw new IllegalArgumentException("文件大小超过1TB，暂不支持上传");
        }
    }
}
