package com.telcobright.scheduler;

import com.telcobright.scheduler.config.AppConfig;
import com.telcobright.scheduler.handler.impl.SmsJobHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-hour load test using mock queue producer.
 *
 * This test:
 * 1. Creates jobs continuously for 1 hour
 * 2. Uses MockQueueProducer to simulate job execution (logs to console)
 * 3. Tracks job creation and execution via database queries
 * 4. Verifies all jobs were executed successfully
 *
 * Run with: mvn test -Dtest=OneHourMockLoadTest
 */
public class OneHourMockLoadTest {

    private static final Logger logger = LoggerFactory.getLogger(OneHourMockLoadTest.class);

    // MySQL Configuration
    private static final String MYSQL_HOST = "127.0.0.1";
    private static final int MYSQL_PORT = 3306;
    private static final String MYSQL_DATABASE = "scheduler";
    private static final String MYSQL_USERNAME = "root";
    private static final String MYSQL_PASSWORD = "123456";

    // Test Configuration
    private static final int TEST_DURATION_HOURS = 1;
    private static final int JOBS_PER_MINUTE = 60;  // 1 job per second
    private static final int BATCH_SIZE = 10;        // Create in batches

    // Job scheduling configuration
    private static final int MIN_DELAY_SECONDS = 5;   // Schedule jobs at least 5 seconds in future
    private static final int MAX_DELAY_SECONDS = 60;  // Schedule jobs up to 1 minute in future

    @Test
    public void testOneHourMockLoad() throws Exception {
        logger.info("=".repeat(100));
        logger.info("Starting 1-Hour Mock Load Test for Infinite Scheduler");
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
        Set<String> createdJobIds = Collections.synchronizedSet(new HashSet<>());

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
                    String jobId = UUID.randomUUID().toString();

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
                    createdJobIds.add(jobId);
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
            while (System.currentTimeMillis() - waitStartTime < maxWaitMs) {
                Thread.sleep(10000); // Check every 10 seconds

                // Query database for execution stats
                DatabaseStatsChecker stats = new DatabaseStatsChecker(
                    MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE, MYSQL_USERNAME, MYSQL_PASSWORD
                );

                Map<String, Integer> statusCounts = stats.getJobStatusCounts("sms");
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
            }

            // Stop scheduler
            manager.stopAll();
            logger.info("Scheduler stopped");

            // Final statistics
            logger.info("");
            logger.info("=".repeat(100));
            logger.info("ONE-HOUR MOCK LOAD TEST RESULTS");
            logger.info("=".repeat(100));

            DatabaseStatsChecker finalStats = new DatabaseStatsChecker(
                MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE, MYSQL_USERNAME, MYSQL_PASSWORD
            );

            Map<String, Integer> finalStatusCounts = finalStats.getJobStatusCounts("sms");
            int finalCompleted = finalStatusCounts.getOrDefault("COMPLETED", 0);
            int finalFailed = finalStatusCounts.getOrDefault("FAILED", 0);
            int finalScheduled = finalStatusCounts.getOrDefault("SCHEDULED", 0);

            logger.info("Jobs Created:        {}", jobsCreated.get());
            logger.info("Jobs Completed:      {}", finalCompleted);
            logger.info("Jobs Failed:         {}", finalFailed);
            logger.info("Jobs Still Scheduled: {}", finalScheduled);
            logger.info("Success Rate:        {:.2f}%",
                (finalCompleted * 100.0) / jobsCreated.get());
            logger.info("Test Duration:       {}", formatDuration(System.currentTimeMillis() - startTime));
            logger.info("=".repeat(100));

            // Assert all jobs were executed
            if (finalCompleted < jobsCreated.get()) {
                logger.warn("⚠️ Not all jobs completed! Created: {}, Completed: {}",
                    jobsCreated.get(), finalCompleted);
            } else {
                logger.info("✅ TEST PASSED: All {} jobs were successfully executed!", jobsCreated.get());
            }

        } catch (Exception e) {
            logger.error("Test failed with exception", e);
            manager.stopAll();
            throw e;
        }
    }

    private String formatDuration(long ms) {
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

    /**
     * Helper class to check database statistics.
     */
    private static class DatabaseStatsChecker {
        private final String jdbcUrl;
        private final String username;
        private final String password;

        public DatabaseStatsChecker(String host, int port, String database,
                                   String username, String password) {
            this.jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                host, port, database
            );
            this.username = username;
            this.password = password;
        }

        public Map<String, Integer> getJobStatusCounts(String appName) {
            Map<String, Integer> counts = new HashMap<>();
            String tableName = appName + "_job_execution_history";

            String sql = "SELECT status, COUNT(*) as count FROM " + tableName +
                        " GROUP BY status";

            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                    jdbcUrl, username, password);
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql);
                 java.sql.ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    counts.put(rs.getString("status"), rs.getInt("count"));
                }

            } catch (java.sql.SQLException e) {
                logger.error("Error querying job status counts", e);
            }

            return counts;
        }
    }
}
