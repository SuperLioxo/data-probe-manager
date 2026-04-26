package com.lixin.probe.agent.plugin.impl.nosql;

import com.lixin.probe.agent.plugin.api.NoSQLPlugin;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ElasticsearchPlugin implements NoSQLPlugin {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchPlugin.class);

    @Override
    public String getPluginId() { return "elasticsearch-nosql-plugin"; }

    @Override
    public String getName() { return "Elasticsearch Plugin"; }

    @Override
    public String getType() { return "NOSQL"; }

    @Override
    public String getSubType() { return "elasticsearch"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getDescription() { return "Elasticsearch search engine plugin for index management and data query"; }

    @Override
    public int getDefaultPort() { return 9200; }

    @Override
    public CompletableFuture<ProbeResponse.NoSQLMetadata> getMetadata(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            String baseUrl = getBaseUrl(config);
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(baseUrl + "/_cat/indices?format=json");
                String response = httpClient.execute(request, rsp -> EntityUtils.toString(rsp.getEntity()));

                ProbeResponse.NoSQLMetadata metadata = new ProbeResponse.NoSQLMetadata();
                metadata.setType("NOSQL");
                metadata.setSubType("elasticsearch");
                metadata.setHost((String) config.get("host"));
                metadata.setPort((Integer) config.getOrDefault("port", 9200));

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> indices = mapper.readValue(response, List.class);

                List<String> indexNames = new ArrayList<>();
                Map<String, ProbeResponse.NoSQLMetadata.CollectionInfo> indexMap = new LinkedHashMap<>();

                for (Map<String, Object> indexInfo : indices) {
                    String indexName = (String) indexInfo.get("index");
                    indexNames.add(indexName);

                    ProbeResponse.NoSQLMetadata.CollectionInfo info = new ProbeResponse.NoSQLMetadata.CollectionInfo();
                    info.setName(indexName);

                    String docsCount = (String) indexInfo.get("docs.count");
                    info.setDocumentCount(docsCount != null ? Long.parseLong(docsCount) : 0L);

                    String storeSize = (String) indexInfo.get("store.size");
                    info.setTotalSize(parseEsSize(storeSize));

                    indexMap.put(indexName, info);
                }

                metadata.setCollectionNames(indexNames);
                metadata.setTotalCollections((long) indexNames.size());
                metadata.setCollections(indexMap);
                return metadata;

            } catch (Exception e) {
                log.error("ES metadata query failed: {}", e.getMessage());
                throw new RuntimeException("ES metadata query failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<ProbeResponse.NoSQLStats> getStats(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            String baseUrl = getBaseUrl(config);
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(baseUrl + "/_cluster/stats");
                String response = httpClient.execute(request, rsp -> EntityUtils.toString(rsp.getEntity()));

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> clusterStats = mapper.readValue(response, Map.class);

                ProbeResponse.NoSQLStats noSqlStats = new ProbeResponse.NoSQLStats();
                @SuppressWarnings("unchecked")
                Map<String, Object> indicesStats = (Map<String, Object>) clusterStats.get("indices");
                if (indicesStats != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> docs = (Map<String, Object>) indicesStats.get("docs");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> store = (Map<String, Object>) indicesStats.get("store");
                    noSqlStats.setTotalDocuments(docs != null ? ((Number) docs.get("count")).longValue() : 0L);
                    noSqlStats.setTotalSize(store != null ? ((Number) store.get("size_in_bytes")).longValue() : 0L);
                }
                return noSqlStats;

            } catch (Exception e) {
                throw new RuntimeException("ES stats query failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<ProbeResponse.NoSQLDataContent> getDataContent(
            Map<String, Object> config, String indexName, int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> {
            String baseUrl = getBaseUrl(config);
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                String body = String.format("{\"from\":%d,\"size\":%d}", (page - 1) * pageSize, pageSize);
                HttpPost httpPost = new HttpPost(baseUrl + "/" + indexName + "/_search");
                httpPost.setHeader("Content-Type", "application/json");
                httpPost.setEntity(new org.apache.http.entity.StringEntity(body));

                String response = httpClient.execute(httpPost, rsp -> EntityUtils.toString(rsp.getEntity()));

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> result = mapper.readValue(response, Map.class);

                @SuppressWarnings("unchecked")
                Map<String, Object> hits = (Map<String, Object>) result.get("hits");
                long total = ((Number) hits.get("total")).longValue();

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");

                List<Map<String, Object>> rows = new ArrayList<>();
                Set<String> allColumns = new LinkedHashSet<>();

                for (Map<String, Object> hit : hitList) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("_id", hit.get("_id"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                    if (source != null) {
                        for (Map.Entry<String, Object> entry : source.entrySet()) {
                            row.put(entry.getKey(), entry.getValue());
                            allColumns.add(entry.getKey());
                        }
                    }
                    rows.add(row);
                }

                ProbeResponse.NoSQLDataContent content = new ProbeResponse.NoSQLDataContent();
                content.setCollectionName(indexName);
                content.setTotal(total);
                content.setPageNum(page);
                content.setPageSize(pageSize);
                content.setRows(rows);
                content.setColumns(new ArrayList<>(allColumns));
                content.setSuccess(true);
                return content;

            } catch (Exception e) {
                ProbeResponse.NoSQLDataContent content = new ProbeResponse.NoSQLDataContent();
                content.setSuccess(false);
                content.setMessage(e.getMessage());
                return content;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> testConnection(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(getBaseUrl(config));
                httpClient.execute(request, rsp -> rsp.getStatusLine().getStatusCode());
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private String getBaseUrl(Map<String, Object> config) {
        String host = (String) config.get("host");
        int port = (Integer) config.getOrDefault("port", 9200);
        boolean ssl = config.containsKey("ssl") && Boolean.TRUE.equals(config.get("ssl"));
        return (ssl ? "https" : "http") + "://" + host + ":" + port;
    }

    private long parseEsSize(String size) {
        if (size == null || size.isEmpty()) return 0L;
        size = size.trim().toLowerCase();
        try {
            if (size.endsWith("gb")) return (long) (Double.parseDouble(size.replace("gb", "")) * 1073741824);
            if (size.endsWith("mb")) return (long) (Double.parseDouble(size.replace("mb", "")) * 1048576);
            if (size.endsWith("kb")) return (long) (Double.parseDouble(size.replace("kb", "")) * 1024);
            return Long.parseLong(size.replace("b", ""));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
