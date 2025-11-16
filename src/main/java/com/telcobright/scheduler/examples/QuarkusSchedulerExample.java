package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.config.KafkaConfigProperties;
import com.telcobright.scheduler.config.KafkaIngestConfigFactory;
import com.telcobright.scheduler.config.SchedulerConfigProperties;
import com.telcobright.scheduler.kafka.KafkaIngestConfig;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quarkus-based Infinite Scheduler Example.
 *
 * This example demonstrates:
 * - Using Quarkus @ConfigMapping for configuration
 * - Injecting KafkaConfigProperties and SchedulerConfigProperties
 * - Starting Multi-App Scheduler with Kafka ingest
 * - Configuration from application.properties
 *
 * To run:
 *   mvn quarkus:dev                              # Dev mode (localhost Kafka)
 *   mvn package && java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=prod  # Production
 */
@QuarkusMain
public class QuarkusSchedulerExample implements QuarkusApplication {

    private static final Logger logger = LoggerFactory.getLogger(QuarkusSchedulerExample.class);

    @Inject
    KafkaConfigProperties kafkaConfig;

    @Inject
    SchedulerConfigProperties schedulerConfig;

    @Inject
    KafkaIngestConfigFactory configFactory;

    @Override
    public int run(String... args) throws Exception {
        logger.info("=============================================================");
        logger.info("  INFINITE SCHEDULER - QUARKUS APPLICATION");
        logger.info("=============================================================");

        // Print configuration
        printConfiguration();

        // Create and start scheduler
        MultiAppSchedulerManager manager = createScheduler();

        // Register SMS retry handler
        registerSmsRetryHandler(manager);

        // Start all schedulers
        manager.startAll();

        logger.info("=============================================================");
        logger.info("  ✅ SCHEDULER STARTED SUCCESSFULLY");
        logger.info("=============================================================");
        logger.info("  Web UI: http://{}:{}/index.html",
            schedulerConfig.web().host(), schedulerConfig.web().port());
        logger.info("  Kafka Ingest: {}",
            schedulerConfig.kafka().ingest().enabled() ? "ENABLED" : "DISABLED");
        logger.info("  Press Ctrl+C to stop");
        logger.info("=============================================================");

        // Keep application running
        Quarkus.waitForExit();
        return 0;
    }

    private void printConfiguration() {
        logger.info("");
        logger.info("📋 Configuration:");
        logger.info("  Kafka Bootstrap Servers: {}", kafkaConfig.bootstrapServers());
        logger.info("  Consumer Group: {}", kafkaConfig.consumer().groupId());
        logger.info("  Ingest Topic: {}", schedulerConfig.kafka().ingest().topic());
        logger.info("  DLQ Topic: {}", schedulerConfig.kafka().ingest().dlqTopic());
        logger.info("  Kafka Ingest Enabled: {}", schedulerConfig.kafka().ingest().enabled());
        logger.info("  Web UI Port: {}", schedulerConfig.web().port());
        logger.info("  Fetcher Interval: {}s", schedulerConfig.fetcher().intervalSeconds());
        logger.info("  Lookahead Window: {}s", schedulerConfig.fetcher().lookaheadSeconds());
        logger.info("");
    }

    private MultiAppSchedulerManager createScheduler() throws Exception {
        // Build scheduler with Kafka ingest from configuration
        MultiAppSchedulerManager.Builder builder = MultiAppSchedulerManager.builder()
            .mysqlHost("127.0.0.1")
            .mysqlPort(3306)
            .mysqlDatabase(schedulerConfig.repository().database())
            .mysqlUsername("root")  // Should come from config in production
            .mysqlPassword("123456"); // Should come from config in production

        // Add Kafka ingest if enabled
        if (configFactory.isEnabled()) {
            KafkaIngestConfig kafkaIngestConfig = configFactory.createConfig();
            builder.withKafkaIngest(kafkaIngestConfig);

            logger.info("✅ Kafka Ingest Configured:");
            logger.info("   - Bootstrap Servers: {}", kafkaIngestConfig.getBootstrapServers());
            logger.info("   - Consumer Group: {}", kafkaIngestConfig.getGroupId());
            logger.info("   - Topic: {}", kafkaIngestConfig.getTopic());
            logger.info("   - DLQ Topic: {}", kafkaIngestConfig.getDlqTopic());
        } else {
            logger.info("⚠️  Kafka Ingest Disabled (scheduler.kafka.ingest.enabled=false)");
        }

        return builder.build();
    }

    private void registerSmsRetryHandler(MultiAppSchedulerManager manager) {
        // Register SMS retry handler
        String bootstrapServers = kafkaConfig.bootstrapServers();
        SmsRetryJobHandler handler = new SmsRetryJobHandler(bootstrapServers, "SMS_Send");
        manager.registerApp("sms_retry", handler);

        logger.info("✅ Registered SMS Retry Handler:");
        logger.info("   - App Name: sms_retry");
        logger.info("   - Output Topic: SMS_Send");
        logger.info("   - Kafka Brokers: {}", bootstrapServers);
    }

    public static void main(String[] args) {
        Quarkus.run(QuarkusSchedulerExample.class, args);
    }
}
