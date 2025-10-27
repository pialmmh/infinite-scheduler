package com.telcobright.scheduler.job;

import com.telcobright.scheduler.handler.JobHandler;
import com.telcobright.scheduler.handler.JobHandlerRegistry;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic job executor that delegates to app-specific handlers.
 * This is the single Job class used by all applications.
 */
public class GenericJob implements Job {
    private static final Logger logger = LoggerFactory.getLogger(GenericJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        // Extract app name
        String appName = dataMap.getString("appName");
        String entityId = dataMap.getString("entityId");
        String jobName = context.getJobDetail().getKey().getName();

        logger.info("Executing job '{}' for app '{}' with entity ID: {}",
            jobName, appName, entityId);

        try {
            // Get app-specific handler
            JobHandler handler = JobHandlerRegistry.getHandler(appName);

            if (handler == null) {
                logger.error("No handler registered for app: {}", appName);
                throw new JobExecutionException("No handler registered for app: " + appName);
            }

            // Convert JobDataMap to regular Map
            Map<String, Object> jobData = new HashMap<>();
            for (String key : dataMap.getKeys()) {
                jobData.put(key, dataMap.get(key));
            }

            // Validate job data
            if (!handler.validate(jobData)) {
                logger.error("Job data validation failed for app: {}", appName);
                throw new JobExecutionException("Job data validation failed");
            }

            // Execute app-specific logic
            long startTime = System.currentTimeMillis();
            handler.execute(jobData);
            long duration = System.currentTimeMillis() - startTime;

            logger.info("Job '{}' for app '{}' completed successfully in {} ms",
                jobName, appName, duration);

        } catch (Exception e) {
            logger.error("Error executing job '{}' for app '{}'", jobName, appName, e);
            throw new JobExecutionException("Job execution failed: " + e.getMessage(), e);
        }
    }
}