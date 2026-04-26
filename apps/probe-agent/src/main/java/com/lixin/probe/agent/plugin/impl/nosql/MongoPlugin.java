package com.lixin.probe.agent.plugin.impl.nosql;

import com.lixin.probe.agent.plugin.api.NoSQLPlugin;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MongoPlugin implements NoSQLPlugin {

    private static final Logger log = LoggerFactory.getLogger(MongoPlugin.class);

    @Override
    public String getPluginId() {
        return "mongodb-nosql-plugin";
    }

    @Override
    public String getName() {
        return "MongoDB Plugin";
    }

    @Override
    public String getType() {
        return "NOSQL";
    }

    @Override
    public String getSubType() {
        return "mongodb";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "MongoDB NoSQL database plugin for metadata, stats and data query";
    }

    @Override
    public int getDefaultPort() {
        return 27017;
    }

    @Override
    public CompletableFuture<ProbeResponse.NoSQLMetadata> getMetadata(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            try (MongoClient client = createClient(config)) {
                String databaseName = (String) config.getOrDefault("database", "admin");
                MongoDatabase db = client.getDatabase(databaseName);

                ProbeResponse.NoSQLMetadata metadata = new ProbeResponse.NoSQLMetadata();
                metadata.setType("NOSQL");
                metadata.setSubType("mongodb");
                metadata.setHost((String) config.get("host"));
                metadata.setPort((Integer) config.getOrDefault("port", 27017));
                metadata.setDatabaseName(databaseName);

                List<String> collectionNames = new ArrayList<>();
                Map<String, ProbeResponse.NoSQLMetadata.CollectionInfo> collections = new LinkedHashMap<>();

                try (MongoCursor<String> cursor = db.listCollectionNames().iterator()) {
                    while (cursor.hasNext()) {
                        String name = cursor.next();
                        collectionNames.add(name);

                        ProbeResponse.NoSQLMetadata.CollectionInfo info = new ProbeResponse.NoSQLMetadata.CollectionInfo();
                        info.setName(name);

                        try {
                            Document stats = db.runCommand(new Document("collStats", name));
                            info.setDocumentCount(stats.containsKey("count") ? stats.getLong("count") : 0L);
                            info.setAvgDocumentSize(stats.containsKey("avgObjSize") ? stats.getLong("avgObjSize") : 0L);
                            info.setTotalSize(stats.containsKey("size") ? stats.getLong("size") : 0L);
                            info.setIndexCount(stats.containsKey("nindexes") ? stats.getLong("nindexes") : 0L);

                            List<ProbeResponse.NoSQLMetadata.IndexInfo> indexes = new ArrayList<>();
                            @SuppressWarnings("unchecked")
                            List<Document> indexList = db.getCollection(name).listIndexes().into(new ArrayList<>());
                            for (Document idx : indexList) {
                                ProbeResponse.NoSQLMetadata.IndexInfo ii = new ProbeResponse.NoSQLMetadata.IndexInfo();
                                ii.setName(idx.getString("name"));
                                ii.setKeys(idx.get("key", Document.class).toJson());
                                ii.setUnique(idx.getBoolean("unique", false));
                                indexes.add(ii);
                            }
                            info.setIndexes(indexes);
                        } catch (Exception e) {
                            log.warn("Failed to get stats for collection {}: {}", name, e.getMessage());
                        }

                        collections.put(name, info);
                    }
                }

                metadata.setCollectionNames(collectionNames);
                metadata.setTotalCollections((long) collectionNames.size());
                metadata.setCollections(collections);
                return metadata;

            } catch (Exception e) {
                log.error("MongoDB metadata query failed: {}", e.getMessage());
                throw new RuntimeException("MongoDB metadata query failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<ProbeResponse.NoSQLStats> getStats(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            try (MongoClient client = createClient(config)) {
                String databaseName = (String) config.getOrDefault("database", "admin");
                MongoDatabase db = client.getDatabase(databaseName);

                ProbeResponse.NoSQLStats stats = new ProbeResponse.NoSQLStats();
                stats.setDatabaseName(databaseName);

                Document dbStats = db.runCommand(new Document("dbStats", 1).append("scale", 1024 * 1024));
                stats.setTotalCollections(dbStats.containsKey("collections") ? dbStats.getLong("collections") : 0L);
                stats.setTotalDocuments(dbStats.containsKey("objects") ? dbStats.getLong("objects") : 0L);
                stats.setTotalSize(dbStats.containsKey("dataSize") ? dbStats.getLong("dataSize") : 0L);
                stats.setTotalIndexes(dbStats.containsKey("indexes") ? dbStats.getLong("indexes") : 0L);
                stats.setAvgDocumentSize(dbStats.containsKey("avgObjSize") ? dbStats.getDouble("avgObjSize") : 0.0);

                Map<String, ProbeResponse.NoSQLStats.CollectionStats> collectionStats = new LinkedHashMap<>();
                try (MongoCursor<String> cursor = db.listCollectionNames().iterator()) {
                    while (cursor.hasNext()) {
                        String name = cursor.next();
                        try {
                            Document cs = db.runCommand(new Document("collStats", name).append("scale", 1024 * 1024));
                            ProbeResponse.NoSQLStats.CollectionStats c = new ProbeResponse.NoSQLStats.CollectionStats();
                            c.setName(name);
                            c.setDocumentCount(cs.containsKey("count") ? cs.getLong("count") : 0L);
                            c.setSize(cs.containsKey("size") ? cs.getLong("size") : 0L);
                            c.setIndexSize(cs.containsKey("totalIndexSize") ? cs.getLong("totalIndexSize") : 0L);
                            c.setIndexCount(cs.containsKey("nindexes") ? cs.getLong("nindexes") : 0L);
                            c.setAvgObjSize(cs.containsKey("avgObjSize") ? cs.getDouble("avgObjSize") : 0.0);
                            collectionStats.put(name, c);
                        } catch (Exception e) {
                            log.warn("Failed to get stats for {}: {}", name, e.getMessage());
                        }
                    }
                }
                stats.setCollectionStats(collectionStats);
                return stats;

            } catch (Exception e) {
                throw new RuntimeException("MongoDB stats query failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<ProbeResponse.NoSQLDataContent> getDataContent(
            Map<String, Object> config, String collectionName, int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> {
            try (MongoClient client = createClient(config)) {
                String databaseName = (String) config.getOrDefault("database", "admin");
                MongoDatabase db = client.getDatabase(databaseName);
                long total = db.getCollection(collectionName).countDocuments();

                List<Map<String, Object>> rows = new ArrayList<>();
                Set<String> allColumns = new LinkedHashSet<>();

                try (MongoCursor<Document> cursor = db.getCollection(collectionName)
                        .find().skip((page - 1) * pageSize).limit(pageSize).iterator()) {
                    while (cursor.hasNext()) {
                        Document doc = cursor.next();
                        doc.remove("_id");
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (String key : doc.keySet()) {
                            row.put(key, doc.get(key));
                            allColumns.add(key);
                        }
                        rows.add(row);
                    }
                }

                ProbeResponse.NoSQLDataContent content = new ProbeResponse.NoSQLDataContent();
                content.setCollectionName(collectionName);
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
            try (MongoClient client = createClient(config)) {
                client.listDatabaseNames().first();
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private MongoClient createClient(Map<String, Object> config) {
        String host = (String) config.get("host");
        int port = (Integer) config.getOrDefault("port", 27017);
        String username = (String) config.get("username");
        String password = (String) config.get("password");
        String authDb = (String) config.getOrDefault("authDatabase", "admin");

        StringBuilder uri = new StringBuilder("mongodb://");
        if (username != null && !username.isEmpty()) {
            uri.append(username).append(":").append(password).append("@");
        }
        uri.append(host).append(":").append(port).append("/").append(authDb);

        com.mongodb.MongoClientSettings settings = com.mongodb.MongoClientSettings.builder()
                .applyConnectionString(new com.mongodb.ConnectionString(uri.toString()))
                .build();
        return MongoClients.create(settings);
    }
}
