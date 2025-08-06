package com.telcobright.scheduler;

import com.telcobright.scheduler.examples.SmsDemo;
import com.telcobright.scheduler.examples.SmsEntity;
import com.telcobright.scheduler.examples.SmsJob;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class BangladeshTimezoneTest {
    
    private static final ZoneId BANGLADESH_TIMEZONE = ZoneId.of("Asia/Dhaka");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Test
    public void testBangladeshTimezoneDemo() {
        try {
            LocalDateTime nowBD = LocalDateTime.now(BANGLADESH_TIMEZONE);
            LocalDateTime nowSystem = LocalDateTime.now();
            
            System.out.println("\n=== BANGLADESH TIMEZONE TEST ===");
            System.out.println("System Time:     " + nowSystem.format(formatter));
            System.out.println("Bangladesh Time: " + nowBD.format(formatter) + " GMT+6");
            System.out.println("Timezone Offset: " + BANGLADESH_TIMEZONE.getId());
            
            // Test utility methods from SmsDemo
            LocalDateTime bdTime = SmsDemo.getBangladeshTime();
            String formattedTime = SmsDemo.formatBangladeshTime(bdTime);
            
            System.out.println("Demo BD Time:    " + formattedTime);
            
            // Create a quick scheduler config test
            SchedulerConfig config = SchedulerConfig.builder()
                .fetchInterval(60)
                .lookaheadWindow(300)
                .mysqlHost("127.0.0.1")
                .mysqlPort(3306)
                .mysqlDatabase("scheduler")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .repositoryDatabase("scheduler")
                .repositoryTablePrefix("sms_bd_test")
                .build();
            
            InfiniteScheduler<SmsEntity, Long> scheduler = 
                new InfiniteScheduler<>(SmsEntity.class, Long.class, config, SmsJob.class);
            
            // Create a test entity with Bangladesh time
            SmsEntity testEntity = new SmsEntity();
            testEntity.setId(999L);
            testEntity.setPhoneNumber("+8801712345678");
            testEntity.setMessage("🇧🇩 Test SMS with Bangladesh timezone");
            testEntity.setStatus("PENDING");
            testEntity.setScheduled(false);
            testEntity.setScheduledTime(bdTime.plusMinutes(2)); // 2 minutes from now
            
            System.out.println("\nCreated test SMS entity:");
            System.out.println("ID: " + testEntity.getId());
            System.out.println("Phone: " + testEntity.getPhoneNumber());
            System.out.println("Message: " + testEntity.getMessage());
            System.out.println("Scheduled Time: " + testEntity.getScheduledTime().format(formatter) + " BD");
            
            // Insert the entity (this will create the table)
            scheduler.getRepository().insert(testEntity);
            System.out.println("✅ Successfully inserted entity with Bangladesh timezone");
            
            // Query it back
            LocalDateTime queryStart = bdTime.minusMinutes(1);
            LocalDateTime queryEnd = bdTime.plusMinutes(5);
            var entities = scheduler.getRepository().findAllByDateRange(queryStart, queryEnd);
            
            System.out.println("✅ Found " + entities.size() + " entities in date range");
            
            scheduler.stop();
            System.out.println("\n=== BANGLADESH TIMEZONE TEST COMPLETED ===");
            
        } catch (Exception e) {
            System.err.println("BANGLADESH TIMEZONE TEST FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}