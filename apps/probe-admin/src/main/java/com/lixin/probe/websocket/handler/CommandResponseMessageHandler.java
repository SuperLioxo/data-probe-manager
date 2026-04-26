package com.lixin.probe.websocket.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lixin.probe.dto.ProbeControlResponse;
import com.lixin.probe.entity.DatabaseProbe;
import com.lixin.probe.mapper.DatabaseProbeMapper;
import com.lixin.probe.service.ProbeControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 命令响应消息处理器
 * 处理类型为COMMAND_RESPONSE的消息
 */
@Component
public class CommandResponseMessageHandler implements MessageHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CommandResponseMessageHandler.class);

    @Autowired(required = false)
    private ProbeControlService probeControlService;

    @Autowired
    @Qualifier("decoratedProbeService")
    private com.lixin.probe.service.ProbeService probeService;

    @Autowired(required = false)
    private com.lixin.probe.service.FileProbeService fileProbeService;

    @Autowired(required = false)
    private DatabaseProbeMapper databaseProbeMapper;

    @Override
    public boolean canHandle(String type, String cmd) {
        return "COMMAND_RESPONSE".equals(cmd);
    }

    @Override
    public void handle(WebSocketSession session, String probeKey, String type, String cmd, Object payload) throws Exception {
        try {
            if (!(payload instanceof Map)) {
                log.warn("命令响应数据格式错误");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload;

            // 提取命令响应信息
            String commandId = (String) data.get("commandId");
            String status = (String) data.get("status");
            String result = (String) data.get("result");
            String errorMessage = (String) data.get("errorMessage");

            log.info("收到探针命令响应: probeKey={}, commandId={}, status={}",
                probeKey, commandId, status);

            // 处理命令响应
            if (probeControlService != null) {
                // 获取命令信息（在清理前）
                com.lixin.probe.service.ProbeControlService.CommandInfo commandInfo =
                    probeControlService.getCommandInfo(commandId);

                // 构建ProbeControlResponse对象
                boolean success = "SUCCESS".equals(status);
                String message = success ? result : errorMessage;

                ProbeControlResponse response = ProbeControlResponse.builder()
                        .commandId(commandId)
                        .success(success)
                        .message(message)
                        .build();

                probeControlService.handleCommandResponse(commandId, response);

                // 如果命令成功，更新探针状态
                if (success && commandInfo != null) {
                    updateProbeStatusBasedOnCommand(commandInfo.getCommandType(), commandInfo.getProbeKey());
                }
            } else {
                log.warn("ProbeControlService未注入，无法处理命令响应");
            }

        } catch (Exception e) {
            log.error("处理命令响应失败: probeKey={}", probeKey, e);
            throw e;
        }
    }

    @Override
    public String getHandlerName() {
        return "CommandResponseMessageHandler";
    }

    /**
     * 根据命令类型更新探针状态
     *
     * ⚠️ 重要：此方法确保START/STOP命令的状态变更持久化到数据库
     * 心跳更新不会覆盖此处设置的状态
     *
     * @param commandType 命令类型（START、STOP、RESTART）
     * @param probeKey 探针key
     */
    private void updateProbeStatusBasedOnCommand(String commandType, String probeKey) {
        try {
            String newStatus = null;

            switch (commandType) {
                case "STOP":
                    newStatus = "offline";
                    break;
                case "START":
                case "RESTART":
                    newStatus = "online";
                    break;
                default:
                    // 其他命令不需要更新状态
                    log.debug("命令类型{}不需要更新探针状态", commandType);
                    return;
            }

            if (newStatus == null) {
                return;
            }

            log.info("🎯 开始更新探针状态: probeKey={}, commandType={}, newStatus={}",
                    probeKey, commandType, newStatus);

            int updatedCount = 0;

            // 1. 更新普通探针表
            com.lixin.probe.entity.Probe probe = probeService.getByProbeKey(probeKey);
            if (probe != null) {
                String oldStatus = probe.getStatus();
                probe.setStatus(newStatus);
                probe.setLastHeartbeat(LocalDateTime.now());
                probeService.update(probe);
                log.info("✅ Probe表状态已更新: probeKey={}, {} → {}, 已持久化到数据库",
                        probeKey, oldStatus, newStatus);
                updatedCount++;
            }

            // 2. 更新文件探针表
            if (fileProbeService != null) {
                com.lixin.probe.entity.FileProbe fileProbe = fileProbeService.getByProbeKey(probeKey);
                if (fileProbe != null) {
                    String oldStatus = fileProbe.getStatus();
                    fileProbe.setStatus(newStatus);
                    fileProbe.setLastHeartbeat(LocalDateTime.now());
                    fileProbeService.update(fileProbe);
                    log.info("✅ FileProbe表状态已更新: probeKey={}, {} → {}, 已持久化到数据库",
                            probeKey, oldStatus, newStatus);
                    updatedCount++;
                }
            }

            // 3. 更新数据库探针表
            if (databaseProbeMapper != null) {
                try {
                    int dbProbeUpdated = databaseProbeMapper.update(
                        null,
                        new LambdaUpdateWrapper<DatabaseProbe>()
                            .eq(DatabaseProbe::getProbeKey, probeKey)
                            .set(DatabaseProbe::getStatus, newStatus)
                            .set(DatabaseProbe::getLastHeartbeat, LocalDateTime.now())
                    );

                    if (dbProbeUpdated > 0) {
                        log.info("✅ DatabaseProbe表状态已更新: probeKey={}, newStatus={}, 已持久化到数据库",
                                probeKey, newStatus);
                        updatedCount++;
                    } else {
                        log.debug("DatabaseProbe表中没有该探针（正常）: {}", probeKey);
                    }
                } catch (Exception e) {
                    log.error("更新DatabaseProbe表失败: {}", probeKey, e);
                }
            }

            if (updatedCount == 0) {
                log.warn("⚠️ 探针表中未找到探针: probeKey={}, 命令状态可能未持久化", probeKey);
            } else {
                log.info("🎉 探针状态更新完成: probeKey={}, newStatus={}, 更新表数={}",
                        probeKey, newStatus, updatedCount);
            }

        } catch (Exception e) {
            log.error("❌ 更新探针状态失败: probeKey={}, commandType={}", probeKey, commandType, e);
        }
    }
}
