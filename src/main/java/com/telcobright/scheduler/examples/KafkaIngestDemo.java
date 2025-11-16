package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.handler.impl.SmsJobHandler;
import com.telcobright.scheduler.kafka.KafkaIngestConfig;
import com.telcobright.scheduler.kafka.KafkaJobIngestConsumer;
import com.telcobright.scheduler.kafka.ScheduleJobRequest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple demo showing Kafka-based job ingestion for infinite-scheduler.
 *
 * This demo:
 * 1. Starts scheduler with Kafka consumer
 * 2. Sends 10 SMS jobs through Kafka topic
 * 3. Shows jobs being processed
 * 4. Displays consumer metrics
 *
 * Prerequisites:
 * - Kafka running on localhost:9092 (or set kafka.bootstrap.servers property)
 * - MySQL running on 127.0.0.1:3306 (or set db.host property)
 */
public class KafkaIngestDemo {

    private static final Logger logger = LoggerFactory.getLogger(KafkaIngestDemo.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Configuration from system properties
    private static final String KAFKA_BOOTSTRAP_SERVERS =
        System.getProperty("kafka.bootstrap.servers", "localhost:9092");
    private static final String KAFKA_TOPIC = "scheduler.jobs.ingest";

    private static final String MYSQL_HOST = System.getProperty("db.host", "127.0.0.1");
    private static final int MYSQL_PORT = Integer.parseInt(System.getProperty("db.port", "3306"));
    private static final String MYSQL_DATABASE = System.getProperty("db.name", "scheduler");
    private static final String MYSQL_USERNAME = System.getProperty("db.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("db.password", "123456");

    private static final AtomicInteger jobCounter = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        logger.info("=== Kafka Ingest Demo for Infinite Scheduler ===");
        logger.info("Kafka: {}", KAFKA_BOOTSTRAP_SERVERS);
        logger.info("MySQL: {}:{}/{}", MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE);
        logger.info("");

        // Create scheduler manager with Kafka ingest enabled
        MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
            .mysqlHost(MYSQL_HOST)
            .mysqlPort(MYSQL_PORT)
            .mysqlDatabase(MYSQL_DATABASE)
            .mysqlUsername(MYSQL_USERNAME)
            .mysqlPassword(MYSQL_PASSWORD)
            .withKafkaIngest(
                KafkaIngestConfig.builder()
                    .bootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                    .topic(KAFKA_TOPIC)
                    .dlqTopic("scheduler.jobs.dlq")
                    .groupId("infinite-scheduler-demo")
                    .maxPollRecords(100)
                    .maxRetries(3)
                    .enabled(true)
                    .build()
            )
            .build();

        // Register SMS application
        manager.registerApp("sms", new SmsJobHandler());
        logger.info("✅ Registered SMS application");

        // Start scheduler (including Kafka consumer)
        manager.startAll();
        logger.info("✅ Scheduler started with Kafka ingest consumer");
        logger.info("✅ Listening on Kafka topic: {}", KAFKA_TOPIC);
        logger.info("");

        // Wait for consumer to connect
        Thread.sleep(2000);

        // Send demo jobs through Kafka
        sendDemoJobs();

        // Show metrics periodically
        showMetricsPeriodically(manager);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Shutting down...");
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

    private static void sendDemoJobs() {
        logger.info("=== Sending 10 Demo SMS Jobs through Kafka ===");

        // Create Kafka producer
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 3);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            LocalDateTime now = LocalDateTime.now();

            for (int i = 1; i <= 10; i++) {
                // Create job to be executed in next minute
                LocalDateTime scheduledTime = now.plusSeconds(10 + (i * 5));

                Map<String, Object> jobData = new HashMap<>();
                jobData.put("phoneNumber", "+880171234" + String.format("%04d", i));
                jobData.put("message", "Demo SMS #" + i + " - Scheduled at " +
                    scheduledTime.format(TIME_FORMATTER));

                ScheduleJobRequest request = new ScheduleJobRequest(
                    "sms",
                    scheduledTime,
                    jobData
                );
                request.setPriority(i % 3 == 0 ? "high" : "normal");
                request.setIdempotencyKey("demo-" + i + "-" + System.currentTimeMillis());

                // Send to Kafka
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    KAFKA_TOPIC,
                    "demo-job-" + i,
                    request.toJson()
                );

                producer.send(record, (metadata, error) -> {
                    if (error != null) {
                        logger.error("❌ Failed to send job to Kafka", error);
                    } else {
                        logger.info("✅ Sent job to Kafka: partition={}, offset={}",
                            metadata.partition(), metadata.offset());
                    }
                });

                logger.info("📱 Created SMS job #{}: {} at {}",
                    i,
                    jobData.get("phoneNumber"),
                    scheduledTime.format(TIME_FORMATTER));
            }

            producer.flush();
            logger.info("");
            logger.info("✅ All 10 jobs sent to Kafka topic: {}", KAFKA_TOPIC);
            logger.info("⏳ Jobs will be processed and executed in the next minute...");
            logger.info("");

        } catch (Exception e) {
            logger.error("❌ Error sending jobs to Kafka", e);
        }
    }

    private static void showMetricsPeriodically(MultiAppSchedulerManager manager) {
        // Show metrics every 10 seconds
        Thread.ofVirtual().name("metrics-reporter").start(() -> {
            while (true) {
                try {
                    Thread.sleep(10000); // Every 10 seconds

                    KafkaJobIngestConsumer consumer = manager.getKafkaIngestConsumer();
                    if (consumer != null && consumer.isRunning()) {
                        Map<String, Long> metrics = consumer.getMetrics();

                        logger.info("=== Kafka Consumer Metrics ===");
                        logger.info("  Messages Received:   {}", metrics.get("messagesReceived"));
                        logger.info("  Messages Processed:  {}", metrics.get("messagesProcessed"));
                        logger.info("  Messages Failed:     {}", metrics.get("messagesFailed"));
                        logger.info("  Messages to DLQ:     {}", metrics.get("messagesSentToDLQ"));
                        logger.info("  Idempotency Cache:   {}", metrics.get("idempotencyCacheSize"));
                        logger.info("  Consumer Status:     {}", consumer.isRunning() ? "RUNNING" : "STOPPED");
                        logger.info("");
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    logger.error("Error showing metrics", e);
                }
            }
        });
    }
}
