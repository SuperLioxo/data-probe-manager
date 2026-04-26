package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.AgentLog;
import com.lixin.probe.service.AgentLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent-logs")
public class AgentLogController {

    @Autowired
    private AgentLogService agentLogService;

    @GetMapping
    public Result<Page<AgentLog>> queryLogs(
            @RequestParam(required = false) String agentCode,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(agentLogService.queryLogs(agentCode, level, pageNum, pageSize));
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadLogs(@RequestParam(required = false) String agentCode) {
        String csv = agentLogService.downloadLogs(agentCode);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=agent-logs.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
