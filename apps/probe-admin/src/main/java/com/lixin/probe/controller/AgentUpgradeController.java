package com.lixin.probe.controller;

import com.lixin.probe.common.Result;
import com.lixin.probe.entity.AgentVersion;
import com.lixin.probe.service.AgentUpgradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent-upgrade")
public class AgentUpgradeController {

    @Autowired
    private AgentUpgradeService agentUpgradeService;

    @PostMapping("/upload")
    public Result<AgentVersion> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("version") String version,
            @RequestParam(value = "releaseNotes", required = false) String releaseNotes) {
        return Result.success(agentUpgradeService.uploadVersion(file, version, releaseNotes));
    }

    @PostMapping("/trigger")
    public Result<Void> trigger(@RequestBody Map<String, Object> body) {
        String agentCode = (String) body.get("agentCode");
        String targetVersion = (String) body.get("targetVersion");
        agentUpgradeService.triggerUpgrade(agentCode, targetVersion);
        return Result.success(null);
    }

    @PostMapping("/trigger-batch")
    public Result<Void> triggerBatch(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> agentCodes = (List<String>) body.get("agentCodes");
        String targetVersion = (String) body.get("targetVersion");
        agentUpgradeService.triggerBatchUpgrade(agentCodes, targetVersion);
        return Result.success(null);
    }

    @GetMapping("/versions")
    public Result<List<AgentVersion>> listVersions() {
        return Result.success(agentUpgradeService.listVersions());
    }

    @DeleteMapping("/versions/{id}")
    public Result<Void> deleteVersion(@PathVariable Long id) {
        agentUpgradeService.deleteVersion(id);
        return Result.success(null);
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(agentUpgradeService.getUpgradeStatus());
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        List<AgentVersion> versions = agentUpgradeService.listVersions();
        AgentVersion version = versions.stream()
                .filter(v -> v.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("版本不存在"));
        FileSystemResource resource = new FileSystemResource(Paths.get(version.getFilePath()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=probe-agent-" + version.getVersion() + ".jar")
                .body(resource);
    }
}
