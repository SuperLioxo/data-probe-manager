package com.lixin.probe.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lixin.probe.entity.Agent;
import com.lixin.probe.mapper.AgentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent状态同步服务
 * 定期检查Agent心跳并更新数据库中的状态
 *
 * @author Claude Code
 * @since 2.0
 */
@Service
public class AgentStatusSyncService {

    private static final Logger log = LoggerFactory.getLogger(AgentStatusSyncService.class);

    /**
     * 心跳超时时间（秒）- 90秒
     */
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 90;

    @Autowired(required = false)
    private AgentHeartbeatService agentHeartbeatService;

    @Autowired
    private AgentMapper agentMapper;

    /**
     * 定期同步Agent状态
     * 每2分钟执行一次
     */
    @Scheduled(fixedRate = 120000)  // 2分钟
    public void syncAgentStatus() {
        if (agentHeartbeatService == null) {
            log.debug("[Agent状态同步] AgentHeartbeatService未启用，跳过同步");
            return;
        }

        log.info("========== [Agent状态同步] 开始同步Agent状态 ==========");

        try {
            // 获取所有Agent
            List<Agent> allAgents = agentMapper.selectList(null);

            if (allAgents.isEmpty()) {
                log.debug("[Agent状态同步] 数据库中无Agent记录");
                return;
            }

            log.info("[Agent状态同步] 检查 {} 个Agent的状态", allAgents.size());

            int onlineCount = 0;
            int offlineCount = 0;
            int updatedCount = 0;

            for (Agent agent : allAgents) {
                String agentCode = agent.getAgentCode();
                String currentStatus = agent.getStatus();

                // 检查Agent是否在线
                boolean isOnline = agentHeartbeatService.isAgentOnline(agentCode);
                String newStatus = isOnline ? "online" : "offline";

                // 状态变化或需要更新时间戳
                if (!newStatus.equals(currentStatus) || shouldUpdateTimestamp(agent)) {
                    try {
                        // 更新Agent状态
                        agentMapper.update(null,
                            new LambdaUpdateWrapper<Agent>()
                                .eq(Agent::getAgentCode, agentCode)
                                .set(Agent::getStatus, newStatus)
                                .set(Agent::getUpdateTime, LocalDateTime.now())
                        );

                        log.info("[Agent状态同步] ✓ {} Agent状态: {} → {}, {}",
                                agentCode, currentStatus, newStatus,
                                isOnline ? "在线" : "离线");
                        updatedCount++;

                        if ("online".equals(newStatus)) {
                            onlineCount++;
                        } else {
                            offlineCount++;
                        }

                    } catch (Exception e) {
                        log.error("[Agent状态同步] ✗ 更新Agent状态失败: agentCode={}", agentCode, e);
                    }
                } else {
                    // 状态未变化
                    if ("online".equals(newStatus)) {
                        onlineCount++;
                    } else {
                        offlineCount++;
                    }
                }
            }

            log.info("[Agent状态同步] 同步完成: 总数={}, 更新={}, 在线={}, 离线={}",
                    allAgents.size(), updatedCount, onlineCount, offlineCount);

        } catch (Exception e) {
            log.error("[Agent状态同步] 同步Agent状态失败", e);
        }

        log.info("=======================================================");
    }

    /**
     * 判断是否需要更新时间戳
     * 如果最后心跳时间超过5分钟，则需要更新
     *
     * @param agent Agent实体
     * @return 是否需要更新
     */
    private boolean shouldUpdateTimestamp(Agent agent) {
        if (agent.getLastHeartbeat() == null) {
            return false;
        }

        long ageSeconds = (Instant.now().toEpochMilli() -
            agent.getLastHeartbeat()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()) / 1000;

        // 超过5分钟记录更新
        return ageSeconds > 300;
    }

    /**
     * 手动触发一次状态同步
     * 用于测试或立即同步
     */
    public void syncNow() {
        log.info("[Agent状态同步] 手动触发同步");
        syncAgentStatus();
    }
}
