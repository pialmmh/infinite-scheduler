package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.kafka.KafkaIngestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Complete example showing how to use infinite-scheduler for SMS retry scheduling.
 *
 * Architecture:
 * 1. Producer sends failed SMS to SMS_Retry topic
 * 2. Scheduler consumes SMS_Retry and schedules for retry at retryTime
 * 3. At retryTime, job executes and sends to SMS_Send topic
 * 4. Your SMS service consumes SMS_Send and sends the actual SMS
 *
 * Topics:
 * - SMS_Retry: Input topic for scheduling retries
 * - SMS_Send: Output topic for actual SMS sending
 *
 * Prerequisites:
 * - Kafka running on localhost:9092
 * - MySQL running on 127.0.0.1:3306 with database 'scheduler'
 *
 * Usage:
 * java -cp target/infinite-scheduler-1.0.0.jar \
 *   com.telcobright.scheduler.examples.SmsRetrySchedulerExample
 */
public class SmsRetrySchedulerExample {

    private static final Logger logger = LoggerFactory.getLogger(SmsRetrySchedulerExample.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS =
        System.getProperty("kafka.bootstrap.servers", "localhost:9092");
    private static final String SMS_RETRY_TOPIC = "SMS_Retry";
    private static final String SMS_SEND_TOPIC = "SMS_Send";

    private static final String MYSQL_HOST = System.getProperty("db.host", "127.0.0.1");
    private static final int MYSQL_PORT = Integer.parseInt(System.getProperty("db.port", "3306"));
    private static final String MYSQL_DATABASE = System.getProperty("db.name", "scheduler");
    private static final String MYSQL_USERNAME = System.getProperty("db.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("db.password", "123456");

    public static void main(String[] args) throws Exception {
        logger.info("=== SMS Retry Scheduler Example ===");
        logger.info("Kafka: {}", KAFKA_BOOTSTRAP_SERVERS);
        logger.info("MySQL: {}:{}/{}", MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE);
        logger.info("Input Topic: {}", SMS_RETRY_TOPIC);
        logger.info("Output Topic: {}", SMS_SEND_TOPIC);
        logger.info("");

        // Step 1: Create and configure the scheduler with Kafka ingest
        MultiAppSchedulerManager manager = createSchedulerManager();

        // Step 2: Register SMS retry job handler
        SmsRetryJobHandler retryHandler = new SmsRetryJobHandler(
            KAFKA_BOOTSTRAP_SERVERS,
            SMS_SEND_TOPIC
        );
        manager.registerApp("sms_retry", retryHandler);
        logger.info("✅ Registered SMS retry job handler");

        // Step 3: Start the scheduler
        manager.startAll();
        logger.info("✅ Scheduler started");
        logger.info("✅ Listening on topic: {}", SMS_RETRY_TOPIC);
        logger.info("✅ Will publish to: {}", SMS_SEND_TOPIC);
        logger.info("");

        // Wait for consumer to connect
        Thread.sleep(2000);

        // Step 4: Demo - Simulate failed SMS and schedule retries
        simulateFailedSmsRetries();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Shutting down...");
                retryHandler.shutdown();
                manager.stopAll();
                logger.info("Shutdown complete");
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

        logger.info("⏰ Press Ctrl+C to stop");
        logger.info("");

        // Keep running
        Thread.currentThread().join();
    }

    /**
     * Create and configure the scheduler manager
     */
    private static MultiAppSchedulerManager createSchedulerManager() throws Exception {
        return MultiAppSchedulerManager.builder()
            .mysqlHost(MYSQL_HOST)
            .mysqlPort(MYSQL_PORT)
            .mysqlDatabase(MYSQL_DATABASE)
            .mysqlUsername(MYSQL_USERNAME)
            .mysqlPassword(MYSQL_PASSWORD)
            .withKafkaIngest(
                KafkaIngestConfig.builder()
                    .bootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                    .topic(SMS_RETRY_TOPIC)              // Listen to SMS_Retry
                    .dlqTopic("SMS_Retry_DLQ")           // DLQ for failed messages
                    .groupId("sms-retry-scheduler")
                    .maxPollRecords(500)
                    .maxRetries(3)
                    .enabled(true)
                    .build()
            )
            .build();
    }

    /**
     * Simulate failed SMS and schedule retries
     */
    private static void simulateFailedSmsRetries() {
        logger.info("=== Simulating Failed SMS Retries ===");

        SmsRetryProducer producer = new SmsRetryProducer(KAFKA_BOOTSTRAP_SERVERS, SMS_RETRY_TOPIC);

        LocalDateTime now = LocalDateTime.now();

        // Scenario 1: Immediate retry (10 seconds from now)
        logger.info("Scheduling retry for campaign task 10001 - Immediate retry in 10s");
        producer.scheduleRetry(
            10001L,
            now.minusMinutes(5),     // Created 5 minutes ago
            now.plusSeconds(10),      // Retry in 10 seconds
            1,
            "Temporary network failure"
        );

        // Scenario 2: Short delay retry (30 seconds from now)
        logger.info("Scheduling retry for campaign task 10002 - Short delay in 30s");
        producer.scheduleRetry(
            10002L,
            now.minusMinutes(10),
            now.plusSeconds(30),
            2,
            "Gateway timeout"
        );

        // Scenario 3: Medium delay retry (1 minute from now)
        logger.info("Scheduling retry for campaign task 10003 - Medium delay in 1 minute");
        producer.scheduleRetry(
            10003L,
            now.minusMinutes(15),
            now.plusMinutes(1),
            3,
            "Recipient temporarily unavailable"
        );

        // Scenario 4: Long delay retry (2 minutes from now)
        logger.info("Scheduling retry for campaign task 10004 - Long delay in 2 minutes");
        producer.scheduleRetry(
            10004L,
            now.minusMinutes(20),
            now.plusMinutes(2),
            1,
            "Rate limit exceeded"
        );

        // Scenario 5: Multiple retries
        logger.info("Scheduling retry for campaign task 10005 - Final retry in 3 minutes");
        producer.scheduleRetry(
            10005L,
            now.minusMinutes(30),
            now.plusMinutes(3),
            5,  // 5th attempt - last try
            "Multiple failures - last attempt"
        );

        producer.close();

        logger.info("");
        logger.info("✅ Scheduled 5 SMS retries:");
        logger.info("   1. Campaign 10001 -> {}  (Immediate)", now.plusSeconds(10).format(TIME_FORMATTER));
        logger.info("   2. Campaign 10002 -> {}  (30s delay)", now.plusSeconds(30).format(TIME_FORMATTER));
        logger.info("   3. Campaign 10003 -> {}  (1 min)", now.plusMinutes(1).format(TIME_FORMATTER));
        logger.info("   4. Campaign 10004 -> {}  (2 min)", now.plusMinutes(2).format(TIME_FORMATTER));
        logger.info("   5. Campaign 10005 -> {}  (3 min)", now.plusMinutes(3).format(TIME_FORMATTER));
        logger.info("");
        logger.info("Watch the logs for execution and publishing to {} topic", SMS_SEND_TOPIC);
        logger.info("");
    }
}
