package com.lixin.probe.agent.service;

import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import com.lixin.probe.agent.util.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 文件扫描服务
 * 负责扫描指定路径的文件并收集文件信息
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    @Autowired
    private AgentProperties agentProperties;

    /**
     * 扫描文件
     *
     * @return 文件信息
     */
    public ProbeResponse.DataFile scanFiles() {
        log.info("开始扫描文件...");

        AgentProperties.FileConfig config = agentProperties.getModules().getFile();

        if (config.getScanPaths() == null || config.getScanPaths().isEmpty()) {
            log.warn("未配置扫描路径");
            return ProbeResponse.DataFile.builder()
                    .directories(Collections.emptyMap())
                    .success(true)
                    .build();
        }

        Map<String, ProbeResponse.DataFile.Directory> directories = new HashMap<>();

        for (String scanPath : config.getScanPaths()) {
            try {
                ProbeResponse.DataFile.Directory dir = scanPath(scanPath, config);
                directories.put(scanPath, dir);
            } catch (Exception e) {
                log.error("扫描路径失败: {}", scanPath, e);
            }
        }

        // 计算统计数据
        long totalFileCount = 0;
        long totalDirectoryCount = directories.size(); // 顶层目录数
        long totalSize = 0;

        for (ProbeResponse.DataFile.Directory dir : directories.values()) {
            if (dir.getFileCount() != null) {
                totalFileCount += dir.getFileCount();
            }
            if (dir.getDirectoryCount() != null) {
                totalDirectoryCount += dir.getDirectoryCount();
            }
            if (dir.getSize() != null) {
                totalSize += dir.getSize();
            }
        }

        ProbeResponse.DataFile result = ProbeResponse.DataFile.builder()
                .directories(directories)
                .totalFileCount(totalFileCount)
                .totalDirectoryCount(totalDirectoryCount)
                .totalSize(totalSize)
                .success(true)
                .build();

        log.info("文件扫描完成，共扫描 {} 个路径", directories.size());

        return result;
    }

    /**
     * 异步扫描文件
     *
     * @return CompletableFuture containing file data
     */
    public CompletableFuture<ProbeResponse.DataFile> scanFilesAsync() {
        return CompletableFuture.supplyAsync(this::scanFiles);
    }

    /**
     * 异步扫描指定路径
     *
     * @param scanPath 要扫描的路径
     * @return CompletableFuture containing file data
     */
    public CompletableFuture<ProbeResponse.DataFile> scanFilesAsync(String scanPath) {
        return CompletableFuture.supplyAsync(() -> scanFiles(scanPath));
    }

    /**
     * 扫描指定路径
     *
     * @param scanPath 要扫描的路径
     * @return 文件信息
     */
    public ProbeResponse.DataFile scanFiles(String scanPath) {
        log.info("开始扫描指定路径: {}", scanPath);

        AgentProperties.FileConfig config = agentProperties.getModules().getFile();

        if (scanPath == null || scanPath.isEmpty()) {
            log.warn("扫描路径为空，使用配置的默认路径");
            return scanFiles();
        }

        Map<String, ProbeResponse.DataFile.Directory> directories = new HashMap<>();

        try {
            ProbeResponse.DataFile.Directory dir = scanPath(scanPath, config);
            directories.put(scanPath, dir);
        } catch (Exception e) {
            log.error("扫描路径失败: {}", scanPath, e);
        }

        // 计算统计数据
        long totalFileCount = 0;
        long totalDirectoryCount = directories.size() > 0 ? directories.size() : 0;
        long totalSize = 0;

        for (ProbeResponse.DataFile.Directory dir : directories.values()) {
            if (dir.getFileCount() != null) {
                totalFileCount += dir.getFileCount();
            }
            if (dir.getDirectoryCount() != null) {
                totalDirectoryCount += dir.getDirectoryCount();
            }
            if (dir.getSize() != null) {
                totalSize += dir.getSize();
            }
        }

        ProbeResponse.DataFile result = ProbeResponse.DataFile.builder()
                .directories(directories)
                .totalFileCount(totalFileCount)
                .totalDirectoryCount(totalDirectoryCount)
                .totalSize(totalSize)
                .success(true)
                .build();

        log.info("文件扫描完成，扫描路径: {}", scanPath);

        return result;
    }

    /**
     * 扫描单个路径（递归构建目录树）
     */
    private ProbeResponse.DataFile.Directory scanPath(String path, AgentProperties.FileConfig config)
            throws IOException {

        List<String> pathList = new ArrayList<>();
        pathList.add(path);

        Path rootPath = Paths.get(path);

        if (!Files.exists(rootPath)) {
            log.warn("路径不存在: {}", path);
            return ProbeResponse.DataFile.Directory.builder()
                    .name(path)
                    .size(0L)
                    .fileCount(0L)
                    .directoryCount(0L)
                    .path(pathList)
                    .files(Collections.emptyMap())
                    .directories(Collections.emptyMap())
                    .build();
        }

        log.debug("开始扫描路径: {}", path);

        // 使用递归方式扫描，构建完整的目录树
        File rootDir = rootPath.toFile();
        ProbeResponse.DataFile.Directory result = scanDirectoryRecursive(rootDir, config, 0);

        log.debug("路径 {} 扫描完成，找到 {} 个文件，{} 个目录，总大小: {} bytes",
                path, result.getFileCount(), result.getDirectoryCount(), result.getSize());

        return result;
    }

    /**
     * 递归扫描目录
     */
    private ProbeResponse.DataFile.Directory scanDirectoryRecursive(File dir, AgentProperties.FileConfig config, int depth) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return null;
        }

        // 检查深度限制
        if (config.getMaxDepth() != null && depth > config.getMaxDepth()) {
            return null;
        }

        Map<String, ProbeResponse.DataFile.File> files = new HashMap<>();
        Map<String, ProbeResponse.DataFile.Directory> subDirectories = new HashMap<>();
        long totalSize = 0;
        long fileCount = 0;
        long directoryCount = 0;

        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }

        for (File child : children) {
            // 跳过隐藏文件（如果配置要求）
            if (!config.getIncludeHidden() && child.isHidden()) {
                continue;
            }

            if (child.isFile()) {
                // 处理文件
                if (shouldIncludeFile(child.toPath(), config)) {
                    long fileSize = child.length();

                    // 检查文件大小限制
                    if (config.getMinFileSize() != null && fileSize < config.getMinFileSize()) {
                        continue;
                    }
                    if (config.getMaxFileSize() != null && fileSize > config.getMaxFileSize()) {
                        continue;
                    }

                    // 获取文件扩展名
                    String extension = "";
                    String fileName = child.getName();
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        extension = fileName.substring(dotIndex);
                    }

                    // 构建路径列表
                    List<String> filePathList = new ArrayList<>();
                    filePathList.add(child.getAbsolutePath());

                    ProbeResponse.DataFile.File file = ProbeResponse.DataFile.File.builder()
                            .name(fileName)
                            .path(filePathList)
                            .size(fileSize)
                            .lastModified(child.lastModified())
                            .extension(extension)
                            .build();

                    files.put(fileName, file);
                    totalSize += fileSize;
                    fileCount++;
                }
            } else if (child.isDirectory() && config.getRecursive()) {
                // 递归处理子目录
                ProbeResponse.DataFile.Directory subDir = scanDirectoryRecursive(child, config, depth + 1);
                if (subDir != null) {
                    subDirectories.put(child.getName(), subDir);
                    totalSize += subDir.getSize() != null ? subDir.getSize() : 0;
                    fileCount += subDir.getFileCount() != null ? subDir.getFileCount() : 0;
                    directoryCount += 1 + (subDir.getDirectoryCount() != null ? subDir.getDirectoryCount() : 0);
                }
            }
        }

        List<String> pathList = new ArrayList<>();
        pathList.add(dir.getAbsolutePath());

        return ProbeResponse.DataFile.Directory.builder()
                .name(dir.getName())
                .path(pathList)
                .size(totalSize)
                .fileCount(fileCount)
                .directoryCount(directoryCount)
                .files(files)
                .directories(subDirectories)
                .build();
    }

    /**
     * 判断是否应该包含此文件
     */
    private boolean shouldIncludeFile(Path filePath, AgentProperties.FileConfig config) {
        String fileName = filePath.getFileName().toString();

        // 检查隐藏文件
        if (!config.getIncludeHidden() && fileName.startsWith(".")) {
            return false;
        }

        // 检查文件扩展名（列表为空或包含 "*" 时不过滤，支持所有文件）
        List<String> extensions = config.getFileExtensions();
        if (extensions != null && !extensions.isEmpty()
                && !extensions.contains("*") && !extensions.contains(".*")) {
            boolean matches = false;
            String lowerFileName = fileName.toLowerCase();

            for (String ext : extensions) {
                if (lowerFileName.endsWith(ext.toLowerCase())) {
                    matches = true;
                    break;
                }
            }

            if (!matches) {
                return false;
            }
        }

        // 检查文件大小
        try {
            long fileSize = Files.size(filePath);
            if (fileSize < config.getMinFileSize() || fileSize > config.getMaxFileSize()) {
                return false;
            }
        } catch (IOException e) {
            log.error("获取文件大小失败: {}", filePath, e);
            return false;
        }

        // 检查排除模式
        if (config.getExcludePatterns() != null) {
            String fullPath = filePath.toString();
            for (String pattern : config.getExcludePatterns()) {
                if (fullPath.matches(pattern)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 收集文件信息
     */
    private ProbeResponse.DataFile.File collectFileInfo(Path filePath, AgentProperties.FileConfig config) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

            String fileName = filePath.getFileName().toString();
            String extension = "";
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = fileName.substring(dotIndex + 1);
            }

            ProbeResponse.DataFile.File.Builder builder = ProbeResponse.DataFile.File.builder()
                    .name(fileName)
                    .size(attrs.size())
                    .type(Files.isDirectory(filePath) ? "directory" : "file")
                    .extension(extension)
                    .lastModified(attrs.lastModifiedTime().toMillis());

            // 构建路径列表
            List<String> pathList = new ArrayList<>();
            filePath.forEach(p -> pathList.add(p.toString()));
            builder.path(pathList);

            // 计算MD5（如果配置要求）
            if (config.getCalculateMD5() && attrs.isRegularFile()) {
                String md5 = CryptoUtil.calculateFileMD5(filePath.toString());
                builder.md5(md5);
            }

            return builder.build();

        } catch (Exception e) {
            log.error("收集文件信息失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 监控文件变化
     */
    public void watchFileChanges() {
        AgentProperties.FileConfig config = agentProperties.getModules().getFile();

        if (config.getScanPaths() == null || config.getScanPaths().isEmpty()) {
            log.warn("未配置监控路径");
            return;
        }

        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();

            for (String scanPath : config.getScanPaths()) {
                Path path = Paths.get(scanPath);
                if (Files.exists(path) && Files.isDirectory(path)) {
                    path.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                    log.info("已注册文件监控: {}", scanPath);
                }
            }

            // 启动监控线程
            new Thread(() -> {
                try {
                    while (true) {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();

                            if (kind == StandardWatchEventKinds.OVERFLOW) {
                                continue;
                            }

                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            Path filename = ev.context();

                            log.info("文件变化: {} - {}", kind.name(), filename);

                            // 这里可以触发重新扫描或发送通知
                        }

                        boolean valid = key.reset();
                        if (!valid) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("文件监控线程已中断");
                }
            }, "FileWatcher").start();

        } catch (Exception e) {
            log.error("启动文件监控失败", e);
        }
    }
}
