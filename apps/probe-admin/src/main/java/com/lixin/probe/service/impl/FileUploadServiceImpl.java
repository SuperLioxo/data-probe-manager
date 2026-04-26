package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.FileMetadata;
import com.lixin.probe.entity.AuditLog;
import com.lixin.probe.mapper.FileMetadataMapper;
import com.lixin.probe.service.AggregationService;
import com.lixin.probe.service.AuditLogService;
import com.lixin.probe.service.FileUploadService;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {

    @Autowired
    private FileMetadataMapper fileMetadataMapper;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    @Autowired(required = false)
    private AggregationService aggregationService;

    @Autowired
    private com.lixin.probe.service.FileProbeService fileProbeService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${file.storage.type:local}")
    private String storageType;

    @Autowired(required = false)
    private MinioClient minioClient;

    @Value("${file.storage.minio.bucket:probe-files}")
    private String minioBucket;

    @Value("${file.storage.minio.presigned-expiry:3600}")
    private int presignedExpiry;

    private volatile boolean minioAvailable = false;

    @Autowired
    public void checkMinio() {
        if (minioClient != null) {
            try {
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioBucket).build());
                }
                minioAvailable = true;
                log.info("[文件上传] MinIO 已就绪, bucket={}", minioBucket);
            } catch (Exception e) {
                log.warn("[文件上传] MinIO 不可用: {}", e.getMessage());
                minioAvailable = false;
            }
        }
    }

    @Override
    public FileMetadata uploadFile(MultipartFile file, String probeKey, String category) {
        try {
            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            String storedName = UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "_" + System.currentTimeMillis() + extension;

            // 先保存到本地
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path localPath = uploadPath.resolve(storedName);
            file.transferTo(localPath.toFile());

            String filePath = localPath.toString();
            String storageLocation = "local";

            // 如果 MinIO 可用，异步上传
            if (minioAvailable && !"local".equals(storageType)) {
                try {
                    String objectName = (probeKey != null ? probeKey + "/" : "") + storedName;
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
                    filePath = "minio://" + minioBucket + "/" + objectName;
                    storageLocation = "minio";
                    log.info("[文件上传] MinIO 上传成功: {}", objectName);
                } catch (Exception e) {
                    log.warn("[文件上传] MinIO 上传失败, 使用本地存储: {}", e.getMessage());
                    storageLocation = "local";
                }
            }

            String md5 = computeMD5(localPath.toFile());

            // Resolve probeId from probeKey
            Long probeId = 0L;
            if (probeKey != null && !probeKey.isEmpty()) {
                try {
                    com.lixin.probe.entity.FileProbe fp = fileProbeService.getByProbeKey(probeKey);
                    if (fp != null) probeId = fp.getId();
                } catch (Exception ignored) {}
            }

            FileMetadata metadata = FileMetadata.builder()
                    .probeId(probeId)
                    .probeKey(probeKey)
                    .fileName(originalName)
                    .filePath(filePath)
                    .fileSize(file.getSize())
                    .fileExtension(extension.replace(".", "").toLowerCase())
                    .fileMd5(md5)
                    .fileType("FILE")
                    .lastModified(System.currentTimeMillis())
                    .depth(0)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDeleted(false)
                    .build();

            fileMetadataMapper.insert(metadata);
            log.info("[文件上传] file={}, size={}, location={}", originalName, file.getSize(), storageLocation);

            // 注册到汇聚库
            if (aggregationService != null) {
                try {
                    String aggTable = null;
                    if (extension.matches("\\.(xlsx?|csv)$")) {
                        aggTable = "imported_" + originalName.replace(extension, "").replaceAll("[^a-zA-Z0-9_]", "_");
                    }
                    aggregationService.registerFile(null, originalName, filePath, file.getSize(),
                            extension.replace(".", "").toLowerCase(), md5, storageLocation, aggTable, null);
                } catch (Exception e) {
                    log.warn("[文件上传] 汇聚注册失败: {}", e.getMessage());
                }
            }

            // 记录审计日志
            if (auditLogService != null) {
                try {
                    AuditLog auditLog = AuditLog.builder()
                            .operation("FILE_UPLOAD")
                            .module("FILE_UPLOAD")
                            .businessType("FileMetadata")
                            .businessId(metadata.getId())
                            .description("上传文件: " + originalName)
                            .requestParams("{\"fileName\":\"" + originalName + "\",\"fileSize\":" + file.getSize() + ",\"probeKey\":\"" + probeKey + "\",\"md5\":\"" + md5 + "\"}")
                            .status("SUCCESS")
                            .createTime(LocalDateTime.now())
                            .build();
                    auditLogService.createAsync(auditLog);
                } catch (Exception e) {
                    log.warn("[文件上传] 审计日志记录失败: {}", e.getMessage());
                }
            }

            return metadata;
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Page<FileMetadata> getFileList(String probeKey, String fileName, int pageNum, int pageSize) {
        Page<FileMetadata> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FileMetadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileMetadata::getIsDeleted, false);
        if (probeKey != null && !probeKey.isEmpty()) {
            wrapper.eq(FileMetadata::getProbeKey, probeKey);
        }
        if (fileName != null && !fileName.isEmpty()) {
            wrapper.like(FileMetadata::getFileName, fileName);
        }
        wrapper.orderByDesc(FileMetadata::getCreateTime);
        return fileMetadataMapper.selectPage(page, wrapper);
    }

    @Override
    public void deleteFile(Long id) {
        FileMetadata metadata = fileMetadataMapper.selectById(id);
        if (metadata != null) {
            // 删除 MinIO 对象
            if (metadata.getFilePath() != null && metadata.getFilePath().startsWith("minio://")) {
                try {
                    String objectName = metadata.getFilePath().replace("minio://" + minioBucket + "/", "");
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(objectName)
                            .build());
                } catch (Exception e) {
                    log.warn("[文件删除] MinIO 删除失败: {}", e.getMessage());
                }
            }

            // 删除本地文件
            if (metadata.getFilePath() != null && !metadata.getFilePath().startsWith("minio://")) {
                try {
                    File file = new File(metadata.getFilePath());
                    if (file.exists()) file.delete();
                } catch (Exception e) {
                    log.warn("[文件删除] 本地删除失败: {}", e.getMessage());
                }
            }

            metadata.setIsDeleted(true);
            metadata.setUpdateTime(LocalDateTime.now());
            fileMetadataMapper.updateById(metadata);
        }
    }

    @Override
    public Map<String, Object> getStatistics(String probeKey) {
        Map<String, Object> stats = new LinkedHashMap<>();

        LambdaQueryWrapper<FileMetadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileMetadata::getIsDeleted, false);
        if (probeKey != null && !probeKey.isEmpty()) {
            wrapper.eq(FileMetadata::getProbeKey, probeKey);
        }

        long totalCount = fileMetadataMapper.selectCount(wrapper);
        stats.put("totalFiles", totalCount);

        List<FileMetadata> files = fileMetadataMapper.selectList(wrapper);
        long totalSize = 0;
        Set<String> extensions = new HashSet<>();
        long minioCount = 0;
        for (FileMetadata f : files) {
            if (f.getFileSize() != null) totalSize += f.getFileSize();
            if (f.getFileExtension() != null) extensions.add(f.getFileExtension());
            if (f.getFilePath() != null && f.getFilePath().startsWith("minio://")) minioCount++;
        }
        stats.put("totalSize", totalSize);
        stats.put("totalSizeReadable", formatBytes(totalSize));
        stats.put("fileTypes", extensions);
        stats.put("minioFiles", minioCount);
        stats.put("localFiles", totalCount - minioCount);
        stats.put("storageType", storageType);

        return stats;
    }

    public String getDownloadUrl(Long id) {
        FileMetadata metadata = fileMetadataMapper.selectById(id);
        if (metadata == null || metadata.getIsDeleted()) return null;

        if (metadata.getFilePath() != null && metadata.getFilePath().startsWith("minio://") && minioAvailable) {
            try {
                String objectName = metadata.getFilePath().replace("minio://" + minioBucket + "/", "");
                return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(io.minio.http.Method.GET)
                        .bucket(minioBucket)
                        .object(objectName)
                        .expiry(presignedExpiry)
                        .build());
            } catch (Exception e) {
                log.warn("[文件下载] MinIO presigned URL 生成失败: {}", e.getMessage());
            }
        }

        // fallback: 本地文件路径
        return "/api/files/" + id + "/download";
    }

    private String computeMD5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[文件上传] MD5 计算失败: {}", e.getMessage());
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.1f GB", bytes / 1073741824.0);
    }
}

@Configuration
class MinioConfig {

    @Bean
    @ConditionalOnProperty(name = "file.storage.type", havingValue = "minio")
    public MinioClient minioClient(
            @Value("${file.storage.minio.endpoint}") String endpoint,
            @Value("${file.storage.minio.access-key}") String accessKey,
            @Value("${file.storage.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
