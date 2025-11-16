package com.telcobright.scheduler;

import com.telcobright.scheduler.kafka.ScheduleJobRequest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-hour load test for Infinite Scheduler.
 *
 * This test:
 * 1. Sends jobs to Kafka input topic for 1 hour
 * 2. Tracks all job IDs sent
 * 3. Uses KafkaOutputVerifier to verify all jobs were executed
 *
 * Run with: mvn test -Dtest=OneHourLoadTest
 */
public class OneHourLoadTest {

    private static final Logger logger = LoggerFactory.getLogger(OneHourLoadTest.class);

    // Kafka Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092";
    private static final String INPUT_TOPIC = "link3_sms_scheduler_in";
    private static final String OUTPUT_TOPIC = "link3_sms_scheduler_out";

    // Test Configuration
    private static final int TEST_DURATION_HOURS = 1;
    private static final int JOBS_PER_MINUTE = 60;  // 1 job per second
    private static final int BATCH_SIZE = 10;        // Send in batches

    // Job scheduling configuration
    private static final int MIN_DELAY_SECONDS = 5;   // Schedule jobs at least 5 seconds in future
    private static final int MAX_DELAY_SECONDS = 120; // Schedule jobs up to 2 minutes in future

    @Test
    public void testOneHourLoad() throws Exception {
        logger.info("=".repeat(100));
        logger.info("Starting 1-Hour Load Test for Infinite Scheduler");
        logger.info("=".repeat(100));
        logger.info("Configuration:");
        logger.info("  Input Topic:       {}", INPUT_TOPIC);
        logger.info("  Output Topic:      {}", OUTPUT_TOPIC);
        logger.info("  Test Duration:     {} hour(s)", TEST_DURATION_HOURS);
        logger.info("  Jobs Per Minute:   {}", JOBS_PER_MINUTE);
        logger.info("  Total Expected:    {} jobs", JOBS_PER_MINUTE * 60 * TEST_DURATION_HOURS);
        logger.info("  Schedule Window:   {} - {} seconds ahead", MIN_DELAY_SECONDS, MAX_DELAY_SECONDS);
        logger.info("=".repeat(100));

        // Track sent jobs
        Set<String> sentJobIds = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger jobCounter = new AtomicInteger(0);

        // Create Kafka producer
        KafkaProducer<String, String> producer = createProducer();

        // Start output verifier in background thread
        KafkaOutputVerifier verifier = new KafkaOutputVerifier(
            KAFKA_BOOTSTRAP_SERVERS,
            OUTPUT_TOPIC,
            sentJobIds
        );
        Thread verifierThread = new Thread(verifier, "Output-Verifier");
        verifierThread.start();

        logger.info("Started Kafka output verifier");
        Thread.sleep(2000); // Give verifier time to connect

        // Calculate test duration
        long testDurationMs = TimeUnit.HOURS.toMillis(TEST_DURATION_HOURS);
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDurationMs;

        // Calculate interval between batches
        long batchIntervalMs = (60 * 1000) / (JOBS_PER_MINUTE / BATCH_SIZE);

        logger.info("Starting job generation (batch every {}ms)...", batchIntervalMs);
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

                // Send a batch of jobs
                for (int i = 0; i < BATCH_SIZE; i++) {
                    int jobNum = jobCounter.incrementAndGet();
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

                    // Create schedule request
                    ScheduleJobRequest request = new ScheduleJobRequest(
                        "sms_retry",
                        scheduledTime,
                        jobData
                    );
                    request.setIdempotencyKey("loadtest-" + jobId);
                    request.setPriority("normal");

                    // Track this job ID
                    sentJobIds.add(request.getIdempotencyKey());

                    // Send to Kafka
                    ProducerRecord<String, String> record = new ProducerRecord<>(
                        INPUT_TOPIC,
                        jobId,
                        request.toJson()
                    );

                    producer.send(record);
                }

                producer.flush();
                batchNumber++;

                // Log progress every 10 batches (every ~10 seconds)
                if (batchNumber % 10 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    long remaining = endTime - System.currentTimeMillis();
                    int jobsSent = jobCounter.get();
                    int jobsVerified = verifier.getVerifiedCount();

                    logger.info("Progress: Sent={}, Verified={}, Pending={}, Elapsed={}, Remaining={}",
                        jobsSent,
                        jobsVerified,
                        jobsSent - jobsVerified,
                        formatDuration(elapsed),
                        formatDuration(remaining)
                    );
                }

                // Schedule next batch
                nextBatchTime += batchIntervalMs;
            }

            logger.info("");
            logger.info("=".repeat(100));
            logger.info("Job generation completed!");
            logger.info("  Total jobs sent: {}", jobCounter.get());
            logger.info("=".repeat(100));

            // Flush and close producer
            producer.flush();
            producer.close();

            // Wait for all jobs to be executed
            // Maximum wait = max scheduled delay + some buffer
            int maxWaitMinutes = (MAX_DELAY_SECONDS / 60) + 5;
            logger.info("Waiting up to {} minutes for all jobs to execute...", maxWaitMinutes);

            long waitStartTime = System.currentTimeMillis();
            long maxWaitMs = TimeUnit.MINUTES.toMillis(maxWaitMinutes);
            int lastVerifiedCount = 0;
            int noProgressCount = 0;

            while (System.currentTimeMillis() - waitStartTime < maxWaitMs) {
                Thread.sleep(10000); // Check every 10 seconds

                int currentVerified = verifier.getVerifiedCount();
                int remaining = jobCounter.get() - currentVerified;

                logger.info("Verification progress: {}/{} jobs verified ({} remaining)",
                    currentVerified, jobCounter.get(), remaining);

                // Check if all jobs verified
                if (currentVerified >= jobCounter.get()) {
                    logger.info("✅ All jobs verified!");
                    break;
                }

                // Check for progress
                if (currentVerified == lastVerifiedCount) {
                    noProgressCount++;
                    if (noProgressCount >= 6) { // 1 minute without progress
                        logger.warn("⚠️ No progress for 1 minute. Current: {}/{}", currentVerified, jobCounter.get());
                    }
                } else {
                    noProgressCount = 0;
                }
                lastVerifiedCount = currentVerified;
            }

            // Stop verifier
            verifier.stop();
            verifierThread.join(10000);

            // Final results
            logger.info("");
            logger.info("=".repeat(100));
            logger.info("ONE-HOUR LOAD TEST RESULTS");
            logger.info("=".repeat(100));
            logger.info("Jobs Sent:           {}", jobCounter.get());
            logger.info("Jobs Verified:       {}", verifier.getVerifiedCount());
            logger.info("Jobs Missing:        {}", jobCounter.get() - verifier.getVerifiedCount());
            logger.info("Verification Rate:   {:.2f}%",
                (verifier.getVerifiedCount() * 100.0) / jobCounter.get());
            logger.info("Test Duration:       {}", formatDuration(System.currentTimeMillis() - startTime));
            logger.info("=".repeat(100));

            // Get missing job IDs if any
            Set<String> missingJobs = verifier.getMissingJobs();
            if (!missingJobs.isEmpty()) {
                logger.error("Missing jobs (first 20):");
                missingJobs.stream().limit(20).forEach(id -> logger.error("  - {}", id));
            }

            // Assert success
            if (verifier.getVerifiedCount() < jobCounter.get()) {
                throw new AssertionError(String.format(
                    "Not all jobs were executed! Sent: %d, Verified: %d, Missing: %d",
                    jobCounter.get(),
                    verifier.getVerifiedCount(),
                    jobCounter.get() - verifier.getVerifiedCount()
                ));
            }

            logger.info("✅ TEST PASSED: All {} jobs were successfully executed!", jobCounter.get());

        } catch (Exception e) {
            logger.error("Test failed with exception", e);
            verifier.stop();
            throw e;
        }
    }

    private KafkaProducer<String, String> createProducer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");
        props.put("retries", 3);
        props.put("max.in.flight.requests.per.connection", 5);
        props.put("enable.idempotence", true);
        props.put("compression.type", "snappy");
        props.put("batch.size", 16384);
        props.put("linger.ms", 10);

        return new KafkaProducer<>(props);
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
}
