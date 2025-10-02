package com.telcobright.scheduler.examples;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock SMS retry job that simulates SMS sending with random failures and retry logic.
 * Tracks execution statistics for testing and verification.
 */
public class SmsRetryJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(SmsRetryJob.class);
    private static final ZoneId BANGLADESH_TIMEZONE = ZoneId.of("Asia/Dhaka");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Random random = new Random();

    // Statistics tracking (shared across all job instances)
    public static final AtomicInteger totalExecutions = new AtomicInteger(0);
    public static final AtomicInteger successfulExecutions = new AtomicInteger(0);
    public static final AtomicInteger failedExecutions = new AtomicInteger(0);
    public static final ConcurrentHashMap<String, JobExecutionStats> executionStats = new ConcurrentHashMap<>();

    // Configuration for mock behavior
    private static double failureRate = 0.3; // 30% failure rate by default
    private static int minExecutionTimeMs = 50;
    private static int maxExecutionTimeMs = 200;

    public static class JobExecutionStats {
        public String entityId;
        public LocalDateTime scheduledTime;
        public LocalDateTime actualExecutionTime;
        public long executionDelayMs;
        public boolean success;
        public String errorMessage;
        public int retryCount;

        public JobExecutionStats(String entityId, LocalDateTime scheduledTime, LocalDateTime actualExecutionTime,
                                 long executionDelayMs, boolean success, String errorMessage, int retryCount) {
            this.entityId = entityId;
            this.scheduledTime = scheduledTime;
            this.actualExecutionTime = actualExecutionTime;
            this.executionDelayMs = executionDelayMs;
            this.success = success;
            this.errorMessage = errorMessage;
            this.retryCount = retryCount;
        }
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        totalExecutions.incrementAndGet();

        try {
            // Get entity data from job context
            String entityId = context.getJobDetail().getJobDataMap().getString("entityId");
            String scheduledTimeStr = context.getJobDetail().getJobDataMap().getString("scheduledTime");
            String phoneNumber = context.getJobDetail().getJobDataMap().getString("phoneNumber");
            String message = context.getJobDetail().getJobDataMap().getString("message");
            int retryCount = context.getJobDetail().getJobDataMap().getInt("retryCount");
            int maxRetries = context.getJobDetail().getJobDataMap().getInt("maxRetries");

            // Parse scheduled time
            LocalDateTime scheduledTime = LocalDateTime.parse(scheduledTimeStr);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nowBD = ZonedDateTime.now(BANGLADESH_TIMEZONE).toLocalDateTime();

            // Calculate execution delay
            long delayMs = Duration.between(scheduledTime, now).toMillis();

            logger.info("📱 [RETRY-{}] Executing SMS retry job at {} BD for entity: {} (scheduled: {}, delay: {}ms)",
                    retryCount, nowBD.format(TIME_FORMATTER), entityId, scheduledTime.format(TIME_FORMATTER), delayMs);

            // Simulate SMS sending with variable execution time
            int executionTime = minExecutionTimeMs + random.nextInt(maxExecutionTimeMs - minExecutionTimeMs);
            Thread.sleep(executionTime);

            // Simulate random failures
            boolean shouldFail = random.nextDouble() < failureRate;

            if (shouldFail && retryCount < maxRetries) {
                // Simulate failure
                String errorMsg = "SMSC connection timeout (simulated failure)";
                logger.error("❌ [RETRY-{}] SMS sending failed for {}: {} - Will retry", retryCount, phoneNumber, errorMsg);

                // Record failure stats
                executionStats.put(entityId + "-retry" + retryCount,
                        new JobExecutionStats(entityId, scheduledTime, now, delayMs, false, errorMsg, retryCount));

                failedExecutions.incrementAndGet();

                // Don't throw exception - let the scheduler handle retry scheduling
            } else {
                // Success case (either succeeded or max retries reached)
                logger.info("✅ [RETRY-{}] SMS sent successfully to {} at {} BD (delay: {}ms, exec: {}ms)",
                        retryCount, phoneNumber, nowBD.format(TIME_FORMATTER), delayMs, executionTime);

                if (message != null && message.length() > 50) {
                    logger.info("   Message: {}...", message.substring(0, 50));
                } else {
                    logger.info("   Message: {}", message);
                }

                // Record success stats
                executionStats.put(entityId + "-retry" + retryCount,
                        new JobExecutionStats(entityId, scheduledTime, now, delayMs, true, null, retryCount));

                successfulExecutions.incrementAndGet();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Job execution interrupted", e);
            throw new JobExecutionException("Job execution interrupted", e);
        } catch (Exception e) {
            logger.error("Error executing SMS retry job", e);
            failedExecutions.incrementAndGet();
            throw new JobExecutionException("Error executing SMS retry job", e);
        }
    }

    // Static methods to configure mock behavior
    public static void setFailureRate(double rate) {
        if (rate < 0 || rate > 1) {
            throw new IllegalArgumentException("Failure rate must be between 0 and 1");
        }
        failureRate = rate;
    }

    public static void setExecutionTimeRange(int minMs, int maxMs) {
        if (minMs < 0 || maxMs < minMs) {
            throw new IllegalArgumentException("Invalid execution time range");
        }
        minExecutionTimeMs = minMs;
        maxExecutionTimeMs = maxMs;
    }

    public static void resetStats() {
        totalExecutions.set(0);
        successfulExecutions.set(0);
        failedExecutions.set(0);
        executionStats.clear();
    }

    public static void printStats() {
        logger.info("==================== SMS Retry Job Statistics ====================");
        logger.info("Total executions: {}", totalExecutions.get());
        logger.info("Successful: {}", successfulExecutions.get());
        logger.info("Failed: {}", failedExecutions.get());
        logger.info("Success rate: {}%",
                totalExecutions.get() > 0 ? (100.0 * successfulExecutions.get() / totalExecutions.get()) : 0);
        logger.info("==================================================================");
    }
}
