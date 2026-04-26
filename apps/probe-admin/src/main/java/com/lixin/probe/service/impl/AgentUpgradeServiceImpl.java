package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixin.probe.entity.AgentVersion;
import com.lixin.probe.mapper.AgentVersionMapper;
import com.lixin.probe.service.AgentUpgradeService;
import com.lixin.probe.websocket.MetaProbeWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AgentUpgradeServiceImpl implements AgentUpgradeService {

    private static final String UPLOAD_DIR = System.getProperty("user.home", "/tmp") + "/probe-upgrades";

    @Autowired
    private AgentVersionMapper agentVersionMapper;

    @Autowired
    private MetaProbeWebSocketHandler webSocketHandler;

    @Override
    public AgentVersion uploadVersion(MultipartFile file, String version, String releaseNotes) {
        try {
            Path uploadDir = Paths.get(UPLOAD_DIR);
            Files.createDirectories(uploadDir);

            String filename = "probe-agent-" + version + ".jar";
            Path filePath = uploadDir.resolve(filename);
            file.transferTo(filePath.toFile());

            String checksum = sha256(filePath);

            AgentVersion agentVersion = AgentVersion.builder()
                    .version(version)
                    .filePath(filePath.toString())
                    .fileSize(file.getSize())
                    .checksum(checksum)
                    .releaseNotes(releaseNotes)
                    .createTime(LocalDateTime.now())
                    .uploadedBy("admin")
                    .build();
            agentVersionMapper.insert(agentVersion);

            log.info("[AgentUpgrade] Uploaded version {}: size={}, checksum={}", version, file.getSize(), checksum);
            return agentVersion;
        } catch (IOException e) {
            throw new RuntimeException("上传版本文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void triggerUpgrade(String agentCode, String targetVersion) {
        AgentVersion version = agentVersionMapper.selectOne(
                new LambdaQueryWrapper<AgentVersion>().eq(AgentVersion::getVersion, targetVersion));
        if (version == null) throw new IllegalArgumentException("版本不存在: " + targetVersion);

        Map<String, Object> payload = Map.of(
                "targetVersion", targetVersion,
                "downloadUrl", "/api/agent-upgrade/download/" + version.getId(),
                "checksum", version.getChecksum(),
                "fileSize", version.getFileSize()
        );

        Map<String, Object> command = Map.of(
                "type", "COMMAND",
                "cmd", "UPGRADE",
                "payload", payload
        );

        boolean sent = webSocketHandler.sendControlCommandByAgentCode(agentCode, "UPGRADE", command);
        if (!sent) throw new RuntimeException("Agent不在线: " + agentCode);
        log.info("[AgentUpgrade] Upgrade command sent: agent={}, version={}", agentCode, targetVersion);
    }

    @Override
    public void triggerBatchUpgrade(List<String> agentCodes, String targetVersion) {
        for (String code : agentCodes) {
            try {
                triggerUpgrade(code, targetVersion);
            } catch (Exception e) {
                log.warn("[AgentUpgrade] Failed for agent {}: {}", code, e.getMessage());
            }
        }
    }

    @Override
    public List<AgentVersion> listVersions() {
        return agentVersionMapper.selectList(
                new LambdaQueryWrapper<AgentVersion>().orderByDesc(AgentVersion::getCreateTime));
    }

    @Override
    public void deleteVersion(Long id) {
        AgentVersion version = agentVersionMapper.selectById(id);
        if (version != null && version.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(version.getFilePath()));
            } catch (IOException e) {
                log.warn("[AgentUpgrade] Failed to delete file: {}", version.getFilePath());
            }
        }
        agentVersionMapper.deleteById(id);
    }

    @Override
    public Map<String, Object> getUpgradeStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("versions", listVersions().size());
        status.put("latestVersion", listVersions().stream().findFirst().map(AgentVersion::getVersion).orElse("none"));
        return status;
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(path));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
