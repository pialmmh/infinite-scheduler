package com.telcobright.scheduler;

import com.telcobright.api.ShardingRepository;
import com.telcobright.core.repository.GenericMultiTableRepository;
import com.telcobright.scheduler.config.AppConfig;
import com.telcobright.scheduler.entity.GenericSchedulableEntity;
import com.telcobright.scheduler.job.GenericJob;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.google.gson.Gson;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Entity-agnostic infinite scheduler for a single application.
 * Works with Map<String, Object> instead of specific entity types.
 */
public class InfiniteAppScheduler {

    private static final Logger logger = LoggerFactory.getLogger(InfiniteAppScheduler.class);

    private final AppConfig appConfig;
    private final ShardingRepository<GenericSchedulableEntity, LocalDateTime> repository;
    private final Scheduler quartzScheduler;
    private final AppJobHistoryTracker historyTracker;
    private final DatabaseStatusUpdater statusUpdater;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread fetcherThread;
    private final boolean ownsQuartzScheduler;
    private final Gson gson = new Gson();

    /**
     * Create a scheduler for an application using a shared Quartz instance.
     */
    public InfiniteAppScheduler(AppConfig appConfig,
                               Scheduler sharedQuartzScheduler,
                               String mysqlHost, int mysqlPort,
                               String mysqlDatabase, String mysqlUsername,
                               String mysqlPassword) {
        this.appConfig = appConfig;
        this.quartzScheduler = sharedQuartzScheduler;
        this.ownsQuartzScheduler = false;

        // Create split-verse repository for GenericSchedulableEntity
        this.repository = GenericMultiTableRepository
            .<GenericSchedulableEntity, LocalDateTime>builder(GenericSchedulableEntity.class)
            .database(mysqlDatabase)
            .tablePrefix(appConfig.getTablePrefix())
            .host(mysqlHost)
            .port(mysqlPort)
            .username(mysqlUsername)
            .password(mysqlPassword)
            .build();

        // Create data source for history tracker and status updater
        DataSource dataSource = createDataSource(mysqlHost, mysqlPort, mysqlDatabase,
            mysqlUsername, mysqlPassword);

        // App-specific history tracker
        this.historyTracker = new AppJobHistoryTracker(dataSource, appConfig.getAppName(),
            appConfig.getHistoryTableName());

        // Status updater for marking entities as scheduled
        this.statusUpdater = new DatabaseStatusUpdater(dataSource);

        logger.info("InfiniteAppScheduler created for app '{}' with table prefix '{}' and history table '{}'",
            appConfig.getAppName(), appConfig.getTablePrefix(), appConfig.getHistoryTableName());
    }

    /**
     * Schedule a job using a Map (no entity knowledge needed).
     */
    public void scheduleJob(Map<String, Object> jobData) {
        // Ensure required fields
        if (!jobData.containsKey("scheduledTime")) {
            throw new IllegalArgumentException("jobData must contain 'scheduledTime'");
        }

        // Create generic entity wrapper
        GenericSchedulableEntity entity = new GenericSchedulableEntity(appConfig.getAppName(), jobData);

        // Save to split-verse repository
        try {
            repository.insert(entity);
            logger.info("Scheduled job for app '{}' with ID '{}' to execute at {}",
                appConfig.getAppName(), entity.getId(), entity.getScheduledTime());
        } catch (Exception e) {
            logger.error("Failed to schedule job for app '{}'", appConfig.getAppName(), e);
            throw new RuntimeException("Failed to schedule job", e);
        }
    }

    /**
     * Start the scheduler's fetcher thread.
     */
    public void start() throws SchedulerException {
        if (running.compareAndSet(false, true)) {
            // Start fetcher thread
            fetcherThread = new Thread(this::runFetcher, "fetcher-" + appConfig.getAppName());
            fetcherThread.start();

            logger.info("Started scheduler for app '{}'", appConfig.getAppName());
        }
    }

    /**
     * Stop the scheduler.
     */
    public void stop() throws SchedulerException {
        if (running.compareAndSet(true, false)) {
            // Stop fetcher thread
            if (fetcherThread != null) {
                fetcherThread.interrupt();
                try {
                    fetcherThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            logger.info("Stopped scheduler for app '{}'", appConfig.getAppName());
        }
    }

    private void runFetcher() {
        logger.info("Fetcher thread started for app '{}'", appConfig.getAppName());

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                fetchAndScheduleJobs();
                Thread.sleep(appConfig.getFetchIntervalSeconds() * 1000L);
            } catch (InterruptedException e) {
                logger.info("Fetcher thread interrupted for app '{}'", appConfig.getAppName());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in fetcher thread for app '{}'", appConfig.getAppName(), e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        logger.info("Fetcher thread stopped for app '{}'", appConfig.getAppName());
    }

    private void fetchAndScheduleJobs() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime endTime = now.plusSeconds(appConfig.getLookaheadWindowSeconds());

            logger.debug("Fetching entities for app '{}' from {} to {}",
                appConfig.getAppName(), now, endTime);

            // Query by scheduled_time range
            LocalDateTime queryStartTime = now.minusMinutes(5);
            LocalDateTime queryEndTime = endTime.plusMinutes(5);

            List<GenericSchedulableEntity> entities;
            try {
                entities = repository.findAllByDateRange(queryStartTime, queryEndTime);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("doesn't exist")) {
                    logger.debug("No tables found for app '{}' in date range {} to {}",
                        appConfig.getAppName(), queryStartTime.toLocalDate(), queryEndTime.toLocalDate());
                    return;
                } else {
                    throw e;
                }
            }

            // Filter entities in the actual lookahead window and not yet scheduled
            List<GenericSchedulableEntity> candidateEntities = entities.stream()
                .filter(entity -> {
                    LocalDateTime scheduledTime = entity.getScheduledTime();
                    return scheduledTime != null &&
                           !scheduledTime.isBefore(now) &&
                           !scheduledTime.isAfter(endTime) &&
                           (entity.getScheduled() == null || !entity.getScheduled());
                })
                .limit(appConfig.getMaxJobsPerFetch())
                .toList();

            if (candidateEntities.isEmpty()) {
                return;
            }

            logger.info("Found {} entities to schedule for app '{}'",
                candidateEntities.size(), appConfig.getAppName());

            // Check which entities already have jobs in Quartz
            Set<JobKey> existingJobKeys = getExistingJobKeys(candidateEntities);

            for (GenericSchedulableEntity entity : candidateEntities) {
                JobKey jobKey = JobKey.jobKey(entity.getJobId(), appConfig.getAppName());
                if (!existingJobKeys.contains(jobKey)) {
                    scheduleQuartzJob(entity);
                }
            }

        } catch (Exception e) {
            logger.error("Error fetching and scheduling jobs for app '{}'",
                appConfig.getAppName(), e);
        }
    }

    private void scheduleQuartzJob(GenericSchedulableEntity entity) {
        try {
            Map<String, Object> jobData = entity.getJobData();

            // Add app context
            jobData.put("appName", appConfig.getAppName());
            jobData.put("entityId", entity.getId());
            jobData.put("jobName", entity.getJobName());

            // Add queue configuration to job data
            if (appConfig.getQueueConfig() != null) {
                jobData.put("queueType", appConfig.getQueueConfig().getQueueType().toString());
                jobData.put("topicName", appConfig.getQueueConfig().getTopicName());
                jobData.put("brokerAddress", appConfig.getQueueConfig().getBrokerAddress());
            } else {
                jobData.put("queueType", "CONSOLE");
                jobData.put("topicName", "console");
                jobData.put("brokerAddress", "");
            }

            String finalJobDataJson = gson.toJson(jobData);

            // Create Quartz job
            JobDetail job = JobBuilder.newJob(GenericJob.class)
                .withIdentity(entity.getJobId(), appConfig.getAppName())
                .build();

            // Store job data as JSON string to preserve types through Quartz persistence
            job.getJobDataMap().put("jobDataJson", finalJobDataJson);
            job.getJobDataMap().put("appName", appConfig.getAppName());
            job.getJobDataMap().put("entityId", entity.getId());

            // Add queue configuration if present
            if (appConfig.getQueueConfig() != null) {
                job.getJobDataMap().put("queueType", appConfig.getQueueConfig().getQueueType().toString());
                job.getJobDataMap().put("topicName", appConfig.getQueueConfig().getTopicName());
                job.getJobDataMap().put("brokerAddress", appConfig.getQueueConfig().getBrokerAddress());
            }

            // Create trigger
            Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("trigger-" + entity.getId(), appConfig.getAppName())
                .startAt(Date.from(entity.getScheduledTime().atZone(ZoneId.systemDefault()).toInstant()))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                    .withMisfireHandlingInstructionFireNow())
                .build();

            // Schedule with Quartz
            quartzScheduler.scheduleJob(job, trigger);

            // Mark as scheduled in database
            entity.setScheduled(true);
            statusUpdater.markAsScheduled(entity.getId(), entity.getScheduledTime(),
                appConfig.getTablePrefix());

            // Track in history
            historyTracker.recordJobScheduled(
                entity.getJobId(),
                entity.getJobName(),
                appConfig.getAppName(),  // job group
                entity.getId(),
                entity.getScheduledTime(),
                gson.toJson(jobData)  // job data as JSON
            );

            logger.info("Scheduled job '{}' for app '{}' to execute at {}",
                entity.getJobId(), appConfig.getAppName(), entity.getScheduledTime());

        } catch (Exception e) {
            logger.error("Failed to schedule job for entity '{}' in app '{}'",
                entity.getId(), appConfig.getAppName(), e);
        }
    }

    private Set<JobKey> getExistingJobKeys(List<GenericSchedulableEntity> entities) {
        Set<JobKey> existingKeys = new HashSet<>();
        try {
            Set<JobKey> allJobKeys = quartzScheduler.getJobKeys(
                GroupMatcher.groupEquals(appConfig.getAppName()));

            Set<String> entityJobIds = entities.stream()
                .map(GenericSchedulableEntity::getJobId)
                .collect(Collectors.toSet());

            for (JobKey key : allJobKeys) {
                if (entityJobIds.contains(key.getName())) {
                    existingKeys.add(key);
                }
            }
        } catch (SchedulerException e) {
            logger.error("Error checking existing jobs for app '{}'", appConfig.getAppName(), e);
        }
        return existingKeys;
    }

    private DataSource createDataSource(String host, int port, String database,
                                       String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setPoolName("InfiniteScheduler-" + appConfig.getAppName());

        return new HikariDataSource(config);
    }

    public AppConfig getAppConfig() {
        return appConfig;
    }

    public ShardingRepository<GenericSchedulableEntity, LocalDateTime> getRepository() {
        return repository;
    }
}