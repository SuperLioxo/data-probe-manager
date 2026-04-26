package com.lixin.probe.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lixin.probe.annotation.Audited;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.Permission;
import com.lixin.probe.service.PermissionService;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.util.ValidationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限管理Controller（重构版）
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (重构版)
 */
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PermissionController.class);

    @Autowired
    private PermissionService permissionService;

    /**
     * 获取所有权限列表
     */
    @GetMapping
    @Audited(operation = "QUERY", module = "Permission", description = "查询所有权限")
    public Result<List<Permission>> list() {
        return ControllerHelper.safeGet(
                permissionService::list,
                "查询权限列表失败"
        );
    }

    /**
     * 根据资源类型查询权限
     */
    @GetMapping("/by-type")
    @Audited(operation = "QUERY", module = "Permission", description = "按类型查询权限")
    public Result<List<Permission>> listByType(@RequestParam String resourceType) {
        // 验证resourceType不为空
        Result<Void> error = ValidationUtil.validateNotEmpty(resourceType, "资源类型");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(
                () -> {
                    QueryWrapper<Permission> wrapper = new QueryWrapper<>();
                    wrapper.eq("resource_type", resourceType);
                    return permissionService.list(wrapper);
                },
                "查询权限列表失败"
        );
    }
}
