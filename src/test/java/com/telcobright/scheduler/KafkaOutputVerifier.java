package com.telcobright.scheduler;

import com.google.gson.Gson;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper class to verify job execution by consuming from Kafka output topic.
 *
 * This consumer:
 * 1. Subscribes to the scheduler output topic
 * 2. Receives messages when jobs are executed
 * 3. Tracks which jobs have been verified
 * 4. Provides statistics and missing job reports
 */
public class KafkaOutputVerifier implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(KafkaOutputVerifier.class);
    private static final Gson gson = new Gson();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String bootstrapServers;
    private final String outputTopic;
    private final Set<String> expectedJobIds;
    private final Set<String> verifiedJobIds;
    private final AtomicInteger verifiedCount;
    private final AtomicBoolean running;

    private KafkaConsumer<String, String> consumer;

    public KafkaOutputVerifier(String bootstrapServers, String outputTopic, Set<String> expectedJobIds) {
        this.bootstrapServers = bootstrapServers;
        this.outputTopic = outputTopic;
        this.expectedJobIds = expectedJobIds;
        this.verifiedJobIds = Collections.synchronizedSet(new HashSet<>());
        this.verifiedCount = new AtomicInteger(0);
        this.running = new AtomicBoolean(false);
    }

    @Override
    public void run() {
        running.set(true);
        consumer = createConsumer();

        try {
            consumer.subscribe(Collections.singletonList(outputTopic));
            logger.info("📡 Kafka output verifier started - consuming from topic: {}", outputTopic);

            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    processOutputMessage(record);
                }

                // Commit offsets
                if (records.count() > 0) {
                    consumer.commitSync();
                }
            }

        } catch (Exception e) {
            logger.error("Error in Kafka output verifier", e);
        } finally {
            if (consumer != null) {
                consumer.close();
            }
            logger.info("📡 Kafka output verifier stopped");
        }
    }

    private void processOutputMessage(ConsumerRecord<String, String> record) {
        try {
            String payload = record.value();

            // Parse JSON to extract idempotencyKey or jobId
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(payload, Map.class);

            // Try to extract job identifier
            String jobId = extractJobId(data);

            if (jobId != null && !jobId.isEmpty()) {
                // Check if this is one of our expected jobs
                if (expectedJobIds.contains(jobId)) {
                    synchronized (verifiedJobIds) {
                        if (!verifiedJobIds.contains(jobId)) {
                            verifiedJobIds.add(jobId);
                            int count = verifiedCount.incrementAndGet();

                            // Log every 100th job
                            if (count % 100 == 0) {
                                logger.debug("✅ Verified job #{}: {} (Total: {})",
                                    count, jobId.substring(0, Math.min(20, jobId.length())), count);
                            }
                        }
                    }
                } else {
                    // Job not in our expected list - might be from previous test run
                    logger.trace("Received job not in expected list: {}", jobId);
                }
            } else {
                logger.warn("Could not extract job ID from output message: {}",
                    payload.substring(0, Math.min(100, payload.length())));
            }

        } catch (Exception e) {
            logger.error("Error processing output message", e);
        }
    }

    /**
     * Extract job identifier from output message.
     * Tries various field names that might contain the job ID.
     */
    private String extractJobId(Map<String, Object> data) {
        // Try different possible field names
        String[] possibleFields = {
            "idempotencyKey",
            "jobId",
            "uniqueId",
            "id",
            "requestId"
        };

        for (String field : possibleFields) {
            Object value = data.get(field);
            if (value != null) {
                return value.toString();
            }
        }

        // Try nested jobData
        Object jobData = data.get("jobData");
        if (jobData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> jobDataMap = (Map<String, Object>) jobData;
            for (String field : possibleFields) {
                Object value = jobDataMap.get(field);
                if (value != null) {
                    return value.toString();
                }
            }
        }

        return null;
    }

    public void stop() {
        running.set(false);
        logger.info("Stopping Kafka output verifier...");
    }

    public int getVerifiedCount() {
        return verifiedCount.get();
    }

    public Set<String> getVerifiedJobIds() {
        return new HashSet<>(verifiedJobIds);
    }

    public Set<String> getMissingJobs() {
        Set<String> missing = new HashSet<>(expectedJobIds);
        missing.removeAll(verifiedJobIds);
        return missing;
    }

    public double getVerificationRate() {
        if (expectedJobIds.isEmpty()) {
            return 0.0;
        }
        return (verifiedCount.get() * 100.0) / expectedJobIds.size();
    }

    public void printStatistics() {
        logger.info("=".repeat(80));
        logger.info("KAFKA OUTPUT VERIFIER STATISTICS");
        logger.info("=".repeat(80));
        logger.info("Expected Jobs:     {}", expectedJobIds.size());
        logger.info("Verified Jobs:     {}", verifiedCount.get());
        logger.info("Missing Jobs:      {}", expectedJobIds.size() - verifiedCount.get());
        logger.info("Verification Rate: {:.2f}%", getVerificationRate());
        logger.info("=".repeat(80));

        if (verifiedCount.get() < expectedJobIds.size()) {
            Set<String> missing = getMissingJobs();
            logger.info("Missing job IDs (first 10):");
            missing.stream().limit(10).forEach(id -> logger.info("  - {}", id));
        }
    }

    private KafkaConsumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", "load-test-verifier-" + System.currentTimeMillis());
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("enable.auto.commit", false);
        props.put("auto.offset.reset", "earliest"); // Start from beginning to catch all messages
        props.put("max.poll.records", 500);
        props.put("session.timeout.ms", 30000);
        props.put("heartbeat.interval.ms", 10000);

        return new KafkaConsumer<>(props);
    }
}
