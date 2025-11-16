package com.telcobright.scheduler.kafka;

import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * SmallRye Reactive Messaging publisher for SMS retry results.
 *
 * This replaces the manual KafkaProducer with Quarkus-native reactive messaging.
 *
 * Features:
 * - Automatic message production to configured channel
 * - Acknowledgment tracking
 * - Message metadata (partition, key)
 * - Type-safe payload construction
 *
 * Configuration:
 * - Channel: sms-send (defined in application.properties)
 * - Topic: SMS_Send
 */
@ApplicationScoped
public class SmallRyeSmsRetryPublisher {

    private static final Logger logger = LoggerFactory.getLogger(SmallRyeSmsRetryPublisher.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    @Channel("sms-send")
    Emitter<String> smsEmitter;

    /**
     * Publish SMS retry result to SMS_Send topic.
     *
     * @param campaignTaskId Campaign task ID
     * @param createdOn Original creation time
     * @param scheduledRetryTime Scheduled retry time
     * @param actualExecutionTime Actual execution time
     * @param retryAttempt Retry attempt number
     */
    public void publishRetryResult(Long campaignTaskId,
                                   LocalDateTime createdOn,
                                   LocalDateTime scheduledRetryTime,
                                   LocalDateTime actualExecutionTime,
                                   int retryAttempt) {

        // Create payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("campaignTaskId", campaignTaskId);
        payload.put("createdOn", createdOn.format(TIME_FORMATTER));
        payload.put("scheduledRetryTime", scheduledRetryTime.format(TIME_FORMATTER));
        payload.put("actualExecutionTime", actualExecutionTime.format(TIME_FORMATTER));
        payload.put("retryAttempt", retryAttempt);

        String jsonPayload = toJson(payload);

        // Create message key
        String key = "task-" + campaignTaskId;

        // Create message with metadata
        OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
            .withKey(key)
            .build();

        Message<String> message = Message.of(jsonPayload)
            .addMetadata(metadata)
            .withAck(() -> {
                logger.debug("✅ Message acknowledged for campaign: {}", campaignTaskId);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            })
            .withNack(throwable -> {
                logger.error("❌ Message failed for campaign: {}", campaignTaskId, throwable);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            });

        // Send message
        smsEmitter.send(message);

        logger.info("📤 Published to SMS_Send - Campaign: {}, Attempt: {}, Key: {}",
            campaignTaskId, retryAttempt, key);
    }

    /**
     * Publish with Map payload (for flexibility)
     */
    public void publishRetryResult(Map<String, Object> jobData) {
        Long campaignTaskId = getLong(jobData, "campaignTaskId");
        LocalDateTime createdOn = parseDateTime((String) jobData.get("createdOn"));
        LocalDateTime scheduledRetryTime = parseDateTime((String) jobData.get("retryTime"));
        LocalDateTime actualExecutionTime = LocalDateTime.now();
        int retryAttempt = getInt(jobData, "retryAttempt", 1);

        publishRetryResult(campaignTaskId, createdOn, scheduledRetryTime,
            actualExecutionTime, retryAttempt);
    }

    // Helper methods

    private String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;

            json.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof Number) {
                json.append(value);
            } else {
                json.append("\"").append(value).append("\"");
            }
        }
        json.append("}");
        return json.toString();
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof Double) {
            return ((Double) value).longValue();
        } else if (value instanceof String) {
            return Long.parseLong((String) value);
        }
        throw new IllegalArgumentException("Cannot convert to Long: " + key + " = " + value);
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Long) {
            return ((Long) value).intValue();
        } else if (value instanceof Double) {
            return ((Double) value).intValue();
        } else if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        return defaultValue;
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null) return LocalDateTime.now();
        return LocalDateTime.parse(dateTimeStr, TIME_FORMATTER);
    }
}
