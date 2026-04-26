package com.lixin.probe.agent.module;

import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.constant.Command;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import com.lixin.probe.agent.support.MinioSupport;
import com.lixin.probe.agent.sync.ProbeSyncService;
import com.lixin.probe.agent.util.FileDigestUtil;
import com.lixin.probe.agent.websocket.MessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 文件探针模块
 * 负责文件系统扫描和 MinIO 上传功能
 */
@Component
public class FileModule implements ProbeModule {

    private static final Logger log = LoggerFactory.getLogger(FileModule.class);

    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private MinioSupport minioSupport;

    @Autowired(required = false)
    private ProbeSyncService probeSyncService;

    private MessageSender messageSender;

    private volatile boolean running = false;

    /**
     * 设置消息发送器（由WebSocketClientHandler调用）
     */
    public void setMessageSender(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public String getName() {
        return "File System Module";
    }

    @Override
    public ProbeType getType() {
        return ProbeType.FILE;
    }

    @Override
    public boolean isEnabled() {
        return agentProperties.getModules().getFile().getEnabled();
    }

    @Override
    public void start() throws Exception {
        if (!isEnabled()) {
            log.info("文件模块未启用");
            return;
        }

        if (running) {
            log.warn("文件模块已在运行中");
            return;
        }

        log.info("启动文件探针模块...");

        // 检查 MinIO 是否可用
        if (minioSupport.isAvailable()) {
            log.info("MinIO 文件存储服务已就绪");
        } else {
            log.warn("MinIO 文件存储服务不可用，文件上传功能将受限");
        }

        running = true;
        log.info("文件探针模块启动成功");

        // 启动后自动执行一次文件扫描（类似DatabaseModule）
        if (agentProperties.getStartup().getExecuteImmediately()) {
            new Thread(() -> {
                try {
                    Thread.sleep(8000); // 延迟8秒，等待WebSocket连接建立
                    if (running) {
                        log.info("自动触发文件扫描...");
                        scanAndReportFiles();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    @Override
    public void stop() throws Exception {
        log.info("停止文件探针模块...");
        running = false;
    }

    @Override
    public ModuleStatus getStatus() {
        return running ? ModuleStatus.RUNNING : ModuleStatus.STOPPED;
    }

    /**
     * 扫描目录并上报文件信息
     */
    public void scanAndReportFiles() {
        if (!running) {
            log.warn("文件模块未运行，无法扫描文件");
            return;
        }

        log.info("开始扫描文件系统...");

        try {
            ProbeResponse.DataFile dataFile = scanFiles();

            if (dataFile != null) {
                log.info("文件扫描完成：目录数={}, 文件数={}, 总大小={} bytes",
                    dataFile.getTotalDirectoryCount(), dataFile.getTotalFileCount(), dataFile.getTotalSize());

                // 上报文件扫描结果
                if (messageSender != null) {
                    try {
                        messageSender.sendFileData(dataFile);
                        log.info("✓ 文件扫描结果已上报到Admin");

                        // 获取文件探针的probeKey并上报扫描报告
                        String fileProbeKey = getFileProbeKey();
                        if (fileProbeKey != null) {
                            messageSender.sendFileScanReport(fileProbeKey, dataFile);
                            log.info("✓ 文件扫描报告已发送: probeKey={}", fileProbeKey);
                        }
                    } catch (Exception e) {
                        log.error("上报文件扫描结果失败", e);
                    }
                } else {
                    log.warn("MessageSender未设置，无法上报扫描结果");
                }
            }
        } catch (Exception e) {
            log.error("扫描文件系统失败", e);
        }
    }

    /**
     * 动态获取文件探针的probeKey
     * 从ProbeSyncService中查找类型为FILE的探针
     *
     * @return 文件探针的probeKey，如果未找到返回null
     */
    private String getFileProbeKey() {
        try {
            if (probeSyncService == null) {
                log.warn("ProbeSyncService未注入，无法获取文件探针Key");
                return null;
            }

            // 获取所有文件类型的探针
            java.util.List<ProbeSyncService.ProbeConfig> fileProbes =
                probeSyncService.getProbesByType("FILE");

            if (fileProbes.isEmpty()) {
                log.warn("未找到任何文件类型的探针");
                return null;
            }

            // 如果有多个文件探针，选择第一个（或者可以根据其他条件选择）
            ProbeSyncService.ProbeConfig fileProbe = fileProbes.get(0);
            String probeKey = fileProbe.getProbeKey();

            log.info("找到文件探针: probeKey={}, status={}", probeKey, fileProbe.getStatus());
            return probeKey;
        } catch (Exception e) {
            log.error("获取文件探针Key失败", e);
            return null;
        }
    }

    /**
     * 扫描文件
     */
    private ProbeResponse.DataFile scanFiles() {
        AgentProperties.FileConfig fileConfig = agentProperties.getModules().getFile();

        if (fileConfig == null || fileConfig.getScanPaths() == null || fileConfig.getScanPaths().isEmpty()) {
            log.warn("未配置文件扫描路径");
            return null;
        }

        log.info("扫描路径: {}", fileConfig.getScanPaths());

        Map<String, ProbeResponse.DataFile.Directory> directories = new HashMap<>();
        long totalSize = 0;
        int totalFiles = 0;

        for (String scanPath : fileConfig.getScanPaths()) {
            try {
                File rootDir = new File(scanPath);
                if (!rootDir.exists() || !rootDir.isDirectory()) {
                    log.warn("扫描路径不存在或不是目录: {}", scanPath);
                    continue;
                }

                ProbeResponse.DataFile.Directory dir = scanDirectory(rootDir, fileConfig, 0);
                if (dir != null) {
                    directories.put(scanPath, dir);
                    totalSize += dir.getSize() != null ? dir.getSize() : 0;
                    totalFiles += countFiles(dir);
                }
            } catch (Exception e) {
                log.error("扫描目录失败: {}", scanPath, e);
            }
        }

        log.info("文件扫描完成，共 {} 个目录，{} 个文件，总大小: {} bytes",
                directories.size(), totalFiles, totalSize);

        return ProbeResponse.DataFile.builder()
                .directories(directories)
                .success(true)
                .totalDirectoryCount((long) directories.size())
                .totalFileCount((long) totalFiles)
                .totalSize(totalSize)
                .build();
    }

    /**
     * 递归扫描目录
     */
    private ProbeResponse.DataFile.Directory scanDirectory(File dir, AgentProperties.FileConfig config, int depth) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return null;
        }

        // 检查深度限制
        if (config.getMaxDepth() != null && depth > config.getMaxDepth()) {
            return null;
        }

        Map<String, ProbeResponse.DataFile.File> files = new HashMap<>();
        Map<String, ProbeResponse.DataFile.Directory> subDirs = new HashMap<>();
        long totalSize = 0;

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
                if (matchesExtension(child, config)) {
                    long fileSize = child.length();

                    // 检查文件大小限制
                    if (config.getMinFileSize() != null && fileSize < config.getMinFileSize()) {
                        continue;
                    }
                    if (config.getMaxFileSize() != null && fileSize > config.getMaxFileSize()) {
                        continue;
                    }

                    String md5 = null;
                    if (config.getCalculateMD5()) {
                        md5 = calculateMD5(child);
                    }

                    // 获取文件扩展名
                    String extension = "";
                    int dotIndex = child.getName().lastIndexOf('.');
                    if (dotIndex > 0) {
                        extension = child.getName().substring(dotIndex);
                    }

                    List<String> pathList = new ArrayList<>();
                    pathList.add(child.getAbsolutePath());

                    ProbeResponse.DataFile.File file = ProbeResponse.DataFile.File.builder()
                            .name(child.getName())
                            .path(pathList)
                            .size(fileSize)
                            .lastModified(child.lastModified())
                            .extension(extension)
                            .md5(md5)
                            .build();

                    files.put(child.getName(), file);
                    totalSize += fileSize;

                    // 可选：上传到 MinIO
                    if (minioSupport.isAvailable()) {
                        uploadToMinio(child);
                    }
                }
            } else if (child.isDirectory() && config.getRecursive()) {
                // 递归处理子目录
                ProbeResponse.DataFile.Directory subDir = scanDirectory(child, config, depth + 1);
                if (subDir != null) {
                    subDirs.put(child.getName(), subDir);
                    totalSize += subDir.getSize() != null ? subDir.getSize() : 0;
                }
            }
        }

        List<String> pathList = new ArrayList<>();
        pathList.add(dir.getAbsolutePath());

        return ProbeResponse.DataFile.Directory.builder()
                .name(dir.getName())
                .path(pathList)
                .size(totalSize)
                .files(files)
                .directories(subDirs)
                .build();
    }

    /**
     * 检查文件扩展名是否匹配
     */
    private boolean matchesExtension(File file, AgentProperties.FileConfig config) {
        if (config.getFileExtensions() == null || config.getFileExtensions().isEmpty()) {
            return true; // 未配置扩展名过滤，接受所有文件
        }

        String fileName = file.getName();
        for (String ext : config.getFileExtensions()) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算 MD5
     */
    private String calculateMD5(File file) {
        return FileDigestUtil.calculateMD5(file);
    }

    /**
     * 上传文件到 MinIO
     */
    private void uploadToMinio(File file) {
        try {
            // 生成对象名称（使用时间戳 + 原文件名）
            String objectName = String.format("files/%d/%s",
                    System.currentTimeMillis(), file.getName());

            boolean success = minioSupport.upload(file, objectName);
            if (success) {
                log.debug("文件已上传到 MinIO: {} -> {}", file.getAbsolutePath(), objectName);

                // 可选：获取预签名 URL
                String url = minioSupport.generatePresignedGetUrl(objectName, file.getName());
                if (url != null) {
                    log.debug("文件访问 URL: {}", url);
                }
            }
        } catch (Exception e) {
            log.error("上传文件到 MinIO 失败: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * 递归统计文件数量
     */
    private int countFiles(ProbeResponse.DataFile.Directory dir) {
        int count = dir.getFiles() != null ? dir.getFiles().size() : 0;
        if (dir.getDirectories() != null) {
            for (ProbeResponse.DataFile.Directory sub : dir.getDirectories().values()) {
                count += countFiles(sub);
            }
        }
        return count;
    }

    @Override
    public void onCommand(Command command, Object payload) {
        log.info("收到文件模块命令: {}", command);

        switch (command) {
            case FILE_PROBE:
                // 扫描文件
                scanAndReportFiles();
                break;

            case MINIO_FILE:
                // 上传文件到 MinIO
                if (payload instanceof String) {
                    String filePath = (String) payload;
                    uploadFileToMinio(filePath);
                }
                break;

            default:
                log.warn("文件模块不支持命令: {}", command);
        }
    }

    /**
     * 上传指定文件到 MinIO
     */
    public void uploadFileToMinio(String filePath) {
        if (!minioSupport.isAvailable()) {
            log.warn("MinIO 不可用，无法上传文件");
            return;
        }

        try {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                log.warn("文件不存在或不是文件: {}", filePath);
                return;
            }

            log.info("开始上传文件到 MinIO: {}", filePath);
            uploadToMinio(file);
            log.info("文件上传任务已完成: {}", filePath);

        } catch (Exception e) {
            log.error("上传文件到 MinIO 失败: {}", filePath, e);
        }
    }

    /**
     * 异步扫描并返回 CompletableFuture
     */
    public CompletableFuture<ProbeResponse.DataFile> scanFilesAsync() {
        return CompletableFuture.supplyAsync(this::scanFiles);
    }

    /**
     * 异步上传文件到 MinIO
     */
    public CompletableFuture<Boolean> uploadFileToMinioAsync(String filePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                uploadFileToMinio(filePath);
                return true;
            } catch (Exception e) {
                log.error("异步上传文件失败: {}", filePath, e);
                return false;
            }
        });
    }
}
