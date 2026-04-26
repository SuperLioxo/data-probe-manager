package com.lixin.probe.agent.plugin.api;

import com.lixin.probe.agent.pojo.response.ProbeResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface CDCPlugin {

    String getPluginId();

    String getName();

    String getSubType();

    String getVersion();

    String getDescription();

    CompletableFuture<ProbeResponse.CDCMetadata> getMetadata(Map<String, Object> config);

    CompletableFuture<List<ProbeResponse.CDCEvent>> captureChanges(
            Map<String, Object> config, String database, String table,
            String fromPosition, int maxEvents);

    CompletableFuture<Boolean> testConnection(Map<String, Object> config);
}
