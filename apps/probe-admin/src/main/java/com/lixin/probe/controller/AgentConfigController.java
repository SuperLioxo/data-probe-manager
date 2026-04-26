package com.lixin.probe.controller;

import com.lixin.probe.common.Result;
import com.lixin.probe.service.ConfigPushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent-config")
public class AgentConfigController {

    @Autowired
    private ConfigPushService configPushService;

    @PostMapping("/push")
    public Result<Map<String, Object>> pushConfig(@RequestBody Map<String, Object> body) {
        String agentCode = (String) body.get("agentCode");
        String configType = (String) body.get("configType");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) body.get("config");
        return Result.success(configPushService.pushConfig(agentCode, configType, config));
    }

    @PostMapping("/push-all")
    public Result<Map<String, Object>> pushConfigToAll(@RequestBody Map<String, Object> body) {
        String configType = (String) body.get("configType");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) body.get("config");
        return Result.success(configPushService.pushConfigToAll(configType, config));
    }
}
