package com.telcobright.scheduler.config;

import com.telcobright.scheduler.kafka.KafkaIngestConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Factory to create KafkaIngestConfig from Quarkus configuration properties.
 */
@ApplicationScoped
public class KafkaIngestConfigFactory {

    @Inject
    KafkaConfigProperties kafkaConfig;

    @Inject
    SchedulerConfigProperties schedulerConfig;

    /**
     * Creates a KafkaIngestConfig from Quarkus configuration.
     *
     * @return KafkaIngestConfig instance configured from application.properties
     */
    public KafkaIngestConfig createConfig() {
        var ingestConfig = schedulerConfig.kafka().ingest();
        var consumerConfig = kafkaConfig.consumer();

        return KafkaIngestConfig.builder()
            .bootstrapServers(kafkaConfig.bootstrapServers())
            .topic(ingestConfig.topic())
            .groupId(consumerConfig.groupId())
            .dlqTopic(ingestConfig.dlqTopic())
            .maxRetries(ingestConfig.maxRetries())
            .pollTimeoutMs(ingestConfig.pollTimeoutMs())
            .maxPollRecords(consumerConfig.maxPollRecords())
            .enableAutoCommit(consumerConfig.enableAutoCommit())
            .enabled(ingestConfig.enabled())
            .build();
    }

    /**
     * Check if Kafka ingest is enabled
     */
    public boolean isEnabled() {
        return schedulerConfig.kafka().ingest().enabled();
    }

    /**
     * Get bootstrap servers
     */
    public String getBootstrapServers() {
        return kafkaConfig.bootstrapServers();
    }

    /**
     * Get consumer group ID
     */
    public String getGroupId() {
        return kafkaConfig.consumer().groupId();
    }

    /**
     * Get ingest topic name
     */
    public String getTopic() {
        return schedulerConfig.kafka().ingest().topic();
    }

    /**
     * Get DLQ topic name
     */
    public String getDlqTopic() {
        return schedulerConfig.kafka().ingest().dlqTopic();
    }
}
