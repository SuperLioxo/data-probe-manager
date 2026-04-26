package com.lixin.probe.agent.plugin.api;

import com.lixin.probe.agent.pojo.response.ProbeResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface MessageQueuePlugin {

    String getPluginId();

    String getName();

    String getType();

    String getSubType();

    String getVersion();

    String getDescription();

    int getDefaultPort();

    CompletableFuture<ProbeResponse.MessageQueueMetadata> getMetadata(Map<String, Object> config);

    CompletableFuture<ProbeResponse.MessageQueueStats> getStats(Map<String, Object> config);

    CompletableFuture<ProbeResponse.MessageQueueDataContent> consumeMessages(
            Map<String, Object> config, String topic, int count);

    CompletableFuture<Boolean> testConnection(Map<String, Object> config);
}
