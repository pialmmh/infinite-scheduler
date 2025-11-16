package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.handler.impl.SmsJobHandler;
import com.telcobright.scheduler.handler.impl.SipCallJobHandler;
import com.telcobright.scheduler.handler.impl.PaymentGatewayJobHandler;
import com.telcobright.scheduler.kafka.KafkaIngestConfig;
import com.telcobright.scheduler.kafka.ScheduleJobRequest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example demonstrating Kafka-based job ingestion for infinite-scheduler.
 *
 * This example shows:
 * 1. How to configure MultiAppSchedulerManager with Kafka ingest
 * 2. How to send job scheduling requests through Kafka
 * 3. Benefits of Kafka-based ingestion:
 *    - Automatic load balancing across multiple scheduler instances
 *    - No need for service discovery
 *    - Kafka handles consumer health and rebalancing
 *    - Decoupled producers and consumers
 */
public class KafkaIngestExample {

    private static final Logger logger = LoggerFactory.getLogger(KafkaIngestExample.class);

    // Database configuration
    private static final String MYSQL_HOST = System.getProperty("db.host", "127.0.0.1");
    private static final int MYSQL_PORT = Integer.parseInt(System.getProperty("db.port", "3306"));
    private static final String MYSQL_DATABASE = System.getProperty("db.name", "scheduler");
    private static final String MYSQL_USERNAME = System.getProperty("db.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("db.password", "123456");

    // Kafka configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = System.getProperty("kafka.bootstrap.servers", "localhost:9092");
    private static final String KAFKA_TOPIC = "scheduler.jobs.ingest";
    private static final String KAFKA_DLQ_TOPIC = "scheduler.jobs.dlq";

    public static void main(String[] args) throws Exception {
        logger.info("=== Infinite Scheduler with Kafka Ingest ===" );

        // Create scheduler manager with Kafka ingest enabled
        MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
            .mysqlHost(MYSQL_HOST)
            .mysqlPort(MYSQL_PORT)
            .mysqlDatabase(MYSQL_DATABASE)
            .mysqlUsername(MYSQL_USERNAME)
            .mysqlPassword(MYSQL_PASSWORD)
            // Enable Kafka ingest with custom configuration
            .withKafkaIngest(
                KafkaIngestConfig.builder()
                    .bootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                    .topic(KAFKA_TOPIC)
                    .dlqTopic(KAFKA_DLQ_TOPIC)
                    .groupId("infinite-scheduler-consumer-group")
                    .maxPollRecords(500)
                    .maxRetries(3)
                    .enabled(true)
                    .build()
            )
            .build();

        // Register applications
        manager.registerApp("sms", new SmsJobHandler());
        manager.registerApp("sipcall", new SipCallJobHandler());
        manager.registerApp("payment_gateway", new PaymentGatewayJobHandler());

        // Start all schedulers (including Kafka consumer)
        manager.startAll();
        logger.info("✅ All application schedulers started");
        logger.info("✅ Kafka ingest consumer started - Listening on topic: {}", KAFKA_TOPIC);
        logger.info("");

        // Simulate sending jobs through Kafka
        simulateKafkaProducers();

        // Keep running
        logger.info("⏰ Press Ctrl+C to stop");
        logger.info("");

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Shutting down...");
                manager.stopAll();
                logger.info("Scheduler stopped");
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

        // Keep running
        Thread.currentThread().join();
    }

    /**
     * Simulate multiple producers sending job scheduling requests through Kafka.
     * In production, these would be your microservices/applications.
     */
    private static void simulateKafkaProducers() {
        // Create Kafka producer
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 3);

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        // Simulate SMS service sending scheduling requests
        Thread.ofVirtual().name("sms-producer").start(() -> {
            AtomicInteger counter = new AtomicInteger(0);
            while (true) {
                try {
                    Thread.sleep(5000); // Send every 5 seconds

                    int count = counter.getAndIncrement();

                    // Create SMS job request
                    Map<String, Object> smsData = new HashMap<>();
                    smsData.put("phoneNumber", "+880" + (1700000000L + count));
                    smsData.put("message", "Hello from SMS service #" + count);
                    smsData.put("senderId", "MyApp");

                    ScheduleJobRequest request = new ScheduleJobRequest(
                        "sms",
                        LocalDateTime.now().plusSeconds(10),
                        smsData
                    );
                    request.setPriority("normal");
                    request.setIdempotencyKey("sms-" + System.currentTimeMillis());

                    // Send to Kafka
                    ProducerRecord<String, String> record = new ProducerRecord<>(
                        KAFKA_TOPIC,
                        "sms-" + count,
                        request.toJson()
                    );

                    producer.send(record, (metadata, error) -> {
                        if (error != null) {
                            logger.error("Failed to send SMS job to Kafka", error);
                        } else {
                            logger.info("📱 Sent SMS job #{} to Kafka (partition={}, offset={})",
                                count, metadata.partition(), metadata.offset());
                        }
                    });

                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        // Simulate SIP Call service sending scheduling requests
        Thread.ofVirtual().name("sipcall-producer").start(() -> {
            AtomicInteger counter = new AtomicInteger(0);
            while (true) {
                try {
                    Thread.sleep(8000); // Send every 8 seconds

                    int count = counter.getAndIncrement();

                    // Create SIP Call job request
                    Map<String, Object> sipData = new HashMap<>();
                    sipData.put("callerNumber", "+8801800000000");
                    sipData.put("calleeNumber", "+880" + (1900000000L + count));
                    sipData.put("duration", 60);

                    ScheduleJobRequest request = new ScheduleJobRequest(
                        "sipcall",
                        LocalDateTime.now().plusSeconds(15),
                        sipData
                    );
                    request.setPriority("high");
                    request.setIdempotencyKey("sipcall-" + System.currentTimeMillis());

                    // Send to Kafka
                    ProducerRecord<String, String> record = new ProducerRecord<>(
                        KAFKA_TOPIC,
                        "sipcall-" + count,
                        request.toJson()
                    );

                    producer.send(record, (metadata, error) -> {
                        if (error != null) {
                            logger.error("Failed to send SIP Call job to Kafka", error);
                        } else {
                            logger.info("📞 Sent SIP Call job #{} to Kafka (partition={}, offset={})",
                                count, metadata.partition(), metadata.offset());
                        }
                    });

                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        // Simulate Payment Gateway sending scheduling requests
        Thread.ofVirtual().name("payment-producer").start(() -> {
            AtomicInteger counter = new AtomicInteger(0);
            while (true) {
                try {
                    Thread.sleep(12000); // Send every 12 seconds

                    int count = counter.getAndIncrement();

                    // Create Payment job request
                    Map<String, Object> paymentData = new HashMap<>();
                    paymentData.put("orderId", "ORD-" + System.currentTimeMillis());
                    paymentData.put("amount", 1000.0 + count * 100);
                    paymentData.put("currency", "BDT");

                    ScheduleJobRequest request = new ScheduleJobRequest(
                        "payment_gateway",
                        LocalDateTime.now().plusSeconds(20),
                        paymentData
                    );
                    request.setPriority("high");
                    request.setIdempotencyKey("payment-" + System.currentTimeMillis());

                    // Send to Kafka
                    ProducerRecord<String, String> record = new ProducerRecord<>(
                        KAFKA_TOPIC,
                        "payment-" + count,
                        request.toJson()
                    );

                    producer.send(record, (metadata, error) -> {
                        if (error != null) {
                            logger.error("Failed to send Payment job to Kafka", error);
                        } else {
                            logger.info("💳 Sent Payment job #{} to Kafka (partition={}, offset={})",
                                count, metadata.partition(), metadata.offset());
                        }
                    });

                } catch (InterruptedException e) {
                    break;
                }
            }
        });

        logger.info("Started Kafka producers simulating multiple microservices");
    }
}
