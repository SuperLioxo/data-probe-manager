package com.lixin.probe.agent.service;

import com.alibaba.fastjson2.JSONObject;
import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.config.DatabaseConfigManager;
import com.lixin.probe.agent.config.DatabaseConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

/**
 * 数据源心跳检测服务
 * 定期对已配置的数据源执行连通性检测，并将结果上报管理端
 */
@Service
public class DataSourceHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceHeartbeatService.class);

    private final AgentProperties properties;
    private final DatabaseConfigManager configManager;
    private final RestTemplate restTemplate;

    public DataSourceHeartbeatService(AgentProperties properties, DatabaseConfigManager configManager, RestTemplate restTemplate) {
        this.properties = properties;
        this.configManager = configManager;
        this.restTemplate = restTemplate;
    }

    private String getAdminUrl() {
        return "http://" + properties.getServer().getHost() + ":" + properties.getServer().getPort();
    }

    private String getAgentCode() {
        return properties.getCode();
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void checkDataSources() {
        List<DatabaseConnectionConfig> databases = configManager.getEnabledDatabases();
        if (databases.isEmpty()) return;

        List<JSONObject> reports = new ArrayList<>();

        for (DatabaseConnectionConfig db : databases) {
            JSONObject report = checkSingleSource(db);
            reports.add(report);
        }

        if (!reports.isEmpty()) {
            sendHeartbeatReport(reports);
        }
    }

    private JSONObject checkSingleSource(DatabaseConnectionConfig db) {
        JSONObject report = new JSONObject();
        report.put("probeKey", db.getInstanceId());
        report.put("databaseType", db.getDatabaseType());
        report.put("host", db.getHost());
        report.put("port", db.getPort());
        report.put("databaseName", db.getDatabaseName());

        long start = System.currentTimeMillis();
        try {
            String url = buildJdbcUrl(db);
            try (Connection conn = DriverManager.getConnection(url, db.getUsername(), db.getPassword())) {
                boolean valid = conn.isValid(5);
                long latency = System.currentTimeMillis() - start;
                report.put("status", valid ? "online" : "offline");
                report.put("latencyMs", latency);
                report.put("errorMessage", valid ? null : "Connection validation failed");
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            report.put("status", "offline");
            report.put("latencyMs", latency);
            report.put("errorMessage", e.getMessage());
            log.warn("[数据源心跳] {} 连接失败: {}", db.getInstanceId(), e.getMessage());
        }

        report.put("timestamp", System.currentTimeMillis());
        return report;
    }

    private void sendHeartbeatReport(List<JSONObject> reports) {
        try {
            String url = getAdminUrl() + "/api/agents/" + getAgentCode() + "/datasource-heartbeat";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            JSONObject payload = new JSONObject();
            payload.put("agentCode", getAgentCode());
            payload.put("reports", reports);
            payload.put("timestamp", System.currentTimeMillis());

            HttpEntity<String> entity = new HttpEntity<>(payload.toJSONString(), headers);
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.debug("[数据源心跳] 上报 {} 个数据源状态", reports.size());
        } catch (Exception e) {
            log.warn("[数据源心跳] 上报失败: {}", e.getMessage());
        }
    }

    private String buildJdbcUrl(DatabaseConnectionConfig db) {
        return switch (db.getDatabaseType().toLowerCase()) {
            case "mysql" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&connectTimeout=5000",
                    db.getHost(), db.getPort(), db.getDatabaseName());
            case "postgresql" -> String.format("jdbc:postgresql://%s:%d/%s?connectTimeout=5",
                    db.getHost(), db.getPort(), db.getDatabaseName());
            case "oracle" -> String.format("jdbc:oracle:thin:@%s:%d:%s",
                    db.getHost(), db.getPort(), db.getDatabaseName());
            case "sqlserver" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s;loginTimeout=5",
                    db.getHost(), db.getPort(), db.getDatabaseName());
            default -> String.format("jdbc:%s://%s:%d/%s",
                    db.getDatabaseType().toLowerCase(), db.getHost(), db.getPort(), db.getDatabaseName());
        };
    }
}
