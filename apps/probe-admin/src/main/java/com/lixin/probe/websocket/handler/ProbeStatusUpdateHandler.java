package com.lixin.probe.websocket.handler;

import com.lixin.probe.entity.Probe;
import com.lixin.probe.service.ProbeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class ProbeStatusUpdateHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(ProbeStatusUpdateHandler.class);

    @Autowired(required = false)
    private ProbeService probeService;

    @Override
    public boolean canHandle(String type, String cmd) {
        return "REQUEST".equals(type) && "PROBE_STATUS_UPDATE".equals(cmd);
    }

    @Override
    public void handle(WebSocketSession session, String probeKey, String type, String cmd, Object payload) throws Exception {
        try {
            if (!(payload instanceof Map)) {
                log.warn("探针状态更新消息格式错误: payload不是Map类型");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload;

            String targetProbeKey = (String) data.get("probeKey");
            String status = (String) data.get("status");

            if (targetProbeKey == null || status == null) {
                log.warn("探针状态更新消息缺少必要字段: probeKey={}, status={}", targetProbeKey, status);
                return;
            }

            log.info("收到探针状态更新: probeKey={}, status={}", targetProbeKey, status);

            if (probeService == null) {
                log.warn("ProbeService未注入，无法更新探针状态");
                return;
            }

            Probe probe = probeService.getByProbeKey(targetProbeKey);
            if (probe == null) {
                log.warn("探针不存在: probeKey={}", targetProbeKey);
                return;
            }

            probe.setStatus(status);
            probe.setUpdateTime(LocalDateTime.now());
            if ("online".equalsIgnoreCase(status)) {
                probe.setLastHeartbeat(LocalDateTime.now());
            }

            probeService.update(probe);
            log.info("探针状态已更新: probeKey={}, status={}", targetProbeKey, status);

        } catch (Exception e) {
            log.error("处理探针状态更新失败", e);
            throw e;
        }
    }

    @Override
    public String getHandlerName() {
        return "ProbeStatusUpdateHandler";
    }
}
