package com.lixin.probe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lixin.probe.entity.Agent;
import com.lixin.probe.mapper.AgentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent Service
 *
 * @author Claude Code
 * @since 2.0
 */
@Service
public class AgentService extends ServiceImpl<AgentMapper, Agent> {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    /**
     * 获取所有Agent列表
     */
    public List<Agent> getAllAgents() {
        return list();
    }

    /**
     * 根据agentCode获取Agent
     */
    public Agent getByAgentCode(String agentCode) {
        return getOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getAgentCode, agentCode));
    }

    /**
     * 注册或更新Agent
     * 当Agent启动时调用此方法
     */
    public Agent registerOrUpdateAgent(String agentCode, String agentName, String hostIp, Integer port, String version) {
        Agent existingAgent = getByAgentCode(agentCode);

        if (existingAgent != null) {
            // 更新现有Agent
            existingAgent.setAgentName(agentName);
            existingAgent.setHostIp(hostIp);
            existingAgent.setPort(port);
            existingAgent.setVersion(version);
            existingAgent.setStatus("online");
            existingAgent.setLastHeartbeat(LocalDateTime.now());
            existingAgent.setUpdateTime(LocalDateTime.now());

            updateById(existingAgent);
            log.info("更新Agent信息: agentCode={}, hostIp={}:{}, version={}",
                    agentCode, hostIp, port, version);
            return existingAgent;
        } else {
            // 创建新Agent
            Agent newAgent = new Agent();
            newAgent.setAgentCode(agentCode);
            newAgent.setAgentName(agentName);
            newAgent.setHostIp(hostIp);
            newAgent.setPort(port);
            newAgent.setStatus("online");
            newAgent.setVersion(version);
            newAgent.setLastHeartbeat(LocalDateTime.now());
            newAgent.setCreateTime(LocalDateTime.now());
            newAgent.setUpdateTime(LocalDateTime.now());

            save(newAgent);
            log.info("注册新Agent: agentCode={}, hostIp={}:{}, version={}",
                    agentCode, hostIp, port, version);
            return newAgent;
        }
    }

    /**
     * 更新Agent状态
     */
    public void updateAgentStatus(String agentCode, String status) {
        Agent agent = getByAgentCode(agentCode);
        if (agent != null) {
            agent.setStatus(status);
            agent.setUpdateTime(LocalDateTime.now());
            if ("online".equals(status)) {
                agent.setLastHeartbeat(LocalDateTime.now());
            }
            updateById(agent);
            log.info("更新Agent状态: agentCode={}, status={}", agentCode, status);
        }
    }

    /**
     * 删除Agent
     */
    public boolean deleteAgent(String agentCode) {
        Agent agent = getByAgentCode(agentCode);
        if (agent != null) {
            return removeById(agent.getId());
        }
        return false;
    }

    /**
     * 获取在线Agent数量
     */
    public long getOnlineAgentCount() {
        return count(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getStatus, "online"));
    }

    /**
     * 获取离线Agent数量
     */
    public long getOfflineAgentCount() {
        return count(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getStatus, "offline"));
    }
}
