package com.telcobright.scheduler.handler.impl;

import com.telcobright.scheduler.handler.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Job handler for SMS application.
 * Processes SMS sending jobs.
 */
public class SmsJobHandler implements JobHandler {
    private static final Logger logger = LoggerFactory.getLogger(SmsJobHandler.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void execute(Map<String, Object> jobData) throws Exception {
        String phoneNumber = (String) jobData.get("phoneNumber");
        String message = (String) jobData.get("message");
        String entityId = (String) jobData.get("entityId");
        String appName = (String) jobData.get("appName");

        logger.info("Executing SMS job for app '{}', entity: {}", appName, entityId);
        logger.info("Sending SMS to {} with message: {}", phoneNumber,
            message != null && message.length() > 100 ?
                message.substring(0, 100) + "..." : message);

        // Simulate SMS sending process
        Thread.sleep(100); // Simulate network delay

        // Here you would integrate with actual SMS gateway
        // SmsGateway.send(phoneNumber, message);

        logger.info("SMS sent successfully to {} at {}", phoneNumber,
            LocalDateTime.now().format(TIME_FORMATTER));
    }

    @Override
    public boolean validate(Map<String, Object> jobData) {
        if (!jobData.containsKey("phoneNumber")) {
            logger.error("Missing required field: phoneNumber");
            return false;
        }
        if (!jobData.containsKey("message")) {
            logger.error("Missing required field: message");
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "SmsJobHandler";
    }
}