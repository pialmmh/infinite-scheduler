package com.telcobright.scheduler.kafka;

import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.handler.impl.SmsJobHandler;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Kafka job ingest with real Kafka instance.
 *
 * Prerequisites:
 * - Kafka running on localhost:9092 (or update KAFKA_BOOTSTRAP_SERVERS)
 * - MySQL running with test database
 *
 * To run: mvn test -Dtest=KafkaIntegrationTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KafkaIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(KafkaIntegrationTest.class);

    private static final String KAFKA_BOOTSTRAP_SERVERS =
        System.getProperty("kafka.bootstrap.servers", "localhost:9092");

    private static final String TEST_TOPIC = "test.scheduler.jobs.ingest";
    private static final String TEST_DLQ_TOPIC = "test.scheduler.jobs.dlq";

    private static final String MYSQL_HOST = System.getProperty("db.host", "127.0.0.1");
    private static final int MYSQL_PORT = Integer.parseInt(System.getProperty("db.port", "3306"));
    private static final String MYSQL_DATABASE = System.getProperty("db.name", "scheduler");
    private static final String MYSQL_USERNAME = System.getProperty("db.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("db.password", "123456");

    private static MultiAppSchedulerManager manager;
    private static KafkaProducer<String, String> producer;

    @BeforeAll
    static void setUpClass() throws Exception {
        logger.info("Setting up Kafka integration test");
        logger.info("Kafka: {}", KAFKA_BOOTSTRAP_SERVERS);
        logger.info("MySQL: {}:{}/{}", MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE);

        // Create test topics
        createTestTopics();

        // Create producer
        producer = createProducer();

        // Create scheduler manager with Kafka ingest
        manager = MultiAppSchedulerManager.builder()
            .mysqlHost(MYSQL_HOST)
            .mysqlPort(MYSQL_PORT)
            .mysqlDatabase(MYSQL_DATABASE)
            .mysqlUsername(MYSQL_USERNAME)
            .mysqlPassword(MYSQL_PASSWORD)
            .withKafkaIngest(
                KafkaIngestConfig.builder()
                    .bootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                    .topic(TEST_TOPIC)
                    .dlqTopic(TEST_DLQ_TOPIC)
                    .groupId("test-scheduler-group")
                    .maxPollRecords(100)
                    .pollTimeoutMs(1000)
                    .maxRetries(2)
                    .enabled(true)
                    .build()
            )
            .build();

        // Register test app
        manager.registerApp("sms", new SmsJobHandler());

        // Start scheduler (including Kafka consumer)
        manager.startAll();
        logger.info("Scheduler started with Kafka ingest");

        // Give consumer time to connect and subscribe
        Thread.sleep(2000);
    }

    @AfterAll
    static void tearDownClass() throws Exception {
        logger.info("Tearing down Kafka integration test");

        if (manager != null) {
            manager.stopAll();
        }

        if (producer != null) {
            producer.close();
        }

        logger.info("Test cleanup complete");
    }

    @Test
    @Order(1)
    void testKafkaConnectionEstablished() {
        KafkaJobIngestConsumer consumer = manager.getKafkaIngestConsumer();
        assertNotNull(consumer, "Kafka consumer should be initialized");
        assertTrue(consumer.isRunning(), "Kafka consumer should be running");
    }

    @Test
    @Order(2)
    void testSendAndProcessSingleJob() throws Exception {
        logger.info("Testing single job processing");

        // Create job request
        Map<String, Object> jobData = new HashMap<>();
        jobData.put("phoneNumber", "+8801712345678");
        jobData.put("message", "Integration test message");

        ScheduleJobRequest request = new ScheduleJobRequest(
            "sms",
            LocalDateTime.now().plusSeconds(10),
            jobData
        );
        request.setIdempotencyKey("test-single-" + System.currentTimeMillis());
        request.setPriority("high");

        // Get initial metrics
        KafkaJobIngestConsumer consumer = manager.getKafkaIngestConsumer();
        Map<String, Long> beforeMetrics = consumer.getMetrics();
        long messagesBefore = beforeMetrics.get("messagesProcessed");

        // Send to Kafka
        ProducerRecord<String, String> record = new ProducerRecord<>(
            TEST_TOPIC,
            "test-key-1",
            request.toJson()
        );

        producer.send(record).get(5, TimeUnit.SECONDS);
        logger.info("Message sent to Kafka");

        // Wait for processing
        Thread.sleep(3000);

        // Check metrics
        Map<String, Long> afterMetrics = consumer.getMetrics();
        long messagesAfter = afterMetrics.get("messagesProcessed");

        assertTrue(messagesAfter > messagesBefore,
            "Message should be processed. Before: " + messagesBefore + ", After: " + messagesAfter);

        logger.info("Single job test passed. Processed: {}", messagesAfter - messagesBefore);
    }

    @Test
    @Order(3)
    void testSendMultipleJobs() throws Exception {
        logger.info("Testing multiple job processing");

        KafkaJobIngestConsumer consumer = manager.getKafkaIngestConsumer();
        Map<String, Long> beforeMetrics = consumer.getMetrics();
        long messagesBefore = beforeMetrics.get("messagesProcessed");

        // Send 10 jobs
        int jobCount = 10;
        for (int i = 0; i < jobCount; i++) {
            Map<String, Object> jobData = new HashMap<>();
            jobData.put("phoneNumber", "+880171234" + String.format("%04d", i));
            jobData.put("message", "Batch test message #" + i);

            ScheduleJobRequest request = new ScheduleJobRequest(
                "sms",
                LocalDateTime.now().plusSeconds(15 + i),
                jobData
            );
            request.setIdempotencyKey("test-batch-" + i + "-" + System.currentTimeMillis());

            ProducerRecord<String, String> record = new ProducerRecord<>(
                TEST_TOPIC,
                "batch-key-" + i,
                request.toJson()
            );

            producer.send(record);
        }

        producer.flush();
        logger.info("Sent {} jobs to Kafka", jobCount);

        // Wait for processing
        Thread.sleep(5000);

        // Check metrics
        Map<String, Long> afterMetrics = consumer.getMetrics();
        long messagesAfter = afterMetrics.get("messagesProcessed");
        long processed = messagesAfter - messagesBefore;

        assertTrue(processed >= jobCount,
            String.format("Should process at least %d messages. Processed: %d", jobCount, processed));

        logger.info("Multiple jobs test passed. Processed: {}", processed);
    }

    @Test
    @Order(4)
    void testIdempotency() throws Exception {
        logger.info("Testing idempotency");

        String idempotencyKey = "idempotency-test-" + System.currentTimeMillis();

        // Create identical job request
        Map<String, Object> jobData = new HashMap<>();
        jobData.put("phoneNumber", "+8801700000000");
        jobData.put("message", "Idempotency test");

        ScheduleJobRequest request = new ScheduleJobRequest(
            "sms",
            LocalDateTime.now().plusSeconds(20),
            jobData
        );
        request.setIdempotencyKey(idempotencyKey);

        KafkaJobIngestConsumer consumer = manager.getKafkaIngestConsumer();
        Map<String, Long> beforeMetrics = consumer.getMetrics();
        long messagesBefore = beforeMetrics.get("messagesProcessed");

        // Send same job 3 times
        for (int i = 0; i < 3; i++) {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                TEST_TOPIC,
                "idempotency-key",
                request.toJson()
            );
            producer.send(record).get(5, TimeUnit.SECONDS);
            Thread.sleep(500);
        }

        // Wait for processing
        Thread.sleep(3000);

        // Check metrics - should only process once
        Map<String, Long> afterMetrics = consumer.getMetrics();
        long messagesAfter = afterMetrics.get("messagesProcessed");
        long processed = messagesAfter - messagesBefore;

        assertEquals(1, processed,
            "With same idempotency key, should only process once. Processed: " + processed);

        logger.info("Idempotency test passed");
    }

    @Test
    @Order(5)
    void testInvalidMessage_SendsToDLQ() throws Exception {
        logger.info("Testing DLQ for invalid messages");

        KafkaJobIngestConsumer consumer = manager.getKafkaIngestConsumer();
        Map<String, Long> beforeMetrics = consumer.getMetrics();
        long dlqBefore = beforeMetrics.get("messagesSentToDLQ");

        // Send invalid JSON
        ProducerRecord<String, String> invalidRecord = new ProducerRecord<>(
            TEST_TOPIC,
            "invalid-key",
            "{invalid json"
        );

        producer.send(invalidRecord).get(5, TimeUnit.SECONDS);
        logger.info("Sent invalid message");

        // Wait for processing and DLQ
        Thread.sleep(5000);

        // Check DLQ metrics
        Map<String, Long> afterMetrics = consumer.getMetrics();
        long dlqAfter = afterMetrics.get("messagesSentToDLQ");

        assertTrue(dlqAfter > dlqBefore,
            "Invalid message should be sent to DLQ. Before: " + dlqBefore + ", After: " + dlqAfter);

        logger.info("DLQ test passed");
    }

    @Test
    @Order(6)
    void testConsumerMetrics() {
        logger.info("Testing consumer metrics");

        KafkaJobIngestConsumer consumer = manager.getKafkaIngestConsumer();
        Map<String, Long> metrics = consumer.getMetrics();

        assertNotNull(metrics);
        assertTrue(metrics.containsKey("messagesReceived"));
        assertTrue(metrics.containsKey("messagesProcessed"));
        assertTrue(metrics.containsKey("messagesFailed"));
        assertTrue(metrics.containsKey("messagesSentToDLQ"));
        assertTrue(metrics.containsKey("idempotencyCacheSize"));

        logger.info("Consumer metrics:");
        metrics.forEach((key, value) -> logger.info("  {}: {}", key, value));

        // Verify we processed some messages
        assertTrue(metrics.get("messagesReceived") > 0, "Should have received messages");
        assertTrue(metrics.get("messagesProcessed") > 0, "Should have processed messages");

        logger.info("Metrics test passed");
    }

    // Helper methods

    private static void createTestTopics() {
        try {
            Properties props = new Properties();
            props.put("bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS);

            try (AdminClient admin = AdminClient.create(props)) {
                List<NewTopic> topics = Arrays.asList(
                    new NewTopic(TEST_TOPIC, 3, (short) 1),
                    new NewTopic(TEST_DLQ_TOPIC, 1, (short) 1)
                );

                admin.createTopics(topics).all().get(10, TimeUnit.SECONDS);
                logger.info("Created test topics: {}, {}", TEST_TOPIC, TEST_DLQ_TOPIC);
            } catch (Exception e) {
                // Topics might already exist, ignore
                logger.warn("Topics might already exist: {}", e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Failed to create test topics", e);
        }
    }

    private static KafkaProducer<String, String> createProducer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 3);

        return new KafkaProducer<>(props);
    }
}
