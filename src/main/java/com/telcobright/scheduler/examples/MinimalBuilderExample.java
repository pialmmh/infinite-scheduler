package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.MultiAppSchedulerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal configuration example - MySQL only, no Kafka.
 *
 * This is the simplest possible configuration for the scheduler.
 * Jobs can be scheduled via REST API or programmatically.
 */
public class MinimalBuilderExample {

    private static final Logger logger = LoggerFactory.getLogger(MinimalBuilderExample.class);

    public static void main(String[] args) throws Exception {

        // ========================================================
        // MINIMAL CONFIGURATION (MySQL Only)
        // ========================================================

        MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
            .mysqlHost("127.0.0.1")
            .mysqlPort(3306)
            .mysqlDatabase("scheduler")
            .mysqlUsername("root")
            .mysqlPassword("123456")
            .build();

        // Register application handler
        manager.registerApp("sms_retry", new SmsRetryJobHandler(
            "localhost:9092",  // Kafka brokers for output
            "SMS_Send"         // Output topic
        ));

        // Start scheduler
        manager.startAll();

        logger.info("╔═══════════════════════════════════════════════════════╗");
        logger.info("║  MINIMAL SCHEDULER - STARTED                          ║");
        logger.info("╠═══════════════════════════════════════════════════════╣");
        logger.info("║  MySQL:  127.0.0.1:3306/scheduler                    ║");
        logger.info("║  Kafka:  Disabled (jobs via REST API only)           ║");
        logger.info("║  Apps:   sms_retry                                    ║");
        logger.info("╚═══════════════════════════════════════════════════════╝");

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                manager.stopAll();
                logger.info("✅ Scheduler stopped");
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

        Thread.sleep(Long.MAX_VALUE);
    }
}
