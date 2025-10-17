package com.telcobright.scheduler.publishers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.scheduler.JobPublisher;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.stream.StreamCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis Stream implementation of JobPublisher.
 * Publishes jobs to Redis streams using Quarkus Redis client.
 */
@ApplicationScoped
public class RedisJobPublisher implements JobPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RedisJobPublisher.class);

    @Inject
    RedisDataSource redisDataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void publish(String topic, Map<String, Object> jobData) {
        try {
            StreamCommands<String, String, String> streamCommands =
                redisDataSource.stream(String.class, String.class, String.class);

            // Convert job data to JSON string
            String json = objectMapper.writeValueAsString(jobData);

            // Prepare stream entry
            Map<String, String> entry = new HashMap<>();
            entry.put("data", json);
            entry.put("jobId", (String) jobData.get("id"));
            entry.put("jobType", (String) jobData.get("jobType"));

            // Add to Redis stream with auto-generated ID (*)
            streamCommands.xadd(topic, entry);

            logger.info("Published job {} to Redis stream: {}", jobData.get("id"), topic);
        } catch (Exception e) {
            logger.error("Failed to publish job to Redis stream: {}", topic, e);
            throw new RuntimeException("Failed to publish job to Redis stream", e);
        }
    }
}
