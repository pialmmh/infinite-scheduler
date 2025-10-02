package com.telcobright.scheduler.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API servlet for querying Quartz scheduled jobs
 */
public class JobApiServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(JobApiServlet.class);
    private final Scheduler scheduler;
    private final ObjectMapper objectMapper;

    public JobApiServlet(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/jobs")) {
                handleGetAllJobs(req, resp);
            } else if (pathInfo.startsWith("/job/")) {
                handleGetJobDetail(req, resp, pathInfo);
            } else if (pathInfo.equals("/executing")) {
                handleGetExecutingJobs(req, resp);
            } else if (pathInfo.equals("/stats")) {
                handleGetStats(req, resp);
            } else {
                sendError(resp, 404, "Endpoint not found: " + pathInfo);
            }
        } catch (SchedulerException e) {
            logger.error("Scheduler error", e);
            sendError(resp, 500, "Scheduler error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("API error", e);
            sendError(resp, 500, "Internal error: " + e.getMessage());
        }
    }

    private void handleGetAllJobs(HttpServletRequest req, HttpServletResponse resp) throws SchedulerException, IOException {
        // Get filter parameters
        String entityId = req.getParameter("entityId");
        String phoneNumber = req.getParameter("phoneNumber");
        String status = req.getParameter("status");
        String startDateStr = req.getParameter("startDate");
        String endDateStr = req.getParameter("endDate");
        int limit = getIntParameter(req, "limit", 100);
        int offset = getIntParameter(req, "offset", 0);

        List<ScheduledJobInfo> allJobs = getAllScheduledJobs();

        // Apply filters
        List<ScheduledJobInfo> filteredJobs = allJobs.stream()
                .filter(job -> entityId == null || entityId.equals(job.getEntityId()))
                .filter(job -> phoneNumber == null || (job.getPhoneNumber() != null && job.getPhoneNumber().contains(phoneNumber)))
                .filter(job -> status == null || status.equals(job.getTriggerState()))
                .filter(job -> {
                    if (startDateStr == null) return true;
                    if (job.getScheduledTime() == null) return false;
                    LocalDateTime startDate = LocalDateTime.parse(startDateStr);
                    return !job.getScheduledTime().isBefore(startDate);
                })
                .filter(job -> {
                    if (endDateStr == null) return true;
                    if (job.getScheduledTime() == null) return false;
                    LocalDateTime endDate = LocalDateTime.parse(endDateStr);
                    return !job.getScheduledTime().isAfter(endDate);
                })
                .sorted(Comparator.comparing(
                        job -> job.getScheduledTime() != null ? job.getScheduledTime() : LocalDateTime.MIN
                ))
                .collect(Collectors.toList());

        // Apply pagination
        int total = filteredJobs.size();
        List<ScheduledJobInfo> paginatedJobs = filteredJobs.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("total", total);
        response.put("offset", offset);
        response.put("limit", limit);
        response.put("count", paginatedJobs.size());
        response.put("jobs", paginatedJobs);

        sendJson(resp, response);
    }

    private void handleGetJobDetail(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws SchedulerException, IOException {
        String[] parts = pathInfo.split("/");
        if (parts.length < 4) {
            sendError(resp, 400, "Invalid job path. Expected: /job/{group}/{name}");
            return;
        }

        String jobGroup = parts[2];
        String jobName = parts[3];
        JobKey jobKey = new JobKey(jobName, jobGroup);

        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        if (jobDetail == null) {
            sendError(resp, 404, "Job not found: " + jobKey);
            return;
        }

        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        ScheduledJobInfo jobInfo = buildJobInfo(jobDetail, triggers.isEmpty() ? null : triggers.get(0));

        sendJson(resp, jobInfo);
    }

    private void handleGetExecutingJobs(HttpServletRequest req, HttpServletResponse resp) throws SchedulerException, IOException {
        List<JobExecutionContext> executingJobs = scheduler.getCurrentlyExecutingJobs();

        List<ScheduledJobInfo> jobInfoList = executingJobs.stream()
                .map(context -> {
                    try {
                        ScheduledJobInfo info = buildJobInfo(context.getJobDetail(), context.getTrigger());
                        info.setCurrentlyExecuting(true);
                        info.setFireTime(context.getFireTime());
                        info.setScheduledFireTime(context.getScheduledFireTime());
                        return info;
                    } catch (SchedulerException e) {
                        logger.error("Error building job info", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("count", jobInfoList.size());
        response.put("jobs", jobInfoList);

        sendJson(resp, response);
    }

    private void handleGetStats(HttpServletRequest req, HttpServletResponse resp) throws SchedulerException, IOException {
        SchedulerMetaData metaData = scheduler.getMetaData();

        Map<String, Object> stats = new HashMap<>();
        stats.put("schedulerName", metaData.getSchedulerName());
        stats.put("schedulerInstanceId", metaData.getSchedulerInstanceId());
        stats.put("started", metaData.isStarted());
        stats.put("inStandbyMode", metaData.isInStandbyMode());
        stats.put("shutdown", metaData.isShutdown());
        stats.put("threadPoolSize", metaData.getThreadPoolSize());
        stats.put("numberOfJobsExecuted", metaData.getNumberOfJobsExecuted());

        // Count jobs by state
        Map<String, Integer> jobsByState = new HashMap<>();
        int totalJobs = 0;

        for (String groupName : scheduler.getJobGroupNames()) {
            Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName));
            totalJobs += jobKeys.size();

            for (JobKey jobKey : jobKeys) {
                List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
                for (Trigger trigger : triggers) {
                    String state = scheduler.getTriggerState(trigger.getKey()).name();
                    jobsByState.merge(state, 1, Integer::sum);
                }
            }
        }

        stats.put("totalJobs", totalJobs);
        stats.put("jobsByState", jobsByState);
        stats.put("currentlyExecuting", scheduler.getCurrentlyExecutingJobs().size());

        sendJson(resp, stats);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.startsWith("/job/") && pathInfo.endsWith("/pause")) {
                handlePauseJob(req, resp, pathInfo);
            } else if (pathInfo != null && pathInfo.startsWith("/job/") && pathInfo.endsWith("/resume")) {
                handleResumeJob(req, resp, pathInfo);
            } else if (pathInfo != null && pathInfo.startsWith("/job/") && pathInfo.endsWith("/trigger")) {
                handleTriggerJob(req, resp, pathInfo);
            } else {
                sendError(resp, 404, "Endpoint not found: " + pathInfo);
            }
        } catch (SchedulerException e) {
            logger.error("Scheduler error", e);
            sendError(resp, 500, "Scheduler error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("API error", e);
            sendError(resp, 500, "Internal error: " + e.getMessage());
        }
    }

    private void handlePauseJob(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws SchedulerException, IOException {
        JobKey jobKey = extractJobKey(pathInfo, "/pause");
        if (jobKey == null) {
            sendError(resp, 400, "Invalid job path");
            return;
        }

        scheduler.pauseJob(jobKey);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Job paused: " + jobKey);

        sendJson(resp, response);
    }

    private void handleResumeJob(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws SchedulerException, IOException {
        JobKey jobKey = extractJobKey(pathInfo, "/resume");
        if (jobKey == null) {
            sendError(resp, 400, "Invalid job path");
            return;
        }

        scheduler.resumeJob(jobKey);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Job resumed: " + jobKey);

        sendJson(resp, response);
    }

    private void handleTriggerJob(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws SchedulerException, IOException {
        JobKey jobKey = extractJobKey(pathInfo, "/trigger");
        if (jobKey == null) {
            sendError(resp, 400, "Invalid job path");
            return;
        }

        scheduler.triggerJob(jobKey);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Job triggered: " + jobKey);

        sendJson(resp, response);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.startsWith("/job/")) {
                handleDeleteJob(req, resp, pathInfo);
            } else {
                sendError(resp, 404, "Endpoint not found: " + pathInfo);
            }
        } catch (SchedulerException e) {
            logger.error("Scheduler error", e);
            sendError(resp, 500, "Scheduler error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("API error", e);
            sendError(resp, 500, "Internal error: " + e.getMessage());
        }
    }

    private void handleDeleteJob(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws SchedulerException, IOException {
        String[] parts = pathInfo.split("/");
        if (parts.length < 4) {
            sendError(resp, 400, "Invalid job path");
            return;
        }

        String jobGroup = parts[2];
        String jobName = parts[3];
        JobKey jobKey = new JobKey(jobName, jobGroup);

        boolean deleted = scheduler.deleteJob(jobKey);

        Map<String, Object> response = new HashMap<>();
        response.put("success", deleted);
        response.put("message", deleted ? "Job deleted: " + jobKey : "Job not found: " + jobKey);

        sendJson(resp, response);
    }

    // Helper methods
    private List<ScheduledJobInfo> getAllScheduledJobs() throws SchedulerException {
        List<ScheduledJobInfo> jobInfoList = new ArrayList<>();

        for (String groupName : scheduler.getJobGroupNames()) {
            Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName));

            for (JobKey jobKey : jobKeys) {
                JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);

                if (triggers.isEmpty()) {
                    jobInfoList.add(buildJobInfo(jobDetail, null));
                } else {
                    for (Trigger trigger : triggers) {
                        jobInfoList.add(buildJobInfo(jobDetail, trigger));
                    }
                }
            }
        }

        return jobInfoList;
    }

    private ScheduledJobInfo buildJobInfo(JobDetail jobDetail, Trigger trigger) throws SchedulerException {
        ScheduledJobInfo info = new ScheduledJobInfo();

        // Job details
        info.setJobName(jobDetail.getKey().getName());
        info.setJobGroup(jobDetail.getKey().getGroup());
        info.setJobClass(jobDetail.getJobClass().getSimpleName());

        // Job data
        JobDataMap dataMap = jobDetail.getJobDataMap();
        info.setEntityId(dataMap.getString("entityId"));
        info.setPhoneNumber(dataMap.getString("phoneNumber"));
        info.setMessage(dataMap.getString("message"));

        String scheduledTimeStr = dataMap.getString("scheduledTime");
        if (scheduledTimeStr != null) {
            info.setScheduledTime(LocalDateTime.parse(scheduledTimeStr));
        }

        // Trigger details
        if (trigger != null) {
            info.setTriggerName(trigger.getKey().getName());
            info.setTriggerGroup(trigger.getKey().getGroup());
            info.setTriggerState(scheduler.getTriggerState(trigger.getKey()).name());
            info.setNextFireTime(trigger.getNextFireTime());
            info.setPreviousFireTime(trigger.getPreviousFireTime());
            info.setStartTime(trigger.getStartTime());
            info.setEndTime(trigger.getEndTime());
            info.setPriority(trigger.getPriority());
            info.setMayFireAgain(trigger.mayFireAgain());
        }

        return info;
    }

    private JobKey extractJobKey(String pathInfo, String suffix) {
        String path = pathInfo.replace(suffix, "");
        String[] parts = path.split("/");
        if (parts.length < 4) {
            return null;
        }
        return new JobKey(parts[3], parts[2]);
    }

    private int getIntParameter(HttpServletRequest req, String name, int defaultValue) {
        String value = req.getParameter(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void sendJson(HttpServletResponse resp, Object data) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        objectMapper.writeValue(resp.getWriter(), data);
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("status", status);
        objectMapper.writeValue(resp.getWriter(), error);
    }
}
