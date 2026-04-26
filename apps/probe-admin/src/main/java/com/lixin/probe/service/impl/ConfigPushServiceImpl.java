package com.lixin.probe.service.impl;

import com.lixin.probe.service.ConfigPushService;
import com.lixin.probe.websocket.MetaProbeWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 配置推送服务实现
 * 通过WebSocket向Agent推送CONFIG_UPDATE命令
 *
 * @author Claude Code
 * @since 1.0
 */
@Service
public class ConfigPushServiceImpl implements ConfigPushService {

    private static final Logger log = LoggerFactory.getLogger(ConfigPushServiceImpl.class);

    @Autowired
    private MetaProbeWebSocketHandler webSocketHandler;

    @Autowired
    private com.lixin.probe.service.AgentService agentService;

    @Override
    public Map<String, Object> pushConfig(String agentCode, String configType, Map<String, Object> config) {
        log.info("推送配置到Agent: agentCode={}, configType={}", agentCode, configType);

        Map<String, Object> result = new HashMap<>();

        try {
            // 验证Agent是否存在
            com.lixin.probe.entity.Agent agent = agentService.getByAgentCode(agentCode);
            if (agent == null) {
                result.put("success", false);
                result.put("message", "Agent不存在: " + agentCode);
                return result;
            }

            // 构建CONFIG_UPDATE命令
            Map<String, Object> payload = new HashMap<>();
            payload.put("commandId", generateCommandId());
            payload.put("configType", configType);
            payload.put("config", config);
            payload.put("timestamp", System.currentTimeMillis());

            Map<String, Object> command = new HashMap<>();
            command.put("type", "COMMAND");
            command.put("cmd", "CONFIG_UPDATE");
            command.put("payload", payload);

            // 通过WebSocket发送
            boolean sent = webSocketHandler.sendControlCommandByAgentCode(agentCode, "CONFIG_UPDATE", command);

            if (sent) {
                log.info("配置推送命令已发送: agentCode={}, configType={}", agentCode, configType);
                result.put("success", true);
                result.put("message", "配置推送命令已发送到Agent");
                result.put("agentCode", agentCode);
                result.put("configType", configType);
            } else {
                log.warn("配置推送命令发送失败: agentCode={}, Agent可能不在线", agentCode);
                result.put("success", false);
                result.put("message", "配置推送失败，Agent不在线或连接异常");
            }

        } catch (Exception e) {
            log.error("推送配置到Agent失败: agentCode={}, configType={}", agentCode, configType, e);
            result.put("success", false);
            result.put("message", "推送配置失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> pushConfigToAll(String configType, Map<String, Object> config) {
        log.info("推送配置到所有在线Agent: configType={}", configType);

        Map<String, Object> result = new HashMap<>();
        List<String> successAgents = new ArrayList<>();
        List<String> failedAgents = new ArrayList<>();
        List<String> offlineAgents = new ArrayList<>();

        try {
            // 获取所有在线Agent
            List<String> onlineAgentCodes = webSocketHandler.getOnlineAgentCodes();
            log.info("当前在线Agent数量: {}", onlineAgentCodes.size());

            if (onlineAgentCodes.isEmpty()) {
                result.put("success", true);
                result.put("message", "没有在线Agent");
                result.put("totalAgents", 0);
                result.put("successCount", 0);
                result.put("failedCount", 0);
                return result;
            }

            // 逐个推送
            for (String agentCode : onlineAgentCodes) {
                try {
                    Map<String, Object> pushResult = pushConfig(agentCode, configType, config);
                    boolean success = Boolean.TRUE.equals(pushResult.get("success"));
                    if (success) {
                        successAgents.add(agentCode);
                    } else {
                        failedAgents.add(agentCode);
                    }
                } catch (Exception e) {
                    log.error("推送配置到Agent失败: agentCode={}", agentCode, e);
                    failedAgents.add(agentCode);
                }
            }

            log.info("配置批量推送完成: total={}, success={}, failed={}",
                    onlineAgentCodes.size(), successAgents.size(), failedAgents.size());

            result.put("success", true);
            result.put("message", String.format("配置推送完成: 成功%d个, 失败%d个",
                    successAgents.size(), failedAgents.size()));
            result.put("totalAgents", onlineAgentCodes.size());
            result.put("successCount", successAgents.size());
            result.put("failedCount", failedAgents.size());
            result.put("successAgents", successAgents);
            result.put("failedAgents", failedAgents);

        } catch (Exception e) {
            log.error("批量推送配置失败: configType={}", configType, e);
            result.put("success", false);
            result.put("message", "批量推送配置失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 生成命令ID
     */
    private String generateCommandId() {
        return "CONFIG-" + System.currentTimeMillis() + "-" +
                String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
