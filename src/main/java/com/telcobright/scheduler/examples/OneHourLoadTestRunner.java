package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.InfiniteAppScheduler;
import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.handler.impl.SmsJobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Five-hour load test runner (standalone application).
 *
 * This creates and schedules jobs continuously for 5 hours at 120 jobs/minute,
 * then verifies all jobs were executed by checking the database.
 */
public class OneHourLoadTestRunner {

    private static final Logger logger = LoggerFactory.getLogger(OneHourLoadTestRunner.class);

    // MySQL Configuration
    private static final String MYSQL_HOST = "127.0.0.1";
    private static final int MYSQL_PORT = 3306;
    private static final String MYSQL_DATABASE = "scheduler";
    private static final String MYSQL_USERNAME = "root";
    private static final String MYSQL_PASSWORD = "123456";

    // Test Configuration
    private static final int TEST_DURATION_HOURS = 5;
    private static final int JOBS_PER_MINUTE = 120;  // 2 jobs per second
    private static final int BATCH_SIZE = 20;        // Create in batches

    // Job scheduling configuration
    private static final int MIN_DELAY_SECONDS = 5;   // Schedule jobs at least 5 seconds in future
    private static final int MAX_DELAY_SECONDS = 60;  // Schedule jobs up to 1 minute in future

    public static void main(String[] args) throws Exception {
        logger.info("=".repeat(100));
        logger.info("Starting 5-Hour Load Test for Infinite Scheduler");
        logger.info("=".repeat(100));
        logger.info("Configuration:");
        logger.info("  MySQL Host:        {}:{}/{}", MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE);
        logger.info("  Test Duration:     {} hour(s)", TEST_DURATION_HOURS);
        logger.info("  Jobs Per Minute:   {}", JOBS_PER_MINUTE);
        logger.info("  Total Expected:    {} jobs", JOBS_PER_MINUTE * 60 * TEST_DURATION_HOURS);
        logger.info("  Schedule Window:   {} - {} seconds ahead", MIN_DELAY_SECONDS, MAX_DELAY_SECONDS);
        logger.info("  Queue Type:        MOCK (console output)");
        logger.info("=".repeat(100));

        // Track created jobs
        AtomicInteger jobsCreated = new AtomicInteger(0);

        // Create Multi-App Scheduler Manager
        MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
            .mysqlHost(MYSQL_HOST)
            .mysqlPort(MYSQL_PORT)
            .mysqlDatabase(MYSQL_DATABASE)
            .mysqlUsername(MYSQL_USERNAME)
            .mysqlPassword(MYSQL_PASSWORD)
            .build();

        // Register SMS app with mock queue producer
        SmsJobHandler smsHandler = new SmsJobHandler();
        manager.registerApp("sms", smsHandler);

        logger.info("");
        logger.info("Starting scheduler...");
        manager.startAll();
        logger.info("✅ Scheduler started successfully");
        logger.info("");

        // Calculate test duration
        long testDurationMs = TimeUnit.HOURS.toMillis(TEST_DURATION_HOURS);
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDurationMs;

        // Calculate interval between batches
        long batchIntervalMs = (60 * 1000) / (JOBS_PER_MINUTE / BATCH_SIZE);

        logger.info("Starting job creation (batch every {}ms)...", batchIntervalMs);
        logger.info("");

        Random random = new Random();
        long nextBatchTime = startTime;
        int batchNumber = 0;

        try {
            while (System.currentTimeMillis() < endTime) {
                long now = System.currentTimeMillis();

                // Wait until next batch time
                if (now < nextBatchTime) {
                    Thread.sleep(nextBatchTime - now);
                }

                // Create a batch of jobs
                for (int i = 0; i < BATCH_SIZE; i++) {
                    int jobNum = jobsCreated.incrementAndGet();

                    // Random delay within configured window
                    int delaySeconds = MIN_DELAY_SECONDS + random.nextInt(MAX_DELAY_SECONDS - MIN_DELAY_SECONDS);
                    LocalDateTime scheduledTime = LocalDateTime.now().plusSeconds(delaySeconds);

                    // Create job data
                    Map<String, Object> jobData = new HashMap<>();
                    jobData.put("jobNumber", jobNum);
                    jobData.put("phoneNumber", "+880171234" + String.format("%04d", jobNum % 10000));
                    jobData.put("message", "Load test message #" + jobNum);
                    jobData.put("testStartTime", startTime);
                    jobData.put("batchNumber", batchNumber);
                    jobData.put("scheduledTime", scheduledTime);

                    // Schedule job
                    manager.scheduleJob("sms", jobData);
                }

                batchNumber++;

                // Log progress every 10 batches (every ~10 seconds)
                if (batchNumber % 10 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    long remaining = endTime - System.currentTimeMillis();
                    int created = jobsCreated.get();

                    logger.info("Progress: Created={}, Elapsed={}, Remaining={}",
                        created,
                        formatDuration(elapsed),
                        formatDuration(remaining)
                    );
                }

                // Schedule next batch
                nextBatchTime += batchIntervalMs;
            }

            logger.info("");
            logger.info("=".repeat(100));
            logger.info("Job creation completed!");
            logger.info("  Total jobs created: {}", jobsCreated.get());
            logger.info("=".repeat(100));

            // Wait for all jobs to be executed
            // Maximum wait = max scheduled delay + some buffer
            int maxWaitMinutes = (MAX_DELAY_SECONDS / 60) + 5;
            logger.info("Waiting up to {} minutes for all jobs to execute...", maxWaitMinutes);

            long waitStartTime = System.currentTimeMillis();
            long maxWaitMs = TimeUnit.MINUTES.toMillis(maxWaitMinutes);

            // Monitor execution progress
            String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE
            );

            int lastCompleted = 0;
            int noProgressCount = 0;

            while (System.currentTimeMillis() - waitStartTime < maxWaitMs) {
                Thread.sleep(10000); // Check every 10 seconds

                // Query database for execution stats
                Map<String, Integer> statusCounts = getJobStatusCounts(jdbcUrl, MYSQL_USERNAME, MYSQL_PASSWORD);
                int completed = statusCounts.getOrDefault("COMPLETED", 0);
                int failed = statusCounts.getOrDefault("FAILED", 0);
                int scheduled = statusCounts.getOrDefault("SCHEDULED", 0);
                int started = statusCounts.getOrDefault("STARTED", 0);

                logger.info("Execution progress: Completed={}, Failed={}, Scheduled={}, Started={}",
                    completed, failed, scheduled, started);

                // Check if all jobs are done (completed or failed)
                if (completed + failed >= jobsCreated.get()) {
                    logger.info("✅ All jobs have been processed!");
                    break;
                }

                // Check for progress
                if (completed == lastCompleted) {
                    noProgressCount++;
                    if (noProgressCount >= 6) { // 1 minute without progress
                        logger.warn("⚠️ No progress for 1 minute. Current: {}/{}", completed, jobsCreated.get());
                    }
                } else {
                    noProgressCount = 0;
                }
                lastCompleted = completed;
            }

            // Stop scheduler
            manager.stopAll();
            logger.info("Scheduler stopped");

            // Final statistics
            logger.info("");
            logger.info("=".repeat(100));
            logger.info("5-HOUR LOAD TEST RESULTS");
            logger.info("=".repeat(100));

            Map<String, Integer> finalStatusCounts = getJobStatusCounts(jdbcUrl, MYSQL_USERNAME, MYSQL_PASSWORD);
            int finalCompleted = finalStatusCounts.getOrDefault("COMPLETED", 0);
            int finalFailed = finalStatusCounts.getOrDefault("FAILED", 0);
            int finalScheduled = finalStatusCounts.getOrDefault("SCHEDULED", 0);

            logger.info("Jobs Created:         {}", jobsCreated.get());
            logger.info("Jobs Completed:       {}", finalCompleted);
            logger.info("Jobs Failed:          {}", finalFailed);
            logger.info("Jobs Still Scheduled: {}", finalScheduled);
            logger.info("Success Rate:         {:.2f}%",
                (finalCompleted * 100.0) / jobsCreated.get());
            logger.info("Test Duration:        {}", formatDuration(System.currentTimeMillis() - startTime));
            logger.info("=".repeat(100));

            // Print result
            if (finalCompleted < jobsCreated.get()) {
                logger.warn("⚠️ Not all jobs completed! Created: {}, Completed: {}",
                    jobsCreated.get(), finalCompleted);
                System.exit(1);
            } else {
                logger.info("✅ TEST PASSED: All {} jobs were successfully executed!", jobsCreated.get());
                System.exit(0);
            }

        } catch (Exception e) {
            logger.error("Test failed with exception", e);
            manager.stopAll();
            throw e;
        }
    }

    private static String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private static Map<String, Integer> getJobStatusCounts(String jdbcUrl, String username, String password) {
        Map<String, Integer> counts = new HashMap<>();
        String tableName = "sms_job_execution_history";

        String sql = "SELECT status, COUNT(*) as count FROM " + tableName +
                    " GROUP BY status";

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                counts.put(rs.getString("status"), rs.getInt("count"));
            }

        } catch (SQLException e) {
            logger.error("Error querying job status counts", e);
        }

        return counts;
    }
}
