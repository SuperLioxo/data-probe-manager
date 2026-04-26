package com.lixin.probe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.entity.Probe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent程序心跳服务
 * 负责跟踪和管理Agent程序在线状态
 *
 * @author Claude Code
 * @date 2026-03-21
 * @version 3.0 (使用probe表判断Agent在线状态)
 */
@Service
public class AgentHeartbeatService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentHeartbeatService.class);

    /**
     * 存储每个Agent的最后心跳时间
     * key: agentCode, value: lastHeartbeatTimestamp (毫秒)
     */
    private final ConcurrentHashMap<String, Long> agentHeartbeats = new ConcurrentHashMap<>();

    /**
     * 超时时间（毫秒）- 90秒无心跳认为离线
     */
    private static final long HEARTBEAT_TIMEOUT_MS = 90 * 1000;

    @Autowired
    private ProbeMapper probeMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("[Agent心跳] 从数据库恢复Agent心跳状态...");

        try {
            // 查询最近90秒内有心跳的探针（用于判断Agent在线状态）
            java.time.LocalDateTime threshold = java.time.LocalDateTime.now()
                    .minusSeconds(HEARTBEAT_TIMEOUT_MS / 1000);

            List<Probe> recentProbes = probeMapper.selectList(
                new LambdaQueryWrapper<Probe>()
                    .isNotNull(Probe::getLastHeartbeat)
                    .ge(Probe::getLastHeartbeat, threshold)
            );

            // 从探针中提取Agent信息（探针key格式：AGENT-system-xxx 或 AGENT-database-xxx）
            for (Probe probe : recentProbes) {
                if (probe.getLastHeartbeat() != null && probe.getProbeKey() != null) {
                    String agentCode = extractAgentCode(probe.getProbeKey());
                    if (agentCode != null) {
                        // 将LocalDateTime转换为毫秒时间戳
                        long heartbeatTime = probe.getLastHeartbeat()
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli();
                        // 更新该Agent的最后心跳时间（取最新值）
                        agentHeartbeats.compute(agentCode, (code, oldTime) -> {
                            return (oldTime == null || heartbeatTime > oldTime) ? heartbeatTime : oldTime;
                        });
                    }
                }
            }

            log.info("[Agent心跳] 恢复完成，恢复了 {} 个Agent的心跳状态", agentHeartbeats.size());

        } catch (Exception e) {
            log.error("[Agent心跳] 从数据库恢复心跳状态失败", e);
            // 不抛出异常，允许服务继续启动
        }
    }

    /**
     * 从探针key中提取Agent代码
     * 支持两种格式：
     * 1. 旧格式：AGENT-database-random -> AGENT
     * 2. 新格式：TEST-AGENT-001-database-random -> TEST-AGENT-001
     */
    private String extractAgentCode(String probeKey) {
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
                    return agentCode.toString();
                }
            }
        }

        // 如果没找到探针类型，返回null（不支持的格式）
        return null;
    }

    /**
     * 更新Agent程序心跳
     *
     * @param agentCode Agent编码
     */
    public void updateAgentHeartbeat(String agentCode) {
        long now = Instant.now().toEpochMilli();
        agentHeartbeats.put(agentCode, now);
        log.debug("[Agent心跳] 更新Agent程序心跳: agentCode={}, timestamp={}", agentCode, now);
    }

    /**
     * 检查Agent是否在线（混合查询策略）
     * 优先从内存查询，如果内存中没有或过期，则从probe表查询
     *
     * @param agentCode Agent编码
     * @return true表示在线，false表示离线
     */
    public boolean isAgentOnline(String agentCode) {
        // 1. 先查内存（快速路径）
        Long memoryHeartbeat = agentHeartbeats.get(agentCode);
        if (memoryHeartbeat != null) {
            long now = Instant.now().toEpochMilli();
            if ((now - memoryHeartbeat) < HEARTBEAT_TIMEOUT_MS) {
                log.debug("[Agent心跳] 内存命中 - Agent在线: agentCode={}", agentCode);
                return true;  // 内存中有最近心跳，认为在线
            }
            // 内存中心跳已过期，继续查数据库
        }

        // 2. 内存中没有或过期，查数据库（慢速路径）
        log.debug("[Agent心跳] 内存未命中或已过期，查询数据库: agentCode={}", agentCode);

        try {
            // 查询属于该Agent的所有探针
            List<Probe> agentProbes = probeMapper.selectList(
                new LambdaQueryWrapper<Probe>()
                    .likeRight(Probe::getProbeKey, agentCode + "-")
                    .isNotNull(Probe::getLastHeartbeat)
            );

            // 获取最新的心跳时间
            Long latestHeartbeat = agentProbes.stream()
                .map(Probe::getLastHeartbeat)
                .filter(h -> h != null)
                .map(localDateTime -> localDateTime
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli())
                .max(Long::compareTo)
                .orElse(null);

            if (latestHeartbeat == null) {
                log.debug("[Agent心跳] 数据库中无心跳记录: agentCode={}", agentCode);
                return false;
            }

            long now = Instant.now().toEpochMilli();
            boolean online = (now - latestHeartbeat) < HEARTBEAT_TIMEOUT_MS;

            // 3. 如果在线，更新到内存（下次直接用内存）
            if (online) {
                agentHeartbeats.put(agentCode, latestHeartbeat);
                log.debug("[Agent心跳] 数据库查询 - Agent在线，已更新内存: agentCode={}, age={}ms",
                         agentCode, now - latestHeartbeat);
            } else {
                log.debug("[Agent心跳] 数据库查询 - Agent离线: agentCode={}, age={}ms",
                         agentCode, now - latestHeartbeat);
            }

            return online;

        } catch (Exception e) {
            log.error("[Agent心跳] 查询数据库失败: agentCode={}", agentCode, e);
            return false;
        }
    }

    /**
     * 获取Agent最后心跳时间
     *
     * @param agentCode Agent编码
     * @return 最后心跳时间（毫秒），如果不存在返回null
     */
    public Long getLastHeartbeat(String agentCode) {
        // 先从内存获取
        Long memoryHeartbeat = agentHeartbeats.get(agentCode);
        if (memoryHeartbeat != null) {
            return memoryHeartbeat;
        }

        // 内存没有，从数据库获取（仅返回，不更新内存）
        try {
            // 查询属于该Agent的所有探针
            List<Probe> agentProbes = probeMapper.selectList(
                new LambdaQueryWrapper<Probe>()
                    .likeRight(Probe::getProbeKey, agentCode + "-")
                    .isNotNull(Probe::getLastHeartbeat)
            );

            return agentProbes.stream()
                .map(Probe::getLastHeartbeat)
                .filter(h -> h != null)
                .map(localDateTime -> localDateTime
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli())
                .max(Long::compareTo)
                .orElse(null);
        } catch (Exception e) {
            log.error("[Agent心跳] 查询最后心跳时间失败: agentCode={}", agentCode, e);
            return null;
        }
    }

    /**
     * 移除Agent心跳记录
     *
     * @param agentCode Agent编码
     */
    public void removeAgent(String agentCode) {
        agentHeartbeats.remove(agentCode);
        log.info("[Agent心跳] 移除Agent心跳记录: agentCode={}", agentCode);
    }

    /**
     * 获取所有在线Agent数量
     *
     * @return 在线Agent数量
     */
    public int getOnlineAgentCount() {
        long now = Instant.now().toEpochMilli();
        return (int) agentHeartbeats.values().stream()
                .filter(timestamp -> (now - timestamp) < HEARTBEAT_TIMEOUT_MS)
                .count();
    }
}
