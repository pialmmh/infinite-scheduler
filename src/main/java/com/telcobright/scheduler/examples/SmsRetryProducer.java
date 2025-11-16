package com.telcobright.scheduler.examples;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Producer for sending SMS retry requests to SMS_Retry Kafka topic.
 *
 * This is used by your SMS service to schedule failed SMS for retry.
 *
 * Usage:
 * <pre>
 * SmsRetryProducer producer = new SmsRetryProducer("localhost:9092");
 * producer.scheduleRetry(12345L, LocalDateTime.now(), LocalDateTime.now().plusMinutes(10));
 * producer.close();
 * </pre>
 */
public class SmsRetryProducer {

    private static final Logger logger = LoggerFactory.getLogger(SmsRetryProducer.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KafkaProducer<String, String> producer;
    private final String retryTopic;

    /**
     * Create SMS retry producer
     *
     * @param kafkaBootstrapServers Kafka bootstrap servers (e.g., "localhost:9092")
     */
    public SmsRetryProducer(String kafkaBootstrapServers) {
        this(kafkaBootstrapServers, "SMS_Retry");
    }

    /**
     * Create SMS retry producer with custom topic
     *
     * @param kafkaBootstrapServers Kafka bootstrap servers
     * @param retryTopic Topic name for retry requests
     */
    public SmsRetryProducer(String kafkaBootstrapServers, String retryTopic) {
        this.retryTopic = retryTopic;
        this.producer = createKafkaProducer(kafkaBootstrapServers);
        logger.info("SmsRetryProducer initialized. Retry topic: {}", retryTopic);
    }

    /**
     * Schedule an SMS retry
     *
     * @param campaignTaskId The campaign task ID
     * @param createdOn When the original SMS was created
     * @param retryTime When to retry the SMS
     */
    public void scheduleRetry(Long campaignTaskId, LocalDateTime createdOn, LocalDateTime retryTime) {
        scheduleRetry(campaignTaskId, createdOn, retryTime, 1, null);
    }

    /**
     * Schedule an SMS retry with additional details
     *
     * @param campaignTaskId The campaign task ID
     * @param createdOn When the original SMS was created
     * @param retryTime When to retry the SMS
     * @param retryAttempt Retry attempt number (1, 2, 3, etc.)
     * @param failureReason Why the SMS failed (optional)
     */
    public void scheduleRetry(Long campaignTaskId, LocalDateTime createdOn, LocalDateTime retryTime,
                              int retryAttempt, String failureReason) {

        try {
            // Create retry request payload
            Map<String, Object> retryRequest = new HashMap<>();
            retryRequest.put("campaignTaskId", campaignTaskId);
            retryRequest.put("createdOn", createdOn.format(DATE_TIME_FORMATTER));
            retryRequest.put("retryTime", retryTime.format(DATE_TIME_FORMATTER));
            retryRequest.put("retryAttempt", retryAttempt);
            if (failureReason != null) {
                retryRequest.put("failureReason", failureReason);
            }

            // Convert to JSON
            String jsonPayload = toJson(retryRequest);

            // Send to SMS_Retry topic
            ProducerRecord<String, String> record = new ProducerRecord<>(
                retryTopic,
                "task-" + campaignTaskId,
                jsonPayload
            );

            producer.send(record, (metadata, error) -> {
                if (error != null) {
                    logger.error("❌ Failed to schedule retry for campaignTaskId={}: {}",
                        campaignTaskId, error.getMessage());
                } else {
                    logger.info("✅ Scheduled retry for campaignTaskId={} at {} (partition={}, offset={})",
                        campaignTaskId,
                        retryTime.format(DATE_TIME_FORMATTER),
                        metadata.partition(),
                        metadata.offset());
                }
            });

            producer.flush();

        } catch (Exception e) {
            logger.error("Error scheduling SMS retry", e);
            throw new RuntimeException("Failed to schedule SMS retry", e);
        }
    }

    /**
     * Close the producer
     */
    public void close() {
        if (producer != null) {
            logger.info("Closing SMS retry producer...");
            producer.close();
        }
    }

    // Helper methods

    private KafkaProducer<String, String> createKafkaProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 3);
        props.put("max.in.flight.requests.per.connection", 1);
        props.put("enable.idempotence", true);

        return new KafkaProducer<>(props);
    }

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
}
