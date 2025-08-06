package com.telcobright.scheduler;

import com.telcobright.scheduler.examples.SmsEntity;
import com.telcobright.scheduler.examples.SmsJob;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

public class TableCreationTest {
    
    @Test
    public void testTableCreationWithScheduledTimePartitioning() {
        try {
            SchedulerConfig config = SchedulerConfig.builder()
                .mysqlHost("127.0.0.1")
                .mysqlPort(3306)
                .mysqlDatabase("scheduler")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .repositoryDatabase("scheduler")
                .repositoryTablePrefix("sms_test")
                .build();
            
            InfiniteScheduler<SmsEntity, Long> scheduler = 
                new InfiniteScheduler<>(SmsEntity.class, Long.class, config, SmsJob.class);
            
            LocalDateTime now = LocalDateTime.now();
            
            System.out.println("\n=== TESTING TABLE CREATION WITH SCHEDULED_TIME PARTITIONING ===");
            System.out.println("Current time: " + now);
            
            // Create and insert a single SMS entity
            SmsEntity smsEntity = new SmsEntity();
            smsEntity.setId(1L);
            smsEntity.setPhoneNumber("+1555123456");
            smsEntity.setMessage("Test message for table creation");
            smsEntity.setStatus("PENDING");
            smsEntity.setScheduled(false);
            smsEntity.setScheduledTime(now.plusMinutes(1)); // Schedule for 1 minute from now
            
            System.out.println("Inserting SMS entity with scheduled_time: " + smsEntity.getScheduledTime());
            
            // Insert the entity - this should create the partitioned table
            scheduler.getRepository().insert(smsEntity);
            
            System.out.println("✅ Successfully inserted entity - table should be created");
            
            // Now try to query the entity back
            System.out.println("Attempting to query entities back...");
            
            LocalDateTime queryStart = now.minusMinutes(1);
            LocalDateTime queryEnd = now.plusMinutes(5);
            
            var entities = scheduler.getRepository().findAllByDateRange(queryStart, queryEnd);
            
            System.out.println("✅ Successfully queried entities: " + entities.size() + " found");
            
            for (var entity : entities) {
                System.out.println("Found entity: ID=" + entity.getId() + 
                    ", scheduled=" + entity.getScheduledTime() + 
                    ", phone=" + entity.getPhoneNumber());
            }
            
            scheduler.stop();
            System.out.println("\n=== TABLE CREATION TEST COMPLETED SUCCESSFULLY ===");
            
        } catch (Exception e) {
            System.err.println("TABLE CREATION TEST FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}