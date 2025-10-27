package com.telcobright.scheduler;

import com.telcobright.api.ShardingRepository;
import com.telcobright.core.repository.GenericMultiTableRepository;
import com.telcobright.core.entity.ShardingEntity;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.EverythingMatcher;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.quartz.Job;

public class InfiniteScheduler<TEntity extends SchedulableEntity> {

    private static final Logger logger = LoggerFactory.getLogger(InfiniteScheduler.class);

    private final ShardingRepository<TEntity, LocalDateTime> repository;
    private final SchedulerConfig config;
    private final Class<? extends Job> jobClass;
    private final Scheduler quartzScheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread fetcherThread;
    private QuartzCleanupService cleanupService;
    private SchedulerMonitor monitor;
    private JobHistoryTracker historyTracker;
    private DatabaseStatusUpdater statusUpdater;
    private static final String SCHEDULER_INSTANCE_KEY = "scheduler_instance";

    public InfiniteScheduler(Class<TEntity> entityClass,
                           SchedulerConfig config,
                           Class<? extends Job> jobClass) throws SchedulerException {
        this.config = config;
        this.jobClass = jobClass;
        this.repository = createRepository(entityClass);
        
        // Initialize Quartz tables if needed
        if (config.isAutoCreateTables()) {
            initializeQuartzTables();
        }
        
        this.quartzScheduler = createQuartzScheduler();
        
        // Store scheduler instance in the scheduler context for job access
        try {
            quartzScheduler.getContext().put(SCHEDULER_INSTANCE_KEY, this);
        } catch (SchedulerException e) {
            logger.error("Failed to store scheduler instance in context", e);
            throw e;
        }
        
        // Initialize cleanup service
        DataSource cleanupDataSource = createDataSourceFromConfig(config);
        this.cleanupService = new QuartzCleanupService(cleanupDataSource, config);
        
        // Initialize monitor
        this.monitor = new SchedulerMonitor(quartzScheduler);
        
        // Initialize job history tracker
        this.historyTracker = new JobHistoryTracker(cleanupDataSource);
        
        // Initialize database status updater
        this.statusUpdater = new DatabaseStatusUpdater(cleanupDataSource);
        
        // Add job listener for monitoring and history tracking
        addJobListener();
        
        logger.info("InfiniteScheduler initialized with fetch interval: {}s, lookahead: {}s, cleanup: {}", 
            config.getFetchIntervalSeconds(), config.getLookaheadWindowSeconds(),
            config.isAutoCleanupCompletedJobs() ? config.getCleanupIntervalMinutes() + "min" : "disabled");
    }
    
    /**
     * Constructor for backward compatibility - accepts pre-built repository
     * @deprecated Use the constructor that accepts entity class and configuration instead
     */
    @Deprecated
    public InfiniteScheduler(ShardingRepository<TEntity, LocalDateTime> repository,
                           SchedulerConfig config,
                           Class<? extends Job> jobClass) throws SchedulerException {
        this.repository = repository;
        this.config = config;
        this.jobClass = jobClass;
        
        // Initialize Quartz tables if needed
        if (config.isAutoCreateTables()) {
            initializeQuartzTables();
        }
        
        this.quartzScheduler = createQuartzScheduler();
        
        // Store scheduler instance in the scheduler context for job access
        try {
            quartzScheduler.getContext().put(SCHEDULER_INSTANCE_KEY, this);
        } catch (SchedulerException e) {
            logger.error("Failed to store scheduler instance in context", e);
            throw e;
        }
        
        // Initialize cleanup service
        DataSource cleanupDataSource = createDataSourceFromConfig(config);
        this.cleanupService = new QuartzCleanupService(cleanupDataSource, config);
        
        // Initialize monitor
        this.monitor = new SchedulerMonitor(quartzScheduler);
        
        // Initialize job history tracker
        this.historyTracker = new JobHistoryTracker(cleanupDataSource);
        
        // Initialize database status updater
        this.statusUpdater = new DatabaseStatusUpdater(cleanupDataSource);
        
        // Add job listener for monitoring and history tracking
        addJobListener();
        
        logger.info("InfiniteScheduler initialized (deprecated constructor) with fetch interval: {}s, lookahead: {}s, cleanup: {}", 
            config.getFetchIntervalSeconds(), config.getLookaheadWindowSeconds(),
            config.isAutoCleanupCompletedJobs() ? config.getCleanupIntervalMinutes() + "min" : "disabled");
    }
    
    private ShardingRepository<TEntity, LocalDateTime> createRepository(Class<TEntity> entityClass) {
        try {
            // Create repository builder using the configuration from SchedulerConfig
            var repositoryBuilder = GenericMultiTableRepository.<TEntity, LocalDateTime>builder(entityClass)
                .database(config.getRepositoryDatabase())
                .tablePrefix(config.getRepositoryTablePrefix())
                .host(config.getMysqlHost())
                .port(config.getMysqlPort())
                .username(config.getMysqlUsername())
                .password(config.getMysqlPassword());
            
            logger.info("Creating internal repository with database: {}, tablePrefix: {}", 
                config.getRepositoryDatabase(), config.getRepositoryTablePrefix());
            logger.info("Repository configured with MySQL: {}:{}/{} (user: {})", 
                config.getMysqlHost(), config.getMysqlPort(), config.getRepositoryDatabase(), config.getMysqlUsername());
            
            return repositoryBuilder.build();
            
        } catch (Exception e) {
            logger.error("Failed to create internal repository", e);
            throw new RuntimeException("Failed to create internal repository", e);
        }
    }
    
    private void initializeQuartzTables() {
        logger.info("Initializing Quartz tables...");
        
        try {
            // Create a DataSource from the configuration
            DataSource dataSource = createDataSourceFromConfig(config);
            
            // Use QuartzTableManager to check and create tables
            QuartzTableManager tableManager = new QuartzTableManager(dataSource);
            tableManager.initializeQuartzTables();
            
            logger.info("Quartz tables initialization completed");
        } catch (Exception e) {
            logger.error("Failed to initialize Quartz tables", e);
            throw new RuntimeException("Failed to initialize Quartz tables", e);
        }
    }
    
    private DataSource createDataSourceFromConfig(SchedulerConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getQuartzDataSource());
        hikariConfig.setUsername(config.getMysqlUsername());
        hikariConfig.setPassword(config.getMysqlPassword());
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(600000);
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setPoolName("QuartzSchedulerPool");
        
        return new HikariDataSource(hikariConfig);
    }
    
    private Scheduler createQuartzScheduler() throws SchedulerException {
        Properties props = new Properties();
        
        props.setProperty("org.quartz.scheduler.instanceName", "InfiniteScheduler");
        props.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        
        props.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        props.setProperty("org.quartz.threadPool.threadCount", String.valueOf(config.getThreadPoolSize()));
        
        props.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        props.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        props.setProperty("org.quartz.jobStore.useProperties", "false");
        props.setProperty("org.quartz.jobStore.acquireTriggersWithinLock", "true");
        props.setProperty("org.quartz.jobStore.dataSource", "myDS");
        props.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        props.setProperty("org.quartz.jobStore.isClustered", String.valueOf(config.isClusteringEnabled()));
        
        if (config.isClusteringEnabled()) {
            props.setProperty("org.quartz.jobStore.clusterCheckinInterval", "20000");
        }
        
        props.setProperty("org.quartz.dataSource.myDS.driver", "com.mysql.cj.jdbc.Driver");
        props.setProperty("org.quartz.dataSource.myDS.URL", config.getQuartzDataSource());
        props.setProperty("org.quartz.dataSource.myDS.user", config.getMysqlUsername());
        props.setProperty("org.quartz.dataSource.myDS.password", config.getMysqlPassword());
        props.setProperty("org.quartz.dataSource.myDS.maxConnections", "10");
        props.setProperty("org.quartz.dataSource.myDS.validationQuery", "SELECT 1");
        
        StdSchedulerFactory factory = new StdSchedulerFactory();
        factory.initialize(props);
        
        return factory.getScheduler();
    }
    
    public void start() throws SchedulerException {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting InfiniteScheduler...");
            
            quartzScheduler.start();
            
            fetcherThread = Thread.ofVirtual()
                .name("scheduler-fetcher")
                .start(this::runFetcher);
            
            // Start cleanup service
            cleanupService.start();
            
            // Start monitor
            monitor.start();
            
            logger.info("InfiniteScheduler started successfully");
        } else {
            logger.warn("InfiniteScheduler is already running");
        }
    }
    
    public void stop() throws SchedulerException {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping InfiniteScheduler...");
            
            if (fetcherThread != null) {
                fetcherThread.interrupt();
            }
            
            // Stop monitor
            monitor.stop();
            
            // Stop cleanup service
            cleanupService.stop();
            
            quartzScheduler.shutdown(true);
            
            logger.info("InfiniteScheduler stopped successfully");
        } else {
            logger.warn("InfiniteScheduler is not running");
        }
    }
    
    private void runFetcher() {
        logger.info("Fetcher thread started");
        
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                fetchAndScheduleJobs();
                Thread.sleep(config.getFetchIntervalSeconds() * 1000L);
            } catch (InterruptedException e) {
                logger.info("Fetcher thread interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in fetcher thread", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.info("Fetcher thread stopped");
    }
    
    private void fetchAndScheduleJobs() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime endTime = now.plusSeconds(config.getLookaheadWindowSeconds());
            
            logger.info("🔍 Fetching entities from {} to {} (lookahead: {}s)", now, endTime, config.getLookaheadWindowSeconds());
            
            // Query by scheduled_time (sharding key) for entities scheduled in the near future
            // Since scheduled_time is now the sharding key, we can efficiently query by scheduled time
            LocalDateTime queryStartTime = now.minusMinutes(5); // Look back 5 minutes for safety
            LocalDateTime queryEndTime = endTime.plusMinutes(5); // Look ahead extra 5 minutes for safety
            
            // Ensure tables exist for the date range we're querying
            // This is necessary because partitioned tables are created based on the sharding key (scheduled_time)
            try {
                ensureTablesExistForDateRange(queryStartTime, queryEndTime);
            } catch (Exception e) {
                logger.warn("Failed to ensure tables exist for date range {} to {}: {}", 
                    queryStartTime, queryEndTime, e.getMessage());
            }
            
            List<TEntity> recentEntities;
            try {
                logger.info("📥 Querying partitioned-repo for date range: {} to {}", queryStartTime, queryEndTime);
                recentEntities = repository.findAllByDateRange(queryStartTime, queryEndTime);
                logger.info("📊 Found {} total entities from database", recentEntities.size());
                
                // Debug: show status of first few entities
                for (int i = 0; i < Math.min(3, recentEntities.size()); i++) {
                    TEntity entity = recentEntities.get(i);
                    logger.info("📋 Entity {}: scheduledTime={}, scheduled={}", 
                        entity.getId(), entity.getScheduledTime(), entity.getScheduled());
                }
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("doesn't exist")) {
                    logger.info("No partitioned tables found for date range {} to {} - this is normal if no entities have been inserted yet", 
                        queryStartTime.toLocalDate(), queryEndTime.toLocalDate());
                    monitor.recordFetch(0);
                    return;
                } else {
                    logger.error("Error querying repository for date range {} to {}: {}", 
                        queryStartTime, queryEndTime, e.getMessage());
                    throw e;
                }
            }
            
            // Filter entities that should be scheduled in the lookahead window
            List<TEntity> candidateEntities = recentEntities.stream()
                .filter(entity -> {
                    LocalDateTime scheduledTime = entity.getScheduledTime();
                    return scheduledTime != null && 
                           !scheduledTime.isBefore(now) && 
                           !scheduledTime.isAfter(endTime);
                })
                .limit(config.getMaxJobsPerFetch())
                .toList();
            
            if (candidateEntities.isEmpty()) {
                logger.info("❌ No candidate entities found in scheduling time range {} to {} (fetched {} total entities)", 
                    now, endTime, recentEntities.size());
                monitor.recordFetch(0);
                return;
            }
            
            logger.info("📋 Found {} candidate entities in time range, checking for unscheduled jobs...", candidateEntities.size());
            
            // Batch check which entities already have jobs in Quartz
            Set<JobKey> existingJobKeys = getExistingJobKeys(candidateEntities);
            
            // Filter out entities that already have jobs scheduled or are already marked as scheduled
            List<TEntity> entities = candidateEntities.stream()
                .filter(entity -> {
                    JobKey jobKey = JobKey.jobKey(entity.getJobId(), "entity-jobs");
                    boolean notInQuartz = !existingJobKeys.contains(jobKey);
                    boolean notScheduled = (entity.getScheduled() == null || !entity.getScheduled());
                    
                    logger.debug("🔍 Entity {}: inQuartz={}, scheduled={}, willSchedule={}", 
                        entity.getId(), !notInQuartz, entity.getScheduled(), (notInQuartz && notScheduled));
                    
                    return notInQuartz && notScheduled;
                })
                .toList();
            
            if (entities.isEmpty()) {
                logger.info("⚠️  No new entities to schedule (filtered {} existing jobs and {} already scheduled from {} candidates)", 
                    existingJobKeys.size(), 
                    candidateEntities.size() - entities.size() - existingJobKeys.size(),
                    candidateEntities.size());
                monitor.recordFetch(0);
                return;
            }
            
            // IMPORTANT: Mark entities as scheduled immediately after fetching to prevent re-fetching
            for (TEntity entity : entities) {
                try {
                    statusUpdater.markAsScheduled(entity.getId(), entity.getScheduledTime(),
                        config.getRepositoryTablePrefix());
                    entity.setScheduled(true); // Update in memory as well
                    logger.info("✅ Marked entity {} as scheduled=1 in database", entity.getId());
                } catch (Exception e) {
                    logger.error("Failed to mark entity {} as scheduled in database", entity.getId(), e);
                }
            }
            
            logger.info("Found {} entities to schedule (filtered {} existing jobs from {} candidates, {} total recent)", 
                entities.size(), existingJobKeys.size(), candidateEntities.size(), recentEntities.size());
            monitor.recordFetch(entities.size());
            
            int scheduled = 0;
            int batchCount = 0;
            
            for (TEntity entity : entities) {
                if (scheduled >= config.getMaxJobsPerFetch()) {
                    logger.warn("Reached max jobs per fetch limit: {}", config.getMaxJobsPerFetch());
                    break;
                }
                
                try {
                    scheduleJob(entity);
                    scheduled++;
                    batchCount++;
                    monitor.recordJobScheduled();
                    
                    if (batchCount >= config.getBatchSize()) {
                        logger.debug("Processed batch of {} jobs", batchCount);
                        batchCount = 0;
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to schedule job for entity: {}", entity.getId(), e);
                }
            }
            
            logger.info("Successfully scheduled {} jobs", scheduled);
            
        } catch (Exception e) {
            logger.error("Error fetching and scheduling jobs", e);
        }
    }
    
    private void scheduleJob(TEntity entity) throws SchedulerException {
        // Use the unique job ID from the entity
        String jobId = entity.getJobId();
        String jobName = jobId;
        String groupName = "entity-jobs";
        
        JobKey jobKey = JobKey.jobKey(jobName, groupName);
        
        // Note: Duplicate checking is now done in batch during fetchAndScheduleJobs()
        // This method assumes the entity has already been filtered for duplicates
        
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
            .withIdentity(jobName, groupName)
            .usingJobData("entityId", entity.getId().toString())
            .usingJobData("scheduledTime", entity.getScheduledTime().toString())
            .build();
        
        Date scheduleTime = Date.from(entity.getScheduledTime().atZone(ZoneId.systemDefault()).toInstant());
        
        Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity("trigger-" + jobId, groupName)
            .startAt(scheduleTime)
            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                .withMisfireHandlingInstructionFireNow())
            .build();
        
        quartzScheduler.scheduleJob(jobDetail, trigger);
        
        // Note: Entity is already marked as scheduled in database during fetchAndScheduleJobs()
        // This ensures jobs are never picked up again even if Quartz scheduling fails
        
        // Record job scheduling in history
        historyTracker.recordJobScheduled(jobId, jobName, groupName, 
            entity.getId().toString(), entity.getScheduledTime());
        
        logger.debug("Scheduled job for entity: {} (jobId: {}) at {}", 
            entity.getId(), jobId, entity.getScheduledTime());
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public SchedulerConfig getConfig() {
        return config;
    }
    
    public ShardingRepository<TEntity, LocalDateTime> getRepository() {
        return repository;
    }
    
    public Class<? extends Job> getJobClass() {
        return jobClass;
    }
    
    @SuppressWarnings("unchecked")
    public static <T extends SchedulableEntity> InfiniteScheduler<T> getFromContext(SchedulerContext context)
            throws SchedulerException {
        return (InfiniteScheduler<T>) context.get(SCHEDULER_INSTANCE_KEY);
    }
    
    /**
     * Batch check which job keys already exist in Quartz scheduler
     * This is much more efficient than individual checkExists() calls
     */
    private Set<JobKey> getExistingJobKeys(List<TEntity> entities) throws SchedulerException {
        Set<JobKey> existingKeys = new HashSet<>();
        
        if (entities.isEmpty()) {
            return existingKeys;
        }
        
        // Get all job keys for the entity-jobs group
        Set<JobKey> allJobKeys = quartzScheduler.getJobKeys(GroupMatcher.jobGroupEquals("entity-jobs"));
        
        // Create a set of job keys we're interested in checking
        Set<JobKey> candidateKeys = entities.stream()
            .map(entity -> JobKey.jobKey(entity.getJobId(), "entity-jobs"))
            .collect(Collectors.toSet());
        
        // Find intersection - which of our candidate keys already exist
        for (JobKey jobKey : allJobKeys) {
            if (candidateKeys.contains(jobKey)) {
                existingKeys.add(jobKey);
            }
        }
        
        logger.debug("Batch job key check: {} entities, {} already exist in Quartz", 
            entities.size(), existingKeys.size());
        
        return existingKeys;
    }
    
    /**
     * Initialize partitioned tables for the date range we'll be querying.
     * This is a best-effort approach to handle table creation issues.
     */
    private void ensureTablesExistForDateRange(LocalDateTime startTime, LocalDateTime endTime) {
        // For now, just log the date range we're trying to query
        logger.debug("Attempting to fetch entities from partitioned tables for date range: {} to {}", 
            startTime.toLocalDate(), endTime.toLocalDate());
            
        // The repository should handle table creation automatically when inserting entities
        // If tables don't exist during fetch, we'll catch the exception and handle gracefully
    }
    
    private void addJobListener() {
        try {
            JobListener jobListener = new JobListener() {
                @Override
                public String getName() {
                    return "InfiniteSchedulerJobListener";
                }
                
                @Override
                public void jobToBeExecuted(JobExecutionContext context) {
                    // Job is about to be executed - record start
                    String jobName = context.getJobDetail().getKey().getName();
                    historyTracker.recordJobStarted(jobName);
                }
                
                @Override
                public void jobExecutionVetoed(JobExecutionContext context) {
                    // Job execution was vetoed
                }
                
                @Override
                public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
                    String jobName = context.getJobDetail().getKey().getName();
                    
                    if (jobException == null) {
                        // Job executed successfully
                        monitor.recordJobExecuted();
                        historyTracker.recordJobCompleted(jobName);
                        logger.debug("Job {} executed successfully", context.getJobDetail().getKey());
                        
                        // No need to update partitioned-repo - scheduled flag stays at 1
                        // This is now Quartz's responsibility and the job is done
                    } else {
                        // Job execution failed
                        monitor.recordJobFailed();
                        historyTracker.recordJobFailed(jobName, jobException.getMessage());
                        logger.error("Job {} failed with exception", context.getJobDetail().getKey(), jobException);
                        
                        // No need to update partitioned-repo on failure either
                        // scheduled=1 means it was picked up, that's all we need to track
                    }
                }
            };
            
            // Add the listener to all jobs
            quartzScheduler.getListenerManager().addJobListener(jobListener, EverythingMatcher.allJobs());
            logger.info("Job listener added for monitoring");
            
        } catch (SchedulerException e) {
            logger.error("Failed to add job listener", e);
        }
    }
}