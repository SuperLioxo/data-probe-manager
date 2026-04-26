package com.lixin.probe.task;

import com.lixin.probe.config.AuditLogProperties;
import com.lixin.probe.entity.AuditLog;
import com.lixin.probe.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * 审计日志归档任务
 * 负责将日志导出为文件并压缩
 */
@Component
public class AuditLogArchiveTask {

    private static final Logger log = LoggerFactory.getLogger(AuditLogArchiveTask.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Autowired
    private AuditLogProperties properties;

    @Autowired
    private AuditLogService auditLogService;

    /**
     * 执行归档任务
     * @param beforeDate 归档此日期之前的日志
     * @return 归档文件路径
     */
    public String executeArchive(LocalDateTime beforeDate) {
        try {
            // 1. 查询需要归档的日志
            List<AuditLog> logsToArchive = auditLogService.getLogsByTimeRange(
                    LocalDateTime.now().minusYears(10),
                    beforeDate
            );

            if (logsToArchive.isEmpty()) {
                log.info("没有需要归档的日志");
                return null;
            }

            log.info("开始归档 {} 条审计日志", logsToArchive.size());

            // 2. 生成归档文件名
            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            String fileName = String.format("audit_log_%s.json", timestamp);

            // 3. 确保归档目录存在
            Path archiveDir = Paths.get(properties.getArchivePath());
            if (!Files.exists(archiveDir)) {
                Files.createDirectories(archiveDir);
                log.info("创建归档目录: {}", archiveDir);
            }

            // 4. 导出日志为JSON
            Path jsonFilePath = archiveDir.resolve(fileName);
            exportLogsToJson(logsToArchive, jsonFilePath);

            // 5. 如果需要压缩，则压缩文件
            if (properties.isCompressArchive()) {
                Path gzFilePath = archiveDir.resolve(fileName + ".gz");
                compressFile(jsonFilePath, gzFilePath);

                // 删除原始JSON文件
                Files.deleteIfExists(jsonFilePath);

                log.info("归档完成，文件路径: {}", gzFilePath);

                return gzFilePath.toString();
            } else {
                log.info("归档完成，文件路径: {}", jsonFilePath);
                return jsonFilePath.toString();
            }

        } catch (Exception e) {
            log.error("归档审计日志失败: {}", e.getMessage(), e);
            throw new RuntimeException("归档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 导出日志为JSON文件
     */
    private void exportLogsToJson(List<AuditLog> logs, Path filePath) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("[");

        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) {
                json.append(",\n");
            }
            json.append(logToJson(logs.get(i)));
        }

        json.append("]");

        Files.write(filePath, json.toString().getBytes("UTF-8"));
        log.info("导出 {} 条日志到文件: {}", logs.size(), filePath);
    }

    /**
     * 将单条日志转换为JSON字符串
     */
    private String logToJson(AuditLog log) {
        return String.format(
                "{\"id\":%d,\"userId\":%s,\"username\":\"%s\",\"operation\":\"%s\",\"module\":\"%s\"," +
                        "\"description\":\"%s\",\"level\":\"%s\",\"businessId\":%s,\"businessType\":\"%s\"," +
                        "\"method\":\"%s\",\"requestUrl\":\"%s\",\"requestParams\":\"%s\"," +
                        "\"responseCode\":%d,\"responseMessage\":\"%s\",\"executionTime\":%d," +
                        "\"ipAddress\":\"%s\",\"userAgent\":\"%s\",\"isException\":%s," +
                        "\"exceptionMessage\":\"%s\",\"createTime\":\"%s\"}",
                log.getId(),
                log.getUserId() != null ? log.getUserId() : "null",
                escapeJson(log.getUsername()),
                escapeJson(log.getOperation()),
                escapeJson(log.getModule()),
                escapeJson(log.getDescription()),
                log.getLevel(),
                log.getBusinessId() != null ? log.getBusinessId() : "null",
                escapeJson(log.getBusinessType()),
                escapeJson(log.getMethod()),
                escapeJson(log.getRequestUrl()),
                escapeJson(log.getRequestParams()),
                log.getResponseCode(),
                escapeJson(log.getResponseMessage()),
                log.getExecutionTime(),
                escapeJson(log.getIpAddress()),
                escapeJson(log.getUserAgent()),
                log.getIsException(),
                escapeJson(log.getExceptionMessage()),
                log.getCreateTime().toString()
        );
    }

    /**
     * 压缩文件
     */
    private void compressFile(Path sourceFile, Path targetFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(sourceFile.toFile());
             FileOutputStream fos = new FileOutputStream(targetFile.toFile());
             GZIPOutputStream gzipOS = new GZIPOutputStream(fos)) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                gzipOS.write(buffer, 0, len);
            }
        }

        log.info("压缩文件完成: {} -> {}", sourceFile, targetFile);
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }

        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 清理旧的归档文件
     * @param retainDays 保留天数
     */
    public void cleanupOldArchives(int retainDays) {
        try {
            Path archiveDir = Paths.get(properties.getArchivePath());
            if (!Files.exists(archiveDir)) {
                return;
            }

            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retainDays);

            Files.list(archiveDir)
                    .filter(Files::isRegularFile)
                    .filter(file -> {
                        try {
                            LocalDateTime fileTime = Files.getLastModifiedTime(file)
                                    .toInstant()
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDateTime();
                            return fileTime.isBefore(cutoffTime);
                        } catch (IOException e) {
                            log.warn("检查文件修改时间失败: {}", file, e);
                            return false;
                        }
                    })
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                            log.info("删除旧归档文件: {}", file);
                        } catch (IOException e) {
                            log.warn("删除归档文件失败: {}", file, e);
                        }
                    });

        } catch (IOException e) {
            log.error("清理旧归档文件失败: {}", e.getMessage(), e);
        }
    }
}
