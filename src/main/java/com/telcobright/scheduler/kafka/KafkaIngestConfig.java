package com.telcobright.scheduler.kafka;

import java.util.Properties;

/**
 * Configuration for Kafka job ingest consumer.
 */
public class KafkaIngestConfig {

    private final String bootstrapServers;
    private final String groupId;
    private final String topic;
    private final String dlqTopic; // Dead Letter Queue topic
    private final int maxPollRecords;
    private final int pollTimeoutMs;
    private final boolean enableAutoCommit;
    private final int maxRetries;
    private final boolean enabled;

    private KafkaIngestConfig(Builder builder) {
        this.bootstrapServers = builder.bootstrapServers;
        this.groupId = builder.groupId;
        this.topic = builder.topic;
        this.dlqTopic = builder.dlqTopic;
        this.maxPollRecords = builder.maxPollRecords;
        this.pollTimeoutMs = builder.pollTimeoutMs;
        this.enableAutoCommit = builder.enableAutoCommit;
        this.maxRetries = builder.maxRetries;
        this.enabled = builder.enabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convert to Kafka consumer properties.
     */
    public Properties toKafkaProperties() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", groupId);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("enable.auto.commit", String.valueOf(enableAutoCommit));
        props.put("max.poll.records", String.valueOf(maxPollRecords));
        props.put("auto.offset.reset", "earliest");
        props.put("session.timeout.ms", "30000");
        props.put("heartbeat.interval.ms", "10000");
        return props;
    }

    // Getters

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getTopic() {
        return topic;
    }

    public String getDlqTopic() {
        return dlqTopic;
    }

    public int getMaxPollRecords() {
        return maxPollRecords;
    }

    public int getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    public boolean isEnableAutoCommit() {
        return enableAutoCommit;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Builder for KafkaIngestConfig.
     */
    public static class Builder {
        private String bootstrapServers = "localhost:9092";
        private String groupId = "infinite-scheduler-ingest";
        private String topic = "scheduler.jobs.ingest";
        private String dlqTopic = "scheduler.jobs.dlq";
        private int maxPollRecords = 500;
        private int pollTimeoutMs = 1000;
        private boolean enableAutoCommit = false;
        private int maxRetries = 3;
        private boolean enabled = true;

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder dlqTopic(String dlqTopic) {
            this.dlqTopic = dlqTopic;
            return this;
        }

        public Builder maxPollRecords(int maxPollRecords) {
            this.maxPollRecords = maxPollRecords;
            return this;
        }

        public Builder pollTimeoutMs(int pollTimeoutMs) {
            this.pollTimeoutMs = pollTimeoutMs;
            return this;
        }

        public Builder enableAutoCommit(boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public KafkaIngestConfig build() {
            if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
                throw new IllegalArgumentException("bootstrapServers is required");
            }
            if (topic == null || topic.trim().isEmpty()) {
                throw new IllegalArgumentException("topic is required");
            }
            return new KafkaIngestConfig(this);
        }
    }

    @Override
    public String toString() {
        return "KafkaIngestConfig{" +
            "bootstrapServers='" + bootstrapServers + '\'' +
            ", groupId='" + groupId + '\'' +
            ", topic='" + topic + '\'' +
            ", dlqTopic='" + dlqTopic + '\'' +
            ", maxPollRecords=" + maxPollRecords +
            ", pollTimeoutMs=" + pollTimeoutMs +
            ", enableAutoCommit=" + enableAutoCommit +
            ", maxRetries=" + maxRetries +
            ", enabled=" + enabled +
            '}';
    }
}
