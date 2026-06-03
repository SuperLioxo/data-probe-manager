package com.lixin.probe.controller;

import com.alibaba.fastjson2.JSONObject;
import com.lixin.probe.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    private static final Logger log = LoggerFactory.getLogger(PluginController.class);

    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestBody List<JSONObject> pluginStatuses) {
        log.debug("[PluginHeartbeat] 收到 {} 个插件状态", pluginStatuses.size());
        return Result.success();
    }

    @PostMapping("/report")
    public Result<Void> report(@RequestBody JSONObject pluginReport) {
        log.debug("[PluginReport] 收到插件上报: agent={}",
                pluginReport.getString("agentCode"));
        return Result.success();
    }
}
