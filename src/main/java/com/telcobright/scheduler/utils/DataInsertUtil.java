package com.telcobright.scheduler.utils;

import com.telcobright.scheduler.InfiniteScheduler;
import com.telcobright.scheduler.SchedulerConfig;
import com.telcobright.scheduler.examples.SmsEntity;
import com.telcobright.scheduler.examples.SmsJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility to insert SMS records with current date/time for immediate testing
 */
public class DataInsertUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(DataInsertUtil.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static void main(String[] args) {
        try {
            insertImmediateJobsForToday();
        } catch (Exception e) {
            logger.error("Failed to insert immediate jobs", e);
        }
    }
    
    public static void insertImmediateJobsForToday() throws Exception {
        logger.info("=== INSERTING SMS JOBS FOR IMMEDIATE EXECUTION ===");
        
        // Create scheduler config
        SchedulerConfig config = SchedulerConfig.builder()
            .mysqlHost("127.0.0.1")
            .mysqlPort(3306)
            .mysqlDatabase("scheduler")
            .mysqlUsername("root")
            .mysqlPassword("123456")
            .repositoryDatabase("scheduler")
            .repositoryTablePrefix("sms_today")
            .build();
        
        // Create scheduler (just for repository access)
        InfiniteScheduler<SmsEntity> scheduler = 
            new InfiniteScheduler<>(SmsEntity.class, config, SmsJob.class);
        
        LocalDateTime now = LocalDateTime.now();
        logger.info("Current time: {}", now.format(formatter));
        
        // Insert 10 SMS jobs with immediate and near-immediate execution times
        String[] phoneNumbers = {
            "+1555001001", "+1555001002", "+1555001003", "+1555001004", "+1555001005",
            "+1555001006", "+1555001007", "+1555001008", "+1555001009", "+1555001010"
        };
        
        String[] messages = {
            "🚀 URGENT: System maintenance in 5 minutes",
            "📧 Your verification code: 123456", 
            "🛒 Order confirmed: #ORD-2025-001",
            "⏰ Meeting reminder: 3:30 PM today",
            "💰 Payment received: $299.99",
            "📦 Package delivered to your door",
            "🔔 New message from Support Team", 
            "⚡ Flash sale: 50% off today only",
            "📱 App update available now",
            "✅ Task completed successfully"
        };
        
        for (int i = 0; i < 10; i++) {
            // Schedule jobs: first one immediately, then every 20 seconds
            LocalDateTime scheduledTime = now.plusSeconds(i * 20);
            
            SmsEntity smsEntity = new SmsEntity();
            smsEntity.setPhoneNumber(phoneNumbers[i]);
            smsEntity.setMessage(messages[i]);
            smsEntity.setStatus("PENDING");
            smsEntity.setScheduled(false); // Will be set to true when scheduled
            smsEntity.setScheduledTime(scheduledTime); // Current time + offset
            
            // Insert into database
            scheduler.getRepository().insert(smsEntity);
            
            logger.info("Inserted SMS {}: '{}' → {} at {}", 
                i + 1, 
                messages[i], 
                phoneNumbers[i], 
                scheduledTime.format(formatter));
        }
        
        logger.info("Successfully inserted {} SMS jobs for TODAY ({}) starting from current time ({})", 
            10, now.toLocalDate(), now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        logger.info("Jobs will execute every 20 seconds starting immediately");
        logger.info("Run the scheduler now to see immediate execution!");
        
        scheduler.stop(); // Clean shutdown
    }
    
    /**
     * Insert a single SMS job to execute right now
     */
    public static void insertSingleImmediateJob() throws Exception {
        SchedulerConfig config = SchedulerConfig.builder()
            .mysqlHost("127.0.0.1")
            .mysqlPort(3306)
            .mysqlDatabase("scheduler")
            .mysqlUsername("root")
            .mysqlPassword("123456")
            .repositoryDatabase("scheduler")
            .repositoryTablePrefix("sms_now")
            .build();
        
        InfiniteScheduler<SmsEntity> scheduler = 
            new InfiniteScheduler<>(SmsEntity.class, config, SmsJob.class);
        
        LocalDateTime now = LocalDateTime.now();
        
        SmsEntity smsEntity = new SmsEntity();
        smsEntity.setPhoneNumber("+1555999999");
        smsEntity.setMessage("⚡ IMMEDIATE: This SMS executes RIGHT NOW at " + now.format(formatter));
        smsEntity.setStatus("PENDING");
        smsEntity.setScheduled(false);
        smsEntity.setScheduledTime(now.plusSeconds(5)); // Execute in 5 seconds
        
        scheduler.getRepository().insert(smsEntity);
        
        logger.info("🚀 INSERTED IMMEDIATE JOB: Will execute in 5 seconds at {}", 
            smsEntity.getScheduledTime().format(formatter));
        
        scheduler.stop();
    }
}