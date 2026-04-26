package com.lixin.probe.agent.reporter;

import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.plugin.api.DatabasePlugin;
import com.lixin.probe.agent.plugin.loader.SpiPluginLoader;
import com.lixin.probe.agent.pojo.report.PluginReport;
import com.lixin.probe.agent.pojo.report.PluginStatusChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 插件上报器
 * 负责向管理系统上报插件信息和状态变更
 *
 * @author probe-agent
 * @since 1.0.0
 */
@Component
public class PluginReporter {

    private static final Logger log = LoggerFactory.getLogger(PluginReporter.class);

    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private SpiPluginLoader pluginLoader;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    /**
     * 启动时上报插件列表
     */
    @PostConstruct
    public void reportPluginsOnStartup() {
        if (restTemplate == null) {
            log.warn("RestTemplate未配置，跳过插件上报");
            return;
        }

        try {
            doReportPlugins();
        } catch (Exception e) {
            log.error("启动时上报插件列表失败", e);
        }
    }

    /**
     * 执行插件上报
     */
    private void doReportPlugins() {
        // 1. 获取所有插件
        Collection<DatabasePlugin> plugins = pluginLoader.getAllPlugins();

        if (plugins.isEmpty()) {
            log.warn("未加载任何插件，跳过上报");
            return;
        }

        // 2. 转换为PluginInfo
        List<PluginReport.PluginInfo> pluginInfos = plugins.stream()
                .map(this::toPluginInfo)
                .collect(Collectors.toList());

        // 3. 构建上报对象
        PluginReport report = PluginReport.builder()
                .agentCode(agentProperties.getCode())
                .agentHost(getLocalHost())
                .agentPort(getAgentPort())
                .plugins(pluginInfos)
                .reportTime(System.currentTimeMillis())
                .build();

        // 4. 上报到管理系统
        String url = buildReportUrl("/api/plugins/report");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PluginReport> request = new HttpEntity<>(report, headers);
            String response = restTemplate.postForObject(url, request, String.class);

            log.info("插件列表上报成功: {} 个插件, 响应: {}", pluginInfos.size(), response);
        } catch (Exception e) {
            log.error("插件列表上报失败, URL: {}", url, e);
        }
    }

    /**
     * 上报插件状态变更
     */
    @Async
    public void reportPluginStatus(String pluginId, String oldStatus, String newStatus, String reason) {
        if (restTemplate == null) {
            return;
        }

        try {
            PluginStatusChange change = PluginStatusChange.builder()
                    .agentCode(agentProperties.getCode())
                    .pluginId(pluginId)
                    .oldStatus(oldStatus)
                    .newStatus(newStatus)
                    .changeReason(reason)
                    .changeTime(System.currentTimeMillis())
                    .operator("SYSTEM")
                    .build();

            String url = buildReportUrl("/api/plugins/status/change");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PluginStatusChange> request = new HttpEntity<>(change, headers);
            restTemplate.postForObject(url, request, String.class);

            log.info("插件状态变更上报成功: {} {} -> {}", pluginId, oldStatus, newStatus);
        } catch (Exception e) {
            log.error("插件状态变更上报失败: pluginId={}", pluginId, e);
        }
    }

    /**
     * 获取本地主机地址
     */
    private String getLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /**
     * 获取探针端口
     */
    private Integer getAgentPort() {
        return agentProperties.getServer().getPort();
    }

    /**
     * 构建上报URL
     */
    private String buildReportUrl(String path) {
        AgentProperties.Server server = agentProperties.getServer();
        return String.format("http://%s:%d%s",
                server.getHost(),
                server.getPort(),
                path);
    }

    /**
     * 转换为PluginInfo
     */
    private PluginReport.PluginInfo toPluginInfo(DatabasePlugin plugin) {
        return PluginReport.PluginInfo.builder()
                .pluginId(plugin.getPluginId())
                .name(plugin.getName())
                .type(plugin.getType())
                .version(plugin.getVersion())
                .dbType(plugin.getDbType())
                .versionRange(plugin.getVersionRange())
                .description(plugin.getDescription())
                .status("ACTIVE")
                .loadTime(System.currentTimeMillis())
                .build();
    }
}
