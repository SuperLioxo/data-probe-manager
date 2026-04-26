package com.lixin.probe.agent.plugin.impl.mq;

import com.lixin.probe.agent.plugin.api.MessageQueuePlugin;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class KafkaPlugin implements MessageQueuePlugin {

    private static final Logger log = LoggerFactory.getLogger(KafkaPlugin.class);

    @Override
    public String getPluginId() { return "kafka-mq-plugin"; }

    @Override
    public String getName() { return "Kafka Plugin"; }

    @Override
    public String getType() { return "MESSAGE_QUEUE"; }

    @Override
    public String getSubType() { return "kafka"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getDescription() { return "Apache Kafka message queue plugin for metadata, stats and data consumption"; }

    @Override
    public int getDefaultPort() { return 9092; }

    @Override
    public CompletableFuture<ProbeResponse.MessageQueueMetadata> getMetadata(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            try (AdminClient admin = createAdminClient(config)) {
                ProbeResponse.MessageQueueMetadata metadata = new ProbeResponse.MessageQueueMetadata();
                metadata.setType("MESSAGE_QUEUE");
                metadata.setSubType("kafka");
                metadata.setHost((String) config.get("host"));
                metadata.setPort((Integer) config.getOrDefault("port", 9092));

                Set<String> topicNames = admin.listTopics(new ListTopicsOptions().timeoutMs(10000)).names().get();
                metadata.setTopicNames(new ArrayList<>(topicNames));
                metadata.setTotalTopics((long) topicNames.size());

                Map<String, ProbeResponse.MessageQueueMetadata.TopicInfo> topics = new LinkedHashMap<>();
                for (String topic : topicNames) {
                    ProbeResponse.MessageQueueMetadata.TopicInfo info = new ProbeResponse.MessageQueueMetadata.TopicInfo();
                    info.setName(topic);
                    try {
                        TopicDescription desc = admin.describeTopics(Collections.singleton(topic)).all().get().get(topic);
                        info.setPartitionCount(desc.partitions().size());
                        info.setReplicationFactor(desc.partitions().get(0).replicas().size());
                    } catch (Exception e) {
                        log.warn("Failed to describe topic {}: {}", topic, e.getMessage());
                    }
                    topics.put(topic, info);
                }
                metadata.setTopics(topics);
                return metadata;
            } catch (Exception e) {
                throw new RuntimeException("Kafka metadata query failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<ProbeResponse.MessageQueueStats> getStats(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            try (AdminClient admin = createAdminClient(config)) {
                ProbeResponse.MessageQueueStats stats = new ProbeResponse.MessageQueueStats();
                Set<String> topics = admin.listTopics().names().get();
                stats.setTotalTopics((long) topics.size());

                Map<String, Long> topicOffsets = new LinkedHashMap<>();
                long totalPartitions = 0;
                long totalMessages = 0;

                for (String topic : topics) {
                    try {
                        var topicDesc = admin.describeTopics(Collections.singleton(topic)).all().get().get(topic);
                        int partitionCount = topicDesc.partitions().size();
                        totalPartitions += partitionCount;

                        Map<org.apache.kafka.common.TopicPartition, org.apache.kafka.clients.admin.OffsetSpec> offsetSpecs = new LinkedHashMap<>();
                        for (var tpInfo : topicDesc.partitions()) {
                            offsetSpecs.put(new org.apache.kafka.common.TopicPartition(topic, tpInfo.partition()),
                                    org.apache.kafka.clients.admin.OffsetSpec.latest());
                        }
                        var listOffsetsResult = admin.listOffsets(offsetSpecs).all().get();
                        long topicMsgCount = 0;
                        for (var entry : listOffsetsResult.entrySet()) {
                            topicMsgCount += entry.getValue().offset();
                        }
                        topicOffsets.put(topic, topicMsgCount);
                        totalMessages += topicMsgCount;
                    } catch (Exception e) {
                        log.warn("Failed to get offsets for {}: {}", topic, e.getMessage());
                    }
                }

                stats.setTopicOffsets(topicOffsets);
                stats.setTotalMessages(totalMessages);
                stats.setTotalPartitions(totalPartitions);
                return stats;
            } catch (Exception e) {
                throw new RuntimeException("Kafka stats query failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<ProbeResponse.MessageQueueDataContent> consumeMessages(
            Map<String, Object> config, String topic, int count) {
        return CompletableFuture.supplyAsync(() -> {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    config.get("host") + ":" + config.getOrDefault("port", 9092));
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "probe-kafka-" + UUID.randomUUID());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Math.min(count, 100));

            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(Collections.singletonList(topic));
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));

                List<Map<String, Object>> rows = new ArrayList<>();
                List<String> columns = Arrays.asList("partition", "offset", "key", "value", "timestamp");
                int i = 0;
                for (ConsumerRecord<String, String> record : records) {
                    if (i >= count) break;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("partition", record.partition());
                    row.put("offset", record.offset());
                    row.put("key", record.key());
                    row.put("value", record.value() != null && record.value().length() > 500
                            ? record.value().substring(0, 500) + "..." : record.value());
                    row.put("timestamp", new Date(record.timestamp()).toString());
                    rows.add(row);
                    i++;
                }

                ProbeResponse.MessageQueueDataContent content = new ProbeResponse.MessageQueueDataContent();
                content.setTopicName(topic);
                content.setTotal((long) records.count());
                content.setRows(rows);
                content.setColumns(columns);
                content.setSuccess(true);
                return content;
            } catch (Exception e) {
                ProbeResponse.MessageQueueDataContent content = new ProbeResponse.MessageQueueDataContent();
                content.setSuccess(false);
                content.setMessage(e.getMessage());
                return content;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> testConnection(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            try (AdminClient admin = createAdminClient(config)) {
                admin.listTopics(new ListTopicsOptions().timeoutMs(5000)).names().get();
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private AdminClient createAdminClient(Map<String, Object> config) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                config.get("host") + ":" + config.getOrDefault("port", 9092));
        return AdminClient.create(props);
    }
}
