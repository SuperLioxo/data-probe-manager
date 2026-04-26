package com.lixin.probe.websocket;

import com.alibaba.fastjson2.JSON;
import com.lixin.probe.dto.ProbeControlResponse;
import com.lixin.probe.service.AgentHeartbeatService;
import com.lixin.probe.service.AgentService;
import com.lixin.probe.service.ProbeControlService;
import com.lixin.probe.util.CryptoUtil;
import com.lixin.probe.websocket.handler.MessageDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Meta探针WebSocket处理器（重构版）
 * 职责：连接管理、消息加解密、会话管理
 * 业务逻辑处理委托给MessageDispatcher
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (重构版)
 */
@Component
public class MetaProbeWebSocketHandler extends TextWebSocketHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MetaProbeWebSocketHandler.class);

    @Autowired(required = false)
    private ProbeControlService probeControlService;

    @Autowired
    private MessageDispatcher messageDispatcher;

    @Autowired(required = false)
    private AgentService agentService;

    @Autowired(required = false)
    private AgentHeartbeatService agentHeartbeatService;

    // 存储连接的会话，key为probeKey
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 存储连接的会话，key为code
    private final Map<String, WebSocketSession> sessionsByCode = new ConcurrentHashMap<>();

    // 存储probeKey和code的映射
    private final Map<String, String> codeToProbeKey = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String code = extractCode(session);
        String probeKey = extractProbeKey(session);

        // 要求至少code不能为空，probe_key可以后续通过消息更新
        if (code != null && !code.isEmpty()) {
            // 总是添加到sessionsByCode，无论probeKey是否为空
            sessionsByCode.put(code, session);

            // 如果有probe_key，建立完整的映射
            if (probeKey != null && !probeKey.isEmpty()) {
                sessions.put(probeKey, session);
                codeToProbeKey.put(code, probeKey);
                log.info("Meta探针WebSocket连接建立: code={}, probeKey={}", code, probeKey);
            } else {
                // 如果只有code，先建立code到session的映射，后续可以更新probe_key
                log.info("Meta探针WebSocket连接建立(待分配probe_key): code={}", code);
            }

            // 配置WebSocket会话以支持更大的消息（10MB文本，50MB二进制）
            try {
                session.setTextMessageSizeLimit(10 * 1024 * 1024);
                session.setBinaryMessageSizeLimit(50 * 1024 * 1024);
                log.debug("WebSocket会话缓冲区配置: maxTextMessageSizeLimit=10MB, maxBinaryMessageSizeLimit=50MB");
            } catch (Exception e) {
                log.warn("无法设置WebSocket会话缓冲区大小: {}", e.getMessage());
            }

            // ⭐ 自动注册或更新Agent状态（修复Agent状态不更新的问题）
            if (agentService != null) {
                try {
                    // 从URI获取主机和端口
                    URI uri = session.getUri();
                    String hostIp = "127.0.0.1";  // 默认本地IP
                    int port = 58081;  // 默认端口

                    if (uri != null && uri.getHost() != null) {
                        hostIp = uri.getHost();
                    }

                    // 尝试从查询参数获取端口
                    if (uri != null && uri.getQuery() != null) {
                        String[] params = uri.getQuery().split("&");
                        for (String param : params) {
                            if (param.startsWith("port=")) {
                                try {
                                    port = Integer.parseInt(param.substring(5));
                                } catch (NumberFormatException e) {
                                    // 使用默认端口
                                }
                            }
                        }
                    }

                    // 注册或更新Agent
                    log.info("[Agent自动注册] code={}, hostIp={}, port={}", code, hostIp, port);
                    agentService.registerOrUpdateAgent(code, "系统探针", hostIp, port, "2.0");

                    // 更新心跳
                    if (agentHeartbeatService != null) {
                        agentHeartbeatService.updateAgentHeartbeat(code);
                        log.info("[Agent自动注册] ✓ Agent心跳已更新: code={}", code);
                    }

                } catch (Exception e) {
                    log.error("[Agent自动注册] 注册Agent失败: code={}", code, e);
                }
            }

            // 发送连接确认消息
            try {
                session.sendMessage(new TextMessage(createConnectedMessage()));
            } catch (Exception e) {
                log.error("发送连接确认消息失败", e);
            }
        } else {
            log.warn("WebSocket连接缺少code参数，拒绝连接");
            session.close();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到Meta探针WebSocket消息: {}", payload);

        try {
            // 1. 解析JSON消息，获取code和加密数据
            @SuppressWarnings("unchecked")
            Map<String, Object> json = JSON.parseObject(payload, new HashMap<String, Object>().getClass());
            String code = (String) json.get("code");
            String encryptedData = (String) json.get("data");

            if (code == null || encryptedData == null) {
                log.warn("消息格式错误，缺少code或data字段");
                sendError(session, "消息格式错误");
                return;
            }

            // 2. 获取加密密钥（用于解密）
            String encryptionKey = codeToProbeKey.get(code);
            if (encryptionKey == null) {
                log.warn("未找到code对应的加密密钥: {}", code);
                sendError(session, "未找到对应的探针");
                return;
            }

            // 3. 解密消息
            String decryptedData;
            try {
                // 将encryptionKey填充或截断为16字节用于AES-128
                String paddedKey = padTo16Bytes(encryptionKey);
                decryptedData = CryptoUtil.decrypt(CryptoUtil.AES, encryptedData, paddedKey);
            } catch (Exception e) {
                log.error("解密消息失败: code={}", code, e);
                sendError(session, "解密失败");
                return;
            }

            // 4. 解析解密后的消息
            @SuppressWarnings("unchecked")
            Map<String, Object> messageObj = JSON.parseObject(decryptedData, new HashMap<String, Object>().getClass());
            String type = (String) messageObj.get("type");
            String cmd = (String) messageObj.get("cmd");
            Object data = messageObj.get("payload");

            // 5. 使用从URI中提取的code作为Agent code，而不是消息中的key字段
            // 消息中的key字段是认证密钥，不是Agent code
            String agentCode = code;  // 使用第92行提取的code

            log.info("收到Meta探针消息: type={}, cmd={}, agentCode={}", type, cmd, agentCode);

            // 6. 委托给MessageDispatcher处理业务逻辑
            messageDispatcher.dispatch(session, agentCode, type, cmd, data);

        } catch (Exception e) {
            log.error("处理Meta探针WebSocket消息失败", e);
            sendError(session, "消息处理失败: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String probeKey = extractProbeKey(session);
        String code = extractCode(session);

        if (probeKey != null) {
            sessions.remove(probeKey);
        }
        if (code != null) {
            sessionsByCode.remove(code);
            codeToProbeKey.remove(code);
        }

        log.info("Meta探针WebSocket连接关闭: probeKey={}, code={}, status={}", probeKey, code, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误: session={}", session.getId(), exception);
    }

    // ========== 发送消息方法 ==========

    /**
     * 发送消息
     */
    public boolean sendMessage(String probeKey, String message) {
        WebSocketSession session = sessions.get(probeKey);
        if (session == null || !session.isOpen()) {
            return false;
        }

        try {
            session.sendMessage(new TextMessage(message));
            return true;
        } catch (Exception e) {
            log.error("发送消息失败", e);
            return false;
        }
    }

    /**
     * 发送加密消息
     */
    public boolean sendEncryptedMessage(String probeKey, String code, String message) {
        WebSocketSession session = sessions.get(probeKey);
        if (session == null || !session.isOpen()) {
            log.warn("探针未连接: probeKey={}", probeKey);
            return false;
        }

        try {
            // 加密消息
            String key = codeToProbeKey.get(code);
            // 将密钥填充或截断为16字节用于AES-128
            String paddedKey = padTo16Bytes(key);
            String encryptedData = CryptoUtil.encrypt(CryptoUtil.AES, message, paddedKey);

            // 构建消息格式（必须包含type和code字段，Agent才能正确解析）
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "REQUEST");
            msg.put("code", code);
            msg.put("data", encryptedData);
            msg.put("notify", false);
            msg.put("request", true);
            msg.put("response", false);

            String jsonMessage = JSON.toJSONString(msg);
            session.sendMessage(new TextMessage(jsonMessage));

            log.debug("发送加密消息成功: probeKey={}", probeKey);
            return true;

        } catch (Exception e) {
            log.error("发送加密消息失败", e);
            return false;
        }
    }

    /**
     * 发送控制命令
     *
     * @param probeKey     探针标识
     * @param commandType  命令类型
     * @param command      命令对象
     * @return 是否发送成功
     */
    public boolean sendControlCommand(String probeKey, String commandType, Map<String, Object> command) {
        WebSocketSession session = sessions.get(probeKey);
        if (session == null || !session.isOpen()) {
            log.warn("探针未连接，无法发送控制命令: probeKey={}", probeKey);
            return false;
        }

        try {
            // 获取code（用于加密）
            String code = getCodeByProbeKey(probeKey);
            if (code != null) {
                // 使用加密方式发送
                return sendEncryptedMessage(probeKey, code, JSON.toJSONString(command));
            } else {
                // 使用普通方式发送
                String message = JSON.toJSONString(command);
                session.sendMessage(new TextMessage(message));
                return true;
            }
        } catch (Exception e) {
            log.error("发送控制命令失败: probeKey={}, commandType={}", probeKey, commandType, e);
            return false;
        }
    }

    /**
     * 发送控制命令（通过Agent Code）
     *
     * @param agentCode   Agent代码
     * @param commandType 命令类型
     * @param command     命令对象
     * @return 是否发送成功
     */
    public boolean sendControlCommandByAgentCode(String agentCode, String commandType, Map<String, Object> command) {
        WebSocketSession session = sessionsByCode.get(agentCode);
        if (session == null || !session.isOpen()) {
            log.warn("Agent未连接，无法发送控制命令: agentCode={}", agentCode);
            return false;
        }

        try {
            // 获取probeKey（用于加密）
            String probeKey = codeToProbeKey.get(agentCode);
            if (probeKey != null) {
                // 使用加密方式发送
                return sendEncryptedMessage(probeKey, agentCode, JSON.toJSONString(command));
            } else {
                // 使用普通方式发送
                String message = JSON.toJSONString(command);
                session.sendMessage(new TextMessage(message));
                return true;
            }
        } catch (Exception e) {
            log.error("发送控制命令失败: agentCode={}, commandType={}", agentCode, commandType, e);
            return false;
        }
    }

    /**
     * 发送采集命令
     */
    public boolean sendCollectCommand(String probeKey, String collectType) {
        // 从probeKey中提取Agent code
        // 例如：AGENT-database-148y6p-3vo -> AGENT
        // 或简短格式：test-db-1, postgres-local -> AGENT (DATABASE探针)
        String agentCode = extractAgentCodeFromProbeKey(probeKey);

        // 如果无法提取，尝试使用默认AGENT（针对DATABASE探针的简短格式）
        if (agentCode == null && probeKey != null && !probeKey.contains("AGENT")) {
            log.info("[sendCollectCommand] 检测到简短probeKey格式，使用默认AGENT: probeKey={}", probeKey);
            agentCode = "AGENT";
        }

        if (agentCode == null) {
            log.warn("无法从probeKey提取Agent code: probeKey={}", probeKey);
            return false;
        }

        // 通过Agent code查找WebSocket会话
        WebSocketSession session = sessionsByCode.get(agentCode);
        if (session == null || !session.isOpen()) {
            log.warn("Agent未连接，无法发送采集命令: agentCode={}, probeKey={}", agentCode, probeKey);
            return false;
        }

        try {
            // 构建命令消息 - 使用PROBE命令（Agent端已实现）
            Map<String, Object> command = new HashMap<>();
            command.put("type", "COMMAND");
            command.put("cmd", "PROBE");  // 使用PROBE命令，不是TRIGGER_COLLECT
            command.put("targetProbeKey", probeKey);  // 添加目标探针的key
            command.put("collectType", collectType);
            command.put("timestamp", System.currentTimeMillis());

            // 获取Agent连接时的probeKey（用于加密）
            String agentProbeKey = codeToProbeKey.get(agentCode);
            if (agentProbeKey != null) {
                // 使用加密方式发送
                log.info("发送采集命令到Agent: agentCode={}, targetProbeKey={}, collectType={}",
                         agentCode, probeKey, collectType);
                return sendEncryptedMessage(agentProbeKey, agentCode, JSON.toJSONString(command));
            } else {
                // 使用普通方式发送
                String message = JSON.toJSONString(command);
                session.sendMessage(new TextMessage(message));
                log.info("发送采集命令到Agent（未加密）: agentCode={}, targetProbeKey={}, collectType={}",
                         agentCode, probeKey, collectType);
                return true;
            }
        } catch (Exception e) {
            log.error("发送采集命令失败: probeKey={}, collectType={}", probeKey, collectType, e);
            return false;
        }
    }

    /**
     * 从probeKey中提取Agent code
     * 旧格式：AGENT-database-148y6p-3vo -> AGENT
     * 新格式：TEST-AGENT-001-database-mnrtl6-udv -> TEST-AGENT-001
     */
    private String extractAgentCodeFromProbeKey(String probeKey) {
        if (probeKey == null || !probeKey.contains("-")) {
            return null;
        }

        // 探针类型列表（小写）
        String[] PROBE_TYPES = {"file", "database", "system", "http", "ping", "port"};

        // 分割probeKey
        String[] parts = probeKey.split("-");

        // 遍历找到探针类型的位置
        for (int i = 1; i < parts.length; i++) {
            String currentPart = parts[i].toLowerCase();
            for (String probeType : PROBE_TYPES) {
                if (currentPart.equals(probeType)) {
                    // 找到探针类型，将之前的部分组合成agent code
                    StringBuilder agentCode = new StringBuilder(parts[0]);
                    for (int j = 1; j < i; j++) {
                        agentCode.append("-").append(parts[j]);
                    }
                    String result = agentCode.toString();
                    log.debug("从probeKey提取agentCode: {} → {}", probeKey, result);
                    return result;
                }
            }
        }

        // 如果没找到探针类型，返回第一部分（兼容旧格式）
        log.debug("未找到探针类型，使用第一部分作为agentCode: {}", parts[0]);
        return parts[0];
    }

    // ========== 辅助方法 ==========

    /**
     * 根据probeKey获取code
     */
    public String getCodeByProbeKey(String probeKey) {
        for (Map.Entry<String, String> entry : codeToProbeKey.entrySet()) {
            if (entry.getValue().equals(probeKey)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 获取会话数量
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * 获取所有在线探针KEY
     */
    public List<String> getOnlineProbeKeys() {
        return List.copyOf(sessions.keySet());
    }

    /**
     * 获取所有在线Agent代码
     */
    public List<String> getOnlineAgentCodes() {
        return List.copyOf(sessionsByCode.keySet());
    }

    /**
     * 检查探针是否在线
     * 支持两种情况：
     * 1. 直接连接的探针（如Agent本身）
     * 2. 通过Agent连接的子探针（如database、file、system探针）
     */
    public boolean isOnline(String probeKey) {
        log.info("[isOnline] 检查探针在线状态: probeKey={}", probeKey);
        log.info("[isOnline] 当前sessions包含: {}", sessions.keySet());
        log.info("[isOnline] 当前sessionsByCode包含: {}", sessionsByCode.keySet());

        // 首先检查是否有直接连接的会话
        WebSocketSession session = sessions.get(probeKey);
        if (session != null && session.isOpen()) {
            log.info("[isOnline] 探针直接在线: probeKey={}", probeKey);
            return true;
        }

        // 如果没有直接连接，检查是否是通过Agent连接的子探针
        // 子探针的probeKey格式可能为：
        // - AGENT-type-random（如：AGENT-database-mnrtl6-udv）
        // - {AGENT-CODE}-type-random（如：TEST-AGENT-001-database-mnrtl6-udv）
        // - 简短格式（如：test-db-1, postgres-local）- DATABASE探针使用
        String agentCode = null;

        if (probeKey != null && probeKey.contains("-") && probeKey.contains("AGENT")) {
            // 标准格式：包含"AGENT"关键字的probeKey
            agentCode = extractAgentCodeFromProbeKey(probeKey);
        } else if (probeKey != null) {
            // 简短格式：DATABASE探针（如test-db-1, postgres-local）
            // 这些探针都通过默认的"AGENT"连接
            log.info("[isOnline] 检测到简短probeKey格式，使用默认AGENT连接: probeKey={}", probeKey);
            agentCode = "AGENT";
        }

        if (agentCode != null) {
            log.info("[isOnline] 从probeKey提取agentCode: probeKey={}, agentCode={}", probeKey, agentCode);
            WebSocketSession agentSession = sessionsByCode.get(agentCode);
            log.info("[isOnline] 查找agentSession: agentCode={}, session存在={}, session打开={}",
                     agentCode, agentSession != null, agentSession != null && agentSession.isOpen());

            if (agentSession != null && agentSession.isOpen()) {
                log.info("[isOnline] Agent在线: agentCode={}, probeKey={}", agentCode, probeKey);
                return true;
            } else {
                log.warn("[isOnline] Agent离线: agentCode={}, probeKey={}", agentCode, probeKey);
            }
        }

        log.warn("[isOnline] 探针离线: probeKey={}", probeKey);
        return false;
    }

    /**
     * 根据code获取WebSocket会话
     */
    public WebSocketSession getSessionByCode(String code) {
        return sessionsByCode.get(code);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 发送错误消息
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            String message = String.format("{\"type\":\"ERROR\",\"message\":\"%s\"}", errorMessage);
            session.sendMessage(new TextMessage(message));
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        }
    }

    /**
     * 创建连接确认消息
     */
    private String createConnectedMessage() {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "CONNECTED");
        message.put("message", "连接成功");
        return JSON.toJSONString(message);
    }

    /**
     * 从URI中提取code
     */
    private String extractCode(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri != null) {
                String query = uri.getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length == 2 && "code".equals(pair[0])) {
                            return pair[1];
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("提取code失败", e);
        }
        return null;
    }

    /**
     * 从URI中提取probe_key
     */
    private String extractProbeKey(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri != null) {
                String query = uri.getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length == 2 && "probe_key".equals(pair[0])) {
                            return pair[1];
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("提取probe_key失败", e);
        }
        return null;
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
}
