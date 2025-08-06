package com.telcobright.scheduler;

import com.telcobright.scheduler.examples.SmsEntity;
import com.telcobright.scheduler.examples.SmsJob;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ImmediateExecutionTest {
    
    @Test
    public void testImmediateJobExecution() {
        try {
            SchedulerConfig config = SchedulerConfig.builder()
                .fetchInterval(3)               // Fetch every 3 seconds for immediate pickup
                .lookaheadWindow(10)            // Look 10 seconds ahead
                .mysqlHost("127.0.0.1")
                .mysqlPort(3306)
                .mysqlDatabase("scheduler")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .repositoryDatabase("scheduler")
                .repositoryTablePrefix("sms_immediate")
                .maxJobsPerFetch(100)
                .autoCreateTables(true)
                .autoCleanupCompletedJobs(true)
                .cleanupIntervalMinutes(5)
                .build();
            
            InfiniteScheduler<SmsEntity, Long> scheduler = 
                new InfiniteScheduler<>(SmsEntity.class, Long.class, config, SmsJob.class);
            
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            
            System.out.println("\n=== CREATING IMMEDIATE EXECUTION SMS JOBS ===");
            System.out.println("Current time: " + now.format(formatter));
            
            // Create 5 SMS jobs with immediate and near-immediate execution
            String[] messages = {
                "IMMEDIATE: Job executing right now!",
                "QUICK: Job executing in 15 seconds", 
                "FAST: Job executing in 30 seconds",
                "SOON: Job executing in 45 seconds", 
                "LAST: Job executing in 60 seconds"
            };
            
            for (int i = 0; i < 5; i++) {
                LocalDateTime scheduledTime = now.plusSeconds(i * 15); // 0s, 15s, 30s, 45s, 60s
                
                SmsEntity smsEntity = new SmsEntity();
                smsEntity.setId((long) (i + 1));
                smsEntity.setPhoneNumber("+1555000" + String.format("%04d", 100 + i));
                smsEntity.setMessage(messages[i]);
                smsEntity.setStatus("PENDING");
                smsEntity.setScheduled(false);
                smsEntity.setScheduledTime(scheduledTime);
                
                scheduler.getRepository().insert(smsEntity);
                
                System.out.println("Created SMS " + (i + 1) + ": '" + messages[i] + 
                    "' → " + smsEntity.getPhoneNumber() + " at " + scheduledTime.format(formatter));
            }
            
            System.out.println("\n=== STARTING SCHEDULER - WATCH IMMEDIATE EXECUTION ===");
            System.out.println("Monitor will show real-time job pickup and execution...\n");
            
            scheduler.start();
            
            // Run for 2 minutes to see all jobs execute
            Thread.sleep(120000); // 2 minutes
            
            scheduler.stop();
            System.out.println("\n=== IMMEDIATE EXECUTION TEST COMPLETED ===");
            
        } catch (Exception e) {
            System.err.println("IMMEDIATE EXECUTION TEST FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}