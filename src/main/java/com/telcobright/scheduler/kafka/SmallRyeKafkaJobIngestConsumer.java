package com.telcobright.scheduler.kafka;

import com.telcobright.scheduler.MultiAppSchedulerManager;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SmallRye Reactive Messaging consumer for job scheduling via Kafka.
 *
 * This replaces the manual KafkaConsumer with Quarkus-native reactive messaging.
 *
 * Features:
 * - Automatic message consumption from configured channel
 * - Manual acknowledgment for at-least-once delivery
 * - Dead Letter Queue (DLQ) for failed messages
 * - Idempotency support with TTL cache
 * - Metrics tracking
 * - Virtual thread-based blocking processing
 *
 * Configuration:
 * - Channel: job-schedule (defined in application.properties)
 * - Topic: Job_Schedule
 * - DLQ Topic: Job_Schedule_DLQ
 */
@ApplicationScoped
public class SmallRyeKafkaJobIngestConsumer {

    private static final Logger logger = LoggerFactory.getLogger(SmallRyeKafkaJobIngestConsumer.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    MultiAppSchedulerManager schedulerManager;

    // Idempotency cache: key -> timestamp
    private final Map<String, Long> idempotencyCache = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours

    // Metrics
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final AtomicLong messagesFailed = new AtomicLong(0);
    private final AtomicLong duplicatesSkipped = new AtomicLong(0);

    /**
     * Consumes messages from the job-schedule channel.
     *
     * Uses @Blocking to process messages on virtual threads for better scalability.
     * Manual acknowledgment ensures at-least-once delivery.
     */
    @Incoming("job-schedule")
    @Blocking(ordered = false)  // Process on virtual threads, order not important
    public CompletionStage<Void> consumeJobSchedule(Message<String> message) {
        messagesReceived.incrementAndGet();

        String payload = message.getPayload();

        // Get Kafka metadata
        var metadata = message.getMetadata(IncomingKafkaRecordMetadata.class);
        String key = metadata.map(m -> m.getKey() != null ? m.getKey().toString() : "null").orElse("unknown");
        int partition = metadata.map(IncomingKafkaRecordMetadata::getPartition).orElse(-1);
        long offset = metadata.map(IncomingKafkaRecordMetadata::getOffset).orElse(-1L);

        logger.debug("📥 Received message - Partition: {}, Offset: {}, Key: {}", partition, offset, key);

        try {
            // Parse the schedule request
            ScheduleJobRequest request = ScheduleJobRequest.fromJson(payload);
            request.validate();

            // Check for duplicate
            String idempotencyKey = request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : request.getRequestId();

            if (isDuplicate(idempotencyKey)) {
                duplicatesSkipped.incrementAndGet();
                logger.info("⏭️  Skipping duplicate request: {}", idempotencyKey);
                return message.ack();  // Acknowledge anyway to move forward
            }

            // Schedule the job
            scheduleJob(request);

            // Mark as processed
            markAsProcessed(idempotencyKey);
            messagesProcessed.incrementAndGet();

            logger.info("✅ Job scheduled successfully - App: {}, Time: {}, Key: {}",
                request.getAppName(), request.getScheduledTime(), idempotencyKey);

            // Acknowledge the message
            return message.ack();

        } catch (Exception e) {
            messagesFailed.incrementAndGet();
            logger.error("❌ Failed to process message - Partition: {}, Offset: {}, Error: {}",
                partition, offset, e.getMessage(), e);

            // Negative acknowledgment triggers DLQ
            return message.nack(e);
        }
    }

    /**
     * Schedule the job using the MultiAppSchedulerManager
     */
    private void scheduleJob(ScheduleJobRequest request) throws Exception {
        String appName = request.getAppName();
        LocalDateTime scheduledTime = request.getScheduledTime();
        Map<String, Object> jobData = request.toJobDataMap();

        logger.debug("📅 Scheduling job - App: {}, Time: {}", appName, scheduledTime.format(TIME_FORMATTER));

        // Use scheduler manager to schedule the job
        // scheduledTime is already in jobData from toJobDataMap()
        schedulerManager.scheduleJob(appName, jobData);

        logger.debug("✅ Job registered with scheduler - App: {}", appName);
    }

    /**
     * Check if we've already processed this request
     */
    private boolean isDuplicate(String key) {
        if (key == null) return false;

        cleanupExpiredEntries();

        Long timestamp = idempotencyCache.get(key);
        if (timestamp != null) {
            long age = System.currentTimeMillis() - timestamp;
            return age < IDEMPOTENCY_TTL_MS;
        }
        return false;
    }

    /**
     * Mark a request as processed
     */
    private void markAsProcessed(String key) {
        if (key != null) {
            idempotencyCache.put(key, System.currentTimeMillis());
        }
    }

    /**
     * Cleanup expired idempotency entries
     */
    private void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        idempotencyCache.entrySet().removeIf(entry ->
            (now - entry.getValue()) > IDEMPOTENCY_TTL_MS
        );
    }

    /**
     * Get metrics for monitoring
     */
    public Map<String, Long> getMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("messagesReceived", messagesReceived.get());
        metrics.put("messagesProcessed", messagesProcessed.get());
        metrics.put("messagesFailed", messagesFailed.get());
        metrics.put("duplicatesSkipped", duplicatesSkipped.get());
        metrics.put("cacheSize", (long) idempotencyCache.size());
        return metrics;
    }

    /**
     * Print current metrics
     */
    public void printMetrics() {
        logger.info("📊 Kafka Consumer Metrics:");
        logger.info("   Messages Received:  {}", messagesReceived.get());
        logger.info("   Messages Processed: {}", messagesProcessed.get());
        logger.info("   Messages Failed:    {}", messagesFailed.get());
        logger.info("   Duplicates Skipped: {}", duplicatesSkipped.get());
        logger.info("   Cache Size:         {}", idempotencyCache.size());
    }

    /**
     * Reset metrics (for testing)
     */
    public void resetMetrics() {
        messagesReceived.set(0);
        messagesProcessed.set(0);
        messagesFailed.set(0);
        duplicatesSkipped.set(0);
        idempotencyCache.clear();
    }
}
