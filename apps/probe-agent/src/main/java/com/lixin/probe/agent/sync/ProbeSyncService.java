package com.lixin.probe.agent.sync;

import com.alibaba.fastjson2.JSON;
import com.lixin.probe.agent.config.AgentProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 探针同步服务
 * 定期从Admin服务获取探针配置，支持动态探针管理
 *
 * @author Claude Code
 * @date 2026-03-21
 */
@Service
public class ProbeSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProbeSyncService.class);

    @Autowired
    private AgentProperties properties;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 本地探针配置缓存
     * key: probeKey, value: 探针配置
     */
    private final Map<String, ProbeConfig> localProbes = new ConcurrentHashMap<>();

    /**
     * 启动时初始化：从Admin获取探针列表
     */
    public void initialize() {
        log.info("[探针同步] 开始初始化探针配置...");
        syncProbes();
        log.info("[探针同步] 初始化完成，当前管理 {} 个探针", localProbes.size());
    }

    /**
     * 定期同步探针配置（每30秒）
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void periodicSync() {
        log.debug("[探针同步] 开始定期同步探针配置...");
        syncProbes();
    }

    /**
     * 启动时初始化：等待后端服务就绪后同步探针配置
     */
    @PostConstruct
    public void init() {
        log.info("[探针同步] 等待后端服务启动...");
        boolean healthy = checkBackendHealth(30, 2);  // 最多等待 60 秒，每 2 秒重试一次

        if (!healthy) {
            log.warn("[探针同步] 后端服务启动超时，Agent 将在后台继续尝试连接");
            // 不抛出异常，让 Agent 继续启动
            // 定期同步任务会在后台继续重试
        } else {
            log.info("[探针同步] 后端服务已就绪，开始探针同步");
            initialize();  // 立即执行一次初始化
        }
    }

    /**
     * 检查后端服务是否可用
     *
     * @param maxAttempts 最大尝试次数
     * @param delaySeconds 重试间隔（秒）
     * @return 后端服务是否可用
     */
    private boolean checkBackendHealth(int maxAttempts, int delaySeconds) {
        String healthUrl = String.format("http://%s:%d/actuator/health",
                properties.getServer().getHost(),
                properties.getServer().getPort());

        for (int i = 0; i < maxAttempts; i++) {
            try {
                ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("[探针同步] 后端服务健康检查通过");
                    return true;
                }
            } catch (Exception e) {
                if (i == 0) {
                    log.debug("[探针同步] 等待后端服务启动... ({}/{})", i + 1, maxAttempts);
                } else {
                    log.debug("[探针同步] 后端服务健康检查失败 ({}/{})：{}",
                            i + 1, maxAttempts, e.getMessage());
                }
                if (i < maxAttempts - 1) {
                    try {
                        Thread.sleep(delaySeconds * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 从Admin服务同步探针配置（带重试机制）
     */
    private void syncProbes() {
        String agentCode = properties.getCode();
        String adminUrl = properties.getServer().getHost() + ":" + properties.getServer().getPort();
        String syncUrl = String.format("http://%s/api/agents/%s/sync", adminUrl, agentCode);

        int maxRetries = 3;
        int retryDelay = 1;  // 初始延迟 1 秒

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                log.debug("[探针同步] 请求Admin服务: {} (尝试 {}/{})", syncUrl, attempt + 1, maxRetries);

                // 构建请求头
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                // 如果有认证密钥，添加到请求头
                String agentKey = properties.getKey();
                if (agentKey != null && !agentKey.isEmpty()) {
                    headers.set("X-Agent-Key", agentKey);
                }

                HttpEntity<String> entity = new HttpEntity<>(headers);

                // 发送请求
                ResponseEntity<String> response = restTemplate.exchange(
                        syncUrl,
                        HttpMethod.GET,
                        entity,
                        String.class
                );

                if (response.getStatusCode() == HttpStatus.OK) {
                    String responseBody = response.getBody();
                    log.debug("[探针同步] 收到响应: {}", responseBody);

                    // 解析响应
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = JSON.parseObject(responseBody, Map.class);
                    if ((Integer) result.get("code") == 200) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) result.get("data");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> probes = (List<Map<String, Object>>) data.get("probes");

                        // 更新本地探针配置
                        updateLocalProbes(probes);

                        log.info("[探针同步] 同步成功，当前管理 {} 个探针", localProbes.size());
                        return;  // 成功，退出方法
                    } else {
                        log.warn("[探针同步] Admin返回错误: {}", result.get("message"));
                        return;  // 业务错误，不重试
                    }
                } else {
                    log.warn("[探针同步] HTTP请求失败，状态码: {}", response.getStatusCode());
                    return;  // HTTP错误，不重试
                }

            } catch (RestClientException e) {
                log.warn("[探针同步] 同步探针配置失败 (尝试 {}/{})：{}",
                        attempt + 1, maxRetries, e.getMessage());

                // 如果是最后一次尝试，不再重试
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(retryDelay * 1000L);
                        retryDelay *= 2;  // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("[探针同步] 同步被中断");
                        return;
                    }
                } else {
                    log.error("[探针同步] 同步探针配置失败，将在下次定时任务时重试");
                }
            } catch (Exception e) {
                log.error("[探针同步] 同步探针配置失败（未预期的错误）", e);
                return;  // 其他异常，不重试
            }
        }
    }

    /**
     * 更新本地探针配置
     *
     * @param probes Admin返回的探针列表
     */
    private void updateLocalProbes(List<Map<String, Object>> probes) {
        Map<String, ProbeConfig> newProbes = new ConcurrentHashMap<>();

        for (Map<String, Object> probeData : probes) {
            String probeKey = (String) probeData.get("probeKey");
            String type = (String) probeData.get("type");
            String status = (String) probeData.get("status");
            String hostIp = (String) probeData.get("hostIp");
            Integer port = (Integer) probeData.get("port");
            String config = (String) probeData.get("config");

            ProbeConfig probeConfig = new ProbeConfig(
                    probeKey,
                    type,
                    status,
                    hostIp,
                    port != null ? port : 58081,
                    config
            );

            newProbes.put(probeKey, probeConfig);

            // 检查是否是新探针
            if (!localProbes.containsKey(probeKey)) {
                log.info("[探针同步] 发现新探针: key={}, type={}, host={}:{}",
                        probeKey, type, hostIp, port);
            }
        }

        // 检查是否有被删除的探针
        for (String existingKey : localProbes.keySet()) {
            if (!newProbes.containsKey(existingKey)) {
                log.info("[探针同步] 探针已被删除: {}", existingKey);
            }
        }

        // 更新本地配置
        localProbes.clear();
        localProbes.putAll(newProbes);
    }

    /**
     * 获取所有本地探针配置
     *
     * @return 探针配置列表
     */
    public List<ProbeConfig> getAllProbes() {
        return List.copyOf(localProbes.values());
    }

    /**
     * 根据类型获取探针配置
     *
     * @param type 探针类型
     * @return 探针配置列表
     */
    public List<ProbeConfig> getProbesByType(String type) {
        return localProbes.values().stream()
                .filter(p -> p.getType().equals(type))
                .toList();
    }

    /**
     * 根据probeKey获取探针配置
     *
     * @param probeKey 探针标识
     * @return 探针配置，如果不存在返回null
     */
    public ProbeConfig getProbe(String probeKey) {
        return localProbes.get(probeKey);
    }

    /**
     * 检查探针是否存在
     *
     * @param probeKey 探针标识
     * @return 是否存在
     */
    public boolean hasProbe(String probeKey) {
        return localProbes.containsKey(probeKey);
    }

    /**
     * 获取探针数量
     *
     * @return 探针数量
     */
    public int getProbeCount() {
        return localProbes.size();
    }

    /**
     * 探针配置类
     */
    public static class ProbeConfig {
        private final String probeKey;
        private final String type;
        private final String status;
        private final String hostIp;
        private final int port;
        private final String config;

        public ProbeConfig(String probeKey, String type, String status, String hostIp, int port, String config) {
            this.probeKey = probeKey;
            this.type = type;
            this.status = status;
            this.hostIp = hostIp;
            this.port = port;
            this.config = config;
        }

        public String getProbeKey() {
            return probeKey;
        }

        public String getType() {
            return type;
        }

        public String getStatus() {
            return status;
        }

        public String getHostIp() {
            return hostIp;
        }

        public int getPort() {
            return port;
        }

        public String getConfig() {
            return config;
        }

        @Override
        public String toString() {
            return "ProbeConfig{" +
                    "probeKey='" + probeKey + '\'' +
                    ", type='" + type + '\'' +
                    ", status='" + status + '\'' +
                    ", hostIp='" + hostIp + '\'' +
                    ", port=" + port +
                    '}';
        }
    }
}
