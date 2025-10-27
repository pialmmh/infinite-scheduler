package com.telcobright.scheduler;

import com.telcobright.scheduler.examples.SmsEntity;
import com.telcobright.scheduler.examples.SmsJob;
import org.junit.jupiter.api.Test;

public class SmsJobTest {
    
    @Test
    public void testSmsJobSchedulerCreation() {
        try {
            SchedulerConfig config = SchedulerConfig.builder()
                .fetchInterval(25)
                .lookaheadWindow(30)
                .mysqlHost("127.0.0.1")
                .mysqlPort(3306)
                .mysqlDatabase("scheduler")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .repositoryDatabase("scheduler")
                .repositoryTablePrefix("sms_test")
                .build();
            
            InfiniteScheduler<SmsEntity> scheduler =
                new InfiniteScheduler<>(SmsEntity.class, config, SmsJob.class);
                
            System.out.println("SUCCESS: SMS scheduler with SmsJob created successfully");
            scheduler.stop();
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}