package com.lixin.probe.controller;

import com.lixin.probe.common.Result;
import com.lixin.probe.entity.Agent;
import com.lixin.probe.service.AgentControlService;
import com.lixin.probe.service.AgentService;
import com.lixin.probe.util.ValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent控制Controller
 * 处理Agent的启动、停止、重启操作
 *
 * @author Claude Code
 * @since 2.0
 */
@RestController
@RequestMapping("/api/agent-control")
public class AgentControlController {

    private static final Logger log = LoggerFactory.getLogger(AgentControlController.class);

    @Autowired
    private AgentControlService agentControlService;

    @Autowired
    private AgentService agentService;

    /**
     * 启动Agent
     *
     * @param agentCode Agent代码
     * @return 启动结果
     */
    @PostMapping("/{agentCode}/start")
    public Result<Map<String, Object>> startAgent(@PathVariable String agentCode) {
        Result<Void> error = ValidationUtil.validateNotEmpty(agentCode, "Agent代码");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        log.info("========== [Agent控制] 请求启动Agent ==========");
        log.info("agentCode: {}", agentCode);

        try {
            // 检查Agent状态
            Agent agent = agentService.getByAgentCode(agentCode);
            if (agent == null) {
                log.warn("Agent不存在: agentCode={}", agentCode);
                return Result.error("Agent不存在，请先让Agent启动并注册");
            }

            boolean isOnline = "online".equals(agent.getStatus());
            log.info("Agent当前状态: agentCode={}, online={}", agentCode, isOnline);

            Map<String, Object> result = new HashMap<>();

            if (isOnline) {
                log.info("Agent已在线，无需启动");
                result.put("success", true);
                result.put("message", "Agent已在线，无需启动");
                result.put("agentCode", agentCode);
                result.put("status", "online");
                result.put("alreadyRunning", true);
                return Result.success(result);
            }

            // Agent离线，需要通过systemd或脚本启动
            log.info("Agent离线，尝试通过系统服务启动");

            Map<String, Object> startResult = agentControlService.startAgentSystem(agentCode);

            if ((Boolean) startResult.get("success")) {
                log.info("✓ Agent启动命令已发送");
                result.putAll(startResult);
                result.put("agentCode", agentCode);
                return Result.success(result);
            } else {
                log.warn("✗ Agent启动失败");
                return Result.error((String) startResult.get("message"));
            }

        } catch (Exception e) {
            log.error("启动Agent失败", e);
            return Result.error("启动Agent失败: " + e.getMessage());
        }
    }

    /**
     * 停止Agent
     *
     * @param agentCode Agent代码
     * @return 停止结果
     */
    @PostMapping("/{agentCode}/stop")
    public Result<Map<String, Object>> stopAgent(@PathVariable String agentCode) {
        Result<Void> error = ValidationUtil.validateNotEmpty(agentCode, "Agent代码");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        log.info("========== [Agent控制] 请求停止Agent ==========");
        log.info("agentCode: {}", agentCode);

        try {
            // 检查Agent状态
            Agent agent = agentService.getByAgentCode(agentCode);
            if (agent == null) {
                log.warn("Agent不存在: agentCode={}", agentCode);
                return Result.error("Agent不存在");
            }

            boolean isOnline = "online".equals(agent.getStatus());
            log.info("Agent当前状态: agentCode={}, online={}", agentCode, isOnline);

            Map<String, Object> result = new HashMap<>();

            if (!isOnline) {
                log.info("Agent已离线，无需停止");
                result.put("success", true);
                result.put("message", "Agent已离线，无需停止");
                result.put("agentCode", agentCode);
                result.put("status", "offline");
                result.put("alreadyStopped", true);
                return Result.success(result);
            }

            // Agent在线，发送停止命令
            log.info("Agent在线，发送停止命令");
            Map<String, Object> stopResult = agentControlService.stopAgentGraceful(agentCode);

            if ((Boolean) stopResult.get("success")) {
                log.info("✓ Agent停止命令已发送");
                result.putAll(stopResult);
                result.put("agentCode", agentCode);
                return Result.success(result);
            } else {
                log.warn("✗ Agent停止命令发送失败");
                return Result.error((String) stopResult.get("message"));
            }

        } catch (Exception e) {
            log.error("停止Agent失败", e);
            return Result.error("停止Agent失败: " + e.getMessage());
        }
    }

    /**
     * 重启Agent
     *
     * @param agentCode Agent代码
     * @return 重启结果
     */
    @PostMapping("/{agentCode}/restart")
    public Result<Map<String, Object>> restartAgent(@PathVariable String agentCode) {
        Result<Void> error = ValidationUtil.validateNotEmpty(agentCode, "Agent代码");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        log.info("========== [Agent控制] 请求重启Agent ==========");
        log.info("agentCode: {}", agentCode);

        try {
            // 检查Agent状态
            Agent agent = agentService.getByAgentCode(agentCode);
            if (agent == null) {
                log.warn("Agent不存在: agentCode={}", agentCode);
                return Result.error("Agent不存在，请先让Agent启动并注册");
            }

            boolean isOnline = "online".equals(agent.getStatus());
            log.info("Agent当前状态: agentCode={}, online={}", agentCode, isOnline);

            Map<String, Object> result = new HashMap<>();

            if (isOnline) {
                // Agent在线，先优雅停止
                log.info("Agent在线，先发送优雅停止命令");
                Map<String, Object> stopResult = agentControlService.stopAgentGraceful(agentCode);

                if (!(Boolean) stopResult.get("success")) {
                    log.warn("✗ Agent停止失败，无法重启");
                    return Result.error("Agent停止失败: " + stopResult.get("message"));
                }

                log.info("✓ Agent停止命令已发送，等待3秒后重启");
                result.put("stopMessage", "Agent已优雅停止，正在重启...");

                // 等待Agent优雅关闭
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                log.info("Agent已离线，直接重启");
                result.put("stopMessage", "Agent已离线，直接启动");
            }

            // 通过systemd或脚本重启
            log.info("通过系统服务重启Agent");
            Map<String, Object> startResult = agentControlService.restartAgentSystem(agentCode);

            if ((Boolean) startResult.get("success")) {
                log.info("✓ Agent重启命令已发送");
                result.putAll(startResult);
                result.put("agentCode", agentCode);
                return Result.success(result);
            } else {
                log.warn("✗ Agent重启失败");
                return Result.error((String) startResult.get("message"));
            }

        } catch (Exception e) {
            log.error("重启Agent失败", e);
            return Result.error("重启Agent失败: " + e.getMessage());
        }
    }

    /**
     * 获取Agent状态
     *
     * @param agentCode Agent代码
     * @return Agent状态信息
     */
    @GetMapping("/{agentCode}/status")
    public Result<Map<String, Object>> getAgentStatus(@PathVariable String agentCode) {
        Result<Void> error = ValidationUtil.validateNotEmpty(agentCode, "Agent代码");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        log.info("========== [Agent控制] 查询Agent状态 ==========");
        log.info("agentCode: {}", agentCode);

        try {
            Agent agent = agentService.getByAgentCode(agentCode);
            if (agent == null) {
                return Result.error("Agent不存在");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("agentCode", agentCode);
            result.put("agentName", agent.getAgentName());
            result.put("status", agent.getStatus());
            result.put("hostIp", agent.getHostIp());
            result.put("port", agent.getPort());
            result.put("version", agent.getVersion());
            result.put("lastHeartbeat", agent.getLastHeartbeat());

            // 计算心跳时间
            if (agent.getLastHeartbeat() != null) {
                long ageSeconds = (System.currentTimeMillis() - agent.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) / 1000;
                result.put("lastHeartbeatAge", ageSeconds);
            }

            return Result.success(result);

        } catch (Exception e) {
            log.error("查询Agent状态失败", e);
            return Result.error("查询Agent状态失败: " + e.getMessage());
        }
    }
}
