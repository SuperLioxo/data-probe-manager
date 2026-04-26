package com.lixin.probe.websocket.handler;

import com.lixin.probe.entity.Probe;
import com.lixin.probe.service.AgentHeartbeatService;
import com.lixin.probe.service.DatabaseProbeService;
import com.lixin.probe.service.FileProbeService;
import com.lixin.probe.service.ProbeMonitorService;
import com.lixin.probe.service.ProbeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * 心跳消息处理器
 * 处理类型为HEARTBEAT的消息
 * 支持两种心跳：
 * 1. Agent程序心跳：当key不是有效的探针key时，视为Agent程序心跳
 * 2. 探针心跳：当key是有效的探针key时，更新探针心跳
 */
@Component
public class HeartbeatMessageHandler implements MessageHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HeartbeatMessageHandler.class);

    @Autowired(required = false)
    private ProbeMonitorService probeMonitorService;

    @Autowired(required = false)
    private AgentHeartbeatService agentHeartbeatService;

    @Autowired(required = false)
    private ProbeService probeService;

    @Autowired(required = false)
    private FileProbeService fileProbeService;

    @Lazy
    @Autowired(required = false)
    private DatabaseProbeService databaseProbeService;

    @Override
    public boolean canHandle(String type, String cmd) {
        // 处理 REQUEST 类型的心跳命令
        return "REQUEST".equals(type) && "HEARTBEAT".equals(cmd);
    }

    @Override
    public void handle(WebSocketSession session, String probeKey, String type, String cmd, Object payload) throws Exception {
        try {
            // 检查payload中是否携带探针状态信息
            Map<String, Object> payloadMap = null;
            if (payload instanceof Map) {
                payloadMap = (Map<String, Object>) payload;
            }

            Map<String, String> probeStates = null;
            if (payloadMap != null && payloadMap.containsKey("probeStates")) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, String> states = (Map<String, String>) payloadMap.get("probeStates");
                    probeStates = states;
                    log.debug("[Agent心跳] 携带 {} 个探针状态", probeStates != null ? probeStates.size() : 0);
                } catch (Exception e) {
                    log.warn("[Agent心跳] 提取探针状态失败", e);
                }
            }

            // 如果携带了探针状态信息，更新所有探针状态
            if (probeStates != null && !probeStates.isEmpty()) {
                updateProbeStates(probeStates);
            }

            // 判断是Agent程序心跳还是探针心跳（统一从 probe 表查询）
            boolean isProbeHeartbeat = false;

            if (probeService != null) {
                try {
                    Probe probe = probeService.getByProbeKey(probeKey);
                    if (probe != null) {
                        isProbeHeartbeat = true;
                        if (probeMonitorService != null) {
                            probeMonitorService.updateHeartbeat(probeKey);
                        }
                        log.debug("[探针心跳] 更新探针心跳: probeKey={}, type={}", probeKey, probe.getType());
                    }
                } catch (Exception e) {
                    log.debug("[心跳判断] 查询probe表异常: probeKey={}", probeKey);
                }
            }

            // 如果不是探针心跳，则视为Agent程序心跳
            if (!isProbeHeartbeat && agentHeartbeatService != null) {
                agentHeartbeatService.updateAgentHeartbeat(probeKey);
                log.debug("[Agent程序心跳] 更新Agent程序心跳: agentCode={}", probeKey);
            }

        } catch (Exception e) {
            log.error("处理心跳消息失败: probeKey={}", probeKey, e);
            throw e;
        }
    }

    /**
     * 更新探针状态（统一写入 probe 表）
     *
     * @param probeStates 探针状态映射 (probeKey -> stateCode)
     *                     stateCode: "running", "stopped", "error", "unknown"
     */
    private void updateProbeStates(Map<String, String> probeStates) {
        if (probeStates == null || probeStates.isEmpty()) {
            return;
        }

        try {
            for (Map.Entry<String, String> entry : probeStates.entrySet()) {
                String probeKey = entry.getKey();
                String stateCode = entry.getValue();

                try {
                    if (probeService != null) {
                        Probe probe = probeService.getByProbeKey(probeKey);
                        if (probe != null) {
                            probe.setStatus("online");
                            probe.setRunningStatus(mapStateToRunningStatus(stateCode));
                            probe.setRunningStatusUpdatedTime(java.time.LocalDateTime.now());
                            probe.setLastHeartbeat(java.time.LocalDateTime.now());
                            probeService.update(probe);
                            log.info("[探针状态更新] probe表 - probeKey={}, connection=online, running={}",
                                     probeKey, probe.getRunningStatus());
                            continue;
                        }
                    }

                    log.warn("[探针状态更新] 探针不存在: probeKey={}", probeKey);

                } catch (Exception e) {
                    log.warn("[探针状态更新] 失败: probeKey={}, state={}", probeKey, stateCode, e);
                }
            }
        } catch (Exception e) {
            log.error("[探针状态更新] 批量更新失败", e);
        }
    }

    /**
     * 将Agent状态代码映射到探针运行状态
     *
     * @param stateCode Agent状态代码
     * @return 运行状态 (running/stopped)
     */
    private String mapStateToRunningStatus(String stateCode) {
        if (stateCode == null) {
            return "stopped";
        }

        switch (stateCode.toLowerCase()) {
            case "running":
            case "starting":
                return "running";
            case "stopped":
            case "stopping":
            case "unknown":
            default:
                return "stopped";
        }
    }

    @Override
    public String getHandlerName() {
        return "HeartbeatMessageHandler";
    }
}
