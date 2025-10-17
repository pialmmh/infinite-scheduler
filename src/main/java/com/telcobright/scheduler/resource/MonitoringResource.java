package com.telcobright.scheduler.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for monitoring scheduler jobs (UI backend).
 * Provides job listings, statistics, and execution history.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class MonitoringResource {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringResource.class);

    @Inject
    org.quartz.Scheduler quartzScheduler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get summary statistics for the dashboard.
     */
    @GET
    @Path("/summary")
    public Map<String, Object> getSummary() {
        try {
            // Get all scheduled jobs
            Set<JobKey> jobKeys = quartzScheduler.getJobKeys(GroupMatcher.anyGroup());

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneHourAhead = now.plusHours(1);

            long scheduledNext1Hour = jobKeys.stream()
                .filter(jobKey -> {
                    try {
                        List<? extends Trigger> triggers = quartzScheduler.getTriggersOfJob(jobKey);
                        if (triggers.isEmpty()) return false;

                        Date nextFire = triggers.get(0).getNextFireTime();
                        if (nextFire == null) return false;

                        LocalDateTime nextFireTime = LocalDateTime.ofInstant(
                            nextFire.toInstant(), ZoneId.systemDefault());

                        return !nextFireTime.isAfter(oneHourAhead);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();

            return Map.of(
                "scheduledNext1Hour", scheduledNext1Hour,
                "completedLast1Hour", 0, // TODO: Implement history tracking
                "totalExecuted", 0 // TODO: Implement history tracking
            );

        } catch (Exception e) {
            logger.error("Error getting summary", e);
            return Map.of(
                "scheduledNext1Hour", 0,
                "completedLast1Hour", 0,
                "totalExecuted", 0
            );
        }
    }

    /**
     * Get paginated list of scheduled jobs.
     */
    @GET
    @Path("/jobs")
    public Map<String, Object> getJobs(
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {

        try {
            // Get all job keys
            Set<JobKey> allJobKeys = quartzScheduler.getJobKeys(GroupMatcher.anyGroup());
            List<JobKey> jobKeysList = new ArrayList<>(allJobKeys);

            // Sort by next fire time
            jobKeysList.sort((k1, k2) -> {
                try {
                    List<? extends Trigger> t1 = quartzScheduler.getTriggersOfJob(k1);
                    List<? extends Trigger> t2 = quartzScheduler.getTriggersOfJob(k2);

                    Date d1 = t1.isEmpty() ? null : t1.get(0).getNextFireTime();
                    Date d2 = t2.isEmpty() ? null : t2.get(0).getNextFireTime();

                    if (d1 == null && d2 == null) return 0;
                    if (d1 == null) return 1;
                    if (d2 == null) return -1;
                    return d1.compareTo(d2);
                } catch (Exception e) {
                    return 0;
                }
            });

            // Apply pagination
            List<Map<String, Object>> jobs = jobKeysList.stream()
                .skip(offset)
                .limit(limit)
                .map(jobKey -> {
                    try {
                        return buildJobInfo(jobKey);
                    } catch (Exception e) {
                        logger.error("Error building job info for {}", jobKey, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            return Map.of("jobs", jobs);

        } catch (Exception e) {
            logger.error("Error getting jobs", e);
            return Map.of("jobs", Collections.emptyList());
        }
    }

    /**
     * Get execution history (currently returns empty - requires history tracking).
     */
    @GET
    @Path("/history")
    public Map<String, Object> getHistory(
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {

        // TODO: Implement history tracking
        // For now, return empty list
        return Map.of("jobs", Collections.emptyList());
    }

    private Map<String, Object> buildJobInfo(JobKey jobKey) throws SchedulerException {
        JobDetail jobDetail = quartzScheduler.getJobDetail(jobKey);
        if (jobDetail == null) {
            return null;
        }

        Map<String, Object> jobInfo = new HashMap<>();

        // Basic info
        jobInfo.put("jobName", jobDetail.getKey().getName());
        jobInfo.put("jobGroup", jobDetail.getKey().getGroup());

        // Get trigger info
        List<? extends Trigger> triggers = quartzScheduler.getTriggersOfJob(jobKey);
        if (!triggers.isEmpty()) {
            Trigger trigger = triggers.get(0);

            if (trigger.getNextFireTime() != null) {
                jobInfo.put("nextFireTime", trigger.getNextFireTime().getTime());
            }

            Trigger.TriggerState state = quartzScheduler.getTriggerState(trigger.getKey());
            jobInfo.put("triggerState", state.name());
        }

        // Get job data (additional parameters)
        JobDataMap dataMap = jobDetail.getJobDataMap();
        Map<String, Object> additionalData = new HashMap<>();

        for (String key : dataMap.getKeys()) {
            Object value = dataMap.get(key);
            if (value != null && !(value instanceof org.quartz.Scheduler)) {
                // Skip complex objects, only include simple types
                if (value instanceof String || value instanceof Number ||
                    value instanceof Boolean || value instanceof Date) {
                    additionalData.put(key, value);
                }
            }
        }

        jobInfo.put("additionalData", additionalData);

        return jobInfo;
    }
}
