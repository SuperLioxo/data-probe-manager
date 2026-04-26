package com.lixin.probe.agent.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.config.DatabaseConfigManager;
import com.lixin.probe.agent.config.DatabaseConnectionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 数据源注册服务
 * Agent 启动时将 database-config.yml 中的连接信息注册到 Admin 端
 */
@Service
public class DataSourceRegistrationService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSourceRegistrationService.class);

    private final AgentProperties properties;
    private final DatabaseConfigManager configManager;
    private final RestTemplate restTemplate;

    public DataSourceRegistrationService(AgentProperties properties,
                                          DatabaseConfigManager configManager,
                                          RestTemplate restTemplate) {
        this.properties = properties;
        this.configManager = configManager;
        this.restTemplate = restTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!Boolean.TRUE.equals(properties.getStartup().getAutoRegister())) {
            log.info("[数据源注册] 自动注册未启用，跳过");
            return;
        }

        int retryTimes = properties.getStartup().getRegisterRetryTimes();
        long retryInterval = properties.getStartup().getRegisterRetryInterval();

        for (int i = 0; i < retryTimes; i++) {
            try {
                register();
                return;
            } catch (Exception e) {
                log.warn("[数据源注册] 第 {} 次注册失败: {}", i + 1, e.getMessage());
                if (i < retryTimes - 1) {
                    try { Thread.sleep(retryInterval); } catch (InterruptedException ignored) { return; }
                }
            }
        }
        log.error("[数据源注册] 达到最大重试次数 {}，注册失败", retryTimes);
    }

    public void register() {
        List<DatabaseConnectionConfig> databases = configManager.getEnabledDatabases();
        if (databases.isEmpty()) {
            log.info("[数据源注册] 没有启用的数据源，跳过注册");
            return;
        }

        JSONArray payload = new JSONArray();
        for (DatabaseConnectionConfig db : databases) {
            JSONObject ds = new JSONObject();
            ds.put("instanceId", db.getInstanceId());
            ds.put("databaseType", db.getDatabaseType());
            ds.put("host", db.getHost());
            ds.put("port", db.getPort());
            ds.put("databaseName", db.getDatabaseName());
            ds.put("username", db.getUsername());
            ds.put("password", db.getPassword());
            ds.put("schemas", db.getSchemas() != null ? String.join(",", db.getSchemas()) : null);
            payload.add(ds);
        }

        String url = "http://" + properties.getServer().getHost() + ":" + properties.getServer().getPort()
                + "/api/agents/" + properties.getCode() + "/datasource-register";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(payload.toJSONString(), headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        log.info("[数据源注册] 注册成功: {} 个数据源, response={}", databases.size(), response.getBody());
    }
}
