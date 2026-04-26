package com.lixin.probe.agent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件服务控制器
 * 提供文件下载功能
 *
 * @author probe-agent
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    /**
     * 允许访问的根目录白名单
     * 默认只允许访问应用的工作目录和日志目录
     */
    private static final List<String> ALLOWED_BASE_DIRS = Arrays.asList(
            System.getProperty("user.dir"),
            System.getProperty("user.home") + "/logs",
            "/var/log/probe",
            "/tmp/probe"
    );

    /**
     * 检查路径是否在允许的目录内
     */
    private boolean isPathAllowed(Path path) {
        Path normalizedPath = path.normalize();

        for (String allowedDir : ALLOWED_BASE_DIRS) {
            try {
                Path allowedPath = Paths.get(allowedDir).normalize();
                if (normalizedPath.startsWith(allowedPath)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("检查允许目录时出错: {}", allowedDir, e);
            }
        }

        log.warn("文件路径不在允许的目录内: {}", path);
        return false;
    }

    /**
     * 下载文件
     * GET /api/files/download
     *
     * @param filePath 文件路径（绝对路径或相对于应用根目录的路径）
     * @return 文件内容
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFile(@RequestParam String filePath) {
        try {
            // 记录文件下载请求
            log.info("文件下载请求: filePath={}", filePath);

            // 解析文件路径
            Path path = Paths.get(filePath).normalize();

            // 如果是相对路径，转换为绝对路径
            if (!path.isAbsolute()) {
                path = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
            }

            // 安全检查：验证路径是否在允许的目录内
            if (!isPathAllowed(path)) {
                log.warn("文件下载被拒绝：路径不在允许的目录内: {}", path);
                return ResponseEntity.status(403).build();
            }

            File file = path.toFile();

            // 检查文件是否存在
            if (!file.exists()) {
                log.warn("文件不存在: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // 检查是否为文件（不是目录）
            if (!file.isFile()) {
                log.warn("路径不是文件: {}", filePath);
                return ResponseEntity.badRequest().build();
            }

            // 读取文件内容
            byte[] fileContent = Files.readAllBytes(path);

            // 确定文件名
            String filename = file.getName();

            // 确定Content-Type
            String contentType = determineContentType(filename);

            log.info("下载文件: path={}, size={}, type={}", filePath, fileContent.length, contentType);

            // 构建响应
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(fileContent.length)
                    .body(fileContent);

        } catch (IOException e) {
            log.error("读取文件失败: filePath={}", filePath, e);
            return ResponseEntity.internalServerError().build();
        } catch (SecurityException e) {
            log.error("无权限访问文件: filePath={}", filePath, e);
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("下载文件失败: filePath={}", filePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取文件信息
     * GET /api/files/info
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getFileInfo(@RequestParam String filePath) {
        Map<String, Object> info = new HashMap<>();

        try {
            Path path = Paths.get(filePath).normalize();

            if (!path.startsWith("/")) {
                path = Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
            }

            File file = path.toFile();

            if (!file.exists()) {
                info.put("exists", false);
                return ResponseEntity.notFound().build();
            }

            info.put("exists", true);
            info.put("name", file.getName());
            info.put("path", file.getAbsolutePath());
            info.put("size", file.length());
            info.put("isFile", file.isFile());
            info.put("isDirectory", file.isDirectory());
            info.put("lastModified", file.lastModified());

            return ResponseEntity.ok(info);

        } catch (Exception e) {
            log.error("获取文件信息失败: filePath={}", filePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据文件扩展名确定Content-Type
     */
    private String determineContentType(String filename) {
        if (filename == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String extension = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            extension = filename.substring(dotIndex).toLowerCase();
        }

        switch (extension) {
            // 文档类型
            case ".pdf":
                return "application/pdf";
            case ".doc":
            case ".docx":
                return "application/vnd.ms-word";
            case ".xls":
            case ".xlsx":
                return "application/vnd.ms-excel";
            case ".ppt":
            case ".pptx":
                return "application/vnd.ms-powerpoint";
            case ".txt":
                return "text/plain; charset=utf-8";
            case ".csv":
                return "text/csv";

            // 图片类型
            case ".jpg":
            case ".jpeg":
                return "image/jpeg";
            case ".png":
                return "image/png";
            case ".gif":
                return "image/gif";
            case ".bmp":
                return "image/bmp";
            case ".svg":
                return "image/svg+xml";
            case ".webp":
                return "image/webp";

            // 音频类型
            case ".mp3":
                return "audio/mpeg";
            case ".wav":
                return "audio/wav";
            case ".ogg":
                return "audio/ogg";

            // 视频类型
            case ".mp4":
                return "video/mp4";
            case ".avi":
                return "video/x-msvideo";
            case ".mkv":
                return "video/x-matroska";
            case ".mov":
                return "video/quicktime";

            // 压缩文件
            case ".zip":
                return "application/zip";
            case ".rar":
                return "application/vnd.rar";
            case ".7z":
                return "application/x-7z-compressed";
            case ".tar":
                return "application/x-tar";
            case ".gz":
                return "application/gzip";

            // 代码类型
            case ".json":
                return "application/json";
            case ".xml":
                return "application/xml";
            case ".html":
                return "text/html";
            case ".css":
                return "text/css";
            case ".js":
                return "application/javascript";
            case ".java":
                return "text/x-java-source";
            case ".py":
                return "text/x-python";

            // 日志文件
            case ".log":
                return "text/plain; charset=utf-8";

            default:
                return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
