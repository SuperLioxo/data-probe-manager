package com.lixin.probe.agent.plugin.api;

import com.lixin.probe.agent.pojo.response.ProbeResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface NoSQLPlugin {

    String getPluginId();

    String getName();

    String getType();

    String getSubType();

    String getVersion();

    String getDescription();

    int getDefaultPort();

    CompletableFuture<ProbeResponse.NoSQLMetadata> getMetadata(Map<String, Object> config);

    CompletableFuture<ProbeResponse.NoSQLStats> getStats(Map<String, Object> config);

    CompletableFuture<ProbeResponse.NoSQLDataContent> getDataContent(
            Map<String, Object> config, String collectionName, int page, int pageSize);

    CompletableFuture<Boolean> testConnection(Map<String, Object> config);
}
