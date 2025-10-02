package com.telcobright.scheduler;

import com.telcobright.scheduler.examples.SmsRetryEntity;
import com.telcobright.scheduler.examples.SmsRetryJob;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Long-running test that creates 100 SMS retry jobs spread across 5 hours
 * and verifies they are executed at the correct time with proper retry logic.
 *
 * NOTE: This is a long-running test (5+ hours). For quick validation, reduce the time window.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SmsRetryLongRunningTest {

    private static final Logger logger = LoggerFactory.getLogger(SmsRetryLongRunningTest.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Test configuration
    private static final int TOTAL_SMS_COUNT = 100;
    private static final int TIME_WINDOW_HOURS = 5;
    private static final int MAX_RETRIES = 3;
    private static final double FAILURE_RATE = 0.3; // 30% chance of failure
    private static final int ACCEPTABLE_DELAY_MS = 5000; // 5 seconds tolerance

    // Database configuration
    private static final String DB_HOST = "127.0.0.1";
    private static final String DB_PORT = "3306";
    private static final String DB_NAME = "scheduler_test";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "123456";

    private static InfiniteScheduler<SmsRetryEntity> scheduler;
    private static final Map<String, SmsRetryEntity> scheduledEntities = new ConcurrentHashMap<>();
    private static final Map<String, LocalDateTime> expectedExecutionTimes = new ConcurrentHashMap<>();
    private static LocalDateTime testStartTime;

    @BeforeAll
    public static void setup() throws Exception {
        logger.info("========================================================================");
        logger.info("Starting SMS Retry Long Running Test");
        logger.info("========================================================================");
        logger.info("Test configuration:");
        logger.info("  - Total SMS count: {}", TOTAL_SMS_COUNT);
        logger.info("  - Time window: {} hours", TIME_WINDOW_HOURS);
        logger.info("  - Max retries per SMS: {}", MAX_RETRIES);
        logger.info("  - Simulated failure rate: {}%", (FAILURE_RATE * 100));
        logger.info("  - Acceptable timing delay: {}ms", ACCEPTABLE_DELAY_MS);
        logger.info("========================================================================");

        testStartTime = LocalDateTime.now();

        // Reset job statistics
        SmsRetryJob.resetStats();
        SmsRetryJob.setFailureRate(FAILURE_RATE);
        SmsRetryJob.setExecutionTimeRange(50, 200);

        // Create/clean database
        setupDatabase();

        // Configure scheduler
        SchedulerConfig config = SchedulerConfig.builder()
                .fetchInterval(25)
                .lookaheadWindow(30)
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
                .build();

        // Create scheduler
        scheduler = new InfiniteScheduler<>(SmsRetryEntity.class, config, SmsRetryJob.class);
        scheduler.start();

        logger.info("✅ Scheduler started successfully");
    }

    @Test
    @Order(1)
    public void testCreateAndSchedule100SmsRetries() throws Exception {
        logger.info("========================================================================");
        logger.info("TEST 1: Creating and scheduling 100 SMS retries across {} hours", TIME_WINDOW_HOURS);
        logger.info("========================================================================");

        Random random = new Random();
        int maxOffsetSeconds = TIME_WINDOW_HOURS * 3600;

        for (int i = 0; i < TOTAL_SMS_COUNT; i++) {
            // Generate random future time within the time window
            int offsetSeconds = random.nextInt(maxOffsetSeconds);
            LocalDateTime retryTime = testStartTime.plusSeconds(offsetSeconds);

            // Create SMS retry entity
            String phoneNumber = String.format("+880171%07d", random.nextInt(10000000));
            String message = String.format("Test SMS retry message #%d - Testing infinite scheduler retry logic", i + 1);

            SmsRetryEntity entity = new SmsRetryEntity(phoneNumber, message, retryTime, MAX_RETRIES);

            // Insert into repository (this will be picked up by the scheduler)
            // Note: We need to directly insert into the database for this test
            insertEntityToDatabase(entity);

            // Track scheduled entity
            scheduledEntities.put(entity.getId(), entity);
            expectedExecutionTimes.put(entity.getId(), retryTime);

            if ((i + 1) % 20 == 0) {
                logger.info("Created {}/{} SMS retry entries", i + 1, TOTAL_SMS_COUNT);
            }
        }

        logger.info("✅ Created {} SMS retry entries spread across {} hours", TOTAL_SMS_COUNT, TIME_WINDOW_HOURS);
        logger.info("   Earliest execution: {}", expectedExecutionTimes.values().stream()
                .min(LocalDateTime::compareTo).orElse(null).format(TIME_FORMATTER));
        logger.info("   Latest execution: {}", expectedExecutionTimes.values().stream()
                .max(LocalDateTime::compareTo).orElse(null).format(TIME_FORMATTER));

        // Give scheduler time to pick up the jobs
        Thread.sleep(60000); // 1 minute to ensure first batch is scheduled
    }

    @Test
    @Order(2)
    public void testWaitForAllExecutions() throws Exception {
        logger.info("========================================================================");
        logger.info("TEST 2: Waiting for all SMS retries to execute");
        logger.info("========================================================================");

        LocalDateTime latestExecutionTime = expectedExecutionTimes.values().stream()
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        Duration waitDuration = Duration.between(LocalDateTime.now(), latestExecutionTime)
                .plusMinutes(10); // Extra buffer

        if (waitDuration.isNegative() || waitDuration.toHours() > 6) {
            logger.warn("Wait duration is {} hours - test may take a long time", waitDuration.toHours());
        }

        logger.info("Waiting for {} hours {} minutes for all executions to complete...",
                waitDuration.toHours(), waitDuration.toMinutes() % 60);

        // Monitor progress periodically
        long totalWaitMs = waitDuration.toMillis();
        long checkIntervalMs = 60000; // Check every minute
        long elapsed = 0;

        while (elapsed < totalWaitMs) {
            Thread.sleep(checkIntervalMs);
            elapsed += checkIntervalMs;

            int progress = (int) ((elapsed * 100) / totalWaitMs);
            logger.info("Progress: {}% - Executed: {}/{} (Success: {}, Failed: {})",
                    progress,
                    SmsRetryJob.totalExecutions.get(),
                    TOTAL_SMS_COUNT,
                    SmsRetryJob.successfulExecutions.get(),
                    SmsRetryJob.failedExecutions.get());
        }

        logger.info("✅ Wait period completed");
    }

    @Test
    @Order(3)
    public void testVerifyTimingAccuracy() throws Exception {
        logger.info("========================================================================");
        logger.info("TEST 3: Verifying timing accuracy of executions");
        logger.info("========================================================================");

        // Allow some extra time for final jobs to complete
        Thread.sleep(120000); // 2 minutes buffer

        SmsRetryJob.printStats();

        // Analyze timing accuracy
        int onTimeExecutions = 0;
        int lateExecutions = 0;
        long totalDelayMs = 0;
        long maxDelayMs = 0;
        String mostDelayedJobId = null;

        for (Map.Entry<String, SmsRetryJob.JobExecutionStats> entry : SmsRetryJob.executionStats.entrySet()) {
            SmsRetryJob.JobExecutionStats stats = entry.getValue();
            long delayMs = stats.executionDelayMs;

            totalDelayMs += Math.abs(delayMs);

            if (Math.abs(delayMs) <= ACCEPTABLE_DELAY_MS) {
                onTimeExecutions++;
            } else {
                lateExecutions++;
                logger.warn("Job {} executed with delay: {}ms (scheduled: {}, actual: {})",
                        stats.entityId,
                        delayMs,
                        stats.scheduledTime.format(TIME_FORMATTER),
                        stats.actualExecutionTime.format(TIME_FORMATTER));
            }

            if (Math.abs(delayMs) > maxDelayMs) {
                maxDelayMs = Math.abs(delayMs);
                mostDelayedJobId = stats.entityId;
            }
        }

        int totalExecutions = SmsRetryJob.totalExecutions.get();
        double avgDelayMs = totalExecutions > 0 ? (double) totalDelayMs / totalExecutions : 0;
        double onTimePercentage = totalExecutions > 0 ? (100.0 * onTimeExecutions / totalExecutions) : 0;

        logger.info("========================================================================");
        logger.info("TIMING ACCURACY RESULTS:");
        logger.info("========================================================================");
        logger.info("Total executions: {}", totalExecutions);
        logger.info("On-time executions (within {}ms): {}", ACCEPTABLE_DELAY_MS, onTimeExecutions);
        logger.info("Late executions: {}", lateExecutions);
        logger.info("On-time percentage: {:.2f}%", onTimePercentage);
        logger.info("Average delay: {:.2f}ms", avgDelayMs);
        logger.info("Maximum delay: {}ms (Job: {})", maxDelayMs, mostDelayedJobId);
        logger.info("========================================================================");

        // Assertions
        Assertions.assertTrue(totalExecutions >= TOTAL_SMS_COUNT,
                "Expected at least " + TOTAL_SMS_COUNT + " executions, but got " + totalExecutions);

        Assertions.assertTrue(onTimePercentage >= 80.0,
                "Expected at least 80% on-time executions, but got " + onTimePercentage + "%");

        Assertions.assertTrue(avgDelayMs <= (ACCEPTABLE_DELAY_MS * 2),
                "Average delay too high: " + avgDelayMs + "ms");

        logger.info("✅ Timing accuracy verification passed");
    }

    @Test
    @Order(4)
    public void testVerifyRetryLogic() throws Exception {
        logger.info("========================================================================");
        logger.info("TEST 4: Verifying retry logic");
        logger.info("========================================================================");

        // Count retries
        Map<String, Integer> retryCountPerEntity = new HashMap<>();
        for (Map.Entry<String, SmsRetryJob.JobExecutionStats> entry : SmsRetryJob.executionStats.entrySet()) {
            SmsRetryJob.JobExecutionStats stats = entry.getValue();
            retryCountPerEntity.merge(stats.entityId, 1, Integer::sum);
        }

        int entitiesWithRetries = 0;
        int totalRetries = 0;
        int maxRetriesForAnyEntity = 0;

        for (Map.Entry<String, Integer> entry : retryCountPerEntity.entrySet()) {
            int retries = entry.getValue() - 1; // Subtract original attempt
            if (retries > 0) {
                entitiesWithRetries++;
                totalRetries += retries;
                maxRetriesForAnyEntity = Math.max(maxRetriesForAnyEntity, retries);
            }
        }

        double avgRetriesPerFailedEntity = entitiesWithRetries > 0 ?
                (double) totalRetries / entitiesWithRetries : 0;

        logger.info("========================================================================");
        logger.info("RETRY LOGIC RESULTS:");
        logger.info("========================================================================");
        logger.info("Entities with retries: {}", entitiesWithRetries);
        logger.info("Total retry executions: {}", totalRetries);
        logger.info("Average retries per failed entity: {:.2f}", avgRetriesPerFailedEntity);
        logger.info("Maximum retries for any entity: {}", maxRetriesForAnyEntity);
        logger.info("========================================================================");

        Assertions.assertTrue(maxRetriesForAnyEntity <= MAX_RETRIES,
                "Some entities exceeded max retries: " + maxRetriesForAnyEntity);

        logger.info("✅ Retry logic verification passed");
    }

    @AfterAll
    public static void cleanup() throws Exception {
        logger.info("========================================================================");
        logger.info("Cleaning up test resources");
        logger.info("========================================================================");

        if (scheduler != null) {
            scheduler.stop();
            logger.info("✅ Scheduler stopped");
        }

        Duration testDuration = Duration.between(testStartTime, LocalDateTime.now());
        logger.info("========================================================================");
        logger.info("TEST COMPLETED");
        logger.info("========================================================================");
        logger.info("Total test duration: {} hours {} minutes",
                testDuration.toHours(), testDuration.toMinutes() % 60);
        logger.info("========================================================================");
    }

    // Helper methods
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

    private static void insertEntityToDatabase(SmsRetryEntity entity) throws Exception {
        String jdbcUrl = String.format("jdbc:mysql://%s:%s/%s", DB_HOST, DB_PORT, DB_NAME);

        // First ensure table exists
        try (Connection conn = DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // Create table if not exists
            String createTableSql = "CREATE TABLE IF NOT EXISTS sms_retry_schedules_" +
                    entity.getRetryTime().toLocalDate().toString().replace("-", "") + " (" +
                    "id VARCHAR(255) PRIMARY KEY, " +
                    "retry_time DATETIME NOT NULL, " +
                    "phone_number VARCHAR(20), " +
                    "message TEXT, " +
                    "retry_count INT DEFAULT 0, " +
                    "max_retries INT DEFAULT 3, " +
                    "status VARCHAR(20), " +
                    "scheduled BOOLEAN DEFAULT FALSE, " +
                    "original_scheduled_time DATETIME, " +
                    "last_error TEXT, " +
                    "INDEX idx_retry_time (retry_time), " +
                    "INDEX idx_scheduled (scheduled)" +
                    ")";

            stmt.execute(createTableSql);

            // Insert entity
            String insertSql = String.format(
                    "INSERT INTO sms_retry_schedules_%s " +
                            "(id, retry_time, phone_number, message, retry_count, max_retries, status, scheduled, original_scheduled_time) " +
                            "VALUES ('%s', '%s', '%s', '%s', %d, %d, '%s', %b, '%s')",
                    entity.getRetryTime().toLocalDate().toString().replace("-", ""),
                    entity.getId(),
                    entity.getRetryTime(),
                    entity.getPhoneNumber(),
                    entity.getMessage().replace("'", "''"),
                    entity.getRetryCount(),
                    entity.getMaxRetries(),
                    entity.getStatus(),
                    entity.getScheduled(),
                    entity.getOriginalScheduledTime()
            );

            stmt.execute(insertSql);
        }
    }
}
