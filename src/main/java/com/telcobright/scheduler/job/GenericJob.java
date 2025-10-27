package com.telcobright.scheduler.job;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.telcobright.scheduler.queue.QueueConfig;
import com.telcobright.scheduler.queue.QueueProducer;
import com.telcobright.scheduler.queue.QueueProducerFactory;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic job executor that produces messages to configured queues.
 * This is the single Job class used by all applications.
 */
public class GenericJob implements Job {
    private static final Logger logger = LoggerFactory.getLogger(GenericJob.class);
    private static final Gson gson = new Gson();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        // Extract metadata
        String appName = dataMap.getString("appName");
        String entityId = dataMap.getString("entityId");
        String jobName = context.getJobDetail().getKey().getName();
        String jobDataJson = dataMap.getString("jobDataJson");

        // Extract queue configuration
        String queueType = dataMap.getString("queueType");
        String topicName = dataMap.getString("topicName");
        String brokerAddress = dataMap.getString("brokerAddress");

        logger.info("Executing job '{}' for app '{}' with entity ID: {}",
            jobName, appName, entityId);

        long startTime = System.currentTimeMillis();

        try {
            // Deserialize job data from JSON
            Map<String, Object> jobData = gson.fromJson(jobDataJson,
                new TypeToken<Map<String, Object>>(){}.getType());

            if (jobData == null) {
                jobData = new HashMap<>();
            }

            // Create queue configuration
            QueueConfig queueConfig = null;
            if (queueType != null && topicName != null) {
                queueConfig = QueueConfig.builder()
                    .queueType(QueueConfig.QueueType.valueOf(queueType))
                    .topicName(topicName)
                    .brokerAddress(brokerAddress != null ? brokerAddress : "localhost:9092")
                    .build();
            } else {
                // Default to mock Kafka for testing
                queueConfig = QueueConfig.builder()
                    .queueType(QueueConfig.QueueType.KAFKA)
                    .topicName(appName + "_topic")
                    .brokerAddress("mock://localhost")
                    .build();
                logger.warn("No queue config provided, using default mock Kafka for app '{}'", appName);
            }

            // Get or create queue producer
            QueueProducer producer = QueueProducerFactory.getProducer(queueConfig);

            // Prepare message payload
            Map<String, Object> message = new HashMap<>();
            message.put("jobId", jobName);
            message.put("appName", appName);
            message.put("entityId", entityId);
            message.put("executionTime", LocalDateTime.now().toString());
            message.put("queueType", queueConfig.getQueueType().toString());
            message.put("topicName", queueConfig.getTopicName());
            message.put("jobParams", jobData);

            // Send message to queue
            boolean sent = producer.send(queueConfig.getTopicName(), message);

            if (sent) {
                long duration = System.currentTimeMillis() - startTime;
                logger.info("✅ Job '{}' for app '{}' produced to queue '{}' successfully in {} ms",
                    jobName, appName, queueConfig.getTopicName(), duration);
            } else {
                throw new JobExecutionException("Failed to send message to queue");
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("❌ Error executing job '{}' for app '{}': {}", jobName, appName, e.getMessage());
            throw new JobExecutionException("Job execution failed: " + e.getMessage(), e);
        }
    }
}