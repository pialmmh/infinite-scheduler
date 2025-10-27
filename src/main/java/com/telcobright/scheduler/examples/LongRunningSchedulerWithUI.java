package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.InfiniteScheduler;
import com.telcobright.scheduler.SchedulerConfig;
import com.telcobright.scheduler.web.JobStatusApi;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Long-running scheduler that creates jobs every 5 seconds and provides a web UI
 */
public class LongRunningSchedulerWithUI {

    private static final Logger logger = LoggerFactory.getLogger(LongRunningSchedulerWithUI.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AtomicInteger jobCounter = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        logger.info("=== Long Running Scheduler with Web UI ===");

        // Configuration
        SchedulerConfig config = SchedulerConfig.builder()
            .fetchInterval(5)               // Fetch every 5 seconds
            .lookaheadWindow(30)            // Look 30 seconds ahead
            .mysqlHost("127.0.0.1")
            .mysqlPort(3306)
            .mysqlDatabase("scheduler")
            .mysqlUsername("root")
            .mysqlPassword("123456")
            .repositoryDatabase("scheduler")
            .repositoryTablePrefix("long_run_sms")
            .maxJobsPerFetch(1000)
            .autoCreateTables(true)
            .autoCleanupCompletedJobs(true)
            .cleanupIntervalMinutes(5)
            .build();

        // Create scheduler
        InfiniteScheduler<SmsEntity> scheduler =
            new InfiniteScheduler<>(SmsEntity.class, config, SmsJob.class);

        // Create web API
        DataSource webDataSource = createDataSource(config);
        JobStatusApi webApi = new JobStatusApi(webDataSource);
        webApi.start(7082);  // Start web UI on port 7082

        logger.info("✅ Web UI started at: http://localhost:7082/index.html");
        logger.info("✅ API endpoints:");
        logger.info("   - GET http://localhost:7082/api/jobs/scheduled");
        logger.info("   - GET http://localhost:7082/api/jobs/history");
        logger.info("   - GET http://localhost:7082/api/jobs/stats");

        // Start scheduler
        scheduler.start();
        logger.info("✅ Scheduler started");

        // Create a scheduled executor to add new jobs every 5 seconds
        ScheduledExecutorService jobCreator = Executors.newScheduledThreadPool(1);
        jobCreator.scheduleAtFixedRate(() -> {
            try {
                createNewJob(scheduler);
            } catch (Exception e) {
                logger.error("Error creating job", e);
            }
        }, 0, 5, TimeUnit.SECONDS);  // Create a job every 5 seconds

        logger.info("✅ Job creator started - creating new job every 5 seconds");
        logger.info("📊 Monitor jobs at: http://localhost:7082/index.html");
        logger.info("⏰ This will run for hours. Press Ctrl+C to stop.");

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Shutting down...");
                jobCreator.shutdownNow();
                scheduler.stop();
                webApi.stop();
                logger.info("Shutdown complete");
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

        // Keep running (forever or until interrupted)
        Thread.currentThread().join();
    }

    private static void createNewJob(InfiniteScheduler<SmsEntity> scheduler) {
        int jobNum = jobCounter.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();

        // Schedule job to execute 10 seconds from now
        LocalDateTime scheduledTime = now.plusSeconds(10);

        SmsEntity smsEntity = new SmsEntity();
        smsEntity.setPhoneNumber("+880171" + String.format("%07d", jobNum));
        smsEntity.setMessage("Job #" + jobNum + " - Scheduled at " + now.format(TIME_FORMATTER));
        smsEntity.setStatus("PENDING");
        smsEntity.setScheduled(false);
        smsEntity.setScheduledTime(scheduledTime);

        try {
            scheduler.getRepository().insert(smsEntity);
            logger.info("📝 Created job #{}: {} - will execute at {}",
                jobNum, smsEntity.getPhoneNumber(), scheduledTime.format(TIME_FORMATTER));
        } catch (Exception e) {
            logger.error("Failed to create job #{}", jobNum, e);
        }
    }

    private static DataSource createDataSource(SchedulerConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getQuartzDataSource());
        hikariConfig.setUsername(config.getMysqlUsername());
        hikariConfig.setPassword(config.getMysqlPassword());
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setPoolName("WebAPIPool");

        return new HikariDataSource(hikariConfig);
    }
}
