package com.telcobright.scheduler.kafka;

import com.telcobright.scheduler.MultiAppSchedulerManager;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka consumer for ingesting job scheduling requests.
 *
 * Features:
 * - Consumer group-based load balancing
 * - Automatic rebalancing
 * - Manual commit for at-least-once delivery
 * - Dead Letter Queue (DLQ) for failed messages
 * - Retry with exponential backoff
 * - Idempotency key support
 */
public class KafkaJobIngestConsumer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaJobIngestConsumer.class);

    private final KafkaIngestConfig config;
    private final MultiAppSchedulerManager schedulerManager;
    private final KafkaConsumer<String, String> consumer;
    private final KafkaProducer<String, String> dlqProducer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread consumerThread;

    // Metrics
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final AtomicLong messagesFailed = new AtomicLong(0);
    private final AtomicLong messagesSentToDLQ = new AtomicLong(0);

    // Idempotency tracking (simple in-memory cache, consider Redis for production)
    private final Map<String, Long> processedIdempotencyKeys = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours

    public KafkaJobIngestConsumer(KafkaIngestConfig config, MultiAppSchedulerManager schedulerManager) {
        this.config = config;
        this.schedulerManager = schedulerManager;

        // Create consumer
        this.consumer = new KafkaConsumer<>(config.toKafkaProperties());

        // Create DLQ producer if DLQ topic is configured
        if (config.getDlqTopic() != null && !config.getDlqTopic().isEmpty()) {
            Properties dlqProps = new Properties();
            dlqProps.put("bootstrap.servers", config.getBootstrapServers());
            dlqProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            dlqProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            dlqProps.put("acks", "all");
            dlqProps.put("retries", 3);
            this.dlqProducer = new KafkaProducer<>(dlqProps);
            logger.info("DLQ producer created for topic: {}", config.getDlqTopic());
        } else {
            this.dlqProducer = null;
            logger.warn("DLQ topic not configured - failed messages will be logged only");
        }

        logger.info("KafkaJobIngestConsumer created with config: {}", config);
    }

    /**
     * Start the consumer thread.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            consumerThread = Thread.ofVirtual().name("kafka-ingest-consumer").start(() -> {
                try {
                    consume();
                } catch (Exception e) {
                    logger.error("Fatal error in Kafka consumer thread", e);
                }
            });
            logger.info("Kafka ingest consumer started for topic: {}", config.getTopic());
        } else {
            logger.warn("Kafka ingest consumer already running");
        }
    }

    /**
     * Stop the consumer thread.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping Kafka ingest consumer...");
            if (consumerThread != null) {
                consumerThread.interrupt();
                try {
                    consumerThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            consumer.close();
            if (dlqProducer != null) {
                dlqProducer.close();
            }
            logger.info("Kafka ingest consumer stopped");
        }
    }

    /**
     * Main consumer loop.
     */
    private void consume() {
        consumer.subscribe(Collections.singletonList(config.getTopic()));
        logger.info("Subscribed to topic: {}", config.getTopic());

        while (running.get()) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(config.getPollTimeoutMs()));

                if (!records.isEmpty()) {
                    logger.debug("Polled {} records", records.count());
                    processRecords(records);

                    // Manual commit after successful processing
                    if (!config.isEnableAutoCommit()) {
                        consumer.commitSync();
                    }
                }

                // Periodic cleanup of idempotency cache
                cleanupIdempotencyCache();

            } catch (Exception e) {
                logger.error("Error processing Kafka records", e);
                try {
                    Thread.sleep(1000); // Back off on error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Process a batch of records.
     */
    private void processRecords(ConsumerRecords<String, String> records) {
        for (ConsumerRecord<String, String> record : records) {
            messagesReceived.incrementAndGet();
            processRecord(record);
        }
    }

    /**
     * Process a single record.
     */
    private void processRecord(ConsumerRecord<String, String> record) {
        String key = record.key();
        String value = record.value();

        logger.debug("Processing record - Key: {}, Value: {}", key, value);

        int attempt = 0;
        Exception lastException = null;

        while (attempt < config.getMaxRetries()) {
            try {
                // Parse request
                ScheduleJobRequest request = ScheduleJobRequest.fromJson(value);
                request.validate();

                // Check idempotency
                if (request.getIdempotencyKey() != null && isDuplicate(request.getIdempotencyKey())) {
                    logger.info("Skipping duplicate request with idempotency key: {}", request.getIdempotencyKey());
                    messagesProcessed.incrementAndGet();
                    return;
                }

                // Schedule the job
                scheduleJob(request);

                // Track idempotency
                if (request.getIdempotencyKey() != null) {
                    processedIdempotencyKeys.put(request.getIdempotencyKey(), System.currentTimeMillis());
                }

                messagesProcessed.incrementAndGet();
                logger.info("Successfully scheduled job from Kafka: appName={}, requestId={}, scheduledTime={}",
                    request.getAppName(), request.getRequestId(), request.getScheduledTime());
                return;

            } catch (Exception e) {
                lastException = e;
                attempt++;
                logger.warn("Failed to process record (attempt {}/{}): {}", attempt, config.getMaxRetries(), e.getMessage());

                if (attempt < config.getMaxRetries()) {
                    // Exponential backoff
                    try {
                        Thread.sleep((long) Math.pow(2, attempt) * 100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // All retries exhausted - send to DLQ
        messagesFailed.incrementAndGet();
        sendToDLQ(record, lastException);
    }

    /**
     * Schedule a job using the scheduler manager.
     */
    private void scheduleJob(ScheduleJobRequest request) {
        String appName = request.getAppName();
        Map<String, Object> jobData = request.toJobDataMap();

        // Schedule through the manager
        schedulerManager.scheduleJob(appName, jobData);
    }

    /**
     * Check if a request is a duplicate based on idempotency key.
     */
    private boolean isDuplicate(String idempotencyKey) {
        return processedIdempotencyKeys.containsKey(idempotencyKey);
    }

    /**
     * Cleanup old entries from idempotency cache.
     */
    private void cleanupIdempotencyCache() {
        long now = System.currentTimeMillis();
        processedIdempotencyKeys.entrySet().removeIf(entry ->
            now - entry.getValue() > IDEMPOTENCY_CACHE_TTL_MS
        );
    }

    /**
     * Send failed message to Dead Letter Queue.
     */
    private void sendToDLQ(ConsumerRecord<String, String> record, Exception exception) {
        if (dlqProducer == null) {
            logger.error("Failed to process message (no DLQ configured): partition={}, offset={}, error={}",
                record.partition(), record.offset(), exception.getMessage());
            return;
        }

        try {
            // Add error metadata to headers
            ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(
                config.getDlqTopic(),
                record.partition(),
                record.key(),
                record.value()
            );

            // Add error information as headers
            dlqRecord.headers().add("original-topic", record.topic().getBytes());
            dlqRecord.headers().add("original-partition", String.valueOf(record.partition()).getBytes());
            dlqRecord.headers().add("original-offset", String.valueOf(record.offset()).getBytes());
            dlqRecord.headers().add("error-message", exception.getMessage().getBytes());
            dlqRecord.headers().add("error-timestamp", String.valueOf(System.currentTimeMillis()).getBytes());

            dlqProducer.send(dlqRecord, (metadata, error) -> {
                if (error != null) {
                    logger.error("Failed to send message to DLQ: {}", error.getMessage());
                } else {
                    messagesSentToDLQ.incrementAndGet();
                    logger.warn("Sent failed message to DLQ: topic={}, partition={}, offset={}",
                        metadata.topic(), metadata.partition(), metadata.offset());
                }
            });

        } catch (Exception e) {
            logger.error("Failed to send message to DLQ", e);
        }
    }

    /**
     * Get consumer metrics.
     */
    public Map<String, Long> getMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("messagesReceived", messagesReceived.get());
        metrics.put("messagesProcessed", messagesProcessed.get());
        metrics.put("messagesFailed", messagesFailed.get());
        metrics.put("messagesSentToDLQ", messagesSentToDLQ.get());
        metrics.put("idempotencyCacheSize", (long) processedIdempotencyKeys.size());
        return metrics;
    }

    public boolean isRunning() {
        return running.get();
    }
}
