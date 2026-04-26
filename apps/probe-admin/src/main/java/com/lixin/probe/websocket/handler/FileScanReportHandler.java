package com.lixin.probe.websocket.handler;

import com.lixin.probe.entity.FileMetadata;
import com.lixin.probe.entity.FileProbe;
import com.lixin.probe.service.FileMetadataService;
import com.lixin.probe.service.FileProbeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件扫描报告消息处理器
 * 处理类型为FILE_SCAN_REPORT的消息
 */
@Component
public class FileScanReportHandler implements MessageHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileScanReportHandler.class);

    @Autowired(required = false)
    private FileProbeService fileProbeService;

    @Autowired(required = false)
    private FileMetadataService fileMetadataService;

    @Autowired
    private com.lixin.probe.service.ProbeStatusValidationService statusValidationService;

    @Override
    public boolean canHandle(String type, String cmd) {
        return "FILE_SCAN_REPORT".equals(cmd);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(WebSocketSession session, String probeKey, String type, String cmd, Object payload) throws Exception {
        try {
            if (!(payload instanceof Map)) {
                log.warn("文件扫描报告格式错误");
                return;
            }

            Map<String, Object> data = (Map<String, Object>) payload;
            if (data == null) {
                log.warn("文件扫描报告缺少data字段");
                return;
            }

            String reportProbeKey = (String) data.get("probeKey");

            // 验证探针是否在线
            if (reportProbeKey != null && !statusValidationService.isProbeOnline(reportProbeKey)) {
                log.warn("拒绝离线文件探针的扫描报告: probeKey={}", reportProbeKey);
                return;
            }

            Long totalFileCount = getLong(data, "totalFileCount");
            Long totalDirectoryCount = getLong(data, "totalDirectoryCount");
            Long totalSize = getLong(data, "totalSize");

            log.info("收到文件扫描报告: probeKey={}, files={}, directories={}, size={}",
                reportProbeKey, totalFileCount, totalDirectoryCount, totalSize);

            // 更新探针统计信息
            if (reportProbeKey != null && fileProbeService != null) {
                fileProbeService.updateScanStatistics(reportProbeKey, totalFileCount, totalDirectoryCount, totalSize);

                // 批量保存文件元数据
                List<FileMetadata> fileList = extractFiles(data, reportProbeKey);
                if (!fileList.isEmpty() && fileMetadataService != null) {
                    try {
                        // 先删除该探针的旧数据，避免重复
                        FileProbe probe = fileProbeService.getByProbeKey(reportProbeKey);
                        if (probe != null) {
                            fileMetadataService.deleteByProbeId(probe.getId());
                            log.info("已删除探针的旧文件数据: probeKey={}, probeId={}", reportProbeKey, probe.getId());
                        }

                        fileMetadataService.batchSave(fileList);
                        log.info("批量保存文件元数据成功: {} 条", fileList.size());
                    } catch (Exception e) {
                        log.error("批量保存文件元数据失败", e);
                        throw new Exception("扫描报告已接收，但保存文件元数据失败: " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.error("处理文件扫描报告失败: probeKey={}", probeKey, e);
            throw e;
        }
    }

    @Override
    public String getHandlerName() {
        return "FileScanReportHandler";
    }

    // ========== 私有辅助方法 ==========

    /**
     * 从Map中获取Long值
     */
    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    /**
     * 从Map中获取Integer值
     */
    private Integer getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    /**
     * 从扫描报告中提取文件列表
     */
    @SuppressWarnings("unchecked")
    private List<FileMetadata> extractFiles(Map<String, Object> data, String probeKey) {
        List<FileMetadata> fileList = new ArrayList<>();

        try {
            // 获取probeId
            if (fileProbeService == null) {
                log.warn("FileProbeService未注入，无法提取文件列表");
                return fileList;
            }

            FileProbe probe = fileProbeService.getByProbeKey(probeKey);
            if (probe == null) {
                log.warn("探针不存在: probeKey={}", probeKey);
                return fileList;
            }
            Long probeId = probe.getId();

            // 提取files和directories
            List<Map<String, Object>> files = (List<Map<String, Object>>) data.get("files");
            List<Map<String, Object>> directories = (List<Map<String, Object>>) data.get("directories");

            // 处理文件列表
            if (files != null) {
                for (Map<String, Object> fileData : files) {
                    FileMetadata file = FileMetadata.builder()
                        .probeId(probeId)
                        .probeKey(probeKey)
                        .fileName((String) fileData.get("name"))
                        .filePath((String) fileData.get("path"))
                        .fileSize(getLong(fileData, "size"))
                        .fileExtension((String) fileData.get("extension"))
                        .fileMd5((String) fileData.get("md5"))
                        .fileType("FILE")
                        .parentPath((String) fileData.get("parentPath"))
                        .lastModified(getLong(fileData, "lastModified"))
                        .isDeleted(false)
                        .build();
                    fileList.add(file);
                }
            }

            // 处理目录列表
            if (directories != null) {
                for (Map<String, Object> dirData : directories) {
                    FileMetadata dir = FileMetadata.builder()
                        .probeId(probeId)
                        .probeKey(probeKey)
                        .fileName((String) dirData.get("name"))
                        .filePath((String) dirData.get("path"))
                        .fileSize(0L)
                        .fileExtension(null)
                        .fileMd5(null)
                        .fileType("DIRECTORY")
                        .parentPath((String) dirData.get("parentPath"))
                        .depth(getInt(dirData, "depth"))
                        .lastModified(getLong(dirData, "lastModified"))
                        .isDeleted(false)
                        .build();
                    fileList.add(dir);
                }
            }

        } catch (Exception e) {
            log.error("提取文件列表失败", e);
        }

        return fileList;
    }
}
