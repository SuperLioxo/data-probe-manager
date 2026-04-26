package com.lixin.probe.factory.impl;

import com.lixin.probe.dto.ProbeCreateRequest;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.factory.AbstractProbeFactory;
import org.springframework.stereotype.Component;

/**
 * 数据库探针工厂
 * 负责创建和配置数据库探针
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
@Component
public class DatabaseProbeFactory extends AbstractProbeFactory {

    @Override
    protected Probe buildProbeEntity(ProbeCreateRequest request) {
        return Probe.builder()
                .probeKey(request.getProbeKey())
                .name(request.getName())
                .type("DATABASE")
                .description("数据库性能监控探针")
                .build();
    }

    @Override
    protected int getDefaultCollectInterval() {
        return 60; // 数据库探针默认1分钟
    }

    @Override
    public String getSupportedType() {
        return "DATABASE";
    }

    @Override
    protected void doConfigure(Probe probe, java.util.Map<String, Object> config) {
        // 数据库探针特定配置
        String dbType = (String) config.get("dbType");
        String host = (String) config.get("host");
        Integer port = (Integer) config.get("port");

        if (dbType != null || host != null || port != null) {
            log.info("配置数据库连接: type={}, host={}, port={}", dbType, host, port);
            // 更新配置JSON
            StringBuilder configJson = new StringBuilder("{");
            if (dbType != null) configJson.append("\"dbType\":\"").append(dbType).append("\"");
            if (host != null) configJson.append(",\"host\":\"").append(host).append("\"");
            if (port != null) configJson.append(",\"port\":").append(port);
            configJson.append("}");
            probe.setConfig(configJson.toString());
        }
    }
}
