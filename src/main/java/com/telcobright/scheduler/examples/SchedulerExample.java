package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.InfiniteScheduler;
import com.telcobright.scheduler.SchedulerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SchedulerExample {
    
    private static final Logger logger = LoggerFactory.getLogger(SchedulerExample.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static void main(String[] args) {
        try {
            smsSchedulerExample();
        } catch (Exception e) {
            logger.error("Error running scheduler examples", e);
        }
    }
    
    public static void smsSchedulerExample() throws Exception {
        logger.info("=== SMS Scheduler Example ===");
        
        // Get current Bangladesh local time
        LocalDateTime now = LocalDateTime.now();
        logger.info("Current Bangladesh time: {}", now.format(TIME_FORMATTER));
        
        SchedulerConfig config = SchedulerConfig.builder()
            .fetchInterval(30)              // Fetch every 30 seconds
            .lookaheadWindow(360)           // Look 6 minutes ahead (to capture all 5 jobs)
            .mysqlHost("127.0.0.1")         // MySQL host
            .mysqlPort(3306)               // MySQL port (default: 3306)
            .mysqlDatabase("scheduler")     // Database name
            .mysqlUsername("root")          // MySQL username
            .mysqlPassword("123456")        // MySQL password
            .repositoryDatabase("scheduler") // Repository database (same as main database)
            .repositoryTablePrefix("sms")   // Repository table prefix
            .maxJobsPerFetch(10000)        // Process up to 10K jobs per fetch
            .autoCreateTables(true)        // Auto-create Quartz tables
            .autoCleanupCompletedJobs(true) // Auto-cleanup completed jobs
            .cleanupIntervalMinutes(30)    // Cleanup every 30 minutes
            .build();
        
        // Create scheduler with complete config - repository is created and managed internally
        InfiniteScheduler<SmsEntity, Long> scheduler = 
            new InfiniteScheduler<>(SmsEntity.class, Long.class, config, SmsJob.class);
        
        // Create SMS jobs to be executed in the next 5 minutes
        createSmsJobs(scheduler, now);
        
        scheduler.start();
        
        logger.info("SMS Scheduler started. Jobs will execute in next 5 minutes at Bangladesh local time.");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Shutting down SMS scheduler...");
                scheduler.stop();
            } catch (Exception e) {
                logger.error("Error stopping scheduler", e);
            }
        }));
        
        // Keep running for 10 minutes to see the jobs execute
        Thread.sleep(600000); // 10 minutes
        scheduler.stop();
    }
    
    private static void createSmsJobs(InfiniteScheduler<SmsEntity, Long> scheduler, LocalDateTime now) {
        logger.info("Creating 5 SMS jobs for next 5 minutes (Bangladesh local time will be stored in sms tables)...");
        
        List<SmsEntity> smsJobs = new ArrayList<>();
        
        // Bangladesh phone numbers
        String[] phoneNumbers = {
            "+8801712345678", "+8801812345679", "+8801912345680", "+8801612345681", "+8801512345682"
        };
        
        String[] messages = {
            "SMS 1: Scheduled for " + now.plusMinutes(1).format(TIME_FORMATTER) + " BD",
            "SMS 2: Scheduled for " + now.plusMinutes(2).format(TIME_FORMATTER) + " BD",
            "SMS 3: Scheduled for " + now.plusMinutes(3).format(TIME_FORMATTER) + " BD",
            "SMS 4: Scheduled for " + now.plusMinutes(4).format(TIME_FORMATTER) + " BD",
            "SMS 5: Scheduled for " + now.plusMinutes(5).format(TIME_FORMATTER) + " BD"
        };
        
        for (int i = 0; i < 5; i++) {
            // Schedule jobs: 1 minute apart starting 1 minute from now
            // This will store the EXACT Bangladesh local time in the sms table
            LocalDateTime scheduledTime = now.plusMinutes(i + 1);
            
            SmsEntity smsEntity = new SmsEntity();
            smsEntity.setId((long) (i + 1));
            smsEntity.setPhoneNumber(phoneNumbers[i]);
            smsEntity.setMessage(messages[i]);
            smsEntity.setStatus("PENDING");
            smsEntity.setScheduled(false); // Not yet scheduled to Quartz
            smsEntity.setScheduledTime(scheduledTime); // This should store Bangladesh local time in DB
            
            smsJobs.add(smsEntity);
        }
        
        // Insert all SMS jobs to the repository
        try {
            for (SmsEntity entity : smsJobs) {
                scheduler.getRepository().insert(entity);
                logger.info("✅ Inserted SMS job #{}: {} at {} (Bangladesh local time stored in sms table)", 
                    entity.getId().intValue(), entity.getPhoneNumber(), entity.getScheduledTime().format(TIME_FORMATTER));
            }
            
            logger.info("📅 All jobs inserted with Bangladesh local times: {} to {}", 
                now.plusMinutes(1).format(TIME_FORMATTER), 
                now.plusMinutes(5).format(TIME_FORMATTER));
            logger.info("🇧🇩 These exact times should appear in the sms_YYYYMMDD tables");
            
        } catch (Exception e) {
            logger.error("❌ Failed to insert SMS jobs", e);
        }
    }
}