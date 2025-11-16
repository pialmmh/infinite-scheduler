package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.handler.JobHandler;
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
 * Job handler for SMS retry jobs.
 *
 * When executed:
 * 1. Receives the retry request (campaignTaskId, createdOn, retryTime)
 * 2. Publishes to SMS_Send topic for actual SMS sending
 *
 * This handler is triggered by the scheduler at the specified retryTime.
 */
public class SmsRetryJobHandler implements JobHandler {

    private static final Logger logger = LoggerFactory.getLogger(SmsRetryJobHandler.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Kafka producer for sending to SMS_Send topic
    private final KafkaProducer<String, String> smsProducer;
    private final String smsSendTopic;

    public SmsRetryJobHandler(String kafkaBootstrapServers, String smsSendTopic) {
        this.smsSendTopic = smsSendTopic;
        this.smsProducer = createKafkaProducer(kafkaBootstrapServers);
        logger.info("SmsRetryJobHandler initialized. Will send to topic: {}", smsSendTopic);
    }

    @Override
    public void execute(Map<String, Object> jobData) throws Exception {
        Long campaignTaskId = getLong(jobData, "campaignTaskId");
        String createdOn = (String) jobData.get("createdOn");
        String retryTime = (String) jobData.get("retryTime");

        logger.info("Executing SMS retry job:");
        logger.info("  Campaign Task ID: {}", campaignTaskId);
        logger.info("  Created On: {}", createdOn);
        logger.info("  Retry Time: {}", retryTime);
        logger.info("  Actual Execution Time: {}", LocalDateTime.now().format(DATE_TIME_FORMATTER));

        // Create payload for SMS_Send topic
        Map<String, Object> smsSendPayload = new HashMap<>();
        smsSendPayload.put("campaignTaskId", campaignTaskId);
        smsSendPayload.put("createdOn", createdOn);
        smsSendPayload.put("scheduledRetryTime", retryTime);
        smsSendPayload.put("actualExecutionTime", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        smsSendPayload.put("retryAttempt", jobData.getOrDefault("retryAttempt", 1));

        // Convert to JSON
        String jsonPayload = toJson(smsSendPayload);

        // Send to SMS_Send topic
        ProducerRecord<String, String> record = new ProducerRecord<>(
            smsSendTopic,
            "task-" + campaignTaskId,
            jsonPayload
        );

        smsProducer.send(record, (metadata, error) -> {
            if (error != null) {
                logger.error("Failed to send to {}: campaignTaskId={}",
                    smsSendTopic, campaignTaskId, error);
            } else {
                logger.info("✅ Sent to {} topic: campaignTaskId={}, partition={}, offset={}",
                    smsSendTopic, campaignTaskId, metadata.partition(), metadata.offset());
            }
        });

        smsProducer.flush();
    }

    @Override
    public boolean validate(Map<String, Object> jobData) {
        if (!jobData.containsKey("campaignTaskId")) {
            logger.error("Missing required field: campaignTaskId");
            return false;
        }
        if (!jobData.containsKey("retryTime")) {
            logger.error("Missing required field: retryTime");
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "SmsRetryJobHandler";
    }

    /**
     * Shutdown method to close Kafka producer
     */
    public void shutdown() {
        if (smsProducer != null) {
            logger.info("Closing Kafka producer...");
            smsProducer.close();
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
