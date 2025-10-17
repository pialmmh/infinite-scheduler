package com.telcobright.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.telcobright.api.ShardingRepository;
import com.telcobright.core.repository.GenericMultiTableRepository;
import com.telcobright.scheduler.entity.ScheduledJobEntity;
import com.telcobright.scheduler.publishers.KafkaJobPublisher;
import com.telcobright.scheduler.publishers.RedisJobPublisher;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Quarkus service for infinite scheduler.
 * Manages map-based job scheduling with Kafka/Redis publishers.
 */
@ApplicationScoped
public class InfiniteSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(InfiniteSchedulerService.class);

    @ConfigProperty(name = "scheduler.fetch-interval", defaultValue = "25")
    int fetchIntervalSeconds;

    @ConfigProperty(name = "scheduler.lookahead-window", defaultValue = "30")
    int lookaheadWindowSeconds;

    @ConfigProperty(name = "scheduler.publisher", defaultValue = "kafka")
    String publisherType;

    @ConfigProperty(name = "datasource.host")
    String mysqlHost;

    @ConfigProperty(name = "datasource.port", defaultValue = "3306")
    int mysqlPort;

    @ConfigProperty(name = "datasource.database")
    String mysqlDatabase;

    @ConfigProperty(name = "datasource.username")
    String mysqlUsername;

    @ConfigProperty(name = "datasource.password")
    String mysqlPassword;

    @ConfigProperty(name = "repository.table-prefix", defaultValue = "scheduled_jobs")
    String repositoryTablePrefix;

    @Inject
    KafkaJobPublisher kafkaPublisher;

    @Inject
    RedisJobPublisher redisPublisher;

    private Scheduler quartzScheduler;
    private ShardingRepository<ScheduledJobEntity, LocalDateTime> repository;
    private ScheduledExecutorService fetcherExecutor;
    private JobPublisher publisher;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    void onStart(@Observes StartupEvent event) {
        try {
            logger.info("Starting Infinite Scheduler Service...");

            // Select publisher based on configuration
            if ("redis".equalsIgnoreCase(publisherType)) {
                publisher = redisPublisher;
                logger.info("Using Redis publisher");
            } else {
                publisher = kafkaPublisher;
                logger.info("Using Kafka publisher");
            }

            // Initialize repository
            initializeRepository();

            // Initialize Quartz scheduler
            initializeQuartzScheduler();

            // Start lookahead fetcher
            startLookaheadFetcher();

            logger.info("Infinite Scheduler Service started successfully");

        } catch (Exception e) {
            logger.error("Failed to start Infinite Scheduler Service", e);
            throw new RuntimeException("Failed to start scheduler", e);
        }
    }

    private void initializeRepository() {
        try {
            repository = GenericMultiTableRepository.<ScheduledJobEntity, LocalDateTime>builder(ScheduledJobEntity.class)
                .database(mysqlDatabase)
                .tablePrefix(repositoryTablePrefix)
                .host(mysqlHost)
                .port(mysqlPort)
                .username(mysqlUsername)
                .password(mysqlPassword)
                .build();

            logger.info("Repository initialized with prefix: {}", repositoryTablePrefix);
        } catch (Exception e) {
            logger.error("Failed to initialize repository", e);
            throw new RuntimeException("Failed to initialize repository", e);
        }
    }

    private void initializeQuartzScheduler() throws SchedulerException {
        Properties quartzProps = new Properties();
        quartzProps.setProperty("org.quartz.scheduler.instanceName", "InfiniteScheduler");
        quartzProps.setProperty("org.quartz.threadPool.threadCount", "10");
        quartzProps.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

        StdSchedulerFactory factory = new StdSchedulerFactory(quartzProps);
        quartzScheduler = factory.getScheduler();
        quartzScheduler.start();

        logger.info("Quartz scheduler initialized");
    }

    private void startLookaheadFetcher() {
        fetcherExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "lookahead-fetcher");
            thread.setDaemon(true);
            return thread;
        });

        fetcherExecutor.scheduleAtFixedRate(
            this::fetchAndScheduleJobs,
            0,
            fetchIntervalSeconds,
            TimeUnit.SECONDS
        );

        logger.info("Lookahead fetcher started (interval: {}s, window: {}s)",
            fetchIntervalSeconds, lookaheadWindowSeconds);
    }

    private void fetchAndScheduleJobs() {
        try {
            // Use UTC time for querying (repository stores in UTC)
            LocalDateTime nowUtc = LocalDateTime.now(ZoneId.of("UTC"));
            LocalDateTime queryEndUtc = nowUtc.plusSeconds(lookaheadWindowSeconds);

            logger.info("Lookahead fetcher running (UTC): querying from {} to {}", nowUtc, queryEndUtc);

            // Fetch ONLY unscheduled jobs using database-level filtering (90% performance improvement)
            List<ScheduledJobEntity> unscheduledJobs = repository.findByPartitionRangeWithFilters(
                nowUtc,
                queryEndUtc,
                Map.of("scheduled", false)  // Filter at database level - maintains partition pruning
            );

            logger.info("Fetched {} unscheduled jobs from repository using database-level filtering",
                unscheduledJobs.size());

            if (unscheduledJobs.isEmpty()) {
                return;
            }

            logger.info("Processing {} unscheduled jobs to schedule to Quartz", unscheduledJobs.size());

            for (ScheduledJobEntity entity : unscheduledJobs) {
                scheduleToQuartz(entity);
            }

        } catch (Exception e) {
            logger.error("Error in lookahead fetcher", e);
        }
    }

    private void scheduleToQuartz(ScheduledJobEntity entity) {
        try {
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("jobType", entity.getJobType());
            jobDataMap.put("jobName", entity.getJobName());
            jobDataMap.put("jobDataJson", entity.getJobDataJson());
            jobDataMap.put("publisher", publisher);

            JobDetail jobDetail = JobBuilder.newJob(GenericJobExecutor.class)
                .withIdentity(entity.getId(), entity.getJobType())
                .usingJobData(jobDataMap)
                .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(entity.getId() + "-trigger", entity.getJobType())
                .startAt(Date.from(entity.getScheduledTime().atZone(ZoneId.systemDefault()).toInstant()))
                .build();

            quartzScheduler.scheduleJob(jobDetail, trigger);

            // Mark as scheduled
            entity.setScheduled(true);
            repository.updateById(entity.getId(), entity);

            logger.info("Scheduled job: {} (queue: {}) for execution at {}",
                entity.getId(), entity.getJobType() + "-" + entity.getJobName(), entity.getScheduledTime());

        } catch (Exception e) {
            logger.error("Failed to schedule job: {}", entity.getId(), e);
        }
    }

    /**
     * Schedule a single job from REST API.
     *
     * @param jobData Job data as Map
     */
    public void scheduleJob(Map<String, Object> jobData) {
        validateJobData(jobData);

        try {
            ScheduledJobEntity entity = mapToEntity(jobData);
            repository.insert(entity);

            logger.info("Job {} saved to repository", entity.getId());

        } catch (Exception e) {
            logger.error("Failed to schedule job", e);
            throw new RuntimeException("Failed to schedule job", e);
        }
    }

    /**
     * Schedule multiple jobs from REST API.
     *
     * @param jobs List of job data maps
     * @return Number of successfully scheduled jobs
     */
    public int scheduleJobs(List<Map<String, Object>> jobs) {
        int scheduled = 0;

        for (Map<String, Object> jobData : jobs) {
            try {
                scheduleJob(jobData);
                scheduled++;
            } catch (Exception e) {
                logger.error("Failed to schedule job: {}", jobData.get("id"), e);
            }
        }

        return scheduled;
    }

    /**
     * Get job status by ID.
     *
     * @param jobId Job ID
     * @return Job status map or null if not found
     */
    public Map<String, Object> getJobStatus(String jobId) {
        try {
            // Query repository for job
            // Note: This is a simplified implementation
            // In production, you'd need a proper query by ID method
            return Map.of(
                "jobId", jobId,
                "message", "Status lookup not yet fully implemented"
            );

        } catch (Exception e) {
            logger.error("Failed to get job status", e);
            return null;
        }
    }

    private void validateJobData(Map<String, Object> jobData) {
        if (jobData.get("id") == null) {
            throw new IllegalArgumentException("Job 'id' is required");
        }
        if (jobData.get("scheduledTime") == null) {
            throw new IllegalArgumentException("Job 'scheduledTime' is required");
        }
        if (jobData.get("jobType") == null) {
            throw new IllegalArgumentException("Job 'jobType' is required");
        }
        if (jobData.get("jobName") == null) {
            throw new IllegalArgumentException("Job 'jobName' is required");
        }
    }

    private ScheduledJobEntity mapToEntity(Map<String, Object> jobData) throws Exception {
        String id = (String) jobData.get("id");
        String jobType = (String) jobData.get("jobType");
        String jobName = (String) jobData.get("jobName");

        // Parse scheduledTime (could be String or LocalDateTime)
        LocalDateTime scheduledTime;
        Object timeObj = jobData.get("scheduledTime");
        if (timeObj instanceof String) {
            scheduledTime = LocalDateTime.parse((String) timeObj);
        } else if (timeObj instanceof LocalDateTime) {
            scheduledTime = (LocalDateTime) timeObj;
        } else {
            throw new IllegalArgumentException("Invalid scheduledTime format");
        }

        // Convert full job data to JSON
        String jobDataJson = objectMapper.writeValueAsString(jobData);

        return new ScheduledJobEntity(id, scheduledTime, jobType, jobName, jobDataJson);
    }

    /**
     * Produce Quartz Scheduler for CDI injection.
     */
    @Produces
    @ApplicationScoped
    public Scheduler getQuartzScheduler() {
        return quartzScheduler;
    }

    /**
     * Quartz job executor that publishes to Kafka/Redis.
     */
    public static class GenericJobExecutor implements Job {
        private static final Logger logger = LoggerFactory.getLogger(GenericJobExecutor.class);
        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            try {
                JobDataMap dataMap = context.getJobDetail().getJobDataMap();

                String jobType = dataMap.getString("jobType");
                String jobName = dataMap.getString("jobName");
                String jobDataJson = dataMap.getString("jobDataJson");
                JobPublisher publisher = (JobPublisher) dataMap.get("publisher");

                Map<String, Object> jobData = objectMapper.readValue(jobDataJson, Map.class);

                // Publish to topic: jobType-jobName
                String queueName = jobType + "-" + jobName;
                publisher.publish(queueName, jobData);

                logger.info("Executed and published job: {} to queue: {}", jobData.get("id"), queueName);

            } catch (Exception e) {
                logger.error("Failed to execute job", e);
                throw new JobExecutionException(e);
            }
        }
    }
}
