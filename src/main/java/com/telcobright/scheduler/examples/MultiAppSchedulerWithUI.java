package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.config.AppConfig;
import com.telcobright.scheduler.queue.QueueConfig;
import com.telcobright.scheduler.handler.impl.SmsJobHandler;
import com.telcobright.scheduler.handler.impl.SipCallJobHandler;
import com.telcobright.scheduler.handler.impl.PaymentGatewayJobHandler;
import com.telcobright.scheduler.web.JobStatusApi;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-app scheduler example with web UI for monitoring.
 */
public class MultiAppSchedulerWithUI {

    private static final Logger logger = LoggerFactory.getLogger(MultiAppSchedulerWithUI.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Counters for each app
    private static final AtomicInteger smsCounter = new AtomicInteger(0);
    private static final AtomicInteger sipCallCounter = new AtomicInteger(0);
    private static final AtomicInteger paymentCounter = new AtomicInteger(0);

    private static final String MYSQL_HOST = "127.0.0.1";
    private static final int MYSQL_PORT = 3306;
    private static final String MYSQL_DATABASE = "scheduler";
    private static final String MYSQL_USERNAME = "root";
    private static final String MYSQL_PASSWORD = "123456";

    public static void main(String[] args) throws Exception {
        logger.info("=== Multi-App Scheduler with Web UI ===" );

        // Initialize the multi-app scheduler manager
        MultiAppSchedulerManager manager = new MultiAppSchedulerManager(
            MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE, MYSQL_USERNAME, MYSQL_PASSWORD
        );

        // Register applications with their handlers
        registerApplications(manager);

        // Start web UI
        DataSource dataSource = createDataSource();
        JobStatusApi api = new JobStatusApi(dataSource);
        api.start(7070);

        // Start all schedulers
        manager.startAll();
        logger.info("✅ All application schedulers started");

        // Create a scheduled executor to generate jobs for different apps
        ScheduledExecutorService jobGenerator = Executors.newScheduledThreadPool(3);

        // Generate SMS jobs every 5 seconds
        jobGenerator.scheduleAtFixedRate(() -> {
            try {
                createSmsJob(manager);
            } catch (Exception e) {
                logger.error("Error creating SMS job", e);
            }
        }, 0, 5, TimeUnit.SECONDS);

        // Generate SIP Call jobs every 8 seconds
        jobGenerator.scheduleAtFixedRate(() -> {
            try {
                createSipCallJob(manager);
            } catch (Exception e) {
                logger.error("Error creating SIP Call job", e);
            }
        }, 2, 8, TimeUnit.SECONDS);

        // Generate Payment jobs every 12 seconds
        jobGenerator.scheduleAtFixedRate(() -> {
            try {
                createPaymentJob(manager);
            } catch (Exception e) {
                logger.error("Error creating Payment job", e);
            }
        }, 4, 12, TimeUnit.SECONDS);

        logger.info("📊 Job generators started:");
        logger.info("   - SMS: every 5 seconds");
        logger.info("   - SIP Call: every 8 seconds");
        logger.info("   - Payment: every 12 seconds");
        logger.info("🌐 Web UI: http://localhost:7070/index.html");
        logger.info("⏰ Press Ctrl+C to stop");

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                logger.info("Shutting down...");
                jobGenerator.shutdownNow();
                manager.stopAll();
                api.stop();
                logger.info("Shutdown complete");
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

        // Keep running
        Thread.currentThread().join();
    }

    private static void registerApplications(MultiAppSchedulerManager manager) {
        // SMS Application - outputs to console for testing
        QueueConfig smsQueueConfig = QueueConfig.builder()
            .queueType(QueueConfig.QueueType.CONSOLE)
            .topicName("sms-notifications")
            .brokerAddress("")  // Empty for console
            .build();

        AppConfig smsConfig = AppConfig.builder("sms")
            .tablePrefix("sms_scheduled_jobs")
            .historyTableName("sms_job_execution_history")
            .fetchIntervalSeconds(5)
            .lookaheadWindowSeconds(30)
            .queueConfig(smsQueueConfig)
            .build();
        manager.registerApp(smsConfig, new SmsJobHandler());
        logger.info("✅ Registered SMS application → Console output: sms-notifications");

        // SIPCall Application - outputs to console for testing
        QueueConfig sipCallQueueConfig = QueueConfig.builder()
            .queueType(QueueConfig.QueueType.CONSOLE)
            .topicName("sipcall-queue")
            .brokerAddress("")  // Empty for console
            .build();

        AppConfig sipCallConfig = AppConfig.builder("sipcall")
            .tablePrefix("sipcall_scheduled_jobs")
            .historyTableName("sipcall_job_execution_history")
            .fetchIntervalSeconds(5)
            .lookaheadWindowSeconds(35)
            .queueConfig(sipCallQueueConfig)
            .build();
        manager.registerApp(sipCallConfig, new SipCallJobHandler());
        logger.info("✅ Registered SIPCall application → Console output: sipcall-queue");

        // Payment Gateway Application - outputs to console for testing
        QueueConfig paymentQueueConfig = QueueConfig.builder()
            .queueType(QueueConfig.QueueType.CONSOLE)
            .topicName("payment-transactions")
            .brokerAddress("")  // Empty for console
            .build();

        AppConfig paymentConfig = AppConfig.builder("payment_gateway")
            .tablePrefix("payment_gateway_scheduled_jobs")
            .historyTableName("payment_gateway_job_execution_history")
            .fetchIntervalSeconds(5)
            .lookaheadWindowSeconds(40)
            .queueConfig(paymentQueueConfig)
            .build();
        manager.registerApp(paymentConfig, new PaymentGatewayJobHandler());
        logger.info("✅ Registered Payment Gateway application → Console output: payment-transactions");
    }

    private static void createSmsJob(MultiAppSchedulerManager manager) {
        int jobNum = smsCounter.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledTime = now.plusSeconds(10); // Execute in 10 seconds

        Map<String, Object> jobData = new HashMap<>();
        jobData.put("jobName", "sms-job-" + jobNum);
        jobData.put("phoneNumber", "+880171" + String.format("%07d", jobNum));
        jobData.put("message", "SMS #" + jobNum + " - Created at " + now.format(TIME_FORMATTER));
        jobData.put("scheduledTime", scheduledTime);
        jobData.put("priority", jobNum % 3 == 0 ? "HIGH" : "NORMAL");

        manager.scheduleJob("sms", jobData);
        logger.info("📱 Scheduled SMS job #{} to execute at {}",
            jobNum, scheduledTime.format(TIME_FORMATTER));
    }

    private static void createSipCallJob(MultiAppSchedulerManager manager) {
        int jobNum = sipCallCounter.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledTime = now.plusSeconds(15); // Execute in 15 seconds

        Map<String, Object> jobData = new HashMap<>();
        jobData.put("jobName", "sipcall-job-" + jobNum);
        jobData.put("callTo", "+880172" + String.format("%07d", jobNum));
        jobData.put("callFrom", "+8801555000000");
        jobData.put("duration", 30 + (jobNum * 5)); // Duration increases with each call
        jobData.put("scheduledTime", scheduledTime);
        jobData.put("callType", jobNum % 2 == 0 ? "VOICE" : "VIDEO");

        manager.scheduleJob("sipcall", jobData);
        logger.info("📞 Scheduled SIP Call job #{} to execute at {}",
            jobNum, scheduledTime.format(TIME_FORMATTER));
    }

    private static void createPaymentJob(MultiAppSchedulerManager manager) {
        int jobNum = paymentCounter.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledTime = now.plusSeconds(20); // Execute in 20 seconds

        Map<String, Object> jobData = new HashMap<>();
        jobData.put("jobName", "payment-job-" + jobNum);
        jobData.put("accountId", "ACC-" + String.format("%08d", jobNum));
        jobData.put("amount", 1000.0 + (jobNum * 50)); // Amount increases
        jobData.put("currency", jobNum % 3 == 0 ? "USD" : "BDT");
        jobData.put("transactionType", jobNum % 2 == 0 ? "PAYMENT" : "REFUND");
        jobData.put("scheduledTime", scheduledTime);
        jobData.put("merchantId", "MERCH-" + (jobNum % 10));

        manager.scheduleJob("payment_gateway", jobData);
        logger.info("💳 Scheduled Payment job #{} to execute at {}",
            jobNum, scheduledTime.format(TIME_FORMATTER));
    }

    private static DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE));
        config.setUsername(MYSQL_USERNAME);
        config.setPassword(MYSQL_PASSWORD);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setPoolName("WebUI-DataSource");

        return new HikariDataSource(config);
    }
}
