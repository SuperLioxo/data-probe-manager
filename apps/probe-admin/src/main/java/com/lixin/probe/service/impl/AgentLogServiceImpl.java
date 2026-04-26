package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.AgentLog;
import com.lixin.probe.mapper.AgentLogMapper;
import com.lixin.probe.service.AgentLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AgentLogServiceImpl implements AgentLogService {

    @Autowired
    private AgentLogMapper agentLogMapper;

    @Override
    public void storeLogs(String agentCode, List<Map<String, String>> logs) {
        for (Map<String, String> entry : logs) {
            AgentLog agentLog = AgentLog.builder()
                    .agentCode(agentCode)
                    .level(entry.getOrDefault("level", "INFO"))
                    .logger(entry.getOrDefault("logger", ""))
                    .message(truncate(entry.getOrDefault("message", ""), 4000))
                    .exceptionStack(truncate(entry.getOrDefault("exceptionStack", ""), 8000))
                    .timestamp(parseTime(entry.get("timestamp")))
                    .build();
            agentLogMapper.insert(agentLog);
        }
        log.debug("[AgentLog] Stored {} logs from agent {}", logs.size(), agentCode);
    }

    @Override
    public Page<AgentLog> queryLogs(String agentCode, String level, int pageNum, int pageSize) {
        LambdaQueryWrapper<AgentLog> wrapper = new LambdaQueryWrapper<AgentLog>()
                .eq(agentCode != null && !agentCode.isEmpty(), AgentLog::getAgentCode, agentCode)
                .eq(level != null && !level.isEmpty(), AgentLog::getLevel, level)
                .orderByDesc(AgentLog::getTimestamp);
        return agentLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public String downloadLogs(String agentCode) {
        LambdaQueryWrapper<AgentLog> wrapper = new LambdaQueryWrapper<AgentLog>()
                .eq(agentCode != null && !agentCode.isEmpty(), AgentLog::getAgentCode, agentCode)
                .orderByDesc(AgentLog::getTimestamp)
                .last("LIMIT 10000");

        List<AgentLog> logs = agentLogMapper.selectList(wrapper);
        StringBuilder sb = new StringBuilder("Timestamp,Level,Logger,Message\n");
        for (AgentLog l : logs) {
            sb.append(l.getTimestamp()).append(",")
              .append(l.getLevel()).append(",")
              .append(escapeCsv(l.getLogger())).append(",")
              .append(escapeCsv(l.getMessage())).append("\n");
        }
        return sb.toString();
    }

    private LocalDateTime parseTime(String ts) {
        if (ts == null || ts.isEmpty()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(ts.replace(" ", "T"));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
