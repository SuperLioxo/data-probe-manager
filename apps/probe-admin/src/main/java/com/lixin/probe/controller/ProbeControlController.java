package com.lixin.probe.controller;

import com.lixin.probe.annotation.RequirePermission;
import com.lixin.probe.common.Result;
import com.lixin.probe.common.Permissions;
import com.lixin.probe.dto.ProbeControlResponse;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.service.ProbeControlService;
import com.lixin.probe.service.ProbeService;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.util.ValidationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 探针远程控制Controller
 */
@RestController
@RequestMapping("/api/probe-control")
public class ProbeControlController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProbeControlController.class);

    @Autowired
    private ProbeService probeService;

    @Autowired
    private ProbeControlService probeControlService;

    /**
     * 停止探针
     */
    // @RequirePermission(Permissions.PROBE_CONTROL)
    @PostMapping("/{probeKey}/stop")
    public Result<Map<String, Object>> stopProbe(@PathVariable String probeKey) {
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针KEY");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        log.info("请求停止探针: probeKey={}", probeKey);

        try {
            ProbeControlResponse response = probeControlService.sendControlCommand(probeKey, "STOP", Map.of());
            if (!response.isSuccess()) {
                return Result.error(response.getMessage());
            }
            return Result.success(response.getData());
        } catch (Exception e) {
            log.error("停止探针失败: probeKey={}", probeKey, e);
            return Result.error("停止探针失败: " + e.getMessage());
        }
    }

    /**
     * 启动探针
     */
    // @RequirePermission(Permissions.PROBE_CONTROL)
    @PostMapping("/{probeKey}/start")
    public Result<Map<String, Object>> startProbe(@PathVariable String probeKey) {
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针KEY");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        log.info("请求启动探针: probeKey={}", probeKey);

        try {
            ProbeControlResponse response = probeControlService.sendControlCommand(probeKey, "START", Map.of());
            if (!response.isSuccess()) {
                return Result.error(response.getMessage());
            }
            return Result.success(response.getData());
        } catch (Exception e) {
            log.error("启动探针失败: probeKey={}", probeKey, e);
            return Result.error("启动探针失败: " + e.getMessage());
        }
    }

    /**
     * 重启探针
     */
    // @RequirePermission(Permissions.PROBE_CONTROL)
    @PostMapping("/{probeKey}/restart")
    public Result<Map<String, Object>> restartProbe(@PathVariable String probeKey) {
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针KEY");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        log.info("请求重启探针: probeKey={}", probeKey);

        try {
            ProbeControlResponse response = probeControlService.sendControlCommand(probeKey, "RESTART", Map.of());
            if (!response.isSuccess()) {
                return Result.error(response.getMessage());
            }
            return Result.success(response.getData());
        } catch (Exception e) {
            log.error("重启探针失败: probeKey={}", probeKey, e);
            return Result.error("重启探针失败: " + e.getMessage());
        }
    }

    /**
     * 更新探针配置
     */
    // @RequirePermission(Permissions.PROBE_CONTROL)
    @PostMapping("/{probeKey}/config")
    public Result<Map<String, Object>> updateConfig(
            @PathVariable String probeKey,
            @RequestBody Map<String, Object> config) {
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针KEY");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        log.info("请求更新探针配置: probeKey={}, config={}", probeKey, config);

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("config", config);

            ProbeControlResponse response = probeControlService.sendControlCommand(probeKey, "UPDATE_CONFIG", params);
            if (!response.isSuccess()) {
                return Result.error(response.getMessage());
            }
            return Result.success(response.getData());
        } catch (Exception e) {
            log.error("更新探针配置失败: probeKey={}", probeKey, e);
            return Result.error("更新探针配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取探针状态
     */
    @RequirePermission(Permissions.PROBE_VIEW)
    @GetMapping("/{probeKey}/status")
    public Result<Probe> getProbeStatus(@PathVariable String probeKey) {
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针KEY");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        log.info("查询探针状态: probeKey={}", probeKey);

        return ControllerHelper.safeGet(() -> {
            Probe probe = probeService.getByProbeKey(probeKey);
            if (probe == null) {
                throw new IllegalArgumentException("探针不存在: " + probeKey);
            }
            return probe;
        }, "查询探针状态失败");
    }
}
