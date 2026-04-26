package com.lixin.probe.agent.websocket;

import com.lixin.probe.agent.handler.TableDataQueryHandler;
import com.lixin.probe.agent.pojo.request.ProbeRequest;
import com.lixin.probe.agent.probe.ProbeManager;
import com.lixin.probe.agent.service.DatabaseService;
import com.lixin.probe.agent.service.FileService;
import com.lixin.probe.agent.sync.ProbeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 探针命令处理器
 * 负责处理从Admin端接收的各种探针控制命令
 *
 * @author Claude Code
 * @since 1.0
 */
@Component
public class ProbeCommandHandler implements MessageHandler.CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(ProbeCommandHandler.class);

    @Lazy
    @Autowired
    private ProbeManager probeManager;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private FileService fileService;

    @Autowired(required = false)
    private ProbeSyncService probeSyncService;

    @Autowired
    private TableDataQueryHandler tableDataQueryHandler;

    private MessageSender messageSender;

    /**
     * 设置消息发送器（由WebSocketClientHandler调用）
     */
    public void setMessageSender(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public void handleProbeCommand() {
        log.info("处理元数据采集命令");
        try {
            if (databaseService != null && messageSender != null) {
                // 1. 采集数据库元数据（表结构）
                com.lixin.probe.agent.pojo.response.ProbeResponse.Metadata metadata =
                    databaseService.collectMetadata();
                if (metadata != null) {
                    messageSender.sendMetadata(metadata);
                    log.info("✓ 元数据已发送: {} 个数据库", metadata.getDatabases() != null ? metadata.getDatabases().size() : 0);
                }

                // 2. 同时采集性能数据（行数、大小等）
                try {
                    com.lixin.probe.agent.pojo.response.ProbeResponse.DataSize dataSize =
                        databaseService.collectDataSize();
                    if (dataSize != null && dataSize.getDatabases() != null && !dataSize.getDatabases().isEmpty()) {
                        messageSender.sendDataSize(dataSize);
                        log.info("✓ 性能数据已发送: {} 个数据库", dataSize.getDatabases().size());
                    } else {
                        log.warn("性能数据采集失败或为空");
                    }
                } catch (Exception e) {
                    log.error("采集性能数据失败（但不影响元数据）", e);
                }
            } else {
                log.warn("DatabaseService或MessageSender未设置，无法发送元数据");
            }
        } catch (Exception e) {
            log.error("处理元数据采集命令失败", e);
        }
    }

    @Override
    public void handleFileProbeCommand() {
        log.info("处理文件扫描命令（默认路径）");
        try {
            if (fileService != null) {
                fileService.scanFiles();
            } else {
                log.warn("FileService未注入，无法执行文件扫描");
            }
        } catch (Exception e) {
            log.error("执行文件扫描失败", e);
        }
    }

    @Override
    public void handleFileProbeCommand(String scanPath) {
        log.info("处理文件扫描命令（指定路径: {}）", scanPath);
        log.info("messageSender状态: {}", messageSender != null ? "已设置" : "未设置");
        try {
            if (fileService != null && messageSender != null) {
                // 执行文件扫描
                com.lixin.probe.agent.pojo.response.ProbeResponse.DataFile fileData =
                    fileService.scanFiles(scanPath);
                log.info("文件扫描完成: {}", fileData != null ? "成功" : "失败");
                if (fileData != null) {
                    log.info("准备发送文件数据...");
                    messageSender.sendFileData(fileData);
                    log.info("✓ 文件数据已发送: {} 个目录", fileData.getDirectories() != null ? fileData.getDirectories().size() : 0);

                    // 动态获取文件探针的probeKey，而不是硬编码
                    String fileProbeKey = getFileProbeKey();
                    if (fileProbeKey != null) {
                        log.info("准备发送文件扫描报告: probeKey={}", fileProbeKey);
                        messageSender.sendFileScanReport(fileProbeKey, fileData);
                        log.info("✓ 文件扫描报告已发送");
                    } else {
                        log.warn("未找到文件探针配置，无法发送扫描报告");
                    }
                } else {
                    log.warn("文件扫描返回null");
                }
            } else {
                log.warn("FileService或MessageSender未设置，无法发送文件数据");
            }
        } catch (Exception e) {
            log.error("执行文件扫描失败: scanPath={}", scanPath, e);
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

    @Override
    public void handleMonitorRequest() {
        log.info("处理系统监控请求");
        try {
            if (databaseService != null && messageSender != null) {
                // 采集数据库数据量信息
                com.lixin.probe.agent.pojo.response.ProbeResponse.DataSize dataSize =
                    databaseService.collectDataSize();
                if (dataSize != null) {
                    messageSender.sendDataSize(dataSize);
                }
            } else {
                log.warn("DatabaseService或MessageSender未设置，无法发送监控数据");
            }
        } catch (Exception e) {
            log.error("处理系统监控请求失败", e);
        }
    }

    @Override
    public void handleStartCommand() {
        log.info("处理启动探针命令（所有探针）");
        try {
            if (probeManager != null) {
                ProbeManager.StartupResult result = probeManager.startAllProbes();
                log.info("探针启动完成: 成功={}, 失败={}", result.successCount, result.failCount);

                // 发送状态更新到Admin
                if (messageSender != null) {
                    sendProbeStatusUpdates("online");
                }
            } else {
                log.warn("ProbeManager未注入，无法启动探针");
            }
        } catch (Exception e) {
            log.error("启动探针失败", e);
        }
    }

    @Override
    public void handleStartCommand(String probeKey, String probeType) {
        log.info("处理启动探针命令（指定探针）: probeKey={}, type={}", probeKey, probeType);
        try {
            if (probeManager != null) {
                // 转换probeType字符串为ProbeType枚举
                com.lixin.probe.agent.module.ProbeType type =
                    com.lixin.probe.agent.module.ProbeType.valueOf(probeType);

                boolean success = probeManager.startProbe(probeKey, type);
                if (success) {
                    log.info("探针启动成功: probeKey={}", probeKey);

                    // 只发送该探针的状态更新
                    if (messageSender != null) {
                        messageSender.sendProbeStatusUpdate(probeKey, "online", probeType);
                    }
                } else {
                    log.warn("探针启动失败: probeKey={}", probeKey);
                }
            } else {
                log.warn("ProbeManager未注入，无法启动探针");
            }
        } catch (Exception e) {
            log.error("启动探针失败: probeKey={}", probeKey, e);
        }
    }

    @Override
    public void handleStopCommand() {
        log.info("处理停止探针命令（所有探针）");
        try {
            if (probeManager != null) {
                ProbeManager.ShutdownResult result = probeManager.stopAllProbes();
                log.info("探针停止完成: 成功={}, 失败={}", result.successCount, result.failCount);

                // 发送状态更新到Admin
                if (messageSender != null) {
                    sendProbeStatusUpdates("offline");
                }
            } else {
                log.warn("ProbeManager未注入，无法停止探针");
            }
        } catch (Exception e) {
            log.error("停止探针失败", e);
        }
    }

    @Override
    public void handleStopCommand(String probeKey, String probeType) {
        log.info("处理停止探针命令（指定探针）: probeKey={}, type={}", probeKey, probeType);
        try {
            if (probeManager != null) {
                // 转换probeType字符串为ProbeType枚举
                com.lixin.probe.agent.module.ProbeType type =
                    com.lixin.probe.agent.module.ProbeType.valueOf(probeType);

                boolean success = probeManager.stopProbe(probeKey, type);
                if (success) {
                    log.info("探针停止成功: probeKey={}", probeKey);

                    // 只发送该探针的状态更新
                    if (messageSender != null) {
                        messageSender.sendProbeStatusUpdate(probeKey, "offline", probeType);
                    }
                } else {
                    log.warn("探针停止失败: probeKey={}", probeKey);
                }
            } else {
                log.warn("ProbeManager未注入，无法停止探针");
            }
        } catch (Exception e) {
            log.error("停止探针失败: probeKey={}", probeKey, e);
        }
    }

    /**
     * 发送所有探针的状态更新
     *
     * @param status 状态 (online/offline)
     */
    private void sendProbeStatusUpdates(String status) {
        try {
            // 从ProbeSyncService获取所有探针配置
            if (probeSyncService == null) {
                log.warn("ProbeSyncService未注入，无法获取探针列表");
                return;
            }

            java.util.List<ProbeSyncService.ProbeConfig> allProbes = probeSyncService.getAllProbes();

            if (allProbes.isEmpty()) {
                log.warn("没有探针需要更新状态");
                return;
            }

            // 为每个探针发送状态更新
            int updateCount = 0;
            for (ProbeSyncService.ProbeConfig probe : allProbes) {
                String probeKey = probe.getProbeKey();
                String probeType = probe.getType();

                // 发送状态更新
                messageSender.sendProbeStatusUpdate(probeKey, status, probeType);

                // 同时通过HTTP API更新状态
                updateProbeStatusViaHttp(probeKey, status, probeType);

                log.info("已发送探针状态更新: probeKey={}, status={}, type={}", probeKey, status, probeType);
                updateCount++;
            }

            log.info("探针状态批量更新完成: 总数={}, status={}", updateCount, status);

        } catch (Exception e) {
            log.error("发送探针状态更新失败", e);
        }
    }

    /**
     * 通过HTTP API更新探针状态
     *
     * @param probeKey 探针标识
     * @param status 状态
     * @param probeType 探针类型
     */
    private void updateProbeStatusViaHttp(String probeKey, String status, String probeType) {
        try {
            // 获取Admin服务器地址
            String adminUrl = "http://localhost:8080";

            // 根据探针类型选择不同的API端点
            String endpoint;
            if ("FILE".equals(probeType)) {
                endpoint = adminUrl + "/api/probe-status/file-probe/" + probeKey;
            } else {
                endpoint = adminUrl + "/api/probe-status/probe/" + probeKey;
            }

            // 构建URL
            java.net.URI uri = java.net.URI.create(endpoint + "?status=" + status);

            // 创建HTTP连接
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("PUT");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Content-Type", "application/json");

            // 获取响应
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                log.info("[HTTP API] 探针状态更新成功: probeKey={}, status={}, responseCode={}", probeKey, status, responseCode);
            } else {
                log.warn("[HTTP API] 探针状态更新失败: probeKey={}, status={}, responseCode={}", probeKey, status, responseCode);
            }

            connection.disconnect();

        } catch (Exception e) {
            log.error("[HTTP API] 更新探针状态失败: probeKey={}, status={}", probeKey, status, e);
        }
    }

    /**
     * 根据探针key确定探针类型
     *
     * @param probeKey 探针key (如: AGENT-file-xxx, AGENT-database-xxx)
     * @return 探针类型 (FILE/DATABASE/SYSTEM)
     */
    private String determineProbeType(String probeKey) {
        if (probeKey.contains("-file")) {
            return "FILE";
        } else if (probeKey.contains("-database")) {
            return "DATABASE";
        } else if (probeKey.contains("-system")) {
            return "SYSTEM";
        }
        return "UNKNOWN";
    }

    @Override
    public void handleRestartCommand() {
        log.info("处理重启探针命令（所有探针）");
        try {
            if (probeManager != null) {
                // 先停止所有探针
                ProbeManager.ShutdownResult stopResult = probeManager.stopAllProbes();
                log.info("探针停止完成: 成功={}, 失败={}", stopResult.successCount, stopResult.failCount);

                // 等待一小段时间让探针完全停止
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // 再启动所有探针
                ProbeManager.StartupResult startResult = probeManager.startAllProbes();
                log.info("探针启动完成: 成功={}, 失败={}", startResult.successCount, startResult.failCount);
            } else {
                log.warn("ProbeManager未注入，无法重启探针");
            }
        } catch (Exception e) {
            log.error("重启探针失败", e);
        }
    }

    @Override
    public void handleRestartCommand(String probeKey, String probeType) {
        log.info("处理重启探针命令（指定探针）: probeKey={}, type={}", probeKey, probeType);
        try {
            if (probeManager != null) {
                // 转换probeType字符串为ProbeType枚举
                com.lixin.probe.agent.module.ProbeType type =
                    com.lixin.probe.agent.module.ProbeType.valueOf(probeType);

                // 先停止探针
                boolean stopSuccess = probeManager.stopProbe(probeKey, type);
                if (!stopSuccess) {
                    log.warn("探针停止失败: probeKey={}", probeKey);
                    return;
                }

                // 等待一小段时间让探针完全停止
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // 再启动探针
                boolean startSuccess = probeManager.startProbe(probeKey, type);
                if (startSuccess) {
                    log.info("探针重启成功: probeKey={}", probeKey);

                    // 发送状态更新
                    if (messageSender != null) {
                        messageSender.sendProbeStatusUpdate(probeKey, "online", probeType);
                    }
                } else {
                    log.warn("探针重启失败: probeKey={}", probeKey);
                }
            } else {
                log.warn("ProbeManager未注入，无法重启探针");
            }
        } catch (Exception e) {
            log.error("重启探针失败: probeKey={}", probeKey, e);
        }
    }

    @Override
    public void handleTableDataQuery(Map<String, Object> params) {
        log.info("处理表数据查询请求: params={}", params);

        try {
            // 构建ProbeRequest
            ProbeRequest request = new ProbeRequest();
            request.setCode(extractProbeKey(params));
            request.setParams(params);

            // 执行查询
            Map<String, Object> result = tableDataQueryHandler.handleQuery(request);

            // 发送结果回Admin
            if (messageSender != null) {
                String probeKey = request.getCode();
                String databaseName = params.get("databaseName") != null ? params.get("databaseName").toString() : "unknown";
                String tableName = params.get("tableName") != null ? params.get("tableName").toString() : "unknown";

                messageSender.sendTableData(probeKey, databaseName, tableName, result);

                if (result.containsKey("error")) {
                    log.warn("表数据查询失败: databaseName={}, tableName={}, error={}",
                            databaseName, tableName, result.get("error"));
                } else {
                    log.info("表数据查询成功: databaseName={}, tableName={}, rows={}",
                            databaseName, tableName, result.get("rows") != null ? ((java.util.List<?>) result.get("rows")).size() : 0);
                }
            } else {
                log.warn("MessageSender未设置，无法发送表数据查询结果");
            }

        } catch (Exception e) {
            log.error("处理表数据查询请求失败", e);
        }
    }

    /**
     * 从params中提取probeKey
     */
    private String extractProbeKey(Map<String, Object> params) {
        Object probeKeyObj = params.get("probeKey");
        if (probeKeyObj != null) {
            return probeKeyObj.toString();
        }
        // 如果没有probeKey，尝试从databaseName推断
        Object dbNameObj = params.get("databaseName");
        if (dbNameObj != null) {
            return "AGENT-database-" + dbNameObj.toString();
        }
        return "unknown";
    }
}
