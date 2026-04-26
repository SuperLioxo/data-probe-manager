package com.lixin.probe.agent.plugin.impl.nosql;

import com.lixin.probe.agent.plugin.api.NoSQLPlugin;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RedisPlugin implements NoSQLPlugin {

    private static final Logger log = LoggerFactory.getLogger(RedisPlugin.class);

    @Override
    public String getPluginId() {
        return "redis-nosql-plugin";
    }

    @Override
    public String getName() {
        return "Redis Plugin";
    }

    @Override
    public String getType() {
        return "NOSQL";
    }

    @Override
    public String getSubType() {
        return "redis";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Redis key-value store plugin for metadata, stats and data query";
    }

    @Override
    public int getDefaultPort() {
        return 6379;
    }

    @Override
    public CompletableFuture<ProbeResponse.NoSQLMetadata> getMetadata(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = createClient(config)) {
                ProbeResponse.NoSQLMetadata metadata = new ProbeResponse.NoSQLMetadata();
                metadata.setType("NOSQL");
                metadata.setSubType("redis");
                metadata.setHost((String) config.get("host"));
                metadata.setPort((Integer) config.getOrDefault("port", 6379));
                metadata.setDatabaseName("db" + config.getOrDefault("database", 0));

                String info = jedis.info();
                Map<String, ProbeResponse.NoSQLMetadata.CollectionInfo> collections = new LinkedHashMap<>();

                Set<String> keyPatterns = new TreeSet<>();
                Set<String> rawKeys = jedis.keys("*");
                for (String key : rawKeys) {
                    String pattern = extractPattern(key);
                    keyPatterns.add(pattern);
                }

                List<String> collectionNames = new ArrayList<>(keyPatterns);
                for (String pattern : keyPatterns) {
                    ProbeResponse.NoSQLMetadata.CollectionInfo info_entry = new ProbeResponse.NoSQLMetadata.CollectionInfo();
                    info_entry.setName(pattern);

                    int count = 0;
                    for (String key : rawKeys) {
                        if (pattern.equals("*") || matchesPattern(key, pattern)) {
                            count++;
                        }
                    }
                    info_entry.setDocumentCount((long) count);

                    String type = jedis.type(pattern.equals("*") ? rawKeys.iterator().next() : pattern);
                    info_entry.setAvgDocumentSize(0L);
                    info_entry.setTotalSize(0L);
                    info_entry.setIndexCount(0L);

                    List<ProbeResponse.NoSQLMetadata.IndexInfo> indexes = new ArrayList<>();
                    ProbeResponse.NoSQLMetadata.IndexInfo ti = new ProbeResponse.NoSQLMetadata.IndexInfo();
                    ti.setName("key_type");
                    ti.setKeys(type);
                    ti.setUnique(false);
                    indexes.add(ti);
                    info_entry.setIndexes(indexes);

                    collections.put(pattern, info_entry);
                }

                metadata.setCollectionNames(collectionNames);
                metadata.setTotalCollections((long) collectionNames.size());
                metadata.setCollections(collections);
                return metadata;

            } catch (Exception e) {
                log.error("Redis metadata query failed: {}", e.getMessage());
                throw new RuntimeException("Redis metadata query failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<ProbeResponse.NoSQLStats> getStats(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = createClient(config)) {
                ProbeResponse.NoSQLStats stats = new ProbeResponse.NoSQLStats();
                stats.setDatabaseName("db" + config.getOrDefault("database", 0));

                String info = jedis.info();
                stats.setTotalCollections(0L);
                stats.setTotalDocuments(jedis.dbSize());
                stats.setTotalSize(0L);
                stats.setTotalIndexes(0L);
                stats.setAvgDocumentSize(0.0);

                Map<String, ProbeResponse.NoSQLStats.CollectionStats> collectionStats = new LinkedHashMap<>();
                Map<String, Long> typeCounts = new HashMap<>();
                typeCounts.put("string", 0L);
                typeCounts.put("list", 0L);
                typeCounts.put("set", 0L);
                typeCounts.put("zset", 0L);
                typeCounts.put("hash", 0L);
                typeCounts.put("stream", 0L);
                typeCounts.put("other", 0L);

                Set<String> keys = jedis.keys("*");
                Pipeline pipeline = jedis.pipelined();
                Map<String, String> typeMap = new HashMap<>();
                for (String key : keys) {
                    pipeline.type(key);
                }
                List<Object> results = pipeline.syncAndReturnAll();
                int idx = 0;
                for (Object result : results) {
                    String type = (String) result;
                    typeMap.put(new ArrayList<>(keys).get(idx), type);
                    typeCounts.merge(type, 1L, Long::sum);
                    idx++;
                }

                for (Map.Entry<String, Long> entry : typeCounts.entrySet()) {
                    if (entry.getValue() > 0) {
                        ProbeResponse.NoSQLStats.CollectionStats cs = new ProbeResponse.NoSQLStats.CollectionStats();
                        cs.setName(entry.getKey());
                        cs.setDocumentCount(entry.getValue());
                        cs.setSize(0L);
                        cs.setIndexSize(0L);
                        cs.setIndexCount(0L);
                        cs.setAvgObjSize(0.0);
                        collectionStats.put(entry.getKey(), cs);
                    }
                }
                stats.setCollectionStats(collectionStats);
                return stats;

            } catch (Exception e) {
                throw new RuntimeException("Redis stats query failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<ProbeResponse.NoSQLDataContent> getDataContent(
            Map<String, Object> config, String collectionName, int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = createClient(config)) {
                Set<String> keys;
                if ("*".equals(collectionName) || collectionName == null || collectionName.isEmpty()) {
                    keys = jedis.keys("*");
                } else {
                    keys = jedis.keys(collectionName);
                }

                List<String> keyList = new ArrayList<>(keys);
                int total = keyList.size();
                int from = (page - 1) * pageSize;
                int to = Math.min(from + pageSize, total);

                List<Map<String, Object>> rows = new ArrayList<>();
                List<String> columns = Arrays.asList("key", "type", "value", "ttl");

                for (int i = from; i < to; i++) {
                    String key = keyList.get(i);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("key", key);
                    String type = jedis.type(key);
                    row.put("type", type);
                    row.put("ttl", jedis.ttl(key));

                    switch (type) {
                        case "string":
                            String val = jedis.get(key);
                            row.put("value", val != null && val.length() > 200 ? val.substring(0, 200) + "..." : val);
                            break;
                        case "list":
                            row.put("value", "List[" + jedis.llen(key) + " items]");
                            break;
                        case "set":
                            row.put("value", "Set[" + jedis.scard(key) + " members]");
                            break;
                        case "zset":
                            row.put("value", "ZSet[" + jedis.zcard(key) + " members]");
                            break;
                        case "hash":
                            row.put("value", "Hash[" + jedis.hlen(key) + " fields]");
                            break;
                        default:
                            row.put("value", "[" + type + "]");
                            break;
                    }

                    rows.add(row);
                }

                ProbeResponse.NoSQLDataContent content = new ProbeResponse.NoSQLDataContent();
                content.setCollectionName(collectionName);
                content.setTotal((long) total);
                content.setPageNum(page);
                content.setPageSize(pageSize);
                content.setRows(rows);
                content.setColumns(columns);
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
            try (Jedis jedis = createClient(config)) {
                return "PONG".equals(jedis.ping());
            } catch (Exception e) {
                return false;
            }
        });
    }

    private Jedis createClient(Map<String, Object> config) {
        String host = (String) config.get("host");
        int port = (Integer) config.getOrDefault("port", 6379);
        String password = (String) config.get("password");
        int database = config.containsKey("database") ? ((Number) config.get("database")).intValue() : 0;

        Jedis jedis = new Jedis(host, port);
        if (password != null && !password.isEmpty()) {
            jedis.auth(password);
        }
        if (database > 0) {
            jedis.select(database);
        }
        return jedis;
    }

    private String extractPattern(String key) {
        if (key.contains(":")) {
            int idx = key.lastIndexOf(':');
            return key.substring(0, idx) + ":*";
        }
        if (key.contains("-")) {
            int idx = key.indexOf('-');
            if (Character.isDigit(key.charAt(idx + 1))) {
                return key.substring(0, idx) + "-*";
            }
        }
        return key;
    }

    private boolean matchesPattern(String key, String pattern) {
        return key.startsWith(pattern.replace("*", ""));
    }
}
