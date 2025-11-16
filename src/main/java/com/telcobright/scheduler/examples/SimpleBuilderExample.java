package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.kafka.KafkaIngestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple example demonstrating single builder configuration.
 *
 * This shows how to configure the entire scheduler (MySQL + Kafka)
 * using a single fluent builder pattern.
 */
public class SimpleBuilderExample {

    private static final Logger logger = LoggerFactory.getLogger(SimpleBuilderExample.class);

    public static void main(String[] args) throws Exception {

        // ========================================================
        // COMPLETE CONFIGURATION IN A SINGLE BUILDER
        // ========================================================

        MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()

            // === MySQL Configuration (Required) ===
            .mysqlHost("127.0.0.1")
            .mysqlPort(3306)
            .mysqlDatabase("scheduler")
            .mysqlUsername("root")
            .mysqlPassword("123456")

            // === Kafka Configuration (Optional) ===
            .withKafkaIngest(
                KafkaIngestConfig.builder()
                    .bootstrapServers("10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092")
                    .groupId("infinite-scheduler-group")
                    .topic("Job_Schedule")
                    .dlqTopic("Job_Schedule_DLQ")
                    .maxPollRecords(500)
                    .maxRetries(3)
                    .enabled(true)
                    .build()
            )
            .build();

        // ========================================================
        // REGISTER APPLICATIONS
        // ========================================================

        String kafkaBrokers = "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092";

        manager.registerApp("sms_retry",
            new SmsRetryJobHandler(kafkaBrokers, "SMS_Send"));

        // ========================================================
        // START ALL SCHEDULERS
        // ========================================================

        manager.startAll();

        // ========================================================
        // DISPLAY STARTUP INFO
        // ========================================================

        logger.info("╔════════════════════════════════════════════════════════════════╗");
        logger.info("║     INFINITE SCHEDULER - STARTED SUCCESSFULLY                  ║");
        logger.info("╠════════════════════════════════════════════════════════════════╣");
        logger.info("║  MySQL Database:                                               ║");
        logger.info("║    Host:     127.0.0.1:3306                                    ║");
        logger.info("║    Database: scheduler                                         ║");
        logger.info("║                                                                ║");
        logger.info("║  Kafka Brokers:                                                ║");
        logger.info("║    Brokers:  10.10.199.20:9092,10.10.198.20:9092...          ║");
        logger.info("║    Input:    Job_Schedule                                      ║");
        logger.info("║    DLQ:      Job_Schedule_DLQ                                  ║");
        logger.info("║                                                                ║");
        logger.info("║  Registered Apps:                                              ║");
        logger.info("║    - sms_retry (Output: SMS_Send)                             ║");
        logger.info("║                                                                ║");
        logger.info("║  Press Ctrl+C to stop                                          ║");
        logger.info("╚════════════════════════════════════════════════════════════════╝");

        // ========================================================
        // GRACEFUL SHUTDOWN
        // ========================================================

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Shutting down scheduler...");
                manager.stopAll();
                logger.info("✅ Scheduler stopped successfully");
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

        // Keep running
        Thread.sleep(Long.MAX_VALUE);
    }
}
