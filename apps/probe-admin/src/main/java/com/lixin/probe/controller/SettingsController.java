package com.lixin.probe.controller;

import com.lixin.probe.common.Result;
import com.lixin.probe.service.SettingsService;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统设置控制器
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    /**
     * 获取所有设置
     */
    @GetMapping
    public Result<Map<String, String>> getSettings() {
        return ControllerHelper.safeGet(
                settingsService::getAllSettings,
                "获取设置失败"
        );
    }

    /**
     * 获取单个设置
     */
    @GetMapping("/{key}")
    public Result<String> getSetting(@PathVariable String key) {
        Result<Void> error = ValidationUtil.validateNotEmpty(key, "设置键");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(() -> {
            String value = settingsService.getSettingValue(key);
            if (value == null) {
                throw new IllegalArgumentException("设置不存在");
            }
            return value;
        }, "获取设置失败");
    }

    /**
     * 更新设置（批量）
     */
    @PutMapping
    public Result<String> updateSettings(@RequestBody Map<String, String> settings) {
        return ControllerHelper.safeExecute(
                () -> {
                    boolean success = settingsService.updateSettings(settings);
                    if (!success) {
                        throw new RuntimeException("更新设置失败");
                    }
                },
                "更新成功",
                "更新设置失败"
        );
    }

    /**
     * 更新单个设置
     */
    @PutMapping("/{key}")
    public Result<String> updateSetting(@PathVariable String key, @RequestBody Map<String, String> request) {
        Result<Void> error = ValidationUtil.validateNotEmpty(key, "设置键");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        String value = request.get("value");
        error = ValidationUtil.validateNotEmpty(value, "设置值");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeExecute(
                () -> {
                    boolean success = settingsService.updateSetting(key, value);
                    if (!success) {
                        throw new RuntimeException("更新设置失败");
                    }
                },
                "更新成功",
                "更新设置失败"
        );
    }

    /**
     * 重置所有设置为默认值
     */
    @PostMapping("/reset")
    public Result<String> resetSettings() {
        return ControllerHelper.safeExecute(
                settingsService::resetToDefaults,
                "重置成功",
                "重置设置失败"
        );
    }
}
