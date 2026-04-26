package com.lixin.probe.agent.plugin.impl.protocol;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lixin.probe.agent.plugin.api.HttpApiPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * HTTP API 数据源插件默认实现
 */
public class HttpApiPluginImpl implements HttpApiPlugin {

    private static final Logger log = LoggerFactory.getLogger(HttpApiPluginImpl.class);

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getPluginId() { return "http-api-plugin"; }

    @Override
    public String getName() { return "HTTP API Plugin"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getDescription() { return "HTTP/REST API data source plugin"; }

    @Override
    public boolean testConnection(Map<String, Object> config) {
        try {
            String url = (String) config.get("url");
            if (url == null || url.isEmpty()) return false;
            ResponseEntity<String> response = executeRequest(config, null);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("[HttpApiPlugin] 连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchData(Map<String, Object> config) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            String url = (String) config.get("url");
            String responsePath = (String) config.getOrDefault("responsePath", "");
            Map<String, Object> paginationConfig = (Map<String, Object>) config.get("paginationConfig");
            int maxPages = paginationConfig != null ? ((Number) paginationConfig.getOrDefault("maxPages", 10)).intValue() : 1;

            for (int page = 0; page < maxPages; page++) {
                String requestUrl = buildUrl(url, config, page);
                ResponseEntity<String> response = executeRequest(config, requestUrl);
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) break;

                JSONObject json = JSON.parseObject(response.getBody());
                JSONArray dataArr = extractDataArray(json, responsePath);
                if (dataArr == null || dataArr.isEmpty()) break;

                for (int i = 0; i < dataArr.size(); i++) {
                    result.add(dataArr.getJSONObject(i));
                }

                if (paginationConfig == null) break;
            }
        } catch (Exception e) {
            log.error("[HttpApiPlugin] 数据获取失败: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public Map<String, Object> getMetadata(Map<String, Object> config) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> sampleData = fetchData(config);
            metadata.put("available", testConnection(config));
            metadata.put("sampleSize", sampleData.size());
            if (!sampleData.isEmpty()) {
                metadata.put("fields", new ArrayList<>(sampleData.get(0).keySet()));
                metadata.put("sampleRecord", sampleData.get(0));
            }
        } catch (Exception e) {
            metadata.put("available", false);
            metadata.put("error", e.getMessage());
        }
        return metadata;
    }

    private ResponseEntity<String> executeRequest(Map<String, Object> config, String overrideUrl) {
        String url = overrideUrl != null ? overrideUrl : (String) config.get("url");
        String method = (String) config.getOrDefault("method", "GET");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        @SuppressWarnings("unchecked")
        Map<String, String> configHeaders = (Map<String, String>) config.get("headers");
        if (configHeaders != null) configHeaders.forEach(headers::set);

        applyAuth(headers, config);

        String body = config.get("body") != null ? config.get("body").toString() : null;
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        HttpMethod httpMethod = "POST".equalsIgnoreCase(method) ? HttpMethod.POST : HttpMethod.GET;
        return restTemplate.exchange(url, httpMethod, entity, String.class);
    }

    @SuppressWarnings("unchecked")
    private void applyAuth(HttpHeaders headers, Map<String, Object> config) {
        String authType = (String) config.getOrDefault("authType", "NONE");
        Map<String, Object> authConfig = (Map<String, Object>) config.get("authConfig");
        if (authConfig == null) return;

        switch (authType.toUpperCase()) {
            case "BASIC":
                String user = (String) authConfig.get("username");
                String pass = (String) authConfig.get("password");
                if (user != null) headers.setBasicAuth(user, pass);
                break;
            case "BEARER":
                String token = (String) authConfig.get("token");
                if (token != null) headers.setBearerAuth(token);
                break;
            case "API_KEY":
                String keyName = (String) authConfig.getOrDefault("headerName", "X-API-Key");
                String keyValue = (String) authConfig.get("apiKey");
                if (keyValue != null) headers.set(keyName, keyValue);
                break;
        }
    }

    @SuppressWarnings("unchecked")
    private String buildUrl(String baseUrl, Map<String, Object> config, int page) {
        Map<String, Object> paginationConfig = (Map<String, Object>) config.get("paginationConfig");
        if (paginationConfig == null || page == 0) return baseUrl;

        String type = (String) paginationConfig.getOrDefault("type", "OFFSET");
        String paramName = (String) paginationConfig.getOrDefault("paramName", "page");
        int pageSize = ((Number) paginationConfig.getOrDefault("pageSize", 100)).intValue();

        String separator = baseUrl.contains("?") ? "&" : "?";
        if ("OFFSET".equals(type)) {
            return baseUrl + separator + paramName + "=" + (page * pageSize) + "&limit=" + pageSize;
        } else {
            return baseUrl + separator + paramName + "=" + page;
        }
    }

    private JSONArray extractDataArray(JSONObject json, String path) {
        if (path == null || path.isEmpty()) {
            // Try common paths
            for (String candidate : new String[]{"data", "items", "results", "records", "list"}) {
                Object obj = json.get(candidate);
                if (obj instanceof JSONArray) return (JSONArray) obj;
            }
            // If root is array
            if (json.get("content") instanceof JSONArray) return json.getJSONArray("content");
            return null;
        }
        // Navigate path like "data.items"
        Object current = json;
        for (String key : path.split("\\.")) {
            if (current instanceof JSONObject) {
                current = ((JSONObject) current).get(key);
            } else return null;
        }
        return current instanceof JSONArray ? (JSONArray) current : null;
    }
}
