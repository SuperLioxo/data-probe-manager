package com.lixin.probe.agent.websocket;

import com.alibaba.fastjson2.JSON;
import com.lixin.probe.agent.constant.Command;
import com.lixin.probe.agent.pojo.WebSocketMessage;
import com.lixin.probe.agent.util.CryptoUtil;
import com.lixin.probe.agent.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebSocket消息处理器
 *
 * <p>负责WebSocket消息的接收和处理：
 * <ul>
 *   <li>解析接收到的消息</li>
 *   <li>处理响应消息</li>
 *   <li>处理请求消息（服务端命令）</li>
 *   <li>命令分发</li>
 * </ul></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
public class MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageHandler.class);
    private final CommandHandler commandHandler;
    private final String encryptionKey;
    private final MessageSender messageSender;
    private ConfigUpdateHandler configUpdateHandler;
    private UpgradeHandler upgradeHandler;
    private final ExecutorService commandExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "command-handler");
        t.setDaemon(true);
        return t;
    });

    /**
     * 命令处理器接口
     */
    public interface CommandHandler {
        /**
         * 处理元数据采集命令
         */
        void handleProbeCommand();

        /**
         * 处理文件扫描命令
         */
        void handleFileProbeCommand();

        /**
         * 处理文件扫描命令（指定路径）
         * @param scanPath 要扫描的路径
         */
        void handleFileProbeCommand(String scanPath);

        /**
         * 处理系统监控命令
         */
        void handleMonitorRequest();

        /**
         * 处理启动探针命令（启动所有探针）
         */
        void handleStartCommand();

        /**
         * 处理启动探针命令（启动指定探针）
         * @param probeKey 探针标识
         * @param probeType 探针类型 (SYSTEM/DATABASE/FILE)
         */
        void handleStartCommand(String probeKey, String probeType);

        /**
         * 处理停止探针命令（停止所有探针）
         */
        void handleStopCommand();

        /**
         * 处理停止探针命令（停止指定探针）
         * @param probeKey 探针标识
         * @param probeType 探针类型 (SYSTEM/DATABASE/FILE)
         */
        void handleStopCommand(String probeKey, String probeType);

        /**
         * 处理重启探针命令（重启所有探针）
         */
        void handleRestartCommand();

        /**
         * 处理重启探针命令（重启指定探针）
         * @param probeKey 探针标识
         * @param probeType 探针类型 (SYSTEM/DATABASE/FILE)
         */
        void handleRestartCommand(String probeKey, String probeType);

        /**
         * 处理表数据查询命令
         * @param params 查询参数
         */
        void handleTableDataQuery(java.util.Map<String, Object> params);
    }

    /**
     * 构造消息处理器
     *
     * @param commandHandler 命令处理器
     * @param encryptionKey 加密密钥
     * @param messageSender 消息发送器
     */
    public MessageHandler(CommandHandler commandHandler, String encryptionKey, MessageSender messageSender) {
        this.commandHandler = commandHandler;
        this.encryptionKey = encryptionKey;
        this.messageSender = messageSender;
    }

    /**
     * 设置配置热更新处理器
     *
     * @param configUpdateHandler 配置热更新处理器
     */
    public void setConfigUpdateHandler(ConfigUpdateHandler configUpdateHandler) {
        this.configUpdateHandler = configUpdateHandler;
    }

    /**
     * 设置升级处理器
     *
     * @param upgradeHandler 升级处理器
     */
    public void setUpgradeHandler(UpgradeHandler upgradeHandler) {
        this.upgradeHandler = upgradeHandler;
    }

    /**
     * 处理WebSocket消息
     *
     * @param session WebSocket会话
     * @param springMessage Spring消息对象
     */
    public void handleMessage(WebSocketSession session, org.springframework.web.socket.WebSocketMessage<?> springMessage) {
        if (!(springMessage instanceof TextMessage)) {
            log.warn("不支持的消息类型: {}", springMessage.getClass());
            return;
        }

        TextMessage textMessage = (TextMessage) springMessage;
        String payload = textMessage.getPayload();

        // 打印原始消息以便调试
        log.info("收到 WebSocket 消息（原始）: {}", payload);

        try {
            // 解析消息
            WebSocketMessage<?> wsMessage = JsonUtil.fromJson(payload, WebSocketMessage.class);

            // 检查是否为加密消息（包含data字段）
            String encryptedData = extractEncryptedData(payload);
            if (encryptedData != null) {
                log.info("检测到加密消息，尝试解密...");
                // 解密消息（使用配置的加密密钥）
                String decryptedMessage = decryptMessage(encryptedData, encryptionKey);
                if (decryptedMessage != null) {
                    log.info("解密成功，原始消息: {}", decryptedMessage);

                    // 解析解密后的消息
                    WebSocketMessage<?> decryptedWsMessage = JsonUtil.fromJson(decryptedMessage, WebSocketMessage.class);
                    log.info("解析后的消息对象: type={}, cmd={}, payload={}",
                            decryptedWsMessage.getType(), decryptedWsMessage.getCmd(), decryptedWsMessage.getPayload());

                    // 处理解密后的消息
                    if ("REQUEST".equals(decryptedWsMessage.getType()) || "COMMAND".equals(decryptedWsMessage.getType())) {
                        handleRequest(decryptedWsMessage);
                    } else if ("RESPONSE".equals(decryptedWsMessage.getType())) {
                        handleResponse(decryptedWsMessage);
                    } else if ("ERROR".equals(decryptedWsMessage.getType())) {
                        handleError(decryptedWsMessage);
                    } else {
                        log.warn("未知的消息类型: {}", decryptedWsMessage.getType());
                    }
                    return;
                } else {
                    log.error("解密失败，加密密钥长度: {}", encryptionKey != null ? encryptionKey.length() : "null");
                }
            }

            // 非加密消息，直接处理
            if ("RESPONSE".equals(wsMessage.getType())) {
                handleResponse(wsMessage);
            } else if ("REQUEST".equals(wsMessage.getType()) || "COMMAND".equals(wsMessage.getType())) {
                handleRequest(wsMessage);
            } else if ("ERROR".equals(wsMessage.getType())) {
                handleError(wsMessage);
            } else {
                log.warn("未知消息类型: {}", wsMessage.getType());
            }

        } catch (Exception e) {
            log.error("处理消息失败", e);
        }
    }

    /**
     * 提取加密数据
     *
     * @param payload 消息载荷
     * @return 加密数据，如果不存在则返回null
     */
    private String extractEncryptedData(String payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = JSON.parseObject(payload, new HashMap<String, Object>().getClass());
            return (String) json.get("data");
        } catch (Exception e) {
            log.debug("提取加密数据失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解密消息
     *
     * @param encryptedData 加密数据
     * @param code Agent代码
     * @return 解密后的消息，失败返回null
     */
    private String decryptMessage(String encryptedData, String code) {
        try {
            // 将code填充或截断为16字节用于AES-128
            String paddedKey = padTo16Bytes(code);
            return CryptoUtil.decrypt(CryptoUtil.AES, encryptedData, paddedKey);
        } catch (Exception e) {
            log.error("解密消息失败: code={}", code, e);
            return null;
        }
    }

    /**
     * 处理响应消息
     *
     * @param message WebSocket消息
     */
    private void handleResponse(WebSocketMessage<?> message) {
        log.info("收到响应: cmd={}, code={}", message.getCmd(), message.getCode());

        try {
            Command cmd = Command.valueOf(message.getCmd());
            switch (cmd) {
                case PROBE_PUSH:
                    log.info("元数据推送成功");
                    break;
                case HEARTBEAT:
                    log.debug("心跳响应");
                    break;
                default:
                    log.warn("未知命令响应: {}", message.getCmd());
            }
        } catch (IllegalArgumentException e) {
            log.warn("无效的命令: {}", message.getCmd());
        }
    }

    /**
     * 处理错误消息
     *
     * @param message WebSocket消息
     */
    private void handleError(WebSocketMessage<?> message) {
        log.error("收到服务端错误消息: code={}, message={}", message.getCode(), message.getPayload());

        // 提取错误消息
        String errorMessage = null;
        if (message.getPayload() instanceof String) {
            errorMessage = (String) message.getPayload();
        } else if (message.getPayload() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) message.getPayload();
            errorMessage = (String) payload.get("message");
        }

        if (errorMessage != null) {
            log.error("服务端错误详情: {}", errorMessage);
        } else {
            log.error("服务端错误（payload 类型: {}）", message.getPayload().getClass());
        }
    }

    /**
     * 处理请求消息（服务端下发的命令）
     *
     * @param message WebSocket消息
     */
    private void handleRequest(WebSocketMessage<?> message) {
        log.info("收到请求: cmd={}, payload类型={}, payload内容={}",
                 message.getCmd(),
                 message.getPayload() != null ? message.getPayload().getClass() : "null",
                 message.getPayload());

        // 提取commandId（如果有的话）
        String commandId = extractCommandId(message);

        // 异步执行命令，避免阻塞WebSocket消息处理线程
        commandExecutor.submit(() -> {
            try {
                Command cmd = Command.valueOf(message.getCmd());
                switch (cmd) {
                case PROBE:
                    // 服务端请求采集元数据
                    log.info("收到元数据采集请求");
                    commandHandler.handleProbeCommand();
                    break;

                case FILE_PROBE:
                    // 服务端请求扫描文件
                    log.info("收到文件扫描请求");

                    // 从payload中提取scanPath参数
                    String scanPath = extractScanPath(message.getPayload());
                    if (scanPath != null && !scanPath.isEmpty()) {
                        log.info("使用指定扫描路径: {}", scanPath);
                        commandHandler.handleFileProbeCommand(scanPath);
                    } else {
                        log.info("使用默认配置的扫描路径");
                        commandHandler.handleFileProbeCommand();
                    }
                    break;

                case MONITOR_REQUEST:
                    // 服务端请求系统监控数据
                    log.info("收到系统监控请求");
                    commandHandler.handleMonitorRequest();
                    break;

                case START:
                    // 服务端请求启动探针
                    log.info("收到启动探针请求");
                    log.info("Message payload类型: {}", message.getPayload() != null ? message.getPayload().getClass() : "null");
                    log.info("Message payload内容: {}", message.getPayload());

                    // 提取目标探针信息
                    String startProbeKey = extractTargetProbeKey(message.getPayload());
                    String startProbeType = extractTargetModule(message.getPayload());

                    // 如果没有targetModule但存在targetProbeKey，从probeKey推断类型
                    if (startProbeKey != null && startProbeType == null) {
                        startProbeType = inferProbeTypeFromKey(startProbeKey);
                        log.info("从probeKey推断探针类型: probeKey={}, inferredType={}", startProbeKey, startProbeType);
                    }

                    log.info("提取结果: startProbeKey={}, startProbeType={}", startProbeKey, startProbeType);

                    if (startProbeKey != null && startProbeType != null) {
                        log.info("启动指定探针: probeKey={}, type={}", startProbeKey, startProbeType);
                        commandHandler.handleStartCommand(startProbeKey, startProbeType);
                        sendCommandResponse(commandId, true, "探针 " + startProbeKey + " 启动成功");
                    } else {
                        log.info("启动所有探针（原因：startProbeKey或startProbeType为null）");
                        commandHandler.handleStartCommand();
                        sendCommandResponse(commandId, true, "所有探针启动成功");
                    }
                    break;

                case STOP:
                    // 服务端请求停止探针
                    log.info("收到停止探针请求");
                    // 提取目标探针信息
                    String stopProbeKey = extractTargetProbeKey(message.getPayload());
                    String stopProbeType = extractTargetModule(message.getPayload());

                    // 如果没有targetModule但存在targetProbeKey，从probeKey推断类型
                    if (stopProbeKey != null && stopProbeType == null) {
                        stopProbeType = inferProbeTypeFromKey(stopProbeKey);
                        log.info("从probeKey推断探针类型: probeKey={}, inferredType={}", stopProbeKey, stopProbeType);
                    }

                    if (stopProbeKey != null && stopProbeType != null) {
                        log.info("停止指定探针: probeKey={}, type={}", stopProbeKey, stopProbeType);
                        commandHandler.handleStopCommand(stopProbeKey, stopProbeType);
                        sendCommandResponse(commandId, true, "探针 " + stopProbeKey + " 停止成功");
                    } else {
                        log.info("停止所有探针（原因：stopProbeKey或stopProbeType为null）");
                        commandHandler.handleStopCommand();
                        sendCommandResponse(commandId, true, "所有探针停止成功");
                    }
                    break;

                case RESTART:
                    // 服务端请求重启探针
                    log.info("收到重启探针请求");
                    // 提取目标探针信息
                    String restartProbeKey = extractTargetProbeKey(message.getPayload());
                    String restartProbeType = extractTargetModule(message.getPayload());

                    // 如果没有targetModule但存在targetProbeKey，从probeKey推断类型
                    if (restartProbeKey != null && restartProbeType == null) {
                        restartProbeType = inferProbeTypeFromKey(restartProbeKey);
                        log.info("从probeKey推断探针类型: probeKey={}, inferredType={}", restartProbeKey, restartProbeType);
                    }

                    if (restartProbeKey != null && restartProbeType != null) {
                        log.info("重启指定探针: probeKey={}, type={}", restartProbeKey, restartProbeType);
                        commandHandler.handleRestartCommand(restartProbeKey, restartProbeType);
                        sendCommandResponse(commandId, true, "探针 " + restartProbeKey + " 重启成功");
                    } else {
                        log.info("重启所有探针（原因：restartProbeKey或restartProbeType为null）");
                        commandHandler.handleRestartCommand();
                        sendCommandResponse(commandId, true, "所有探针重启成功");
                    }
                    break;

                case UPDATE_DB_CONFIG:
                    // 服务端请求更新数据库配置
                    log.info("收到更新数据库配置请求");
                    handleUpdateDbConfig(message, commandId);
                    break;

                case CONFIG_UPDATE:
                    // 服务端请求配置热更新
                    log.info("收到配置热更新请求");
                    handleConfigUpdateCommand(message, commandId);
                    break;

                case TABLE_DATA:
                    // 服务端请求表数据查询
                    log.info("收到表数据查询请求");
                    handleTableDataQuery(message);
                    break;

                case SHUTDOWN:
                    // 服务端请求优雅关闭Agent
                    log.info("收到Agent关闭请求");
                    handleAgentShutdown(message, commandId);
                    break;

                case UPGRADE:
                    // 服务端请求Agent升级
                    log.info("收到Agent升级请求");
                    handleUpgradeCommand(message, commandId);
                    break;

                default:
                    log.warn("未知命令: {}", message.getCmd());
                    sendCommandResponse(commandId, false, "未知命令: " + message.getCmd());
            }

        } catch (IllegalArgumentException e) {
            log.warn("无效的命令: {}", message.getCmd());
            sendCommandResponse(commandId, false, "无效的命令: " + message.getCmd());
        } catch (Exception e) {
            log.error("执行命令失败", e);
            sendCommandResponse(commandId, false, "执行命令失败: " + e.getMessage());
        }
        });
    }

    /**
     * 处理配置热更新命令
     */
    @SuppressWarnings("unchecked")
    private void handleConfigUpdateCommand(WebSocketMessage<?> message, String commandId) {
        try {
            if (configUpdateHandler == null) {
                log.warn("ConfigUpdateHandler未设置，无法处理配置热更新");
                sendCommandResponse(commandId, false, "ConfigUpdateHandler未初始化");
                return;
            }

            Object payload = message.getPayload();
            if (payload instanceof Map) {
                Map<String, Object> configPayload = (Map<String, Object>) payload;

                // 如果payload包含嵌套的config字段，提取出来
                Map<String, Object> actualPayload = configPayload;
                if (configPayload.containsKey("configType") && configPayload.get("config") instanceof Map) {
                    actualPayload = configPayload;
                } else if (configPayload.containsKey("config") && !(configPayload.containsKey("configType"))) {
                    // 如果只有config字段没有configType，提取config作为payload
                    actualPayload = (Map<String, Object>) configPayload.get("config");
                    if (actualPayload == null) {
                        actualPayload = configPayload;
                    }
                }

                log.info("处理CONFIG_UPDATE命令: configType={}, configKeys={}",
                        actualPayload.get("configType"), actualPayload.keySet());

                Map<String, Object> result = configUpdateHandler.handleConfigUpdate(actualPayload);
                boolean success = Boolean.TRUE.equals(result.get("success"));
                String resultMessage = (String) result.get("message");
                sendCommandResponse(commandId, success, resultMessage);

                log.info("CONFIG_UPDATE命令处理完成: success={}, message={}", success, resultMessage);
            } else {
                log.warn("CONFIG_UPDATE payload格式错误，应为Map类型");
                sendCommandResponse(commandId, false, "配置格式错误");
            }
        } catch (Exception e) {
            log.error("处理CONFIG_UPDATE命令失败", e);
            sendCommandResponse(commandId, false, "配置更新失败: " + e.getMessage());
        }
    }

    /**
     * 提取commandId
     *
     * @param message WebSocket消息
     * @return commandId，如果不存在则返回null
     */
    private String extractCommandId(WebSocketMessage<?> message) {
        try {
            if (message.getPayload() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = (Map<String, Object>) message.getPayload();
                return (String) payload.get("commandId");
            }
        } catch (Exception e) {
            log.debug("提取commandId失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 提取scanPath参数
     *
     * @param payload 消息payload
     * @return scanPath，如果不存在则返回null
     */
    private String extractScanPath(Object payload) {
        if (payload != null) {
            try {
                if (payload instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) payload;
                    Object scanPathObj = map.get("scanPath");
                    if (scanPathObj != null) {
                        return scanPathObj.toString();
                    }
                }
            } catch (Exception e) {
                log.debug("提取scanPath失败: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 提取目标探针Key
     *
     * @param payload 消息payload
     * @return targetProbeKey，如果不存在则返回null
     */
    private String extractTargetProbeKey(Object payload) {
        if (payload != null) {
            try {
                if (payload instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) payload;
                    log.info("Payload Map内容: {}", map);
                    log.info("Payload Map的keys: {}", map.keySet());
                    Object targetProbeKeyObj = map.get("targetProbeKey");
                    if (targetProbeKeyObj != null) {
                        log.info("成功提取targetProbeKey: {}", targetProbeKeyObj);
                        return targetProbeKeyObj.toString();
                    } else {
                        log.warn("Payload Map中没有targetProbeKey字段");
                    }
                } else {
                    log.warn("Payload不是Map类型，而是: {}", payload.getClass());
                }
            } catch (Exception e) {
                log.debug("提取targetProbeKey失败: {}", e.getMessage());
            }
        } else {
            log.warn("Payload为null");
        }
        return null;
    }

    /**
     * 提取目标模块类型
     *
     * @param payload 消息payload
     * @return targetModule，如果不存在则返回null
     */
    private String extractTargetModule(Object payload) {
        if (payload != null) {
            try {
                if (payload instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) payload;
                    Object targetModuleObj = map.get("targetModule");
                    if (targetModuleObj != null) {
                        log.info("成功提取targetModule: {}", targetModuleObj);
                        return targetModuleObj.toString();
                    } else {
                        log.warn("Payload Map中没有targetModule字段");
                    }
                }
            } catch (Exception e) {
                log.debug("提取targetModule失败: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 从探针key推断探针类型
     *
     * @param probeKey 探针key (如: AGENT-system-xxx, AGENT-database-xxx, AGENT-file-xxx)
     * @return 探针类型 (SYSTEM/DATABASE/FILE)，如果无法推断则返回null
     */
    private String inferProbeTypeFromKey(String probeKey) {
        if (probeKey == null) {
            return null;
        }

        String lowerKey = probeKey.toLowerCase();
        if (lowerKey.contains("-system-") || lowerKey.contains("-system")) {
            return "SYSTEM";
        } else if (lowerKey.contains("-database-") || lowerKey.contains("-database")) {
            return "DATABASE";
        } else if (lowerKey.contains("-file-") || lowerKey.contains("-file")) {
            return "FILE";
        }

        log.warn("无法从probeKey推断探针类型: {}", probeKey);
        return null;
    }

    /**
     * 从Map中提取scanPath
     *
     * @param map 消息Map
     * @return scanPath，如果不存在则返回null
     */
    @SuppressWarnings("unchecked")
    private String extractScanPathFromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Object scanPathObj = map.get("scanPath");
        if (scanPathObj != null) {
            return scanPathObj.toString();
        }
        return null;
    }

    /**
     * 发送命令响应
     *
     * @param commandId 命令ID
     * @param success 是否成功
     * @param message 响应消息
     */
    private void sendCommandResponse(String commandId, boolean success, String message) {
        if (commandId == null) {
            log.warn("commandId为空，无法发送响应");
            return;
        }

        try {
            // 使用MessageSender发送响应
            messageSender.sendCommandResponse(commandId, success, message);
        } catch (Exception e) {
            log.error("发送命令响应失败: commandId={}", commandId, e);
        }
    }

    /**
     * 填充或截断字符串到16字节用于AES-128
     */
    private String padTo16Bytes(String input) {
        if (input == null) {
            return null;
        }
        byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] key = new byte[16];
        System.arraycopy(bytes, 0, key, 0, Math.min(bytes.length, 16));
        return new String(key, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 处理更新数据库配置命令
     */
    private void handleUpdateDbConfig(WebSocketMessage<?> message, String commandId) {
        try {
            Object payload = message.getPayload();
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) payload;

                // 获取配置内容（可能在config字段中，也可能直接在payload中）
                @SuppressWarnings("unchecked")
                Map<String, Object> dbConfig = config.containsKey("config") ?
                    (Map<String, Object>) config.get("config") : config;

                log.info("处理UPDATE_DB_CONFIG命令: {}", dbConfig);

                // 通过commandHandler来处理（需要添加到CommandHandler接口）
                // 或者直接处理这个特殊命令
                // 由于这是DatabaseModule特定的命令，我们不需要通过CommandHandler
                // 我们记录日志并发送响应，实际处理由DatabaseModule的onCommand完成
                sendCommandResponse(commandId, true, "数据库配置更新命令已接收");

                log.info("UPDATE_DB_CONFIG命令已记录，等待DatabaseModule处理");
            } else {
                log.warn("UPDATE_DB_CONFIG payload格式错误");
                sendCommandResponse(commandId, false, "配置格式错误");
            }
        } catch (Exception e) {
            log.error("处理UPDATE_DB_CONFIG命令失败", e);
            sendCommandResponse(commandId, false, "处理失败: " + e.getMessage());
        }
    }

    /**
     * 处理表数据查询命令
     */
    private void handleTableDataQuery(WebSocketMessage<?> message) {
        try {
            Object payload = message.getPayload();
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) payload;

                log.info("处理TABLE_DATA命令: params={}", params);
                commandHandler.handleTableDataQuery(params);
            } else {
                log.warn("TABLE_DATA payload格式错误，应为Map类型");
            }
        } catch (Exception e) {
            log.error("处理TABLE_DATA命令失败", e);
        }
    }

    /**
     * 处理Agent关闭命令
     */
    private void handleAgentShutdown(WebSocketMessage<?> message, String commandId) {
        try {
            log.info("========== [Agent关闭] 开始处理 ==========");
            log.info("收到SHUTDOWN命令，准备优雅关闭Agent");

            // 发送命令响应
            sendCommandResponse(commandId, true, "Agent即将关闭");

            // 记录日志
            log.info("✓ SHUTDOWN命令已确认，Agent将在3秒后关闭");

            // 延迟3秒后关闭，给响应发送留出时间
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    log.info("========== [Agent关闭] 开始优雅关闭 ==========");

                    log.info("正在停止所有探针模块...");
                    // 这里的cleanup会在Spring Boot关闭时自动执行

                    log.info("Agent即将退出...");
                    Thread.sleep(1000);

                    // 优雅退出JVM
                    log.info("✓ Agent已关闭");
                    System.exit(0);

                } catch (Exception e) {
                    log.error("关闭Agent失败", e);
                    System.exit(1);
                }
            }, "AgentShutdownThread").start();

        } catch (Exception e) {
            log.error("处理SHUTDOWN命令失败", e);
            sendCommandResponse(commandId, false, "处理关闭命令失败: " + e.getMessage());
        }
    }

    /**
     * 处理Agent升级命令
     */
    @SuppressWarnings("unchecked")
    private void handleUpgradeCommand(WebSocketMessage<?> message, String commandId) {
        try {
            if (upgradeHandler == null) {
                log.warn("UpgradeHandler未设置，无法处理升级命令");
                sendCommandResponse(commandId, false, "UpgradeHandler未初始化");
                return;
            }

            Object payload = message.getPayload();
            if (payload instanceof Map) {
                Map<String, Object> upgradePayload = (Map<String, Object>) payload;

                // The outer message wraps payload inside a "payload" key
                Map<String, Object> actualPayload = upgradePayload;
                if (upgradePayload.containsKey("payload") && upgradePayload.get("payload") instanceof Map) {
                    actualPayload = (Map<String, Object>) upgradePayload.get("payload");
                }

                log.info("处理UPGRADE命令: targetVersion={}", actualPayload.get("targetVersion"));
                sendCommandResponse(commandId, true, "升级命令已接收，开始下载");

                upgradeHandler.handleUpgrade(actualPayload);
            } else {
                log.warn("UPGRADE payload格式错误，应为Map类型");
                sendCommandResponse(commandId, false, "升级命令格式错误");
            }
        } catch (Exception e) {
            log.error("处理UPGRADE命令失败", e);
            sendCommandResponse(commandId, false, "升级处理失败: " + e.getMessage());
        }
    }
}
