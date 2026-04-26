package com.lixin.probe.controller;

import com.lixin.probe.annotation.RequirePermission;
import com.lixin.probe.common.Result;
import com.lixin.probe.common.Permissions;
import com.lixin.probe.service.ProbeWhitelistService;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.util.ValidationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 探针白名单管理Controller
 * 提供白名单的增删改查接口
 */
@RestController
@RequestMapping("/api/probe-whitelist")
public class ProbeWhitelistController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProbeWhitelistController.class);

    @Autowired
    private ProbeWhitelistService probeWhitelistService;

    /**
     * 添加探针到白名单
     *
     * @param probeKey 探针标识
     * @return 操作结果
     */
    @RequirePermission(Permissions.WHITELIST_MANAGE)
    @PostMapping("/{probeKey}")
    public Result<String> addToWhitelist(@PathVariable String probeKey) {
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针KEY");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        if (!probeWhitelistService.isRedisAvailable()) {
            return Result.error("Redis未配置，白名单功能不可用");
        }

        return ControllerHelper.safeExecute(
                () -> probeWhitelistService.addToWhitelist(probeKey),
                "已添加到白名单",
                "添加探针到白名单失败"
        );
    }

    /**
     * 从白名单移除探针
     *
     * @param probeKey 探针标识
     * @return 操作结果
     */
    @RequirePermission(Permissions.WHITELIST_MANAGE)
    @DeleteMapping("/{probeKey}")
    public Result<String> removeFromWhitelist(@PathVariable String probeKey) {
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针KEY");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        if (!probeWhitelistService.isRedisAvailable()) {
            return Result.error("Redis未配置，白名单功能不可用");
        }

        return ControllerHelper.safeExecute(
                () -> probeWhitelistService.removeFromWhitelist(probeKey),
                "已从白名单移除",
                "从白名单移除探针失败"
        );
    }

    /**
     * 为探针添加IP白名单
     *
     * @param probeKey 探针标识
     * @param ip IP地址
     * @return 操作结果
     */
    @RequirePermission(Permissions.WHITELIST_MANAGE)
    @PostMapping("/{probeKey}/ip")
    public Result<String> addIpWhitelist(
            @PathVariable String probeKey,
            @RequestParam String ip) {
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针KEY");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        error = ValidationUtil.validateNotEmpty(ip, "IP地址");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        if (!probeWhitelistService.isRedisAvailable()) {
            return Result.error("Redis未配置，白名单功能不可用");
        }

        return ControllerHelper.safeExecute(
                () -> probeWhitelistService.addIpToWhitelist(probeKey, ip),
                "IP已添加到白名单",
                "添加IP到白名单失败"
        );
    }

    /**
     * 从探针的IP白名单移除IP
     *
     * @param probeKey 探针标识
     * @param ip IP地址
     * @return 操作结果
     */
    @RequirePermission(Permissions.WHITELIST_MANAGE)
    @DeleteMapping("/{probeKey}/ip")
    public Result<String> removeIpWhitelist(
            @PathVariable String probeKey,
            @RequestParam String ip) {
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针KEY");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        error = ValidationUtil.validateNotEmpty(ip, "IP地址");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        if (!probeWhitelistService.isRedisAvailable()) {
            return Result.error("Redis未配置，白名单功能不可用");
        }

        return ControllerHelper.safeExecute(
                () -> probeWhitelistService.removeIpFromWhitelist(probeKey, ip),
                "IP已从白名单移除",
                "从白名单移除IP失败"
        );
    }

    /**
     * 获取所有白名单探针
     *
     * @return 白名单探针列表
     */
    @RequirePermission(Permissions.WHITELIST_MANAGE)
    @GetMapping
    public Result<Set<String>> getAllWhitelistedProbes() {
        if (!probeWhitelistService.isRedisAvailable()) {
            return Result.error("Redis未配置，白名单功能不可用");
        }

        return ControllerHelper.safeGet(
                probeWhitelistService::getAllWhitelistedProbes,
                "获取白名单探针失败"
        );
    }

    /**
     * 获取指定探针的所有白名单IP
     *
     * @param probeKey 探针标识
     * @return 白名单IP列表
     */
    @RequirePermission(Permissions.WHITELIST_MANAGE)
    @GetMapping("/{probeKey}/ips")
    public Result<Set<String>> getWhitelistedIps(@PathVariable String probeKey) {
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针KEY");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        if (!probeWhitelistService.isRedisAvailable()) {
            return Result.error("Redis未配置，白名单功能不可用");
        }

        return ControllerHelper.safeGet(
                () -> probeWhitelistService.getWhitelistedIps(probeKey),
                "获取白名单IP失败"
        );
    }

    /**
     * 批量添加探针到白名单
     *
     * @param probeKeys 探针标识列表
     * @return 操作结果
     */
    @RequirePermission(Permissions.WHITELIST_MANAGE)
    @PostMapping("/batch")
    public Result<Map<String, Object>> batchAddToWhitelist(@RequestBody Set<String> probeKeys) {
        Result<Void> error = ValidationUtil.validateCollectionSize(probeKeys, "探针KEY列表", 1000);
        if (error != null) {
            return Result.error(error.getMessage());
        }

        if (!probeWhitelistService.isRedisAvailable()) {
            return Result.error("Redis未配置，白名单功能不可用");
        }

        return ControllerHelper.safeGet(() -> {
            int successCount = 0;
            for (String probeKey : probeKeys) {
                try {
                    probeWhitelistService.addToWhitelist(probeKey);
                    successCount++;
                } catch (Exception e) {
                    log.error("添加探针到白名单失败: probeKey={}", probeKey, e);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("total", probeKeys.size());
            result.put("success", successCount);
            result.put("failed", probeKeys.size() - successCount);

            return result;
        }, "批量添加探针到白名单失败");
    }

    /**
     * 清空所有白名单
     *
     * @return 操作结果
     */
    @RequirePermission(Permissions.SYSTEM_ADMIN)
    @DeleteMapping("/all")
    public Result<String> clearAllWhitelists() {
        if (!probeWhitelistService.isRedisAvailable()) {
            return Result.error("Redis未配置，白名单功能不可用");
        }

        return ControllerHelper.safeExecute(
                probeWhitelistService::clearAllWhitelists,
                "已清空所有白名单",
                "清空白名单失败"
        );
    }

    /**
     * 检查白名单功能状态
     *
     * @return 白名单状态信息
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getWhitelistStatus() {
        return ControllerHelper.safeGet(() -> {
            Map<String, Object> status = new HashMap<>();
            boolean available = probeWhitelistService.isRedisAvailable();
            status.put("enabled", available);
            status.put("message", available ? "白名单功能已启用" : "Redis未配置，白名单功能不可用");

            if (available) {
                Set<String> probes = probeWhitelistService.getAllWhitelistedProbes();
                status.put("whitelistedProbes", probes.size());
            }

            return status;
        }, "获取白名单状态失败");
    }
}
