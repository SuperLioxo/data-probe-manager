package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.FileMetadata;
import com.lixin.probe.service.FileMetadataService;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.util.ValidationUtil;
import com.lixin.probe.websocket.MetaProbeWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 文件元数据Controller
 */
@RestController
@RequestMapping("/api/file-metadata")
public class FileMetadataController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileMetadataController.class);

    @Autowired
    private FileMetadataService fileMetadataService;

    @Autowired
    private MetaProbeWebSocketHandler metaProbeWebSocketHandler;

    @Autowired
    private com.lixin.probe.service.ProbeService probeService;

    /**
     * 分页查询文件列表
     */
    @GetMapping("/files")
    public Result<Page<FileMetadata>> getPage(
            @RequestParam Long probeId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String parentPath,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String extension) {

        Result<Void> error = ValidationUtil.validateId(probeId, "探针ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        error = ValidationUtil.validatePageParams(pageNum, pageSize);
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(() -> {
            return fileMetadataService.getPage(
                pageNum, pageSize, probeId, parentPath, fileName, extension);
        }, "查询文件列表失败");
    }

    /**
     * 获取文件树（按路径查询）
     */
    @GetMapping("/tree")
    public Result<List<FileMetadata>> getFileTree(
            @RequestParam Long probeId,
            @RequestParam(required = false) String parentPath) {

        Result<Void> error = ValidationUtil.validateId(probeId, "探针ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(() -> {
            Page<FileMetadata> page = fileMetadataService.listByPath(probeId, parentPath, 1, 1000);
            return page.getRecords();
        }, "获取文件树失败");
    }

    /**
     * 文件搜索
     */
    @PostMapping("/search")
    public Result<Page<FileMetadata>> search(@RequestBody Map<String, Object> params) {
        Long probeId = getLong(params, "probeId");
        Integer pageNum = getInt(params, "pageNum", 1);
        Integer pageSize = getInt(params, "pageSize", 20);
        String fileName = (String) params.get("fileName");
        String fileExtension = (String) params.get("extension");
        Long minSize = getLong(params, "minSize");
        Long maxSize = getLong(params, "maxSize");

        Result<Void> error = ValidationUtil.validateId(probeId, "探针ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        error = ValidationUtil.validatePageParams(pageNum, pageSize);
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(() -> {
            return fileMetadataService.search(
                probeId, fileName, fileExtension, minSize, maxSize, pageNum, pageSize);
        }, "文件搜索失败");
    }

    /**
     * 获取文件详情
     */
    @GetMapping("/{id}")
    public Result<FileMetadata> getById(@PathVariable Long id) {
        Result<Void> error = ValidationUtil.validateId(id, "文件ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(() -> {
            FileMetadata metadata = fileMetadataService.getById(id);
            if (metadata == null) {
                throw new IllegalArgumentException("文件不存在");
            }
            return metadata;
        }, "查询文件详情失败");
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(@RequestParam Long probeId) {
        Result<Void> error = ValidationUtil.validateId(probeId, "探针ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(
                () -> fileMetadataService.getStatistics(probeId),
                "获取统计信息失败"
        );
    }

    /**
     * 下载文件 - 从Agent代理下载
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id) {
        try {
            FileMetadata metadata = fileMetadataService.getById(id);
            if (metadata == null) {
                log.error("文件不存在: fileId={}", id);
                return ResponseEntity.notFound().build();
            }

            log.info("开始下载文件: fileId={}, fileName={}, filePath={}",
                    id, metadata.getFileName(), metadata.getFilePath());

            // 从probeKey获取探针信息（包含host和port）
            String agentUrl = getAgentUrlByProbeKey(metadata.getProbeKey());
            if (agentUrl == null) {
                log.error("无法获取Agent URL: probeKey={}", metadata.getProbeKey());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }

            // 构建Agent下载URL
            String agentDownloadUrl = String.format("%s/api/files/download?filePath=%s",
                    agentUrl,
                    java.net.URLEncoder.encode(metadata.getFilePath(), "UTF-8"));

            log.info("从Agent下载文件: url={}", agentDownloadUrl);

            // 使用HTTP客户端从Agent下载文件
            java.net.HttpURLConnection connection = null;
            try {
                java.net.URL url = new java.net.URL(agentDownloadUrl);
                connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpStatus.OK.value()) {
                    log.error("Agent返回错误: code={}", responseCode);
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
                }

                // 读取文件内容
                byte[] fileContent = connection.getInputStream().readAllBytes();

                // 获取Content-Type
                String contentType = connection.getContentType();
                if (contentType == null) {
                    contentType = determineContentType(metadata.getFileName());
                }

                // 获取Content-Disposition（如果Agent设置了）
                String contentDisposition = connection.getHeaderField("Content-Disposition");
                if (contentDisposition == null) {
                    contentDisposition = "attachment; filename=\"" + metadata.getFileName() + "\"";
                }

                log.info("文件下载成功: size={}, type={}", fileContent.length, contentType);

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                        .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                        .body(fileContent);

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

        } catch (java.net.SocketTimeoutException e) {
            log.error("下载文件超时: fileId={}", id, e);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
        } catch (Exception e) {
            log.error("下载文件失败: fileId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 从probeKey提取Agent code
     * 支持多种格式:
     * - AGENT-{type}-{random} -> AGENT (如: AGENT-file-xxx)
     * - {AGENT-CODE}-{type}-{random} -> {AGENT-CODE} (如: TEST-AGENT-001-file-xxx)
     *
     * @param probeKey 探针key
     * @return Agent代码
     */
    private String extractAgentCode(String probeKey) {
        if (probeKey == null || probeKey.isEmpty()) {
            throw new IllegalArgumentException("探针Key不能为空");
        }

        String[] parts = probeKey.split("-", 3);
        if (parts.length < 3) {
            throw new IllegalArgumentException("无效的探针Key格式，无法提取Agent code: " + probeKey);
        }

        // 探针类型关键字
        String[] PROBE_TYPES = {"file", "database", "system", "http", "ping", "port"};

        String agentCode;
        // 检查第一部分是否为"AGENT"或包含"AGENT"
        if (parts[0].equals("AGENT")) {
            // 传统格式: AGENT-{type}-{random}
            agentCode = "AGENT";
        } else if (parts[0].contains("AGENT") || parts[0].contains("agent")) {
            // 新格式: {AGENT-CODE}-{type}-{random} (如: TEST-AGENT-001-file-xxx)
            // 检查第二部分是否为探针类型
            if (isProbeType(parts[1], PROBE_TYPES)) {
                // 格式: {AGENT-CODE}-{type}-{random} → Agent code是第一部分
                agentCode = parts[0];
            } else {
                // 格式: {AGENT-CODE}-{sub-code}-{type}-{random} → Agent code是前两部分
                agentCode = parts[0] + "-" + parts[1];
            }
        } else {
            throw new IllegalArgumentException("探针Key格式错误，无法提取Agent code: " + probeKey);
        }

        return agentCode;
    }

    /**
     * 检查字符串是否为探针类型
     */
    private boolean isProbeType(String str, String[] probeTypes) {
        for (String type : probeTypes) {
            if (type.equalsIgnoreCase(str)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据probeKey获取Agent的URL
     * 从probe表查询host和port
     */
    private String getAgentUrlByProbeKey(String probeKey) {
        try {
            // 从probe表获取探针信息
            var probe = probeService.getByProbeKey(probeKey);
            if (probe == null) {
                log.warn("探针不存在: probeKey={}", probeKey);
                return "http://localhost:58081";  // 默认地址
            }

            String host = probe.getHostIp();
            Integer port = probe.getPort();

            if (host == null || host.isEmpty()) {
                host = "localhost";
            }
            if (port == null) {
                port = 58081;
            }

            String agentUrl = String.format("http://%s:%d", host, port);
            log.info("从probe表获取Agent URL: probeKey={}, url={}", probeKey, agentUrl);
            return agentUrl;

        } catch (Exception e) {
            log.error("获取Agent URL失败，使用默认地址: probeKey={}", probeKey, e);
            return "http://localhost:58081";
        }
    }

    /**
     * 获取Agent的URL
     * 从WebSocket连接中提取host和port
     */
    private String getAgentUrl(String agentCode) {
        try {
            // 获取Agent的WebSocket会话
            WebSocketSession session = metaProbeWebSocketHandler.getSessionByCode(agentCode);

            String scheme = "http";  // Agent使用HTTP协议
            String host = "localhost";
            int port = 58081;  // Agent默认HTTP端口

            if (session != null && session.isOpen()) {
                // 从WebSocket URI提取host（用于生产环境）
                URI wsUri = session.getUri();
                if (wsUri != null && wsUri.getHost() != null) {
                    host = wsUri.getHost();
                    log.debug("从WebSocket会话提取host: {}", host);
                }
            } else {
                log.warn("Agent WebSocket会话不存在，使用默认地址: {}", agentCode);
            }

            // 构建Agent的HTTP URL
            String agentUrl = String.format("%s://%s:%d", scheme, host, port);
            log.info("Agent URL: {}", agentUrl);
            return agentUrl;

        } catch (Exception e) {
            log.error("获取Agent URL失败: agentCode={}，使用默认地址", agentCode, e);
            // 返回默认地址
            return "http://localhost:58081";
        }
    }

    /**
     * 根据文件名确定Content-Type
     */
    private String determineContentType(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }

        String extension = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            extension = filename.substring(dotIndex).toLowerCase();
        }

        switch (extension) {
            case ".pdf":
                return "application/pdf";
            case ".txt":
                return "text/plain; charset=utf-8";
            case ".jpg":
            case ".jpeg":
                return "image/jpeg";
            case ".png":
                return "image/png";
            case ".gif":
                return "image/gif";
            case ".log":
                return "text/plain; charset=utf-8";
            case ".zip":
                return "application/zip";
            case ".json":
                return "application/json";
            case ".xml":
                return "application/xml";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteFile(@PathVariable Long id) {
        Result<Void> error = ValidationUtil.validateId(id, "文件ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeExecute(
                () -> {
                    FileMetadata metadata = fileMetadataService.getById(id);
                    if (metadata == null) {
                        throw new IllegalArgumentException("文件不存在");
                    }

                    // 删除物理文件
                    java.io.File file = new java.io.File(metadata.getFilePath());
                    if (file.exists()) {
                        boolean deleted = file.delete();
                        if (!deleted) {
                            log.warn("删除物理文件失败: {}", metadata.getFilePath());
                        }
                    }

                    // 删除数据库记录（逻辑删除）
                    fileMetadataService.delete(id);
                },
                "删除文件成功",
                "删除文件失败"
        );
    }

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
     * 从Map中获取Int值
     */
    private Integer getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
}
