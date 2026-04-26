package com.lixin.probe.websocket.handler;

import com.lixin.probe.service.AgentLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

@Component
public class LogUploadMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(LogUploadMessageHandler.class);

    @Autowired
    private AgentLogService agentLogService;

    @Override
    public boolean canHandle(String type, String cmd) {
        return "REQUEST".equals(type) && "LOG_UPLOAD".equals(cmd);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(WebSocketSession session, String probeKey, String type, String cmd, Object payload) {
        try {
            String agentCode = probeKey;
            if (payload instanceof Map) {
                Map<String, Object> payloadMap = (Map<String, Object>) payload;
                Object logsObj = payloadMap.get("logs");
                if (logsObj instanceof List) {
                    List<Map<String, String>> logs = (List<Map<String, String>>) logsObj;
                    agentLogService.storeLogs(agentCode, logs);
                    log.debug("[LogUpload] Stored {} logs from agent {}", logs.size(), agentCode);
                }
            }
        } catch (Exception e) {
            log.error("[LogUpload] Failed to process log upload from agent {}", probeKey, e);
        }
    }

    @Override
    public String getHandlerName() {
        return "LogUploadMessageHandler";
    }
}
