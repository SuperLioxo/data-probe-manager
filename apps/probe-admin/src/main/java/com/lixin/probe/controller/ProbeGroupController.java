package com.lixin.probe.controller;

import com.lixin.probe.annotation.Audited;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.entity.ProbeGroup;
import com.lixin.probe.service.ProbeGroupService;
import com.lixin.probe.service.ProbeService;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.util.ControllerHelper.Messages;
import com.lixin.probe.util.ValidationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 探针分组管理Controller（重构版）
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (重构版)
 */
@RestController
@RequestMapping("/api/probe-groups")
public class ProbeGroupController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProbeGroupController.class);

    @Autowired
    private ProbeGroupService probeGroupService;

    @Autowired
    private ProbeService probeService;

    /**
     * 获取分组树形结构
     */
    @GetMapping("/tree")
    @Audited(operation = "QUERY", module = "ProbeGroup", description = "查询分组树")
    public Result<List<ProbeGroup>> getTree() {
        return ControllerHelper.safeGet(
                probeGroupService::getGroupTree,
                "查询分组树失败"
        );
    }

    /**
     * 根据ID查询分组详情
     */
    @GetMapping("/{id}")
    @Audited(operation = "QUERY", module = "ProbeGroup", description = "查询分组详情")
    public Result<ProbeGroup> getById(@PathVariable Long id) {
        Result<Void> error = ValidationUtil.validateId(id, "分组ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(
                () -> {
                    ProbeGroup group = probeGroupService.getById(id);
                    if (group == null) {
                        throw new IllegalArgumentException(Messages.notFound("分组"));
                    }
                    return group;
                },
                "查询分组详情失败"
        );
    }

    /**
     * 创建分组
     */
    @PostMapping
    @Audited(operation = "CREATE", module = "ProbeGroup", description = "创建分组")
    public Result<String> create(@RequestBody ProbeGroup group) {
        return ControllerHelper.safeExecute(
                () -> {
                    boolean success = probeGroupService.createGroup(group);
                    if (!success) {
                        throw new RuntimeException("创建分组失败");
                    }
                },
                Messages.createSuccess("分组"),
                "创建分组失败"
        );
    }

    /**
     * 更新分组
     */
    @PutMapping("/{id}")
    @Audited(operation = "UPDATE", module = "ProbeGroup", description = "更新分组")
    public Result<String> update(
            @PathVariable Long id,
            @RequestBody ProbeGroup group) {

        Result<Void> error = ValidationUtil.validateId(id, "分组ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeExecute(
                () -> {
                    ProbeGroup existGroup = probeGroupService.getById(id);
                    if (existGroup == null) {
                        throw new IllegalArgumentException(Messages.notFound("分组"));
                    }

                    group.setId(id);
                    boolean success = probeGroupService.updateGroup(group);
                    if (!success) {
                        throw new RuntimeException("更新分组失败");
                    }
                },
                Messages.UPDATE_SUCCESS,
                "更新分组失败"
        );
    }

    /**
     * 删除分组
     */
    @DeleteMapping("/{id}")
    @Audited(operation = "DELETE", module = "ProbeGroup", description = "删除分组")
    public Result<String> delete(@PathVariable Long id) {
        return ControllerHelper.safeExecute(
                () -> {
                    Result<Void> validationError = ValidationUtil.validateId(id, "分组ID");
                    if (validationError != null) {
                        throw new IllegalArgumentException(validationError.getMessage());
                    }

                    boolean success = probeGroupService.deleteGroup(id);
                    if (!success) {
                        throw new RuntimeException("删除分组失败");
                    }
                },
                Messages.DELETE_SUCCESS,
                "删除分组失败"
        );
    }

    /**
     * 将探针添加到分组
     */
    @PostMapping("/{id}/probes")
    @Audited(operation = "UPDATE", module = "ProbeGroup", description = "添加探针到分组")
    public Result<String> addProbe(
            @PathVariable Long id,
            @RequestBody List<Long> probeIds) {

        Result<Void> error = ValidationUtil.validateId(id, "分组ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        // 验证探针列表
        Result<Void> listError = ValidationUtil.validateCollectionSize(probeIds, "探针列表", 100);
        if (listError != null) {
            return Result.error(listError.getMessage());
        }

        return ControllerHelper.safeExecute(
                () -> {
                    // 验证分组存在
                    ProbeGroup group = probeGroupService.getById(id);
                    if (group == null) {
                        throw new IllegalArgumentException(Messages.notFound("分组"));
                    }

                    // 验证所有探针存在
                    for (Long probeId : probeIds) {
                        Result<Void> probeError = ValidationUtil.validateId(probeId, "探针ID");
                        if (probeError != null) {
                            throw new IllegalArgumentException(probeError.getMessage());
                        }

                        Probe probe = probeService.getById(probeId);
                        if (probe == null) {
                            throw new IllegalArgumentException("探针不存在: " + probeId);
                        }

                        probeGroupService.addProbeToGroup(probeId, id);
                    }
                },
                "添加成功",
                "添加探针到分组失败"
        );
    }

    /**
     * 将探针从分组中移除
     */
    @DeleteMapping("/{id}/probes/{probeId}")
    @Audited(operation = "UPDATE", module = "ProbeGroup", description = "从分组移除探针")
    public Result<String> removeProbe(
            @PathVariable Long id,
            @PathVariable Long probeId) {

        // 验证两个ID
        Result<Void> groupError = ValidationUtil.validateId(id, "分组ID");
        if (groupError != null) {
            return Result.error(groupError.getMessage());
        }

        Result<Void> probeError = ValidationUtil.validateId(probeId, "探针ID");
        if (probeError != null) {
            return Result.error(probeError.getMessage());
        }

        return ControllerHelper.safeExecute(
                () -> {
                    boolean success = probeGroupService.removeProbeFromGroup(probeId, id);
                    if (!success) {
                        throw new RuntimeException("移除探针失败");
                    }
                },
                "移除成功",
                "移除探针失败"
        );
    }

    /**
     * 获取分组下的探针列表
     */
    @GetMapping("/{id}/probes")
    @Audited(operation = "QUERY", module = "ProbeGroup", description = "查询分组下的探针")
    public Result<List<Probe>> getProbes(@PathVariable Long id) {
        Result<Void> error = ValidationUtil.validateId(id, "分组ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(
                () -> {
                    List<Long> probeIds = probeGroupService.getProbeIdsByGroupId(id);

                    // 使用批量查询避免 N+1 问题
                    // 一次性查询所有探针，而不是循环单个查询
                    List<Probe> probes = probeService.listByIds(probeIds);

                    // 过滤掉 null 值（如果某些 ID 不存在）
                    return probes.stream()
                            .filter(probe -> probe != null)
                            .collect(java.util.stream.Collectors.toList());
                },
                "查询探针列表失败"
        );
    }
}
