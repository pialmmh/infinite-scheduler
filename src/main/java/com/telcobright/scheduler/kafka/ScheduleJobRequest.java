package com.telcobright.scheduler.kafka;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Request model for scheduling a job through Kafka.
 *
 * Example JSON:
 * {
 *   "requestId": "uuid",
 *   "appName": "sms",
 *   "scheduledTime": "2025-11-03T22:00:00",
 *   "jobData": {
 *     "phoneNumber": "+880123456789",
 *     "message": "Hello World",
 *     "senderId": "MyApp"
 *   }
 * }
 */
public class ScheduleJobRequest {

    private static final Gson gson = new GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        .create();

    private String requestId;
    private String appName;
    private LocalDateTime scheduledTime;
    private Map<String, Object> jobData;
    private String priority; // Optional: high, normal, low
    private String idempotencyKey; // Optional: for duplicate detection

    public ScheduleJobRequest() {
        this.requestId = UUID.randomUUID().toString();
        this.jobData = new HashMap<>();
    }

    public ScheduleJobRequest(String appName, LocalDateTime scheduledTime, Map<String, Object> jobData) {
        this();
        this.appName = appName;
        this.scheduledTime = scheduledTime;
        this.jobData = jobData != null ? new HashMap<>(jobData) : new HashMap<>();
    }

    /**
     * Convert to job data map for scheduler.
     */
    public Map<String, Object> toJobDataMap() {
        Map<String, Object> data = new HashMap<>(jobData);
        data.put("scheduledTime", scheduledTime);
        data.put("requestId", requestId);
        if (idempotencyKey != null) {
            data.put("idempotencyKey", idempotencyKey);
        }
        if (priority != null) {
            data.put("priority", priority);
        }
        return data;
    }

    /**
     * Validate the request.
     */
    public void validate() {
        if (appName == null || appName.trim().isEmpty()) {
            throw new IllegalArgumentException("appName is required");
        }
        if (scheduledTime == null) {
            throw new IllegalArgumentException("scheduledTime is required");
        }
        if (jobData == null) {
            jobData = new HashMap<>();
        }
    }

    /**
     * Parse from JSON string.
     */
    public static ScheduleJobRequest fromJson(String json) {
        return gson.fromJson(json, ScheduleJobRequest.class);
    }

    /**
     * Convert to JSON string.
     */
    public String toJson() {
        return gson.toJson(this);
    }

    // Getters and Setters

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(LocalDateTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public Map<String, Object> getJobData() {
        return jobData;
    }

    public void setJobData(Map<String, Object> jobData) {
        this.jobData = jobData;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public String toString() {
        return "ScheduleJobRequest{" +
            "requestId='" + requestId + '\'' +
            ", appName='" + appName + '\'' +
            ", scheduledTime=" + scheduledTime +
            ", priority='" + priority + '\'' +
            ", idempotencyKey='" + idempotencyKey + '\'' +
            ", jobData=" + jobData +
            '}';
    }
}
