package com.lixin.probe.websocket.handler;

import com.lixin.probe.service.ProbeControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

/**
 * 表数据查询响应处理器
 * 处理Agent发送的表数据查询结果
 *
 * @author Claude Code
 * @date 2026-04-12
 */
@Component
public class TableDataMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(TableDataMessageHandler.class);

    @Autowired
    private ProbeControlService probeControlService;

    @Override
    public String getHandlerName() {
        return "TableDataMessageHandler";
    }

    @Override
    public boolean canHandle(String type, String cmd) {
        return "REQUEST".equalsIgnoreCase(type) && "TABLE_DATA_PUSH".equalsIgnoreCase(cmd);
    }

    @Override
    public void handle(WebSocketSession session, String probeKey, String type, String cmd, Object payload) throws Exception {
        log.info("[表数据查询] 收到TABLE_DATA_PUSH消息: probeKey={}, payload类型={}",
                probeKey, payload != null ? payload.getClass() : "null");

        if (payload == null) {
            log.warn("[表数据查询] payload为空，无法处理");
            return;
        }

        if (!(payload instanceof Map)) {
            log.warn("[表数据查询] payload类型错误，应为Map: {}", payload.getClass());
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload;

        // 提取字段
        String responseProbeKey = extractString(data, "probeKey");
        String databaseName = extractString(data, "databaseName");
        String tableName = extractString(data, "tableName");
        Integer pageNum = extractInteger(data, "pageNum");
        Map<String, Object> queryResult = extractMap(data, "queryResult");

        if (responseProbeKey == null || databaseName == null || tableName == null || queryResult == null) {
            log.warn("[表数据查询] 缺少必要字段: probeKey={}, databaseName={}, tableName={}, queryResult={}",
                    responseProbeKey, databaseName, tableName, queryResult != null ? "present" : "null");
            return;
        }

        log.info("[表数据查询] 处理查询响应: probeKey={}, databaseName={}, tableName={}, pageNum={}",
                responseProbeKey, databaseName, tableName, pageNum);

        // 通知ProbeControlService完成Future
        try {
            probeControlService.handleTableDataResponse(responseProbeKey, databaseName, tableName, pageNum, queryResult);
            log.info("[表数据查询] 查询响应已处理: probeKey={}, databaseName={}, tableName={}",
                    responseProbeKey, databaseName, tableName);
        } catch (Exception e) {
            log.error("[表数据查询] 处理查询响应失败: probeKey={}, databaseName={}, tableName={}",
                    responseProbeKey, databaseName, tableName, e);
        }
    }

    /**
     * 从Map中提取String字段
     */
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    /**
     * 从Map中提取Integer字段
     */
    private Integer extractInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从Map中提取Map字段
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
}
