package com.lixin.probe.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.dto.QualityRuleDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 质量规则同步服务 - 定期从 Admin 拉取质量规则
 */
@Service
public class QualityRuleSyncService {

    private static final Logger log = LoggerFactory.getLogger(QualityRuleSyncService.class);

    private final AgentProperties properties;
    private final RestTemplate restTemplate;

    private final ConcurrentHashMap<String, List<QualityRuleDTO>> ruleCache = new ConcurrentHashMap<>();

    public QualityRuleSyncService(AgentProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    public void syncRules() {
        try {
            String url = "http://" + properties.getServer().getHost() + ":" + properties.getServer().getPort()
                    + "/api/agents/" + properties.getCode() + "/quality-rules";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, List<QualityRuleDTO>> newCache = new HashMap<>();
                JSONArray arr = JSON.parseArray(JSON.parseObject(response.getBody()).getString("data"));
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        QualityRuleDTO rule = arr.getJSONObject(i).toJavaObject(QualityRuleDTO.class);
                        String key = ruleKey(rule);
                        newCache.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
                    }
                }
                ruleCache.clear();
                ruleCache.putAll(newCache);
                int total = newCache.values().stream().mapToInt(List::size).sum();
                if (total > 0) log.info("[QualityRuleSync] 同步 {} 条质量规则", total);
            }
        } catch (Exception e) {
            log.debug("[QualityRuleSync] 同步失败: {}", e.getMessage());
        }
    }

    /**
     * 获取匹配的质量规则
     */
    public List<QualityRuleDTO> getRules(String probeKey, String databaseName, String tableName) {
        List<QualityRuleDTO> result = new ArrayList<>();
        // 精确匹配
        String exactKey = ruleKeyExact(probeKey, databaseName, tableName);
        if (ruleCache.containsKey(exactKey)) result.addAll(ruleCache.get(exactKey));
        // 表级匹配
        String tableKey = ruleKeyExact(probeKey, databaseName, null);
        if (ruleCache.containsKey(tableKey)) result.addAll(ruleCache.get(tableKey));
        // 库级匹配
        String dbKey = ruleKeyExact(probeKey, null, null);
        if (ruleCache.containsKey(dbKey)) result.addAll(ruleCache.get(dbKey));
        // 全局规则
        String globalKey = "*";
        if (ruleCache.containsKey(globalKey)) result.addAll(ruleCache.get(globalKey));
        return result;
    }

    private String ruleKey(QualityRuleDTO rule) {
        return ruleKeyExact(rule.getProbeKey(), rule.getDatabaseName(), rule.getTableName());
    }

    private String ruleKeyExact(String probeKey, String db, String table) {
        return (probeKey != null ? probeKey : "*") + ":" +
                (db != null && !db.isEmpty() ? db : "*") + ":" +
                (table != null && !table.isEmpty() ? table : "*");
    }
}
