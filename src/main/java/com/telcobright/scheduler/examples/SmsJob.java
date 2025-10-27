package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.InfiniteScheduler;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class SmsJob implements Job {
    
    private static final Logger logger = LoggerFactory.getLogger(SmsJob.class);
    private static final ZoneId BANGLADESH_TIMEZONE = ZoneId.of("Asia/Dhaka");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // Get entity ID and scheduled time from job data
            String entityIdStr = context.getJobDetail().getJobDataMap().getString("entityId");
            String scheduledTimeStr = context.getJobDetail().getJobDataMap().getString("scheduledTime");
            
            // Get current Bangladesh time for logging
            LocalDateTime nowBD = ZonedDateTime.now(BANGLADESH_TIMEZONE).toLocalDateTime();
            logger.info("🇧🇩 Executing SMS job at {} BD for entity ID: {} (scheduled: {})", 
                nowBD.format(TIME_FORMATTER), entityIdStr, scheduledTimeStr);
            
            // Get scheduler instance from context to access repository
            @SuppressWarnings("unchecked")
            InfiniteScheduler<SmsEntity> scheduler =
                InfiniteScheduler.getFromContext(context.getScheduler().getContext());

            // Entity ID is already a String (UUID)
            String entityId = entityIdStr;

            // Get the entity from repository
            SmsEntity smsEntity = scheduler.getRepository().findById(entityId);
            
            if (smsEntity == null) {
                logger.warn("SMS entity not found for ID: {}", entityId);
                return;
            }
            
            // Execute SMS sending logic with Bangladesh context
            logger.info("📱 Sending SMS to {} with message: {}", 
                smsEntity.getPhoneNumber(), 
                smsEntity.getMessage().length() > 100 ? 
                    smsEntity.getMessage().substring(0, 100) + "..." : smsEntity.getMessage());
            
            // Simulate SMS sending process
            Thread.sleep(100); // Simulate network delay
            
            // Update entity status
            smsEntity.setStatus("SENT");
            
            // Note: Repository save method might not be available
            // The entity status is updated in-memory for this execution
            
            LocalDateTime completedBD = ZonedDateTime.now(BANGLADESH_TIMEZONE).toLocalDateTime();
            logger.info("✅ SMS sent successfully at {} BD for ID: {} to {}", 
                completedBD.format(TIME_FORMATTER), entityId, smsEntity.getPhoneNumber());
                
        } catch (SchedulerException e) {
            logger.error("Failed to get scheduler from context", e);
            throw new JobExecutionException("Failed to get scheduler from context", e);
        } catch (NumberFormatException e) {
            logger.error("Invalid entity ID format", e);
            throw new JobExecutionException("Invalid entity ID format", e);
        } catch (Exception e) {
            logger.error("Error executing SMS job", e);
            throw new JobExecutionException("Error executing SMS job", e);
        }
    }
}