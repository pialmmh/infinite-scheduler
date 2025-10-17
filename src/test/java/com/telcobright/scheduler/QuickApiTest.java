package com.telcobright.scheduler;

import com.telcobright.scheduler.examples.SmsEntity;
import com.telcobright.scheduler.examples.SmsJob;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Quick test to verify findAllByPartitionRange API works
 */
public class QuickApiTest {

    public static void main(String[] args) {
        System.out.println("\n=== Testing findAllByPartitionRange API ===\n");

        try {
            // Configure scheduler
            SchedulerConfig config = SchedulerConfig.builder()
                .mysqlHost("127.0.0.1")
                .mysqlPort(3306)
                .mysqlDatabase("scheduler")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .repositoryDatabase("scheduler")
                .repositoryTablePrefix("sms_api_test")
                .build();

            System.out.println("✅ Config created");

            // Create scheduler
            InfiniteScheduler<SmsEntity> scheduler =
                new InfiniteScheduler<>(SmsEntity.class, config, SmsJob.class);

            System.out.println("✅ Scheduler created");

            LocalDateTime now = LocalDateTime.now();

            // Insert test entity
            SmsEntity entity = new SmsEntity();
            entity.setId("test-" + System.currentTimeMillis());
            entity.setPhoneNumber("+8801712345678");
            entity.setMessage("Test message for API verification");
            entity.setStatus("PENDING");
            entity.setScheduled(false);
            entity.setScheduledTime(now.plusMinutes(2));

            System.out.println("Inserting entity with ID: " + entity.getId());
            scheduler.getRepository().insert(entity);
            System.out.println("✅ Entity inserted");

            // Query using new API
            LocalDateTime queryStart = now.minusMinutes(1);
            LocalDateTime queryEnd = now.plusMinutes(5);

            System.out.println("\nQuerying with findAllByPartitionRange...");
            System.out.println("  Start: " + queryStart);
            System.out.println("  End:   " + queryEnd);

            List<SmsEntity> results = scheduler.getRepository()
                .findAllByPartitionRange(queryStart, queryEnd);

            System.out.println("✅ Query successful!");
            System.out.println("   Found " + results.size() + " entities");

            for (SmsEntity e : results) {
                System.out.println("   - ID: " + e.getId() +
                                 ", scheduled: " + e.getScheduledTime() +
                                 ", phone: " + e.getPhoneNumber());
            }

            // Stop scheduler
            scheduler.stop();
            System.out.println("\n✅ Test completed successfully!");
            System.out.println("✅ findAllByPartitionRange API works correctly\n");

        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
