package com.telcobright.scheduler.handler.impl;

import com.telcobright.scheduler.handler.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Job handler for SIP Call application.
 * Processes SIP call initiation jobs.
 */
public class SipCallJobHandler implements JobHandler {
    private static final Logger logger = LoggerFactory.getLogger(SipCallJobHandler.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void execute(Map<String, Object> jobData) throws Exception {
        String callTo = (String) jobData.get("callTo");
        String callFrom = (String) jobData.get("callFrom");
        Object durationObj = jobData.get("duration");
        Integer duration = durationObj instanceof Integer ? (Integer) durationObj :
            Integer.parseInt(durationObj.toString());
        String entityId = (String) jobData.get("entityId");
        String appName = (String) jobData.get("appName");

        logger.info("Executing SIP Call job for app '{}', entity: {}", appName, entityId);
        logger.info("Initiating SIP call from {} to {} for {} seconds",
            callFrom, callTo, duration);

        // Simulate SIP call setup
        Thread.sleep(200); // Simulate call setup delay

        // Here you would integrate with actual SIP server
        // SipServer.initiateCall(callFrom, callTo, duration);

        logger.info("SIP call initiated successfully at {}",
            LocalDateTime.now().format(TIME_FORMATTER));
    }

    @Override
    public boolean validate(Map<String, Object> jobData) {
        if (!jobData.containsKey("callTo")) {
            logger.error("Missing required field: callTo");
            return false;
        }
        if (!jobData.containsKey("callFrom")) {
            logger.error("Missing required field: callFrom");
            return false;
        }
        if (!jobData.containsKey("duration")) {
            logger.error("Missing required field: duration");
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "SipCallJobHandler";
    }
}