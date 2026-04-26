package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.AgentLog;

import java.util.List;
import java.util.Map;

public interface AgentLogService {

    void storeLogs(String agentCode, List<Map<String, String>> logs);

    Page<AgentLog> queryLogs(String agentCode, String level, int pageNum, int pageSize);

    String downloadLogs(String agentCode);
}
