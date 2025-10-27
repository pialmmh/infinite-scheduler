package com.telcobright.scheduler;

import com.telcobright.scheduler.config.AppConfig;
import com.telcobright.scheduler.handler.JobHandler;
import com.telcobright.scheduler.handler.JobHandlerRegistry;
import com.telcobright.scheduler.handler.impl.PaymentGatewayJobHandler;
import com.telcobright.scheduler.handler.impl.SipCallJobHandler;
import com.telcobright.scheduler.handler.impl.SmsJobHandler;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for multiple application schedulers sharing a single Quartz instance.
 */
public class MultiAppSchedulerManager {

    private static final Logger logger = LoggerFactory.getLogger(MultiAppSchedulerManager.class);

    private final Scheduler sharedQuartzScheduler;
    private final Map<String, InfiniteAppScheduler> appSchedulers;
    private final QuartzCleanupService cleanupService;
    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUsername;
    private final String mysqlPassword;

    public MultiAppSchedulerManager(String mysqlHost, int mysqlPort,
                                   String mysqlDatabase, String mysqlUsername,
                                   String mysqlPassword) throws SchedulerException {
        this.mysqlHost = mysqlHost;
        this.mysqlPort = mysqlPort;
        this.mysqlDatabase = mysqlDatabase;
        this.mysqlUsername = mysqlUsername;
        this.mysqlPassword = mysqlPassword;

        this.sharedQuartzScheduler = createQuartzScheduler();
        this.appSchedulers = new ConcurrentHashMap<>();

        // Initialize Quartz tables if needed
        initializeQuartzTables();

        // Initialize cleanup service
        DataSource dataSource = createDataSource();
        SchedulerConfig cleanupConfig = SchedulerConfig.builder()
            .mysqlHost(mysqlHost)
            .mysqlPort(mysqlPort)
            .mysqlDatabase(mysqlDatabase)
            .mysqlUsername(mysqlUsername)
            .mysqlPassword(mysqlPassword)
            .repositoryDatabase(mysqlDatabase)  // Required field
            .repositoryTablePrefix("cleanup")   // Dummy prefix for cleanup service
            .autoCleanupCompletedJobs(true)
            .cleanupIntervalMinutes(5)
            .build();
        this.cleanupService = new QuartzCleanupService(dataSource, cleanupConfig);

        logger.info("MultiAppSchedulerManager initialized with shared Quartz scheduler");
    }

    /**
     * Register a new application with its handler.
     */
    public void registerApp(String appName, JobHandler handler) {
        registerApp(new AppConfig(appName), handler);
    }

    /**
     * Register a new application with custom configuration and handler.
     */
    public void registerApp(AppConfig appConfig, JobHandler handler) {
        String appName = appConfig.getAppName();

        if (appSchedulers.containsKey(appName)) {
            logger.warn("App '{}' is already registered", appName);
            return;
        }

        // Create scheduler for this app
        InfiniteAppScheduler scheduler = new InfiniteAppScheduler(
            appConfig,
            sharedQuartzScheduler,
            mysqlHost,
            mysqlPort,
            mysqlDatabase,
            mysqlUsername,
            mysqlPassword
        );

        appSchedulers.put(appName, scheduler);
        JobHandlerRegistry.register(appName, handler);

        logger.info("Registered app '{}' with handler {}", appName, handler.getName());
    }

    /**
     * Register default applications (SMS, SIPCall, PaymentGateway).
     */
    public void registerDefaultApps() {
        // Register SMS app
        registerApp("sms", new SmsJobHandler());

        // Register SIPCall app
        registerApp("sipcall", new SipCallJobHandler());

        // Register Payment Gateway app
        registerApp("payment_gateway", new PaymentGatewayJobHandler());

        logger.info("Registered default apps: sms, sipcall, payment_gateway");
    }

    /**
     * Schedule a job for a specific application.
     */
    public void scheduleJob(String appName, Map<String, Object> jobData) {
        InfiniteAppScheduler scheduler = appSchedulers.get(appName);
        if (scheduler == null) {
            throw new IllegalArgumentException("Unknown app: " + appName + ". Please register the app first.");
        }

        scheduler.scheduleJob(jobData);
    }

    /**
     * Start all registered application schedulers.
     */
    public void startAll() throws SchedulerException {
        // Start Quartz scheduler
        if (!sharedQuartzScheduler.isStarted()) {
            sharedQuartzScheduler.start();
            logger.info("Started shared Quartz scheduler");
        }

        // Start cleanup service
        cleanupService.start();

        // Start all app schedulers
        for (Map.Entry<String, InfiniteAppScheduler> entry : appSchedulers.entrySet()) {
            entry.getValue().start();
        }

        logger.info("Started all {} application schedulers", appSchedulers.size());
    }

    /**
     * Stop all schedulers.
     */
    public void stopAll() throws SchedulerException {
        // Stop all app schedulers
        for (Map.Entry<String, InfiniteAppScheduler> entry : appSchedulers.entrySet()) {
            try {
                entry.getValue().stop();
            } catch (Exception e) {
                logger.error("Error stopping scheduler for app '{}'", entry.getKey(), e);
            }
        }

        // Stop cleanup service
        cleanupService.stop();

        // Stop Quartz scheduler
        if (sharedQuartzScheduler.isStarted()) {
            sharedQuartzScheduler.shutdown(true);
            logger.info("Stopped shared Quartz scheduler");
        }

        logger.info("Stopped all schedulers");
    }

    /**
     * Get list of registered applications.
     */
    public Set<String> getRegisteredApps() {
        return new HashSet<>(appSchedulers.keySet());
    }

    /**
     * Get scheduler for a specific app.
     */
    public InfiniteAppScheduler getScheduler(String appName) {
        return appSchedulers.get(appName);
    }

    private Scheduler createQuartzScheduler() throws SchedulerException {
        Properties props = new Properties();
        props.put("org.quartz.scheduler.instanceName", "MultiAppScheduler");
        props.put("org.quartz.scheduler.instanceId", "AUTO");
        props.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        props.put("org.quartz.threadPool.threadCount", "20");
        props.put("org.quartz.threadPool.threadPriority", "5");

        // JobStore configuration for MySQL
        props.put("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        props.put("org.quartz.jobStore.driverDelegateClass",
            "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        props.put("org.quartz.jobStore.useProperties", "false");
        props.put("org.quartz.jobStore.dataSource", "quartzDS");
        props.put("org.quartz.jobStore.tablePrefix", "QRTZ_");
        props.put("org.quartz.jobStore.isClustered", "false");

        // DataSource configuration
        props.put("org.quartz.dataSource.quartzDS.driver", "com.mysql.cj.jdbc.Driver");
        props.put("org.quartz.dataSource.quartzDS.URL",
            String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                mysqlHost, mysqlPort, mysqlDatabase));
        props.put("org.quartz.dataSource.quartzDS.user", mysqlUsername);
        props.put("org.quartz.dataSource.quartzDS.password", mysqlPassword);
        props.put("org.quartz.dataSource.quartzDS.maxConnections", "10");
        props.put("org.quartz.dataSource.quartzDS.validationQuery", "SELECT 1");

        StdSchedulerFactory factory = new StdSchedulerFactory(props);
        return factory.getScheduler();
    }

    private void initializeQuartzTables() {
        try {
            DataSource dataSource = createDataSource();
            QuartzTableManager tableManager = new QuartzTableManager(dataSource);
            tableManager.initializeQuartzTables();
            logger.info("Quartz tables initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize Quartz tables", e);
        }
    }

    private DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            mysqlHost, mysqlPort, mysqlDatabase));
        config.setUsername(mysqlUsername);
        config.setPassword(mysqlPassword);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setPoolName("MultiAppScheduler");

        return new HikariDataSource(config);
    }
}