package com.telcobright.scheduler.publishers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.scheduler.JobPublisher;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Kafka implementation of JobPublisher.
 * Publishes jobs to Kafka topics using SmallRye Kafka.
 */
@ApplicationScoped
public class KafkaJobPublisher implements JobPublisher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaJobPublisher.class);

    @Inject
    @Channel("jobs-out")
    Emitter<String> emitter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void publish(String topic, Map<String, Object> jobData) {
        try {
            String json = objectMapper.writeValueAsString(jobData);

            // Create metadata to set the dynamic topic
            OutgoingKafkaRecordMetadata<String> metadata =
                OutgoingKafkaRecordMetadata.<String>builder()
                    .withTopic(topic)
                    .withKey((String) jobData.get("id"))
                    .build();

            // Send message with metadata
            emitter.send(Message.of(json).addMetadata(metadata));

            logger.info("Published job {} to Kafka topic: {}", jobData.get("id"), topic);
        } catch (Exception e) {
            logger.error("Failed to publish job to Kafka topic: {}", topic, e);
            throw new RuntimeException("Failed to publish job to Kafka", e);
        }
    }
}
