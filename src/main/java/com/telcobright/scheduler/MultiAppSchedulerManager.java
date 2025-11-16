package com.telcobright.scheduler;

import com.telcobright.scheduler.config.AppConfig;
import com.telcobright.scheduler.handler.JobHandler;
import com.telcobright.scheduler.handler.JobHandlerRegistry;
import com.telcobright.scheduler.handler.impl.PaymentGatewayJobHandler;
import com.telcobright.scheduler.handler.impl.SipCallJobHandler;
import com.telcobright.scheduler.handler.impl.SmsJobHandler;
import com.telcobright.scheduler.kafka.KafkaIngestConfig;
import com.telcobright.scheduler.kafka.KafkaJobIngestConsumer;
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
    private final KafkaJobIngestConsumer kafkaIngestConsumer;
    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUsername;
    private final String mysqlPassword;

    /**
     * Constructor for backwards compatibility.
     */
    public MultiAppSchedulerManager(String mysqlHost, int mysqlPort,
                                   String mysqlDatabase, String mysqlUsername,
                                   String mysqlPassword) throws SchedulerException {
        this(mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, null);
    }

    /**
     * Constructor with Kafka ingest support.
     */
    private MultiAppSchedulerManager(String mysqlHost, int mysqlPort,
                                    String mysqlDatabase, String mysqlUsername,
                                    String mysqlPassword, KafkaIngestConfig kafkaConfig) throws SchedulerException {
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

        // Initialize Kafka ingest consumer if configured
        if (kafkaConfig != null && kafkaConfig.isEnabled()) {
            this.kafkaIngestConsumer = new KafkaJobIngestConsumer(kafkaConfig, this);
            logger.info("Kafka ingest consumer initialized with config: {}", kafkaConfig);
        } else {
            this.kafkaIngestConsumer = null;
            logger.info("Kafka ingest disabled");
        }

        logger.info("MultiAppSchedulerManager initialized with shared Quartz scheduler");
    }

    /**
     * Create a builder for MultiAppSchedulerManager.
     */
    public static Builder builder() {
        return new Builder();
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
            // Add global job listener for tracking completion/failure
            DataSource listenerDataSource = createDataSource();
            JobCompletionListener completionListener = new JobCompletionListener(listenerDataSource);
            sharedQuartzScheduler.getListenerManager().addJobListener(completionListener);
            logger.info("Registered JobCompletionListener");

            sharedQuartzScheduler.start();
            logger.info("Started shared Quartz scheduler");
        }

        // Start cleanup service
        cleanupService.start();

        // Start all app schedulers
        for (Map.Entry<String, InfiniteAppScheduler> entry : appSchedulers.entrySet()) {
            entry.getValue().start();
        }

        // Start Kafka ingest consumer if configured
        if (kafkaIngestConsumer != null) {
            kafkaIngestConsumer.start();
            logger.info("Started Kafka ingest consumer");
        }

        logger.info("Started all {} application schedulers", appSchedulers.size());
    }

    /**
     * Stop all schedulers.
     */
    public void stopAll() throws SchedulerException {
        // Stop Kafka ingest consumer first
        if (kafkaIngestConsumer != null) {
            kafkaIngestConsumer.stop();
            logger.info("Stopped Kafka ingest consumer");
        }

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

    /**
     * Get Kafka ingest consumer (if configured).
     */
    public KafkaJobIngestConsumer getKafkaIngestConsumer() {
        return kafkaIngestConsumer;
    }

    /**
     * Builder for MultiAppSchedulerManager with Kafka ingest support.
     */
    public static class Builder {
        private String mysqlHost;
        private int mysqlPort = 3306;
        private String mysqlDatabase;
        private String mysqlUsername;
        private String mysqlPassword;
        private KafkaIngestConfig kafkaIngestConfig;

        public Builder mysqlHost(String mysqlHost) {
            this.mysqlHost = mysqlHost;
            return this;
        }

        public Builder mysqlPort(int mysqlPort) {
            this.mysqlPort = mysqlPort;
            return this;
        }

        public Builder mysqlDatabase(String mysqlDatabase) {
            this.mysqlDatabase = mysqlDatabase;
            return this;
        }

        public Builder mysqlUsername(String mysqlUsername) {
            this.mysqlUsername = mysqlUsername;
            return this;
        }

        public Builder mysqlPassword(String mysqlPassword) {
            this.mysqlPassword = mysqlPassword;
            return this;
        }

        /**
         * Enable Kafka job ingestion with custom configuration.
         */
        public Builder withKafkaIngest(KafkaIngestConfig kafkaIngestConfig) {
            this.kafkaIngestConfig = kafkaIngestConfig;
            return this;
        }

        /**
         * Enable Kafka job ingestion with default configuration.
         */
        public Builder withKafkaIngest(String bootstrapServers, String topic) {
            this.kafkaIngestConfig = KafkaIngestConfig.builder()
                .bootstrapServers(bootstrapServers)
                .topic(topic)
                .build();
            return this;
        }

        /**
         * Enable Kafka job ingestion with default configuration (localhost:9092).
         */
        public Builder withKafkaIngest() {
            this.kafkaIngestConfig = KafkaIngestConfig.builder().build();
            return this;
        }

        public MultiAppSchedulerManager build() throws SchedulerException {
            if (mysqlHost == null || mysqlHost.trim().isEmpty()) {
                throw new IllegalArgumentException("mysqlHost is required");
            }
            if (mysqlDatabase == null || mysqlDatabase.trim().isEmpty()) {
                throw new IllegalArgumentException("mysqlDatabase is required");
            }
            if (mysqlUsername == null) {
                throw new IllegalArgumentException("mysqlUsername is required");
            }
            if (mysqlPassword == null) {
                throw new IllegalArgumentException("mysqlPassword is required");
            }

            return new MultiAppSchedulerManager(
                mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, kafkaIngestConfig
            );
        }
    }
}