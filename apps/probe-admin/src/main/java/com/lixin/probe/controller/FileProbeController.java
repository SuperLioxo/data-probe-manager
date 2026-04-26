package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.FileMetadata;
import com.lixin.probe.entity.FileProbe;
import com.lixin.probe.service.FileProbeService;
import com.lixin.probe.util.ControllerHelper;
import com.lixin.probe.websocket.MetaProbeWebSocketHandler;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/file-probes")
public class FileProbeController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileProbeController.class);

    @Autowired
    private FileProbeService fileProbeService;

    @Autowired(required = false)
    private MetaProbeWebSocketHandler metaProbeWebSocketHandler;

    @GetMapping
    public Result<Page<FileProbe>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status) {
        return ControllerHelper.safeGet(
                () -> fileProbeService.getPage(pageNum, pageSize, name, status),
                "查询文件探针列表失败");
    }

    @GetMapping("/{id}")
    public Result<?> getById(@PathVariable Long id) {
        return ControllerHelper.safeGet(() -> {
            FileProbe probe = fileProbeService.getById(id);
            if (probe != null) {
                return probe;
            }
            // 回退到统一probe表
            com.lixin.probe.entity.Probe p = fileProbeService.getProbeById(id);
            if (p == null || !"FILE".equals(p.getType())) {
                throw new IllegalArgumentException("文件探针不存在");
            }
            return p;
        }, "查询文件探针失败");
    }

    @PostMapping
    public Result<String> create(@Valid @RequestBody FileProbe fileProbe) {
        log.info("[FileProbeController] 创建文件探针: name={}, probeKey={}, scanPath={}",
                fileProbe.getName(), fileProbe.getProbeKey(), fileProbe.getScanPath());
        return ControllerHelper.safeExecute(
                () -> fileProbeService.create(fileProbe),
                "创建成功", "创建文件探针失败");
    }

    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Long id, @Valid @RequestBody FileProbe fileProbe) {
        log.info("[FileProbeController] 更新文件探针: id={}, scanPath={}", id, fileProbe.getScanPath());
        return ControllerHelper.safeExecute(() -> {
            FileProbe existing = fileProbeService.getById(id);
            if (existing == null) throw new IllegalArgumentException("文件探针不存在");
            fileProbe.setId(id);
            fileProbeService.update(fileProbe);
        }, "更新成功", "更新文件探针失败");
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        return ControllerHelper.safeExecute(() -> {
            FileProbe existing = fileProbeService.getById(id);
            if (existing == null) throw new IllegalArgumentException("文件探针不存在");
            fileProbeService.delete(id);
        }, "删除成功", "删除文件探针失败");
    }

    @PostMapping("/{id}/scan")
    public Result<Map<String, Object>> triggerScan(@PathVariable Long id) {
        return ControllerHelper.safeGet(() -> {
            // 先查 file_probe 表，再回退到 probe 表
            FileProbe fileProbe = fileProbeService.getById(id);
            String probeKey;
            String scanPath = null;

            if (fileProbe != null) {
                probeKey = fileProbe.getProbeKey();
                scanPath = fileProbe.getScanPath();
            } else {
                com.lixin.probe.entity.Probe probe = fileProbeService.getProbeById(id);
                if (probe == null || !"FILE".equals(probe.getType())) {
                    throw new IllegalArgumentException("文件探针不存在");
                }
                probeKey = probe.getProbeKey();
                scanPath = probe.getConfig() != null ?
                    com.alibaba.fastjson2.JSON.parseObject(probe.getConfig()).getString("scanPath") : null;

                // probe.config 没有路径时，通过 probeKey 从 file_probe 表查找
                if ((scanPath == null || scanPath.isEmpty()) && fileProbeService != null) {
                    FileProbe fp = fileProbeService.getByProbeKey(probeKey);
                    if (fp != null) {
                        scanPath = fp.getScanPath();
                    }
                }
            }

            if (metaProbeWebSocketHandler == null) {
                throw new RuntimeException("WebSocket服务不可用");
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("scanPath", scanPath != null && !scanPath.isEmpty() ? scanPath : "/");

            Map<String, Object> command = new HashMap<>();
            command.put("type", "REQUEST");
            command.put("cmd", "FILE_PROBE");
            command.put("probeKey", probeKey);
            command.put("payload", payload);
            command.put("timestamp", System.currentTimeMillis());

            // 从probeKey提取agentCode（如 AGENT-file-mnvdnq-uwj -> AGENT）
            String agentCode = probeKey.split("-")[0];
            boolean sent = metaProbeWebSocketHandler.sendControlCommandByAgentCode(
                    agentCode, "FILE_PROBE", command);

            if (!sent) {
                throw new RuntimeException("Agent未在线或发送失败");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("probeKey", probeKey);
            data.put("message", "扫描指令已发送到Agent");
            return data;
        }, "触发扫描失败");
    }

    @GetMapping("/{id}/files")
    public Result<Page<FileMetadata>> getFiles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String search) {
        return ControllerHelper.safeGet(
                () -> fileProbeService.getFileMetadata(id, pageNum, pageSize, search),
                "获取文件列表失败");
    }
}
