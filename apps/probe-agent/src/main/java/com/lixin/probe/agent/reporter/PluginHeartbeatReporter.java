package com.lixin.probe.agent.reporter;

import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.plugin.loader.SpiPluginLoader;
import com.lixin.probe.agent.pojo.report.PluginStatus;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件心跳上报器
 * 定期向管理系统上报插件状态，保持连接活跃
 *
 * @author probe-agent
 * @since 1.0.0
 */
@Component
public class PluginHeartbeatReporter {

    private static final Logger log = LoggerFactory.getLogger(PluginHeartbeatReporter.class);

    @Autowired
    private AgentProperties agentProperties;

    @Autowired
    private SpiPluginLoader pluginLoader;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    /**
     * 定期心跳上报
     * 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void heartbeatReport() {
        if (restTemplate == null) {
            return;
        }

        try {
            // 1. 收集所有插件状态
            List<PluginStatus> statuses = collectPluginStatuses();

            if (statuses.isEmpty()) {
                return;
            }

            // 2. 上报到管理系统
            String url = buildReportUrl("/api/plugins/heartbeat");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<List<PluginStatus>> request = new HttpEntity<>(statuses, headers);
            restTemplate.postForObject(url, request, String.class);

            log.debug("插件心跳上报成功: {} 个插件", statuses.size());

        } catch (RestClientException e) {
            String host = agentProperties.getServer().getHost();
            int port = agentProperties.getServer().getPort();

            // 检查是否是连接拒绝错误
            if (e.getCause() instanceof HttpHostConnectException) {
                log.error("插件心跳上报失败：无法连接到后端服务器 http://{}:{}，" +
                        "请确认：\n" +
                        "  1. 后端服务已启动\n" +
                        "  2. 端口 {} 已开放\n" +
                        "  3. 检查后端日志确认服务状态",
                        host, port, port);
            } else {
                log.warn("插件心跳上报失败（可能管理系统未启动）: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("插件心跳上报失败（未预期的错误）: {}", e.getMessage());
        }
    }

    /**
     * 收集所有插件状态
     */
    private List<PluginStatus> collectPluginStatuses() {
        List<PluginStatus> statuses = new ArrayList<>();

        pluginLoader.getAllPlugins().forEach(plugin -> {
            PluginStatus status = PluginStatus.builder()
                    .agentCode(agentProperties.getCode())
                    .pluginId(plugin.getPluginId())
                    .status("ACTIVE")
                    .heartbeatTime(System.currentTimeMillis())
                    .version(plugin.getVersion())
                    .errorMessage(null)
                    .build();

            statuses.add(status);
        });

        return statuses;
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
}
