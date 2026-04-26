package com.lixin.probe.controller;

import com.lixin.probe.common.Result;
import com.lixin.probe.dto.DatabaseConnectionDTO;
import com.lixin.probe.entity.DatabaseConnection;
import com.lixin.probe.service.DatabaseConnectionService;
import com.lixin.probe.service.ProbeStatusValidationService;
import com.lixin.probe.util.ControllerHelper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据库连接管理Controller
 *
 * @author Claude Code
 * @date 2026-03-26
 */
@Slf4j
@RestController
@RequestMapping("/api/database-connections")
public class DatabaseConnectionController {

    @Autowired
    private DatabaseConnectionService databaseConnectionService;

    @Autowired
    private ProbeStatusValidationService statusValidationService;

    /**
     * 获取所有数据库连接列表
     */
    @GetMapping
    public Result<List<DatabaseConnectionDTO>> getAllConnections() {
        return ControllerHelper.safeGet(() -> {
            List<DatabaseConnection> connections = databaseConnectionService.getAllConnections();
            return connections.stream().map(this::toDTO).collect(Collectors.toList());
        }, "获取数据库连接列表失败");
    }

    /**
     * 获取同类型的数据库连接列表
     */
    @GetMapping("/type/{databaseType}")
    public Result<List<DatabaseConnectionDTO>> getConnectionsByType(
            @PathVariable String databaseType,
            @RequestParam(required = false) String probeKey) {

        log.info("========== [getConnectionsByType] 开始查询数据库连接 ==========");
        log.info("databaseType={}, probeKey={}", databaseType, probeKey);

        // 如果提供了probeKey，验证探针状态
        if (probeKey != null) {
            log.info("步骤1: 验证探针在线状态...");
            boolean isOnline = statusValidationService.isProbeOnline(probeKey);
            log.info("  探针在线状态: {}", isOnline ? "在线" : "离线");

            if (!isOnline) {
                log.warn("✗ 拒绝离线探针的连接查询请求: probeKey={}", probeKey);
                return Result.error("探针离线，无法获取数据库连接");
            }
            log.info("✓ 探针在线验证通过");
        } else {
            log.info("步骤1: 跳过探针状态验证（未提供probeKey）");
        }

        return ControllerHelper.safeGet(() -> {
            log.info("步骤2: 查询数据库连接...");
            List<DatabaseConnection> connections = databaseConnectionService.getConnectionsByType(databaseType);
            log.info("✓ 查询到 {} 个数据库连接", connections.size());

            // 转换为DTO（不包含密码）
            List<DatabaseConnectionDTO> result = connections.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());

            log.info("✓ 转换为DTO完成，返回 {} 个连接", result.size());
            log.info("====================================================");
            return result;
        }, "获取数据库连接列表失败");
    }

    /**
     * 创建数据库连接
     */
    @PostMapping
    public Result<DatabaseConnectionDTO> create(@Valid @RequestBody DatabaseConnectionDTO dto) {
        log.info("[DatabaseConnectionController] 创建数据库连接: name={}, type={}", dto.getName(), dto.getDatabaseType());

        return ControllerHelper.safeGet(() -> {
            DatabaseConnection connection = toEntity(dto);
            DatabaseConnection created = databaseConnectionService.createConnection(connection);
            return toDTO(created);
        }, "创建数据库连接失败");
    }

    /**
     * 更新数据库连接
     */
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Long id, @Valid @RequestBody DatabaseConnectionDTO dto) {
        log.info("[DatabaseConnectionController] 更新数据库连接: id={}", id);

        return ControllerHelper.safeExecute(() -> {
            DatabaseConnection connection = toEntity(dto);
            connection.setId(id);
            databaseConnectionService.updateConnection(connection);
        }, "更新成功", "更新数据库连接失败");
    }

    /**
     * 删除数据库连接
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        log.info("[DatabaseConnectionController] 删除数据库连接: id={}", id);

        return ControllerHelper.safeExecute(() -> {
            databaseConnectionService.deleteConnection(id);
        }, "删除成功", "删除数据库连接失败");
    }

    /**
     * 测试数据库连接
     */
    @PostMapping("/test")
    public Result<Map<String, Object>> testConnection(@RequestBody DatabaseConnectionDTO dto) {
        log.info("[DatabaseConnectionController] 测试数据库连接: type={}, host:{}, port:{}, database={}",
                dto.getDatabaseType(), dto.getDatabaseHost(),
                dto.getDatabasePort(), dto.getDatabaseName());

        return ControllerHelper.safeGet(() -> {
            DatabaseConnection connection = toEntity(dto);
            boolean success = databaseConnectionService.testConnection(connection);

            return Map.of(
                    "success", success,
                    "message", success ? "连接成功" : "连接失败"
            );
        }, "测试连接失败");
    }

    /**
     * 按ID测试数据库连接
     */
    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> testConnectionById(@PathVariable Long id) {
        log.info("[DatabaseConnectionController] 按ID测试数据库连接: id={}", id);

        return ControllerHelper.safeGet(() -> {
            List<DatabaseConnection> connections = databaseConnectionService.getAllConnections();
            DatabaseConnection connection = connections.stream()
                    .filter(c -> c.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("连接不存在: " + id));
            boolean success = databaseConnectionService.testConnection(connection);

            return Map.of(
                    "success", success,
                    "message", success ? "连接成功" : "连接失败"
            );
        }, "测试连接失败");
    }

    /**
     * 转换为实体
     */
    private DatabaseConnection toEntity(DatabaseConnectionDTO dto) {
        DatabaseConnection entity = new DatabaseConnection();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    /**
     * 转换为DTO（不包含密码）
     */
    private DatabaseConnectionDTO toDTO(DatabaseConnection entity) {
        return DatabaseConnectionDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .databaseType(entity.getDatabaseType())
                .databaseHost(entity.getDatabaseHost())
                .databasePort(entity.getDatabasePort())
                .databaseName(entity.getDatabaseName())
                .username(entity.getUsername())
                .password(null) // 不返回密码
                .schemas(entity.getSchemas())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
