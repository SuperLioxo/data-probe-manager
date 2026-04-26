package com.lixin.probe.service;

import com.lixin.probe.entity.AgentVersion;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface AgentUpgradeService {

    AgentVersion uploadVersion(MultipartFile file, String version, String releaseNotes);

    void triggerUpgrade(String agentCode, String targetVersion);

    void triggerBatchUpgrade(List<String> agentCodes, String targetVersion);

    List<AgentVersion> listVersions();

    void deleteVersion(Long id);

    Map<String, Object> getUpgradeStatus();
}
