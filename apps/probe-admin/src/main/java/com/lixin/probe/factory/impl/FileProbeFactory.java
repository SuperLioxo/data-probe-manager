package com.lixin.probe.factory.impl;

import com.lixin.probe.dto.ProbeCreateRequest;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.factory.AbstractProbeFactory;
import org.springframework.stereotype.Component;

/**
 * 文件探针工厂
 * 负责创建和配置文件探针
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Component
public class FileProbeFactory extends AbstractProbeFactory {

    @Override
    protected Probe buildProbeEntity(ProbeCreateRequest request) {
        return Probe.builder()
                .probeKey(request.getProbeKey())
                .name(request.getName())
                .type("FILE")
                .description("文件系统监控探针")
                .build();
    }

    @Override
    protected int getDefaultCollectInterval() {
        return 300; // 文件探针默认5分钟
    }

    @Override
    public String getSupportedType() {
        return "FILE";
    }

    @Override
    protected void doConfigure(Probe probe, java.util.Map<String, Object> config) {
        // 文件探针特定配置
        String scanPath = (String) config.get("scanPath");
        if (scanPath != null) {
            log.info("配置文件扫描路径: {}", scanPath);
            // 更新配置JSON
            probe.setConfig("{\"scanPath\":\"" + scanPath + "\"}");
        }
    }
}
