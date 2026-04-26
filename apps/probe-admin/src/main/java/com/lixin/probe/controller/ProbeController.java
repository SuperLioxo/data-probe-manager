package com.lixin.probe.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.annotation.Audited;
import com.lixin.probe.annotation.RequirePermission;
import com.lixin.probe.common.Result;
import com.lixin.probe.common.Permissions;
import com.lixin.probe.dto.DatabaseTypeInfo;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.enums.ProbeType;
import com.lixin.probe.service.ProbeService;
import com.lixin.probe.service.FileProbeService;
import com.lixin.probe.service.FileMetadataService;
import com.lixin.probe.websocket.MetaProbeWebSocketHandler;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.util.ValidationUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 探针管理Controller（重构版）
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (重构版)
 */
@RestController
@RequestMapping("/api/probes")
public class ProbeController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProbeController.class);

    @Autowired
    @Qualifier("decoratedProbeService")
    private ProbeService probeService;

    @Autowired(required = false)
    private FileProbeService fileProbeService;

    @Autowired(required = false)
    private FileMetadataService fileMetadataService;

    @Autowired(required = false)
    private MetaProbeWebSocketHandler metaProbeWebSocketHandler;

    @Autowired(required = false)
    private com.lixin.probe.mapper.DatabaseConnectionMapper databaseConnectionMapper;

    @Autowired(required = false)
    private com.lixin.probe.service.DatabaseProbeService databaseProbeService;

    @Autowired(required = false)
    private com.lixin.probe.mapper.FileProbeMapper fileProbeMapper;

    /**
     * 分页查询探针列表
     */
    @RequirePermission(Permissions.PROBE_VIEW)
    @GetMapping
    public Result<Page<Probe>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {

        // 使用ValidationUtil验证分页参数
        Result<Void> error = ValidationUtil.validatePageParams(pageNum, pageSize);
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(
                () -> probeService.getPage(pageNum, pageSize, name, status, type),
                "查询探针列表失败"
        );
    }

    /**
     * 获取支持的数据库类型列表
     */
    @RequirePermission(Permissions.PROBE_VIEW)
    @GetMapping("/database-types")
    public Result<List<DatabaseTypeInfo>> getDatabaseTypes() {
        log.info("[获取数据库类型] 接收到数据库类型列表请求");

        return ControllerHelper.safeGet(
                () -> probeService.getAvailableDatabaseTypes(),
                "获取数据库类型列表失败"
        );
    }

    /**
     * 根据ID查询探针详情
     */
    @RequirePermission(Permissions.PROBE_VIEW)
    @GetMapping("/{id}")
    public Result<?> getById(@PathVariable Long id) {
        // 使用ValidationUtil验证ID
        Result<Void> error = ValidationUtil.validateId(id, "探针ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(
                () -> {
                    Probe probe = probeService.getById(id);
                    if (probe == null) {
                        throw new IllegalArgumentException("探针不存在");
                    }
                    // FILE 类型：展开 config JSON 到顶层字段，补充 file_probe 表统计
                    if ("FILE".equals(probe.getType())) {
                        com.alibaba.fastjson2.JSONObject result = com.alibaba.fastjson2.JSON.parseObject(
                                com.alibaba.fastjson2.JSON.toJSONString(probe));
                        // 从 config 提取文件配置
                        if (probe.getConfig() != null && !probe.getConfig().isEmpty()) {
                            com.alibaba.fastjson2.JSONObject config = com.alibaba.fastjson2.JSON.parseObject(probe.getConfig());
                            for (String key : config.keySet()) {
                                if (!result.containsKey(key)) {
                                    result.put(key, config.get(key));
                                }
                            }
                        }
                        // 从 file_probe 表补充统计字段
                        com.lixin.probe.entity.FileProbe fp = fileProbeService.getByProbeKey(probe.getProbeKey());
                        if (fp != null) {
                            if (fp.getScanPath() != null) result.put("scanPath", fp.getScanPath());
                            if (fp.getFileExtensions() != null) result.put("fileExtensions", fp.getFileExtensions());
                            if (fp.getIgnorePaths() != null) result.put("ignorePaths", fp.getIgnorePaths());
                            if (fp.getMaxDepth() != null) result.put("maxDepth", fp.getMaxDepth());
                            if (fp.getScanInterval() != null) result.put("scanInterval", fp.getScanInterval());
                            if (fp.getTotalFileCount() != null) result.put("totalFileCount", fp.getTotalFileCount());
                            if (fp.getTotalDirectoryCount() != null) result.put("totalDirectoryCount", fp.getTotalDirectoryCount());
                            if (fp.getTotalSize() != null) result.put("totalSize", fp.getTotalSize());
                            if (fp.getLastScanTime() != null) result.put("lastScanTime", fp.getLastScanTime());
                        }
                        return result;
                    }
                    return probe;
                },
                "查询探针详情失败"
        );
    }

    /**
     * 创建探针
     */
    @RequirePermission(Permissions.PROBE_CREATE)
    @Audited(operation = "CREATE", module = "Probe", description = "创建探针")
    @PostMapping
    public Result<String> create(@Valid @RequestBody Probe probe) {
        log.info("[创建探针] 接收到创建请求 - name={}, type={}, probeKey={}, hostIp={}, port={}",
                probe.getName(), probe.getType(), probe.getProbeKey(), probe.getHostIp(), probe.getPort());

        return ControllerHelper.safeExecute(
                () -> {
                    probeService.create(probe);
                    log.info("[创建探针] 创建成功，返回probeKey: {}", probe.getProbeKey());
                },
                ControllerHelper.Messages.CREATE_SUCCESS,
                "创建探针失败"
        );
    }

    /**
     * 更新探针
     */
    @RequirePermission(Permissions.PROBE_UPDATE)
    @Audited(operation = "UPDATE", module = "Probe", description = "更新探针")
    @PutMapping("/{id}")
    public Result<String> update(
            @PathVariable Long id,
            @Valid @RequestBody Probe probe) {

        log.info("[更新探针] 接收到更新请求 - ID: {}, 探针数据: id={}, name={}, type={}, probeKey={}, hostIp={}, port={}",
                id, probe.getId(), probe.getName(), probe.getType(), probe.getProbeKey(), probe.getHostIp(), probe.getPort());

        // 使用ValidationUtil验证ID
        Result<Void> error = ValidationUtil.validateId(id, "探针ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeExecute(
                () -> {
                    Probe existProbe = probeService.getById(id);
                    if (existProbe == null) {
                        throw new IllegalArgumentException("探针不存在");
                    }
                    log.info("[更新探针] 更新前 - 数据库中的探针: id={}, name={}, type={}, probeKey={}",
                            existProbe.getId(), existProbe.getName(), existProbe.getType(), existProbe.getProbeKey());
                    probe.setId(id);
                    probeService.update(probe);
                    log.info("[更新探针] 更新完成 - 已更新探针: id={}, type={}", id, probe.getType());
                },
                ControllerHelper.Messages.UPDATE_SUCCESS,
                "更新探针失败"
        );
    }

    /**
     * 删除探针
     */
    @RequirePermission(Permissions.PROBE_DELETE)
    @Audited(operation = "DELETE", module = "Probe", description = "删除探针")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        // 使用ValidationUtil验证ID
        Result<Void> error = ValidationUtil.validateId(id, "探针ID");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeExecute(
                () -> {
                    Probe existProbe = probeService.getById(id);
                    if (existProbe == null) {
                        throw new IllegalArgumentException("探针不存在");
                    }
                    probeService.delete(id);
                },
                ControllerHelper.Messages.DELETE_SUCCESS,
                "删除探针失败"
        );
    }

    /**
     * 更新探针心跳
     */
    @PostMapping("/heartbeat/{probeKey}")
    public Result<String> heartbeat(@PathVariable String probeKey) {
        // 使用ValidationUtil验证probeKey
        Result<Void> error = ValidationUtil.validateNotEmpty(probeKey, "探针标识");
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeExecute(
                () -> {
                    probeService.updateHeartbeat(probeKey);
                },
                "心跳更新成功",
                "更新心跳失败"
        );
    }

    /**
     * 获取探针类型列表
     */
    @GetMapping("/types")
    public Result<Map<String, Object>> getProbeTypes() {
        return ControllerHelper.safeGet(() -> {
            Map<String, Object> result = new HashMap<>();
            for (ProbeType type : ProbeType.values()) {
                Map<String, String> typeInfo = new HashMap<>();
                typeInfo.put("code", type.getCode());
                typeInfo.put("desc", type.getDesc());
                typeInfo.put("protocol", type.getProtocol());
                result.put(type.getCode(), typeInfo);
            }
            return result;
        }, "获取探针类型失败");
    }

    /**
     * 根据类型查询探针
     */
    @GetMapping("/type/{type}")
    public Result<Page<Probe>> listByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        // 使用ValidationUtil验证分页参数
        Result<Void> error = ValidationUtil.validatePageParams(pageNum, pageSize);
        if (error != null) {
            return Result.error(error.getMessage());
        }

        return ControllerHelper.safeGet(
                () -> probeService.getPage(pageNum, pageSize, null, null, type),
                "按类型查询探针失败"
        );
    }

    /**
     * 获取所有在线探针列表
     */
    @GetMapping("/online")
    public Result<List<Probe>> getOnlineProbes() {
        log.info("[获取在线探针] 接收在线探针列表请求");

        return ControllerHelper.safeGet(
                () -> probeService.getOnlineProbes(),
                "获取在线探针列表失败"
        );
    }

    /**
     * 导出探针列表为Excel
     */
    @RequirePermission(Permissions.METRIC_EXPORT)
    @GetMapping("/export")
    public void exportProbes(
            HttpServletResponse response,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) throws java.io.IOException {
        // 调用Service层生成Excel
        byte[] excelBytes = probeService.exportProbesToExcel(name, status, type);

        // 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = URLEncoder.encode("探针列表.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);

        // 写入响应
        try (var outputStream = response.getOutputStream()) {
            outputStream.write(excelBytes);
            outputStream.flush();
        }
    }

    /**
     * 批量创建探针
     */
    @RequirePermission(Permissions.PROBE_CREATE)
    @Audited(operation = "CREATE", module = "Probe", description = "批量创建探针")
    @PostMapping("/batch")
    public Result<Map<String, Object>> batchCreate(@Valid @RequestBody List<Probe> probes) {
        return ControllerHelper.safeGet(() -> {
            List<Probe> createdProbes = probeService.batchCreate(probes);
            Map<String, Object> result = new HashMap<>();
            result.put("total", probes.size());
            result.put("success", createdProbes.size());
            result.put("failed", probes.size() - createdProbes.size());
            result.put("probes", createdProbes);
            log.info("批量创建探针成功，总数: {}，成功: {}", probes.size(), createdProbes.size());
            return result;
        }, "批量创建探针失败");
    }

    /**
     * JSON配置导入探针
     */
    @RequirePermission(Permissions.PROBE_CREATE)
    @Audited(operation = "CREATE", module = "Probe", description = "JSON导入探针")
    @PostMapping("/import/json")
    public Result<Map<String, Object>> importFromJson(@RequestBody String jsonString) {
        return ControllerHelper.safeGet(() -> {
            JSONObject json = JSON.parseObject(jsonString);
            List<Probe> probes = json.getList("probes", Probe.class);

            if (probes == null || probes.isEmpty()) {
                throw new IllegalArgumentException("JSON配置中没有探针数据");
            }

            // 批量创建探针
            List<Probe> createdProbes = probeService.batchCreate(probes);

            Map<String, Object> result = new HashMap<>();
            result.put("total", probes.size());
            result.put("success", createdProbes.size());
            result.put("failed", probes.size() - createdProbes.size());
            result.put("probes", createdProbes);

            log.info("JSON导入探针成功，总数: {}，成功: {}", probes.size(), createdProbes.size());
            return result;
        }, "JSON导入探针失败");
    }

    /**
     * 导出探针配置为JSON
     */
    @RequirePermission(Permissions.METRIC_EXPORT)
    @GetMapping("/export/json")
    public void exportJson(
            HttpServletResponse response,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) throws java.io.IOException {
        // 调用Service层生成JSON
        String jsonStr = probeService.exportProbesToJson(name, status, type);

        // 设置响应头
        response.setContentType("application/json;charset=UTF-8");
        String filename = URLEncoder.encode("探针配置.json", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);

        // 写入响应
        try (var outputStream = response.getOutputStream()) {
            outputStream.write(jsonStr.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }
    }

    // ========================================
    // FILE 类型探针特有操作
    // ========================================

    /**
     * 触发文件扫描（统一入口）
     */
    @PostMapping("/{id}/scan")
    public Result<Map<String, Object>> triggerFileScan(@PathVariable Long id) {
        return ControllerHelper.safeGet(() -> {
            Probe probe = probeService.getById(id);
            if (probe == null) {
                throw new IllegalArgumentException("探针不存在");
            }
            if (!"FILE".equals(probe.getType())) {
                throw new IllegalArgumentException("该探针不是FILE类型");
            }

            // 从 probeKey 提取 agentCode
            String agentCode = probe.getAgentCode();
            if (agentCode == null || agentCode.isEmpty()) {
                agentCode = probe.getProbeKey().split("-")[0];
            }

            Map<String, Object> payload = new HashMap<>();
            if (probe.getConfig() != null) {
                try {
                    Map<String, Object> config = JSON.parseObject(probe.getConfig(), Map.class);
                    if (config.containsKey("scanPath")) {
                        payload.put("scanPath", config.get("scanPath"));
                    }
                } catch (Exception ignored) {}
            }
            if (payload.isEmpty()) {
                payload.put("scanPath", "/");
            }

            Map<String, Object> command = new HashMap<>();
            command.put("type", "REQUEST");
            command.put("cmd", "FILE_PROBE");
            command.put("probeKey", probe.getProbeKey());
            command.put("payload", payload);
            command.put("timestamp", System.currentTimeMillis());

            boolean sent = metaProbeWebSocketHandler.sendControlCommandByAgentCode(
                agentCode, "FILE_PROBE", command);

            if (!sent) {
                throw new RuntimeException("Agent未在线或发送失败");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("probeKey", probe.getProbeKey());
            data.put("message", "扫描指令已发送到Agent");
            return data;
        }, "触发扫描失败");
    }

    /**
     * 获取文件列表（统一入口）
     */
    @GetMapping("/{id}/files")
    public Result<Page<com.lixin.probe.entity.FileMetadata>> getFiles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String search) {

        return ControllerHelper.safeGet(() -> {
            Probe probe = probeService.getById(id);
            if (probe == null) {
                throw new IllegalArgumentException("探针不存在");
            }

            return fileProbeService.getFileMetadata(id, pageNum, pageSize, search);
        }, "获取文件列表失败");
    }

    // ========================================
    // FILE 类型探针 - 文件上传到扫描目录
    // ========================================

    /**
     * 上传文件到文件探针的扫描路径
     */
    @PostMapping("/{id}/upload")
    public Result<Map<String, Object>> uploadFileToProbe(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "relativePath", required = false, defaultValue = "") String relativePath) {

        return ControllerHelper.safeGet(() -> {
            Probe probe = probeService.getById(id);
            if (probe == null) {
                throw new IllegalArgumentException("探针不存在");
            }
            if (!"FILE".equals(probe.getType())) {
                throw new IllegalArgumentException("只有文件探针支持上传文件");
            }

            // 从 config 中获取 scanPath，取不到则从 file_probe 表获取
            String scanPath = null;
            try {
                if (probe.getConfig() != null && !probe.getConfig().isEmpty()) {
                    JSONObject config = JSON.parseObject(probe.getConfig());
                    scanPath = config.getString("scanPath");
                    if (scanPath == null || scanPath.isEmpty()) {
                        scanPath = config.getString("path");
                    }
                }
            } catch (Exception ignored) {}

            // 从 file_probe 表获取扫描路径
            if ((scanPath == null || scanPath.isEmpty() || "/".equals(scanPath)) && fileProbeMapper != null) {
                try {
                    com.lixin.probe.entity.FileProbe fileProbe = fileProbeMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.lixin.probe.entity.FileProbe>()
                            .eq(com.lixin.probe.entity.FileProbe::getProbeKey, probe.getProbeKey()));
                    if (fileProbe != null && fileProbe.getScanPath() != null) {
                        scanPath = fileProbe.getScanPath();
                    }
                } catch (Exception ignored) {}
            }

            if (scanPath == null || scanPath.isEmpty() || "/".equals(scanPath)) {
                scanPath = System.getProperty("user.home") + "/probe-uploads";
            }

            // 构建目标目录
            String targetDir = scanPath;
            if (relativePath != null && !relativePath.isEmpty()) {
                targetDir = scanPath.endsWith("/") ? scanPath + relativePath : scanPath + "/" + relativePath;
            }

            java.io.File dir = new java.io.File(targetDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 保存文件
            String originalName = file.getOriginalFilename();
            String targetPath = dir.getAbsolutePath() + "/" + (originalName != null ? originalName : "upload");
            java.io.File dest = new java.io.File(targetPath);

            // 避免文件名冲突
            int counter = 1;
            while (dest.exists()) {
                String name = originalName != null ? originalName.substring(0, originalName.lastIndexOf('.')) : "upload";
                String ext = originalName != null && originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : "";
                targetPath = dir.getAbsolutePath() + "/" + name + "_" + counter + ext;
                dest = new java.io.File(targetPath);
                counter++;
            }

            try {
                file.transferTo(dest);
            } catch (java.io.IOException e) {
                throw new RuntimeException("文件保存失败: " + e.getMessage(), e);
            }

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("filename", originalName);
            result.put("savedPath", targetPath);
            result.put("size", file.getSize());
            return result;
        }, "上传文件失败");
    }

    // ========================================
    // DATABASE 类型探针特有操作
    // ========================================

    /**
     * 获取数据库实例列表（统一入口）
     */
    @GetMapping("/{probeKey}/instances")
    public Result<Map<String, Object>> getDatabaseInstances(@PathVariable String probeKey) {
        return ControllerHelper.safeGet(() -> {
            Probe probe = probeService.getByProbeKey(probeKey);
            if (probe == null) {
                throw new IllegalArgumentException("探针不存在: " + probeKey);
            }

            String databaseType = null;
            try {
                String configJson = probe.getConfig();
                if (configJson != null && !configJson.isEmpty()) {
                    Map<String, Object> config = JSON.parseObject(configJson, Map.class);
                    databaseType = (String) config.get("databaseType");
                }
            } catch (Exception ignored) {}

            if (databaseType == null || databaseType.isEmpty()) {
                log.warn("探针 {} 的config中未找到databaseType，将返回所有活跃的数据库连接", probeKey);
            }

            boolean isOnline = "online".equalsIgnoreCase(probe.getStatus());

            Map<String, Object> result = new HashMap<>();
            result.put("databaseType", databaseType);
            result.put("agentStatus", isOnline ? "online" : "offline");

            if (databaseConnectionMapper != null) {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.lixin.probe.entity.DatabaseConnection> queryWrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.lixin.probe.entity.DatabaseConnection>()
                        .eq(com.lixin.probe.entity.DatabaseConnection::getIsActive, true);

                if (databaseType != null && !databaseType.isEmpty()) {
                    queryWrapper.apply("LOWER(database_type) = LOWER({0})", databaseType);
                }

                List<com.lixin.probe.entity.DatabaseConnection> connections =
                    databaseConnectionMapper.selectList(
                        queryWrapper.orderByAsc(com.lixin.probe.entity.DatabaseConnection::getId)
                    );

                List<Map<String, Object>> instances = new ArrayList<>();
                for (com.lixin.probe.entity.DatabaseConnection conn : connections) {
                    Map<String, Object> instance = new HashMap<>();
                    instance.put("id", conn.getId());
                    instance.put("name", conn.getName());
                    instance.put("databaseType", conn.getDatabaseType());
                    instance.put("databaseHost", conn.getDatabaseHost());
                    instance.put("databasePort", conn.getDatabasePort());
                    instance.put("databaseName", conn.getDatabaseName());
                    instance.put("username", conn.getUsername());
                    instance.put("status", isOnline ? "online" : "offline");
                    instance.put("probeKey", probeKey);
                    instances.add(instance);
                }
                result.put("instances", instances);
            }

            // 返回用户上次选择的数据库实例ID（从database_probe表获取）
            if (databaseProbeService != null) {
                com.lixin.probe.entity.DatabaseProbe dbProbe =
                    databaseProbeService.getByProbeKey(probeKey);
                if (dbProbe != null && dbProbe.getCurrentConnectionId() != null) {
                    result.put("currentConnectionId", dbProbe.getCurrentConnectionId());
                }
            }

            return result;
        }, "获取数据库实例列表失败");
    }

    /**
     * 保存用户选择的数据库实例（持久化到database_probe表）
     */
    @PostMapping("/{probeKey}/selected-instance")
    public Result<String> saveSelectedInstance(
            @PathVariable String probeKey,
            @RequestParam Long connectionId) {
        log.info("保存选择的数据库实例: probeKey={}, connectionId={}", probeKey, connectionId);
        return ControllerHelper.safeExecute(() -> {
            if (databaseProbeService != null) {
                com.lixin.probe.entity.DatabaseProbe dbProbe =
                    databaseProbeService.getByProbeKey(probeKey);
                if (dbProbe != null) {
                    dbProbe.setCurrentConnectionId(connectionId);
                    dbProbe.setUpdateTime(java.time.LocalDateTime.now());
                    databaseProbeService.update(dbProbe);
                    log.info("已保存选择的数据库实例: probeKey={}, connectionId={}", probeKey, connectionId);
                }
            }
        }, "保存成功", "保存失败");
    }

    /**
     * 测试数据库连接
     */
    @PostMapping("/test-connection")
    public Result<Map<String, Object>> testConnection(@RequestBody Map<String, Object> request) {
        return ControllerHelper.safeGet(() -> {
            if (databaseProbeService == null) {
                throw new RuntimeException("DatabaseProbeService不可用");
            }
            com.lixin.probe.entity.DatabaseProbe entity = new com.lixin.probe.entity.DatabaseProbe();
            entity.setDatabaseType((String) request.get("databaseType"));
            entity.setDatabaseHost((String) request.get("databaseHost"));
            entity.setDatabasePort(request.get("databasePort") != null ? ((Number) request.get("databasePort")).intValue() : null);
            entity.setDatabaseName((String) request.get("databaseName"));
            entity.setUsername((String) request.get("username"));
            entity.setPassword((String) request.get("password"));

            boolean success = databaseProbeService.testConnection(entity);
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "连接成功" : "连接失败");
            return result;
        }, "测试连接失败");
    }

    /**
     * 触发数据库元数据采集（统一入口）
     */
    @PostMapping("/{probeKey}/collect")
    public Result<String> triggerCollect(@PathVariable String probeKey) {
        return ControllerHelper.safeExecute(() -> {
            Probe probe = probeService.getByProbeKey(probeKey);
            if (probe == null) {
                throw new IllegalArgumentException("探针不存在: " + probeKey);
            }

            boolean sent = metaProbeWebSocketHandler.sendCollectCommand(probeKey, "DATABASE");
            if (!sent) {
                throw new RuntimeException("采集命令发送失败");
            }
        }, "采集命令发送成功", "触发采集失败");
    }
}
