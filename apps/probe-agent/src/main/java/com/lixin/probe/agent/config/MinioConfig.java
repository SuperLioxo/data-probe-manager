package com.lixin.probe.agent.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 配置类
 * 用于配置 MinIO 对象存储服务
 *
 * @author probe-agent
 * @since 1.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "probe.minio")
public class MinioConfig {

    /**
     * MinIO 服务端点
     * 例如: http://localhost:9000
     */
    private String endpoint;

    /**
     * MinIO 公网访问URL
     * 用于生成预签名URL时替换内网地址
     * 例如: http://your-domain.com
     */
    private String publicUrl;

    /**
     * MinIO 访问密钥（Access Key）
     */
    private String accessKey;

    /**
     * MinIO 密钥（Secret Key）
     */
    private String secretKey;

    /**
     * 默认存储桶名称
     */
    private String bucketName = "probe-data";

    /**
     * 预签名URL配置
     */
    private Presigned presigned = new Presigned();

    // Manual getters for Lombok compatibility
    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public Presigned getPresigned() {
        return presigned;
    }

    public void setPresigned(Presigned presigned) {
        this.presigned = presigned;
    }

    /**
     * 预签名URL配置
     */
    public static class Presigned {
        /**
         * GET 预签名URL过期时间（分钟）
         */
        private Integer getExpiryMinutes = 10;

        /**
         * PUT 预签名URL过期时间（分钟）
         */
        private Integer putExpiryMinutes = 30;

        public Integer getGetExpiryMinutes() { return getExpiryMinutes; }
        public void setGetExpiryMinutes(Integer getExpiryMinutes) { this.getExpiryMinutes = getExpiryMinutes; }
        public Integer getPutExpiryMinutes() { return putExpiryMinutes; }
        public void setPutExpiryMinutes(Integer putExpiryMinutes) { this.putExpiryMinutes = putExpiryMinutes; }
    }

    /**
     * 创建 MinIO 客户端 Bean
     *
     * @return MinioClient 实例
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
