package com.telcobright.scheduler.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Quarkus configuration properties for Infinite Scheduler.
 *
 * Maps to properties prefixed with "scheduler." in application.properties
 */
@ConfigMapping(prefix = "scheduler")
public interface SchedulerConfigProperties {

    /**
     * Kafka configuration
     */
    KafkaConfig kafka();

    /**
     * Repository configuration (Split-Verse)
     */
    RepositoryConfig repository();

    /**
     * Fetcher configuration
     */
    FetcherConfig fetcher();

    /**
     * Cleanup configuration
     */
    CleanupConfig cleanup();

    /**
     * Quartz configuration
     */
    QuartzConfig quartz();

    /**
     * Web UI configuration
     */
    WebConfig web();

    interface KafkaConfig {
        /**
         * Kafka ingest configuration
         */
        IngestConfig ingest();

        interface IngestConfig {
            /**
             * Ingest topic name
             */
            @WithDefault("Job_Schedule")
            String topic();

            /**
             * Dead letter queue topic
             */
            @WithName("dlq.topic")
            @WithDefault("Job_Schedule_DLQ")
            String dlqTopic();

            /**
             * Enable Kafka job ingest
             */
            @WithDefault("false")
            boolean enabled();

            /**
             * Maximum retry attempts
             */
            @WithName("max.retries")
            @WithDefault("3")
            int maxRetries();

            /**
             * Poll timeout in milliseconds
             */
            @WithName("poll.timeout.ms")
            @WithDefault("1000")
            int pollTimeoutMs();
        }
    }

    interface RepositoryConfig {
        /**
         * Database name
         */
        @WithDefault("scheduler")
        String database();

        /**
         * Table prefix
         */
        TableConfig table();

        /**
         * Retention configuration
         */
        RetentionConfig retention();

        interface TableConfig {
            /**
             * Table name prefix
             */
            @WithDefault("scheduled_jobs")
            String prefix();
        }

        interface RetentionConfig {
            /**
             * Retention period in days
             */
            @WithDefault("30")
            int days();
        }
    }

    interface FetcherConfig {
        /**
         * Fetch interval in seconds
         */
        @WithName("interval.seconds")
        @WithDefault("25")
        int intervalSeconds();

        /**
         * Lookahead window in seconds
         */
        @WithName("lookahead.seconds")
        @WithDefault("30")
        int lookaheadSeconds();

        /**
         * Maximum jobs to fetch per cycle
         */
        @WithName("max.jobs.per.fetch")
        @WithDefault("10000")
        int maxJobsPerFetch();

        /**
         * Enable fetcher
         */
        @WithDefault("true")
        boolean enabled();
    }

    interface CleanupConfig {
        /**
         * Enable cleanup
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Cleanup interval in hours
         */
        @WithName("interval.hours")
        @WithDefault("24")
        int intervalHours();

        /**
         * Cleanup batch size
         */
        @WithName("batch.size")
        @WithDefault("1000")
        int batchSize();
    }

    interface QuartzConfig {
        /**
         * Thread pool configuration
         */
        ThreadConfig thread();

        /**
         * Misfire threshold in milliseconds
         */
        @WithName("misfire.threshold.ms")
        @WithDefault("60000")
        long misfireThresholdMs();

        interface ThreadConfig {
            /**
             * Thread pool size
             */
            @WithName("pool.size")
            @WithDefault("20")
            int poolSize();
        }
    }

    interface WebConfig {
        /**
         * Enable web UI
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Web UI port
         */
        @WithDefault("7070")
        int port();

        /**
         * Web UI host
         */
        @WithDefault("0.0.0.0")
        String host();
    }
}
