package com.lixin.probe.service;

import com.alibaba.fastjson2.JSON;
import com.lixin.probe.dto.ProbeControlResponse;
import com.lixin.probe.entity.FileProbe;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.service.impl.FileProbeServiceImpl;
import com.lixin.probe.websocket.FileProbeWebSocketHandler;
import com.lixin.probe.websocket.MetaProbeWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 探针控制服务
 * 负责发送控制命令到探针并等待响应
 *
 * @author Claude Code
 * @date 2026-03-21
 * @version 2.0 (支持文件探针)
 */
@Service
public class ProbeControlService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProbeControlService.class);

    @Lazy
    @Autowired
    private MetaProbeWebSocketHandler webSocketHandler;

    @Autowired
    @Qualifier("decoratedProbeService")
    private ProbeService probeService;

    @Autowired(required = false)
    private FileProbeServiceImpl fileProbeService;

    @Lazy
    @Autowired(required = false)
    private FileProbeWebSocketHandler fileProbeWebSocketHandler;

    /**
     * 存储待处理的命令，key为命令ID
     * 使用LRU策略防止内存无限增长
     */
    private static final int MAX_PENDING_COMMANDS = 1000;
    private final Map<String, CompletableFuture<ProbeControlResponse>> pendingCommands =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<ProbeControlResponse>> eldest) {
                    // 只有当超过最大数量时才移除最老的条目
                    if (size() > MAX_PENDING_COMMANDS) {
                        // 完成Future，让等待的线程收到超时错误
                        eldest.getValue().complete(
                            ProbeControlResponse.error("命令队列已满，请重试")
                        );
                        log.warn("[LRU淘汰] 待处理命令队列已满，移除最老的命令: {}", eldest.getKey());
                        return true;
                    }
                    return false;
                }
            };

    /**
     * 存储命令ID对应的命令类型和探针key（用于更新探针状态）
     */
    private final Map<String, CommandInfo> pendingCommandInfos = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CommandInfo> eldest) {
            return size() > MAX_PENDING_COMMANDS;
        }
    };

    /**
     * 存储待处理的表数据查询，key为查询ID（probeKey:databaseName:tableName:pageNum）
     */
    private final Map<String, CompletableFuture<Map<String, Object>>> pendingTableDataQueries =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<Map<String, Object>>> eldest) {
                    return size() > MAX_PENDING_COMMANDS;
                }
            };

    /**
     * 命令信息
     */
    public static class CommandInfo {
        private String commandType;
        private String probeKey;

        CommandInfo(String commandType, String probeKey) {
            this.commandType = commandType;
            this.probeKey = probeKey;
        }

        public String getCommandType() {
            return commandType;
        }

        public String getProbeKey() {
            return probeKey;
        }
    }

    /**
     * 命令超时时间（秒）
     */
    private static final int COMMAND_TIMEOUT = 10;

    /**
     * 发送控制命令到探针
     *
     * @param probeKey 探针标识
     * @param commandType 命令类型（START、STOP、RESTART、UPDATE_CONFIG）
     * @param params 命令参数
     * @return 探针响应
     */
    public ProbeControlResponse sendControlCommand(String probeKey, String commandType, Map<String, Object> params) {
        log.info("[探针控制] 收到控制命令: probeKey={}, commandType={}", probeKey, commandType);

        // 1. 检查探针是否存在
        Probe probe = probeService.getByProbeKey(probeKey);
        FileProbe fileProbe = null;

        // 如果在普通探针表中找不到，尝试在文件探针表中查找
        if (probe == null && fileProbeService != null) {
            try {
                // 使用索引查询替代线性遍历
                fileProbe = fileProbeService.getByProbeKey(probeKey);
                if (fileProbe != null) {
                    log.info("找到文件探针: probeKey={}", probeKey);
                }
            } catch (Exception e) {
                log.warn("查询文件探针失败: {}", e.getMessage());
            }
        }

        if (probe == null && fileProbe == null) {
            log.warn("探针不存在: probeKey={}", probeKey);
            return ProbeControlResponse.error("探针不存在: " + probeKey);
        }

        // 2. 确定实际的WebSocket连接探针和目标模块
        String targetProbeKey = probeKey;
        boolean isFileProbe = (fileProbe != null);
        String agentCode = null;  // 原始提取的code
        String actualAgentCode = null;  // 实际使用的code（可能经过智能匹配）
        String targetModule = null;

        // 确定探针类型
        String probeType = null;
        if (probe != null) {
            probeType = probe.getType();
        } else if (fileProbe != null) {
            probeType = "FILE"; // 文件探针类型
        }

        // 提取原始的agent code
        agentCode = extractAgentCode(probeKey);
        log.info("[探针控制] 提取Agent代码: probeKey={}, agentCode={}", probeKey, agentCode);

        // 智能匹配：找到实际在线的Agent code
        if (agentCode != null) {
            var onlineAgentCodes = webSocketHandler.getOnlineAgentCodes();
            log.info("[探针控制] 当前在线Agent列表: {}", onlineAgentCodes);

            if (onlineAgentCodes.contains(agentCode)) {
                actualAgentCode = agentCode;
                log.info("[探针控制] Agent在线: {}", actualAgentCode);
            } else {
                // 提取的code不在线，尝试使用主code（AGENT）
                String primaryCode = "AGENT";
                if (onlineAgentCodes.contains(primaryCode)) {
                    actualAgentCode = primaryCode;
                    log.info("智能匹配: 提取的code {} 不在线，使用主code {} 替代: probeKey={}",
                            agentCode, primaryCode, probeKey);
                }
            }

            if (actualAgentCode == null) {
                log.warn("所有Agent离线，无法发送命令: extractedCode={}, probeKey={}", agentCode, probeKey);
                return ProbeControlResponse.error("Agent离线，无法发送命令");
            }

            // 确定目标模块
            if ("SYSTEM".equals(probeType)) {
                targetModule = "SYSTEM";
            } else if ("DATABASE".equals(probeType)) {
                targetModule = "DATABASE";
            } else if ("FILE".equals(probeType)) {
                targetModule = "FILE";
            }

            log.info("路由命令: probeKey={}, originalCode={}, actualAgentCode={}, module={}",
                    probeKey, agentCode, actualAgentCode, targetModule);

        } else {
            // 没有提取到agent code，说明这是直接管理的探针
            // 对于直接管理的探针（如probe-system-xxx），我们尝试将整个probeKey作为目标
            log.info("[探针控制] 探针不包含Agent code，尝试直接管理: probeKey={}", probeKey);

            // 检查探针是否直接连接到WebSocket
            var onlineProbes = webSocketHandler.getOnlineProbeKeys();
            log.info("[探针控制] 当前在线的WebSocket探针: {}", onlineProbes);

            if (!onlineProbes.contains(probeKey)) {
                log.warn("[探针控制] 直接管理的探针离线: probeKey={}", probeKey);
                return ProbeControlResponse.error("探针离线，无法发送命令");
            }

            // 对于直接管理的探针，actualAgentCode为null，将在发送命令时使用probeKey
            log.info("[探针控制] 直接管理的探针在线: probeKey={}", probeKey);
        }

        // 3. 生成唯一的命令ID
        String commandId = UUID.randomUUID().toString();
        log.info("生成命令ID: {}", commandId);

        // 4. 创建Future用于等待响应
        CompletableFuture<ProbeControlResponse> future = new CompletableFuture<>();
        pendingCommands.put(commandId, future);

        // 存储命令信息（用于后续更新探针状态）
        pendingCommandInfos.put(commandId, new CommandInfo(commandType, probeKey));

        log.info("添加待处理命令: commandId={}, 当前pendingCommands大小={}", commandId, pendingCommands.size());

        try {
            // 5. 构建命令消息payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("commandId", commandId);
            if (params != null && !params.isEmpty()) {
                payload.put("params", params);
            }
            payload.put("timestamp", System.currentTimeMillis());

            // 添加目标探针信息（确保Agent只启动指定的探针而不是所有探针）
            payload.put("targetProbeKey", probeKey);  // 总是添加探针key
            if (targetModule != null) {
                payload.put("targetModule", targetModule);
                log.info("模块命令: targetModule={}, targetProbeKey={}, 完整payload={}",
                        targetModule, probeKey, payload);
            } else {
                log.warn("警告: targetModule为null，使用probeKey进行匹配: probeKey={}, payload={}", probeKey, payload);
            }

            // 构建完整命令消息
            Map<String, Object> command = new HashMap<>();
            command.put("type", "COMMAND");
            command.put("cmd", commandType);
            command.put("payload", payload);

            // 6. 通过WebSocket发送命令
            boolean sent;
            if (actualAgentCode != null) {
                // 通过Agent code发送（使用智能匹配后的actualAgentCode）
                log.info("[探针控制] 通过Agent code发送命令: actualAgentCode={}, commandType={}", actualAgentCode, commandType);
                sent = webSocketHandler.sendControlCommandByAgentCode(actualAgentCode, commandType, command);
            } else {
                // 对于直接管理的探针（不通过Agent），直接发送到探针
                log.info("[探针控制] 直接发送命令到探针: targetProbeKey={}, commandType={}", targetProbeKey, commandType);
                sent = webSocketHandler.sendControlCommand(targetProbeKey, commandType, command);
            }

            if (!sent) {
                log.error("发送命令失败: probeKey={}, commandType={}", probeKey, commandType);
                return ProbeControlResponse.error("发送命令失败");
            }

            log.info("命令已发送: commandId={}, probeKey={}, commandType={}",
                    commandId, probeKey, commandType);

            // 7. 等待响应（带超时）
            ProbeControlResponse response = future.get(COMMAND_TIMEOUT, TimeUnit.SECONDS);
            log.info("收到命令响应: commandId={}, success={}", commandId, response.isSuccess());
            return response;

        } catch (TimeoutException e) {
            log.error("命令执行超时: commandId={}, timeout={}s", commandId, COMMAND_TIMEOUT);
            return ProbeControlResponse.error("命令执行超时，探针未在规定时间内响应");
        } catch (InterruptedException e) {
            log.error("命令执行被中断: commandId={}", commandId, e);
            Thread.currentThread().interrupt();
            return ProbeControlResponse.error("命令执行被中断");
        } catch (Exception e) {
            log.error("发送控制命令异常: commandId={}, probeKey={}", commandId, probeKey, e);
            return ProbeControlResponse.error("发送命令失败: " + e.getMessage());
        } finally {
            // 清理pending命令和命令信息
            pendingCommands.remove(commandId);
            pendingCommandInfos.remove(commandId);
            log.info("清理待处理命令: commandId={}, 当前pendingCommands大小={}", commandId, pendingCommands.size());
        }
    }

    /**
     * 处理探针的命令响应（由WebSocketHandler调用）
     *
     * @param commandId 命令ID
     * @param response 响应对象
     */
    public void handleCommandResponse(String commandId, ProbeControlResponse response) {
        log.info("处理命令响应: commandId={}, 当前pendingCommands大小={}, 包含keys={}",
                commandId, pendingCommands.size(), pendingCommands.keySet());

        CompletableFuture<ProbeControlResponse> future = pendingCommands.get(commandId);
        if (future != null) {
            log.info("完成命令Future: commandId={}", commandId);
            future.complete(response);
        } else {
            log.error("收到未知命令的响应: commandId={}, pendingCommands大小={}, 已存在的命令IDs: {}",
                    commandId, pendingCommands.size(),
                    pendingCommands.keySet().stream()
                            .limit(10)
                            .collect(java.util.stream.Collectors.joining(", ")));
        }
    }

    /**
     * 获取命令信息（用于更新探针状态）
     *
     * @param commandId 命令ID
     * @return 命令信息，如果不存在则返回null
     */
    public CommandInfo getCommandInfo(String commandId) {
        return pendingCommandInfos.get(commandId);
    }

    /**
     * 获取待处理命令数量
     */
    public int getPendingCommandCount() {
        return pendingCommands.size();
    }

    /**
     * 清理超时的命令（定时任务调用）
     */
    public void cleanupExpiredCommands() {
        log.debug("开始清理超时命令，当前待处理命令数: {}", pendingCommands.size());
        // 这里不强制清理，让Future自然超时
        // 如果需要主动清理，可以记录超时的命令ID
    }

    /**
     * 查询表数据（通过WebSocket发送查询请求到Agent）
     *
     * @param probeKey 探针标识
     * @param databaseName 数据库名称
     * @param tableName 表名
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 查询结果
     */
    public Map<String, Object> queryTableData(String probeKey, String databaseName, String tableName, Integer pageNum, Integer pageSize) {
        log.info("[表数据查询] 收到查询请求: probeKey={}, databaseName={}, tableName={}, pageNum={}, pageSize={}",
                probeKey, databaseName, tableName, pageNum, pageSize);

        try {
            // 1. 提取Agent代码
            String agentCode = extractAgentCode(probeKey);
            if (agentCode == null) {
                log.warn("[表数据查询] 无法从probeKey提取Agent代码: {}", probeKey);
                return createErrorResult("无法从probeKey提取Agent代码: " + probeKey);
            }

            // 2. 获取在线的Agent代码
            var onlineAgentCodes = webSocketHandler.getOnlineAgentCodes();
            log.info("[表数据查询] 当前在线Agent列表: {}", onlineAgentCodes);

            String actualAgentCode = null;
            if (onlineAgentCodes.contains(agentCode)) {
                actualAgentCode = agentCode;
            } else {
                // 尝试使用主code
                String primaryCode = "AGENT";
                if (onlineAgentCodes.contains(primaryCode)) {
                    actualAgentCode = primaryCode;
                    log.info("[表数据查询] 智能匹配: 使用主code替代: {} → {}", agentCode, primaryCode);
                }
            }

            if (actualAgentCode == null) {
                log.warn("[表数据查询] 所有Agent离线: extractedCode={}", agentCode);
                return createErrorResult("Agent离线，无法查询表数据");
            }

            // 3. 生成查询ID
            String queryId = String.format("%s:%s:%s:%d", probeKey, databaseName, tableName, pageNum != null ? pageNum : 1);

            // 4. 创建Future用于等待响应
            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            pendingTableDataQueries.put(queryId, future);
            log.info("[表数据查询] 添加待处理查询: queryId={}, 当前pendingQueries大小={}", queryId, pendingTableDataQueries.size());

            try {
                // 5. 构建查询参数
                Map<String, Object> params = new HashMap<>();
                params.put("probeKey", probeKey);
                params.put("databaseName", databaseName);
                params.put("tableName", tableName);
                params.put("pageNum", pageNum != null ? pageNum : 1);
                params.put("pageSize", pageSize != null ? pageSize : 50);
                params.put("queryId", queryId);  // 添加queryId用于匹配响应

                // 6. 构建命令消息
                Map<String, Object> payload = new HashMap<>();
                payload.putAll(params);

                Map<String, Object> command = new HashMap<>();
                command.put("type", "REQUEST");
                command.put("cmd", "TABLE_DATA");
                command.put("payload", payload);

                // 7. 发送命令（使用agentCode）
                boolean sent = webSocketHandler.sendControlCommandByAgentCode(actualAgentCode, "TABLE_DATA", command);

                if (!sent) {
                    log.warn("[表数据查询] 命令发送失败: probeKey={}, databaseName={}, tableName={}",
                            probeKey, databaseName, tableName);
                    return createErrorResult("发送查询命令失败");
                }

                log.info("[表数据查询] 命令已发送: queryId={}", queryId);

                // 8. 等待响应（带超时）
                Map<String, Object> result = future.get(30, TimeUnit.SECONDS);
                log.info("[表数据查询] 收到查询结果: queryId={}", queryId);
                return result;

            } catch (TimeoutException e) {
                log.error("[表数据查询] 查询超时: queryId={}", queryId);
                return createErrorResult("查询超时，Agent未在规定时间内响应");
            } catch (InterruptedException e) {
                log.error("[表数据查询] 查询被中断: queryId={}", queryId, e);
                Thread.currentThread().interrupt();
                return createErrorResult("查询被中断");
            } finally {
                // 清理pending查询
                pendingTableDataQueries.remove(queryId);
                log.info("[表数据查询] 清理待处理查询: queryId={}, 当前pendingQueries大小={}", queryId, pendingTableDataQueries.size());
            }

        } catch (Exception e) {
            log.error("[表数据查询] 查询异常: probeKey={}, databaseName={}, tableName={}",
                    probeKey, databaseName, tableName, e);
            return createErrorResult("查询失败: " + e.getMessage());
        }
    }

    /**
     * 处理表数据查询响应（由WebSocketHandler调用）
     *
     * @param probeKey 探针标识
     * @param databaseName 数据库名称
     * @param tableName 表名
     * @param pageNum 页码
     * @param result 查询结果
     */
    public void handleTableDataResponse(String probeKey, String databaseName, String tableName, Integer pageNum, Map<String, Object> result) {
        String queryId = String.format("%s:%s:%s:%d", probeKey, databaseName, tableName, pageNum != null ? pageNum : 1);

        log.info("[表数据查询] 收到查询响应: queryId={}", queryId);

        CompletableFuture<Map<String, Object>> future = pendingTableDataQueries.get(queryId);
        if (future != null) {
            log.info("[表数据查询] 完成查询Future: queryId={}", queryId);
            future.complete(result);
        } else {
            log.warn("[表数据查询] 收到未知查询的响应: queryId={}, pendingQueries大小={}",
                    queryId, pendingTableDataQueries.size());
        }
    }

    /**
     * 创建错误结果
     */
    private Map<String, Object> createErrorResult(String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", errorMessage);
        result.put("columns", List.of());
        result.put("rows", List.of());
        result.put("total", 0);
        result.put("pageNum", 1);
        result.put("pageSize", 50);
        return result;
    }

    /**
     * 发送表数据查询命令到探针（仅发送，不等待响应）
     * 注意：此方法已废弃，请使用 queryTableData 方法
     *
     * @deprecated 请使用 queryTableData 方法
     */
    @Deprecated
    public boolean sendTableDataQuery(String probeKey, String databaseName, String tableName, Integer pageNum, Integer pageSize) {
        try {
            queryTableData(probeKey, databaseName, tableName, pageNum, pageSize);
            return true;
        } catch (Exception e) {
            log.error("[表数据查询] 发送查询命令异常", e);
            return false;
        }
    }

    /**
     * 从探针key中提取Agent代码
     * 支持多种格式:
     * - AGENT-{type} → AGENT (统一probeKey格式, 如: AGENT-postgresql, AGENT-mysql)
     * - AGENT-{type}-{random} → AGENT (如: AGENT-file-xxx, AGENT-database-xxx)
     * - AGENT-{number}-{type}-{random} → AGENT-{number} (如: AGENT-001-database-xxx)
     * - AGENT-{custom}-{type}-{random} → AGENT-{custom} (如: AGENT-server1-file-xxx)
     * - {AGENT-CODE}-{type}-{random} → {AGENT-CODE} (如: TEST-AGENT-001-database-xxx)
     *
     * @param probeKey 探针key
     * @return Agent代码，如果无法提取则返回null
     */
    private String extractAgentCode(String probeKey) {
        if (probeKey == null) {
            return null;
        }

        // 不限制分割数量，获取所有部分
        String[] parts = probeKey.split("-");

        // 特殊处理：统一probeKey格式（AGENT-postgresql, AGENT-mysql等）
        if (parts.length == 2) {
            String firstPart = parts[0];
            String secondPart = parts[1].toLowerCase();

            // 数据库类型列表
            String[] DATABASE_TYPES = {"postgresql", "mysql", "oracle", "sqlserver", "mongodb", "redis"};

            // 检查是否是数据库类型
            for (String dbType : DATABASE_TYPES) {
                if (dbType.equals(secondPart) && firstPart.contains("AGENT")) {
                    log.debug("从统一probeKey提取Agent代码: {} → {}", probeKey, firstPart);
                    return firstPart;
                }
            }
        }

        if (parts.length < 3) {
            log.debug("无法从探针key提取Agent代码（格式不正确）: {}", probeKey);
            return null;
        }

        // 探针类型关键字（用于判断中间部分是类型还是Agent编号）
        String[] PROBE_TYPES = {"file", "database", "system", "http", "ping", "port"};

        // 从前往后找到探针类型的位置
        for (int i = 1; i < parts.length; i++) {
            String currentPart = parts[i].toLowerCase();
            if (isProbeType(currentPart, PROBE_TYPES)) {
                // 找到探针类型，提取之前的所有部分作为agentCode
                StringBuilder agentCodeBuilder = new StringBuilder(parts[0]);
                for (int j = 1; j < i; j++) {
                    agentCodeBuilder.append("-").append(parts[j]);
                }
                String agentCode = agentCodeBuilder.toString();

                // 检查agentCode是否包含"AGENT"
                if (agentCode.contains("AGENT") || agentCode.contains("agent")) {
                    log.debug("从探针key提取Agent代码: {} → {}", probeKey, agentCode);
                    return agentCode;
                }
            }
        }

        log.debug("无法从探针key提取Agent代码: {}", probeKey);
        return null;
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
}
