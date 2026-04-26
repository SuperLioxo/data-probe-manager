package com.lixin.probe.agent.websocket;

import com.lixin.probe.agent.constant.Command;
import com.lixin.probe.agent.pojo.WebSocketMessage;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import com.lixin.probe.agent.util.CryptoUtil;
import com.lixin.probe.agent.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebSocket消息发送器
 *
 * <p>负责WebSocket消息的发送：
 * <ul>
 *   <li>发送握手消息</li>
 *   <li>发送元数据</li>
 *   <li>发送数据量信息</li>
 *   <li>发送心跳</li>
 *   <li>AES加密处理</li>
 * </ul></p>
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
public class MessageSender {

    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);
    private final ConnectionManager connectionManager;
    private final AgentConfigProvider configProvider;

    /**
     * 配置提供者接口
     */
    public interface AgentConfigProvider {
        String getCode();
        String getKey();
    }

    /**
     * 构造消息发送器
     *
     * @param connectionManager 连接管理器
     * @param configProvider 配置提供者
     */
    public MessageSender(ConnectionManager connectionManager, AgentConfigProvider configProvider) {
        this.connectionManager = connectionManager;
        this.configProvider = configProvider;
    }

    /**
     * 发送握手消息
     */
    public void sendHandshake() {
        if (!ensureConnected()) {
            return;
        }

        try {
            // 1. 构建内层消息
            WebSocketMessage<Object> innerMessage = WebSocketMessage.<Object>builder()
                    .type("REQUEST")
                    .cmd("HANDSHAKE")
                    .code(configProvider.getCode())
                    .key(configProvider.getKey())
                    .payload(null)
                    .build();

            // 2. 序列化并加密内层消息
            String innerJson = JsonUtil.toJson(innerMessage);
            String aesKey = getAesKey();
            String encryptedData = CryptoUtil.encrypt("AES", innerJson, aesKey);

            // 3. 构建外层消息（服务端期望的格式：{code, data}）
            Map<String, Object> outerMessage = new HashMap<>();
            outerMessage.put("code", configProvider.getCode());
            outerMessage.put("data", encryptedData);

            // 4. 发送
            String messageJson = JsonUtil.toJson(outerMessage);
            log.info("[发送握手] 消息内容: {}", messageJson);
            connectionManager.getSession().sendMessage(new TextMessage(messageJson));

            log.info("发送握手消息成功");

        } catch (Exception e) {
            log.error("发送握手消息失败", e);
        }
    }

    /**
     * 发送元数据
     *
     * @param metadata 元数据对象
     */
    public void sendMetadata(ProbeResponse.Metadata metadata) {
        if (!ensureConnected()) {
            return;
        }

        try {
            // 1. 构建内层消息负载（包含metadata字段）
            Map<String, Object> payloadContent = new HashMap<>();
            payloadContent.put("metadata", metadata);

            // 2. 构建内层WebSocketMessage
            WebSocketMessage<Map<String, Object>> innerMessage = WebSocketMessage.<Map<String, Object>>builder()
                    .type("REQUEST")
                    .cmd(Command.PROBE_PUSH.name())
                    .code(configProvider.getCode())
                    .key(configProvider.getKey())
                    .payload(payloadContent)
                    .build();

            // 3. 序列化内层消息
            String innerJson = JsonUtil.toJson(innerMessage);
            log.debug("内层消息大小: {} bytes", innerJson.length());

            // 4. AES 加密内层消息
            String aesKey = getAesKey();
            String encryptedData = CryptoUtil.encrypt("AES", innerJson, aesKey);

            // 5. 构建外层消息（服务端期望的格式：{code, data}）
            Map<String, Object> outerMessage = new HashMap<>();
            outerMessage.put("code", configProvider.getCode());
            outerMessage.put("data", encryptedData);

            // 6. 发送
            String messageJson = JsonUtil.toJson(outerMessage);
            connectionManager.getSession().sendMessage(new TextMessage(messageJson));

            log.info("发送元数据成功，加密后大小: {} bytes", messageJson.length());

        } catch (Exception e) {
            log.error("发送元数据失败", e);
        }
    }

    /**
     * 发送数据量信息
     *
     * @param dataSize 数据量对象
     */
    public void sendDataSize(ProbeResponse.DataSize dataSize) {
        if (!ensureConnected()) {
            return;
        }

        try {
            // 1. 构建内层消息负载（包含dataSize字段）
            Map<String, Object> payloadContent = new HashMap<>();
            payloadContent.put("dataSize", dataSize);

            // 2. 构建内层WebSocketMessage
            WebSocketMessage<Map<String, Object>> innerMessage = WebSocketMessage.<Map<String, Object>>builder()
                    .type("REQUEST")
                    .cmd(Command.PROBE_PUSH.name())
                    .code(configProvider.getCode())
                    .key(configProvider.getKey())
                    .payload(payloadContent)
                    .build();

            // 3. 序列化内层消息
            String innerJson = JsonUtil.toJson(innerMessage);

            // 4. AES 加密内层消息
            String aesKey = getAesKey();
            String encryptedData = CryptoUtil.encrypt("AES", innerJson, aesKey);

            // 5. 构建外层消息（服务端期望的格式：{code, data}）
            Map<String, Object> outerMessage = new HashMap<>();
            outerMessage.put("code", configProvider.getCode());
            outerMessage.put("data", encryptedData);

            // 6. 发送
            connectionManager.getSession().sendMessage(new TextMessage(JsonUtil.toJson(outerMessage)));

            log.info("发送数据量信息成功");

        } catch (Exception e) {
            log.error("发送数据量信息失败", e);
        }
    }

    /**
     * 发送心跳（Agent级别）
     */
    public void sendHeartbeat() {
        sendHeartbeat(null);
    }

    /**
     * 发送心跳（Agent级别，携带探针状态）
     *
     * @param probeStates 探针状态映射
     */
    public void sendHeartbeat(Map<String, String> probeStates) {
        if (!connectionManager.isConnected()) {
            log.warn("[Agent心跳] WebSocket 未连接，无法发送心跳");
            return;
        }

        try {
            // 1. 构建心跳载荷
            Map<String, Object> payload = new HashMap<>();
            if (probeStates != null && !probeStates.isEmpty()) {
                payload.put("probeStates", probeStates);
                log.debug("[Agent心跳] 携带 {} 个探针状态", probeStates.size());
            }

            // 2. 构建内层消息
            WebSocketMessage<Map<String, Object>> innerMessage = WebSocketMessage.<Map<String, Object>>builder()
                    .type("REQUEST")
                    .cmd(Command.HEARTBEAT.name())
                    .code(configProvider.getCode())
                    .key(configProvider.getKey())
                    .payload(payload)
                    .build();

            // 3. 序列化并加密内层消息
            String innerJson = JsonUtil.toJson(innerMessage);
            String aesKey = getAesKey();
            String encryptedData = CryptoUtil.encrypt("AES", innerJson, aesKey);

            // 4. 构建外层消息（服务端期望的格式：{code, data}）
            Map<String, Object> outerMessage = new HashMap<>();
            outerMessage.put("code", configProvider.getCode());
            outerMessage.put("data", encryptedData);

            // 5. 发送
            String messageJson = JsonUtil.toJson(outerMessage);
            log.debug("[Agent心跳] 发送心跳消息");
            connectionManager.getSession().sendMessage(new TextMessage(messageJson));

            log.debug("[Agent心跳] Agent 心跳发送成功");

        } catch (Exception e) {
            log.error("发送 Agent 心跳失败", e);
        }
    }

    /**
     * 为指定探针发送心跳
     *
     * @param probeKey 探针标识
     */
    public void sendHeartbeatForProbe(String probeKey) {
        if (!connectionManager.isConnected()) {
            log.warn("[探针心跳] WebSocket 未连接，无法发送探针心跳: probeKey={}", probeKey);
            return;
        }

        try {
            log.info("[探针心跳] 发送探针心跳: probeKey={}", probeKey);

            // 1. 构建内层消息
            WebSocketMessage<Object> innerMessage = WebSocketMessage.<Object>builder()
                    .type("REQUEST")
                    .cmd(Command.HEARTBEAT.name())
                    .code(configProvider.getCode())
                    .key(probeKey)  // 使用探针的 key
                    .payload(null)
                    .build();

            // 2. 序列化并加密内层消息
            String innerJson = JsonUtil.toJson(innerMessage);
            String aesKey = getAesKey();
            String encryptedData = CryptoUtil.encrypt("AES", innerJson, aesKey);

            // 3. 构建外层消息（服务端期望的格式：{code, data}）
            Map<String, Object> outerMessage = new HashMap<>();
            outerMessage.put("code", configProvider.getCode());
            outerMessage.put("data", encryptedData);

            // 4. 发送
            String messageJson = JsonUtil.toJson(outerMessage);
            connectionManager.getSession().sendMessage(new TextMessage(messageJson));

            log.info("[探针心跳] 探针心跳发送成功: probeKey={}", probeKey);

        } catch (Exception e) {
            log.error("发送探针心跳失败: probeKey={}", probeKey, e);
        }
    }

    /**
     * 发送命令响应（加密格式）
     *
     * @param commandId 命令ID
     * @param success 是否成功
     * @param message 响应消息
     */
    public void sendCommandResponse(String commandId, boolean success, String message) {
        if (!connectionManager.isConnected()) {
            log.warn("WebSocket 未连接，无法发送命令响应: commandId={}", commandId);
            return;
        }

        try {
            // 1. 构建payload内容（匹配CommandResponseMessageHandler期望的格式）
            Map<String, Object> payload = new HashMap<>();
            payload.put("commandId", commandId);
            payload.put("status", success ? "SUCCESS" : "FAILED");
            payload.put(success ? "result" : "errorMessage", message);
            payload.put("timestamp", System.currentTimeMillis());

            // 2. 构建WebSocket消息（Admin期望的格式）
            WebSocketMessage<Map<String, Object>> wsMessage = WebSocketMessage.<Map<String, Object>>builder()
                    .type("RESPONSE")
                    .cmd("COMMAND_RESPONSE")
                    .payload(payload)
                    .build();

            // 3. 序列化WebSocket消息
            String responseJson = JsonUtil.toJson(wsMessage);

            // 4. 使用probe key加密响应（使用16字节密钥）
            String aesKey = getAesKey();
            String encryptedData = CryptoUtil.encrypt("AES", responseJson, aesKey);

            // 5. 构建外层消息格式（Admin期望的格式：{code, data}）
            Map<String, Object> outerMessage = new HashMap<>();
            outerMessage.put("code", configProvider.getCode());
            outerMessage.put("data", encryptedData);

            // 6. 发送加密消息
            String messageJson = JsonUtil.toJson(outerMessage);
            connectionManager.getSession().sendMessage(new TextMessage(messageJson));

            log.info("发送命令响应成功(加密): commandId={}, status={}, message={}", commandId, success ? "SUCCESS" : "FAILED", message);

        } catch (Exception e) {
            log.error("发送命令响应失败: commandId={}", commandId, e);
        }
    }

    /**
     * 获取AES密钥（填充到16字节）
     */
    private String getAesKey() {
        return padTo16Bytes(configProvider.getKey());
    }

    /**
     * 填充或截断字符串到16字节用于AES-128
     */
    private String padTo16Bytes(String input) {
        byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] key = new byte[16];
        System.arraycopy(bytes, 0, key, 0, Math.min(bytes.length, 16));
        return new String(key, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 发送文件扫描结果
     *
     * @param fileData 文件数据对象
     */
    public void sendFileData(ProbeResponse.DataFile fileData) {
        if (!ensureConnected()) {
            return;
        }

        try {
            // 1. 构建内层消息负载（包含fileInfo字段）
            Map<String, Object> payloadContent = new HashMap<>();
            payloadContent.put("fileInfo", fileData);

            // 2. 构建内层WebSocketMessage
            WebSocketMessage<Map<String, Object>> innerMessage = WebSocketMessage.<Map<String, Object>>builder()
                    .type("REQUEST")
                    .cmd("FILE_PROBE_PUSH")
                    .code(configProvider.getCode())
                    .key(configProvider.getKey())
                    .payload(payloadContent)
                    .build();

            // 3. 序列化内层消息
            String innerJson = JsonUtil.toJson(innerMessage);
            log.debug("内层消息大小: {} bytes", innerJson.length());

            // 4. AES 加密内层消息
            String aesKey = getAesKey();
            String encryptedData = CryptoUtil.encrypt("AES", innerJson, aesKey);

            // 5. 构建外层消息（服务端期望的格式：{code, data}）
            Map<String, Object> outerMessage = new HashMap<>();
            outerMessage.put("code", configProvider.getCode());
            outerMessage.put("data", encryptedData);

            // 6. 发送
            String messageJson = JsonUtil.toJson(outerMessage);
            connectionManager.getSession().sendMessage(new TextMessage(messageJson));

            log.info("发送文件数据成功，加密后大小: {} bytes", messageJson.length());

        } catch (Exception e) {
            log.error("发送文件数据失败", e);
        }
    }

    /**
     * 发送文件扫描报告（更新统计信息）
     *
     * @param probeKey 探针Key
     * @param fileData 文件数据对象
     */
    public void sendFileScanReport(String probeKey, ProbeResponse.DataFile fileData) {
        if (!ensureConnected()) {
            return;
        }

        try {
            // 1. 构建扫描报告数据
            Map<String, Object> reportData = new HashMap<>();
            reportData.put("probeKey", probeKey);
            reportData.put("totalFileCount", fileData.getTotalFileCount());
            reportData.put("totalDirectoryCount", fileData.getTotalDirectoryCount());
            reportData.put("totalSize", fileData.getTotalSize());
            reportData.put("files", extractFilesList(fileData));
            reportData.put("directories", extractDirectoriesList(fileData));

            // 2. 构建内层WebSocketMessage
            WebSocketMessage<Map<String, Object>> innerMessage = WebSocketMessage.<Map<String, Object>>builder()
                    .type("REQUEST")
                    .cmd("FILE_SCAN_REPORT")
                    .code(configProvider.getCode())
                    .key(configProvider.getKey())
                    .payload(reportData)
                    .build();

            // 3. 序列化内层消息
            String innerJson = JsonUtil.toJson(innerMessage);
            log.debug("扫描报告消息大小: {} bytes", innerJson.length());

            // 4. AES 加密内层消息
            String aesKey = getAesKey();
            String encryptedData = CryptoUtil.encrypt("AES", innerJson, aesKey);

            // 5. 构建外层消息
            Map<String, Object> outerMessage = new HashMap<>();
            outerMessage.put("code", configProvider.getCode());
            outerMessage.put("data", encryptedData);

            // 6. 发送
            String messageJson = JsonUtil.toJson(outerMessage);
            connectionManager.getSession().sendMessage(new TextMessage(messageJson));

            log.info("发送文件扫描报告成功: files={}, directories={}, size={}",
                    fileData.getTotalFileCount(),
                    fileData.getTotalDirectoryCount(),
                    fileData.getTotalSize());

        } catch (Exception e) {
            log.error("发送文件扫描报告失败", e);
        }
    }

    /**
     * 确保连接状态
     *
     * @return 如果已连接返回true
     */
    private boolean ensureConnected() {
        if (!connectionManager.isConnected()) {
            log.warn("WebSocket 未连接，无法发送消息");
            return false;
        }
        return true;
    }

    /**
     * 从DataFile中提取文件列表（递归提取所有层级的文件）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractFilesList(ProbeResponse.DataFile fileData) {
        List<Map<String, Object>> filesList = new ArrayList<>();
        if (fileData.getDirectories() == null) {
            return filesList;
        }

        for (ProbeResponse.DataFile.Directory dir : fileData.getDirectories().values()) {
            extractFilesFromDirectory(dir, filesList);
        }
        return filesList;
    }

    /**
     * 递归提取目录中的所有文件
     */
    private void extractFilesFromDirectory(ProbeResponse.DataFile.Directory dir, List<Map<String, Object>> filesList) {
        if (dir == null) {
            return;
        }

        // 提取当前目录的文件
        if (dir.getFiles() != null) {
            String parentPath = getDirectoryPath(dir);
            for (ProbeResponse.DataFile.File file : dir.getFiles().values()) {
                String fullPath = parentPath + "/" + file.getName();
                Map<String, Object> fileMap = new HashMap<>();
                fileMap.put("name", file.getName());
                fileMap.put("path", fullPath);  // 完整路径
                fileMap.put("size", file.getSize());
                fileMap.put("extension", file.getExtension());
                fileMap.put("md5", file.getMd5());
                fileMap.put("parentPath", parentPath);  // 父目录路径
                fileMap.put("lastModified", file.getLastModified());
                filesList.add(fileMap);

                // 验证数据类型
                log.debug("添加文件: name={}, path={}, parentPath类型={}",
                    file.getName(), fullPath, parentPath.getClass().getSimpleName());
            }
        }

        // 递归提取子目录中的文件
        if (dir.getDirectories() != null) {
            for (ProbeResponse.DataFile.Directory subDir : dir.getDirectories().values()) {
                extractFilesFromDirectory(subDir, filesList);
            }
        }
    }

    /**
     * 获取目录的路径
     */
    private String getDirectoryPath(ProbeResponse.DataFile.Directory dir) {
        List<String> paths = dir.getPath();
        if (paths != null && !paths.isEmpty()) {
            return paths.get(0);
        }
        return dir.getName();
    }

    /**
     * 获取目录的父目录路径
     */
    private String getParentDirectoryPath(ProbeResponse.DataFile.Directory dir) {
        String dirPath = getDirectoryPath(dir);
        if (dirPath != null && dirPath.contains("/")) {
            int lastSlashIndex = dirPath.lastIndexOf("/");
            return dirPath.substring(0, lastSlashIndex);
        }
        return "/";
    }

    /**
     * 从DataFile中提取目录列表（递归提取所有层级的目录）
     */
    private List<Map<String, Object>> extractDirectoriesList(ProbeResponse.DataFile fileData) {
        List<Map<String, Object>> dirsList = new ArrayList<>();
        if (fileData.getDirectories() == null) {
            return dirsList;
        }

        for (ProbeResponse.DataFile.Directory dir : fileData.getDirectories().values()) {
            extractDirectoriesFromDirectory(dir, dirsList, 0);
        }
        return dirsList;
    }

    /**
     * 递归提取目录及其子目录
     */
    private void extractDirectoriesFromDirectory(ProbeResponse.DataFile.Directory dir, List<Map<String, Object>> dirsList, int depth) {
        if (dir == null) {
            return;
        }

        // 添加当前目录
        Map<String, Object> dirMap = new HashMap<>();
        dirMap.put("name", dir.getName());
        dirMap.put("path", getDirectoryPath(dir));
        dirMap.put("depth", depth);
        // 使用当前时间戳作为目录的最后修改时间（Directory对象没有lastModified字段）
        dirMap.put("lastModified", System.currentTimeMillis());
        dirMap.put("parentPath", getParentDirectoryPath(dir));
        dirsList.add(dirMap);

        // 递归添加子目录
        if (dir.getDirectories() != null) {
            for (ProbeResponse.DataFile.Directory subDir : dir.getDirectories().values()) {
                extractDirectoriesFromDirectory(subDir, dirsList, depth + 1);
            }
        }
    }

    /**
     * 获取当前Agent的Key（供外部调用）
     */
    public String getAgentKey() {
        return configProvider.getKey();
    }

    /**
     * 发送探针状态更新
     *
     * @param probeKey 探针标识
     * @param status 状态 (online/offline)
     * @param probeType 探针类型 (FILE/DATABASE/SYSTEM)
     */
    public void sendProbeStatusUpdate(String probeKey, String status, String probeType) {
        if (!ensureConnected()) {
            log.warn("[探针状态] WebSocket 未连接，无法发送状态更新: probeKey={}, status={}", probeKey, status);
            return;
        }

        try {
            log.info("[探针状态] 发送探针状态更新: probeKey={}, status={}, type={}", probeKey, status, probeType);

            // 1. 构建payload内容
            Map<String, Object> payloadContent = new HashMap<>();
            payloadContent.put("probeKey", probeKey);
            payloadContent.put("status", status);
            payloadContent.put("probeType", probeType);
            payloadContent.put("timestamp", System.currentTimeMillis());

            // 2. 构建内层WebSocketMessage
            WebSocketMessage<Map<String, Object>> innerMessage = WebSocketMessage.<Map<String, Object>>builder()
                    .type("REQUEST")
                    .cmd("PROBE_STATUS_UPDATE")
                    .code(configProvider.getCode())
                    .key(configProvider.getKey())
                    .payload(payloadContent)
                    .build();

            // 3. 序列化内层消息
            String innerJson = JsonUtil.toJson(innerMessage);

            // 4. AES 加密内层消息
            String aesKey = getAesKey();
            String encryptedData = CryptoUtil.encrypt("AES", innerJson, aesKey);

            // 5. 构建外层消息
            Map<String, Object> outerMessage = new HashMap<>();
            outerMessage.put("code", configProvider.getCode());
            outerMessage.put("data", encryptedData);

            // 6. 发送
            String messageJson = JsonUtil.toJson(outerMessage);
            connectionManager.getSession().sendMessage(new TextMessage(messageJson));

            log.info("[探针状态] 探针状态更新发送成功: probeKey={}, status={}", probeKey, status);

        } catch (Exception e) {
            log.error("[探针状态] 发送探针状态更新失败: probeKey={}, status={}", probeKey, status, e);
        }
    }

    /**
     * 发送表数据查询结果
     *
     * @param probeKey 探针标识
     * @param databaseName 数据库名称
     * @param tableName 表名
     * @param result 查询结果
     */
    public void sendTableData(String probeKey, String databaseName, String tableName, Map<String, Object> result) {
        if (!ensureConnected()) {
            log.warn("[表数据查询] WebSocket 未连接，无法发送查询结果");
            return;
        }

        try {
            log.info("[表数据查询] 发送查询结果: probeKey={}, databaseName={}, tableName={}",
                    probeKey, databaseName, tableName);

            // 1. 构建查询结果数据
            Map<String, Object> tableDataPayload = new HashMap<>();
            tableDataPayload.put("probeKey", probeKey);
            tableDataPayload.put("databaseName", databaseName);
            tableDataPayload.put("tableName", tableName);
            tableDataPayload.put("queryResult", result);

            // 2. 构建内层WebSocketMessage
            WebSocketMessage<Map<String, Object>> innerMessage = WebSocketMessage.<Map<String, Object>>builder()
                    .type("REQUEST")
                    .cmd("TABLE_DATA_PUSH")
                    .code(configProvider.getCode())
                    .key(configProvider.getKey())
                    .payload(tableDataPayload)
                    .build();

            // 3. 序列化内层消息
            String innerJson = JsonUtil.toJson(innerMessage);
            log.debug("[表数据查询] 消息大小: {} bytes", innerJson.length());

            // 4. AES 加密内层消息
            String aesKey = getAesKey();
            String encryptedData = CryptoUtil.encrypt("AES", innerJson, aesKey);

            // 5. 构建外层消息
            Map<String, Object> outerMessage = new HashMap<>();
            outerMessage.put("code", configProvider.getCode());
            outerMessage.put("data", encryptedData);

            // 6. 发送
            String messageJson = JsonUtil.toJson(outerMessage);
            connectionManager.getSession().sendMessage(new TextMessage(messageJson));

            log.info("[表数据查询] 查询结果发送成功: rows={}",
                    result.containsKey("rows") && result.get("rows") instanceof List
                        ? ((List<?>) result.get("rows")).size()
                        : 0);

        } catch (Exception e) {
            log.error("[表数据查询] 发送查询结果失败: probeKey={}, databaseName={}, tableName={}",
                    probeKey, databaseName, tableName, e);
        }
    }
}
