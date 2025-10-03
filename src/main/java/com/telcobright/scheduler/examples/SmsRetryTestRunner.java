package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.InfiniteScheduler;
import com.telcobright.scheduler.SchedulerConfig;
import com.telcobright.api.ShardingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * Standalone runner to test SMS retry scheduling with UI
 */
public class SmsRetryTestRunner {

    private static final Logger logger = LoggerFactory.getLogger(SmsRetryTestRunner.class);

    // Database configuration
    private static final String DB_HOST = "127.0.0.1";
    private static final String DB_PORT = "3306";
    private static final String DB_NAME = "scheduler_test";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "123456";

    public static void main(String[] args) throws Exception {
        logger.info("========================================================================");
        logger.info("Starting SMS Retry Test with UI");
        logger.info("========================================================================");

        // Setup database
        setupDatabase();

        // Configure scheduler with UI enabled
        SchedulerConfig config = SchedulerConfig.builder()
                .fetchInterval(10)  // Faster fetch for testing
                .lookaheadWindow(15)  // Shorter lookahead
                .mysqlHost(DB_HOST)
                .mysqlPort(Integer.parseInt(DB_PORT))
                .mysqlDatabase(DB_NAME)
                .mysqlUsername(DB_USER)
                .mysqlPassword(DB_PASSWORD)
                .repositoryDatabase(DB_NAME)
                .repositoryTablePrefix("sms_retry")
                .maxJobsPerFetch(50)
                .autoCreateTables(true)
                .autoCleanupCompletedJobs(false) // Keep jobs for verification
                .cleanupIntervalMinutes(120)
                .threadPoolSize(10)
                .enableUI(true)  // Enable UI for monitoring
                .uiPort(9000)    // Default UI port
                .build();

        // Create scheduler
        InfiniteScheduler<SmsRetryEntity> scheduler =
            new InfiniteScheduler<>(SmsRetryEntity.class, config, SmsRetryJob.class);

        // Start mock job statistics
        SmsRetryJob.resetStats();
        SmsRetryJob.setFailureRate(0.2); // 20% failure rate
        SmsRetryJob.setExecutionTimeRange(50, 200);

        scheduler.start();

        logger.info("✅ Scheduler started successfully");
        logger.info("========================================================================");
        logger.info("🌐 Web UI available at: http://localhost:9000");
        logger.info("========================================================================");

        // Insert test SMS retry entities using scheduler's repository
        insertTestData(scheduler);

        logger.info("========================================================================");
        logger.info("📊 Test data inserted. Monitor jobs at: http://localhost:9000");
        logger.info("========================================================================");
        logger.info("Press Ctrl+C to stop the scheduler");

        // Keep running
        Thread.sleep(Long.MAX_VALUE);
    }

    private static void setupDatabase() throws Exception {
        String jdbcUrl = String.format("jdbc:mysql://%s:%s/", DB_HOST, DB_PORT);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // Drop and create database
            stmt.execute("DROP DATABASE IF EXISTS " + DB_NAME);
            stmt.execute("CREATE DATABASE " + DB_NAME);
            logger.info("✅ Database '{}' created", DB_NAME);
        }
    }

    private static void insertTestData(InfiniteScheduler<SmsRetryEntity> scheduler) throws Exception {
        Random random = new Random();
        LocalDateTime now = LocalDateTime.now();

        // Get repository from scheduler
        ShardingRepository<SmsRetryEntity, LocalDateTime> repository = scheduler.getRepository();

        // Create 20 SMS retry entities spread across next 5 minutes
        for (int i = 0; i < 20; i++) {
            int offsetSeconds = random.nextInt(300); // 0-5 minutes
            LocalDateTime retryTime = now.plusSeconds(offsetSeconds);

            String phoneNumber = String.format("+880171%07d", random.nextInt(10000000));
            String message = String.format("Test SMS retry message #%d - Testing infinite scheduler", i + 1);

            SmsRetryEntity entity = new SmsRetryEntity(phoneNumber, message, retryTime, 3);

            // Insert using repository
            repository.insert(entity);

            logger.info("Created SMS retry entity {} for {}", entity.getId(), retryTime);
        }

        logger.info("✅ Inserted 20 test SMS retry entities");
    }
}
