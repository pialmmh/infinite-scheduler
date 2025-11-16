package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.handler.JobHandler;
import com.telcobright.scheduler.kafka.SmallRyeSmsRetryPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * SmallRye-based SMS Retry Job Handler.
 *
 * When executed:
 * 1. Receives the retry request (campaignTaskId, createdOn, retryTime)
 * 2. Publishes to SMS_Send topic using SmallRye Reactive Messaging
 *
 * This handler is triggered by the scheduler at the specified retryTime.
 */
@ApplicationScoped
public class SmallRyeSmsRetryJobHandler implements JobHandler {

    private static final Logger logger = LoggerFactory.getLogger(SmallRyeSmsRetryJobHandler.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    SmallRyeSmsRetryPublisher smsPublisher;

    @Override
    public void execute(Map<String, Object> jobData) throws Exception {
        Long campaignTaskId = getLong(jobData, "campaignTaskId");
        String createdOn = (String) jobData.get("createdOn");
        String retryTime = (String) jobData.get("retryTime");
        int retryAttempt = getInt(jobData, "retryAttempt", 1);

        logger.info("🔥 Executing SMS retry job:");
        logger.info("   Campaign Task ID: {}", campaignTaskId);
        logger.info("   Created On: {}", createdOn);
        logger.info("   Retry Time: {}", retryTime);
        logger.info("   Actual Execution Time: {}", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        logger.info("   Retry Attempt: {}", retryAttempt);

        // Parse date times
        LocalDateTime createdOnDt = LocalDateTime.parse(createdOn, DATE_TIME_FORMATTER);
        LocalDateTime retryTimeDt = LocalDateTime.parse(retryTime, DATE_TIME_FORMATTER);
        LocalDateTime actualExecutionTime = LocalDateTime.now();

        // Publish to SMS_Send topic using SmallRye
        smsPublisher.publishRetryResult(
            campaignTaskId,
            createdOnDt,
            retryTimeDt,
            actualExecutionTime,
            retryAttempt
        );

        logger.info("✅ SMS retry job completed - Campaign: {}, Published to SMS_Send topic", campaignTaskId);
    }

    @Override
    public boolean validate(Map<String, Object> jobData) {
        if (!jobData.containsKey("campaignTaskId")) {
            logger.error("❌ Missing required field: campaignTaskId");
            return false;
        }
        if (!jobData.containsKey("retryTime")) {
            logger.error("❌ Missing required field: retryTime");
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "SmallRyeSmsRetryJobHandler";
    }

    // Helper methods

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
}
