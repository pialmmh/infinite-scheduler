package com.telcobright.scheduler.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Quarkus configuration properties for Kafka.
 *
 * Maps to properties prefixed with "kafka." in application.properties
 */
@ConfigMapping(prefix = "kafka")
public interface KafkaConfigProperties {

    /**
     * Kafka bootstrap servers
     * Example: "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092"
     */
    @WithName("bootstrap.servers")
    @WithDefault("localhost:9092")
    String bootstrapServers();

    /**
     * Consumer configuration
     */
    ConsumerConfig consumer();

    /**
     * Producer configuration
     */
    ProducerConfig producer();

    interface ConsumerConfig {
        /**
         * Consumer group ID
         */
        @WithName("group.id")
        @WithDefault("infinite-scheduler-group")
        String groupId();

        /**
         * Enable auto commit
         */
        @WithName("enable.auto.commit")
        @WithDefault("false")
        boolean enableAutoCommit();

        /**
         * Auto offset reset strategy
         */
        @WithName("auto.offset.reset")
        @WithDefault("earliest")
        String autoOffsetReset();

        /**
         * Max poll records
         */
        @WithName("max.poll.records")
        @WithDefault("500")
        int maxPollRecords();

        /**
         * Session timeout in milliseconds
         */
        @WithName("session.timeout.ms")
        @WithDefault("30000")
        int sessionTimeoutMs();

        /**
         * Heartbeat interval in milliseconds
         */
        @WithName("heartbeat.interval.ms")
        @WithDefault("10000")
        int heartbeatIntervalMs();
    }

    interface ProducerConfig {
        /**
         * Acknowledgment mode
         */
        @WithDefault("all")
        String acks();

        /**
         * Number of retries
         */
        @WithDefault("3")
        int retries();

        /**
         * Max in-flight requests per connection
         */
        @WithName("max.in.flight.requests.per.connection")
        @WithDefault("1")
        int maxInFlightRequestsPerConnection();

        /**
         * Enable idempotence
         */
        @WithName("enable.idempotence")
        @WithDefault("true")
        boolean enableIdempotence();

        /**
         * Compression type
         */
        @WithName("compression.type")
        @WithDefault("snappy")
        String compressionType();

        /**
         * Batch size in bytes
         */
        @WithName("batch.size")
        @WithDefault("16384")
        int batchSize();

        /**
         * Linger time in milliseconds
         */
        @WithName("linger.ms")
        @WithDefault("10")
        int lingerMs();
    }
}
