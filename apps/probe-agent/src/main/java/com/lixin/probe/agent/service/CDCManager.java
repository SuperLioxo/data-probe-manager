package com.lixin.probe.agent.service;

import com.alibaba.fastjson2.JSON;
import com.lixin.probe.agent.config.AgentProperties;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;

/**
 * CDC Manager - 管理 CDC 流的生命周期，定期批量上报变更事件到管理端
 */
@Service
public class CDCManager {

    private static final Logger log = LoggerFactory.getLogger(CDCManager.class);

    private final AgentProperties properties;
    private final RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private QualityRuleSyncService qualityRuleSyncService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private QualityValidator qualityValidator;

    private final BlockingQueue<ProbeResponse.CDCEvent> eventBuffer = new LinkedBlockingQueue<>(50000);
    private volatile boolean running = true;

    public CDCManager(AgentProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    private String getAdminUrl() {
        return "http://" + properties.getServer().getHost() + ":" + properties.getServer().getPort();
    }

    private String getAgentCode() {
        return properties.getCode();
    }

    /**
     * 接收 CDC 事件到缓冲区
     */
    public void enqueueEvent(ProbeResponse.CDCEvent event) {
        if (!eventBuffer.offer(event)) {
            log.warn("[CDCManager] Event buffer full, dropping event: {}/{}.{}", event.getEventType(), event.getDatabase(), event.getTable());
        }
    }

    /**
     * 批量接收 CDC 事件
     */
    public void enqueueEvents(List<ProbeResponse.CDCEvent> events) {
        for (ProbeResponse.CDCEvent event : events) {
            if (!eventBuffer.offer(event)) {
                log.warn("[CDCManager] Event buffer full, dropped {} events", events.size());
                break;
            }
        }
    }

    /**
     * 定期批量上报 CDC 事件到管理端（每5秒）
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void flushEvents() {
        if (!running || eventBuffer.isEmpty()) return;

        List<ProbeResponse.CDCEvent> batch = new ArrayList<>(500);
        eventBuffer.drainTo(batch, 500);
        if (batch.isEmpty()) return;

        try {
            // 质量过滤
            List<ProbeResponse.CDCEvent> goodEvents = batch;
            List<Map<String, Object>> badRecords = new ArrayList<>();

            if (qualityRuleSyncService != null && qualityValidator != null) {
                goodEvents = new ArrayList<>();
                for (ProbeResponse.CDCEvent event : batch) {
                    List<com.lixin.probe.agent.dto.QualityRuleDTO> rules = qualityRuleSyncService.getRules(
                            "", event.getDatabase(), event.getTable());
                    if (rules.isEmpty()) {
                        goodEvents.add(event);
                        continue;
                    }
                    Map<String, Object> row = event.getAfter() != null ? event.getAfter() : event.getBefore();
                    if (row == null) {
                        goodEvents.add(event);
                        continue;
                    }
                    List<com.lixin.probe.agent.dto.QualityRuleDTO> violations =
                            qualityValidator.validate(row, event.getDatabase(), event.getTable(), rules);
                    if (violations.isEmpty()) {
                        goodEvents.add(event);
                    } else {
                        Map<String, Object> badRecord = new java.util.LinkedHashMap<>();
                        badRecord.put("probeKey", "");
                        badRecord.put("databaseName", event.getDatabase());
                        badRecord.put("tableName", event.getTable());
                        badRecord.put("eventType", event.getEventType());
                        badRecord.put("data", row);
                        badRecord.put("violations", violations.stream().map(v -> {
                            Map<String, Object> m = new java.util.LinkedHashMap<>();
                            m.put("ruleId", v.getId());
                            m.put("ruleName", v.getRuleName());
                            m.put("ruleType", v.getRuleType());
                            m.put("columnName", v.getColumnName());
                            m.put("severity", v.getSeverity());
                            return m;
                        }).toList());
                        badRecord.put("timestamp", event.getTimestamp());
                        badRecords.add(badRecord);
                    }
                }
                if (!badRecords.isEmpty()) {
                    reportBadRecords(badRecords);
                }
            }

            if (goodEvents.isEmpty()) return;

            String url = getAdminUrl() + "/api/agents/" + getAgentCode() + "/cdc-events";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(goodEvents), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[CDCManager] Flushed {} CDC events to admin", batch.size());
            } else {
                log.warn("[CDCManager] Failed to flush events: status={}", response.getStatusCode());
                // Re-queue on failure (best effort)
                for (int i = 0; i < Math.min(batch.size(), 100); i++) {
                    eventBuffer.offer(batch.get(i));
                }
            }
        } catch (Exception e) {
            log.error("[CDCManager] Error flushing CDC events: {}", e.getMessage());
            // Re-queue on failure (best effort)
            for (int i = 0; i < Math.min(batch.size(), 100); i++) {
                eventBuffer.offer(batch.get(i));
            }
        }
    }

    /**
     * 获取缓冲区状态
     */
    public int getBufferSize() {
        return eventBuffer.size();
    }

    /**
     * 停止 CDC Manager
     */
    public void shutdown() {
        running = false;
        flushEvents();
    }

    /**
     * 上报坏记录到 Admin
     */
    private void reportBadRecords(List<Map<String, Object>> badRecords) {
        try {
            String url = getAdminUrl() + "/api/agents/" + getAgentCode() + "/quality-bad-records";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(badRecords), headers);
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("[CDCManager] Reported {} bad records to admin", badRecords.size());
        } catch (Exception e) {
            log.warn("[CDCManager] Failed to report bad records: {}", e.getMessage());
        }
    }
}
