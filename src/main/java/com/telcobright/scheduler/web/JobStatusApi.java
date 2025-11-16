package com.telcobright.scheduler.web;

import com.google.gson.Gson;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for job status monitoring
 */
public class JobStatusApi {

    private static final Logger logger = LoggerFactory.getLogger(JobStatusApi.class);
    private final DataSource dataSource;
    private final Gson gson = new Gson();
    private Javalin app;

    public JobStatusApi(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void start(int port) {
        app = Javalin.create(config -> {
            config.staticFiles.add("/public");
            config.plugins.enableCors(cors -> {
                cors.add(it -> {
                    it.anyHost();
                });
            });
        }).start(port);

        // API endpoints
        app.get("/api/jobs/scheduled", this::getScheduledJobs);
        app.get("/api/jobs/history", this::getJobHistory);
        app.get("/api/jobs/stats", this::getJobStats);
        app.get("/api/tables", this::getSplitVerseTables);

        logger.info("Job Status API started on port {}", port);
        logger.info("Access UI at: http://localhost:{}/index.html", port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
            logger.info("Job Status API stopped");
        }
    }

    private void getScheduledJobs(Context ctx) {
        try (Connection conn = dataSource.getConnection()) {
            // Query all app history tables with UNION ALL
            String sql = "SELECT id, job_id, job_name, job_group, app_name, entity_id, scheduled_time, status, created_at, job_data " +
                        "FROM sms_job_execution_history " +
                        "WHERE status = 'SCHEDULED' OR status = 'STARTED' " +
                        "UNION ALL " +
                        "SELECT id, job_id, job_name, job_group, app_name, entity_id, scheduled_time, status, created_at, job_data " +
                        "FROM sipcall_job_execution_history " +
                        "WHERE status = 'SCHEDULED' OR status = 'STARTED' " +
                        "UNION ALL " +
                        "SELECT id, job_id, job_name, job_group, app_name, entity_id, scheduled_time, status, created_at, job_data " +
                        "FROM payment_gateway_job_execution_history " +
                        "WHERE status = 'SCHEDULED' OR status = 'STARTED' " +
                        "ORDER BY scheduled_time ASC " +
                        "LIMIT 100";

            List<Map<String, Object>> jobs = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    Map<String, Object> job = new HashMap<>();
                    job.put("id", rs.getLong("id"));
                    job.put("jobId", rs.getString("job_id"));
                    job.put("jobName", rs.getString("job_name"));
                    job.put("jobGroup", rs.getString("job_group"));
                    job.put("appName", rs.getString("app_name"));
                    job.put("entityId", rs.getString("entity_id"));
                    job.put("scheduledTime", rs.getTimestamp("scheduled_time").toString());
                    job.put("status", rs.getString("status"));
                    job.put("createdAt", rs.getTimestamp("created_at").toString());

                    // Extract queue configuration from job_data JSON
                    String jobDataJson = rs.getString("job_data");
                    if (jobDataJson != null && !jobDataJson.isEmpty()) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> jobData = gson.fromJson(jobDataJson, Map.class);
                            if (jobData != null) {
                                job.put("queueType", jobData.get("queueType"));
                                job.put("topicName", jobData.get("topicName"));
                                job.put("brokerAddress", jobData.get("brokerAddress"));
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to parse job_data JSON for job {}", job.get("jobId"), e);
                        }
                    }

                    jobs.add(job);
                }
            }

            ctx.json(jobs);

        } catch (SQLException e) {
            logger.error("Error fetching scheduled jobs", e);
            ctx.status(500).result("Error fetching scheduled jobs: " + e.getMessage());
        }
    }

    private void getJobHistory(Context ctx) {
        try (Connection conn = dataSource.getConnection()) {
            // Query all app history tables with UNION ALL
            String sql = "SELECT id, job_id, job_name, job_group, app_name, entity_id, scheduled_time, started_at, " +
                        "completed_at, status, error_message, execution_duration_ms, job_data " +
                        "FROM sms_job_execution_history " +
                        "WHERE status IN ('COMPLETED', 'FAILED') " +
                        "UNION ALL " +
                        "SELECT id, job_id, job_name, job_group, app_name, entity_id, scheduled_time, started_at, " +
                        "completed_at, status, error_message, execution_duration_ms, job_data " +
                        "FROM sipcall_job_execution_history " +
                        "WHERE status IN ('COMPLETED', 'FAILED') " +
                        "UNION ALL " +
                        "SELECT id, job_id, job_name, job_group, app_name, entity_id, scheduled_time, started_at, " +
                        "completed_at, status, error_message, execution_duration_ms, job_data " +
                        "FROM payment_gateway_job_execution_history " +
                        "WHERE status IN ('COMPLETED', 'FAILED') " +
                        "ORDER BY completed_at DESC " +
                        "LIMIT 100";

            List<Map<String, Object>> jobs = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    Map<String, Object> job = new HashMap<>();
                    job.put("id", rs.getLong("id"));
                    job.put("jobId", rs.getString("job_id"));
                    job.put("jobName", rs.getString("job_name"));
                    job.put("jobGroup", rs.getString("job_group"));
                    job.put("appName", rs.getString("app_name"));
                    job.put("entityId", rs.getString("entity_id"));
                    job.put("scheduledTime", rs.getTimestamp("scheduled_time").toString());

                    Timestamp startedAt = rs.getTimestamp("started_at");
                    job.put("startedAt", startedAt != null ? startedAt.toString() : null);

                    Timestamp completedAt = rs.getTimestamp("completed_at");
                    job.put("completedAt", completedAt != null ? completedAt.toString() : null);

                    job.put("status", rs.getString("status"));
                    job.put("errorMessage", rs.getString("error_message"));
                    job.put("executionDurationMs", rs.getLong("execution_duration_ms"));

                    // Extract queue configuration from job_data JSON
                    String jobDataJson = rs.getString("job_data");
                    if (jobDataJson != null && !jobDataJson.isEmpty()) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> jobData = gson.fromJson(jobDataJson, Map.class);
                            if (jobData != null) {
                                job.put("queueType", jobData.get("queueType"));
                                job.put("topicName", jobData.get("topicName"));
                                job.put("brokerAddress", jobData.get("brokerAddress"));
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to parse job_data JSON for job {}", job.get("jobId"), e);
                        }
                    }

                    jobs.add(job);
                }
            }

            ctx.json(jobs);

        } catch (SQLException e) {
            logger.error("Error fetching job history", e);
            ctx.status(500).result("Error fetching job history: " + e.getMessage());
        }
    }

    private void getJobStats(Context ctx) {
        try (Connection conn = dataSource.getConnection()) {
            Map<String, Object> stats = new HashMap<>();

            // Count by status across all app tables
            String countSql = "SELECT status, COUNT(*) as count FROM ( " +
                             "SELECT status FROM sms_job_execution_history " +
                             "UNION ALL " +
                             "SELECT status FROM sipcall_job_execution_history " +
                             "UNION ALL " +
                             "SELECT status FROM payment_gateway_job_execution_history " +
                             ") AS all_jobs GROUP BY status";
            try (PreparedStatement stmt = conn.prepareStatement(countSql);
                 ResultSet rs = stmt.executeQuery()) {

                Map<String, Integer> statusCounts = new HashMap<>();
                while (rs.next()) {
                    statusCounts.put(rs.getString("status"), rs.getInt("count"));
                }
                stats.put("statusCounts", statusCounts);
            }

            // Total jobs across all app tables
            String totalSql = "SELECT COUNT(*) as total FROM ( " +
                             "SELECT 1 FROM sms_job_execution_history " +
                             "UNION ALL " +
                             "SELECT 1 FROM sipcall_job_execution_history " +
                             "UNION ALL " +
                             "SELECT 1 FROM payment_gateway_job_execution_history " +
                             ") AS all_jobs";
            try (PreparedStatement stmt = conn.prepareStatement(totalSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalJobs", rs.getInt("total"));
                }
            }

            ctx.json(stats);

        } catch (SQLException e) {
            logger.error("Error fetching job stats", e);
            ctx.status(500).result("Error fetching job stats: " + e.getMessage());
        }
    }

    private void getSplitVerseTables(Context ctx) {
        try (Connection conn = dataSource.getConnection()) {
            // First, get the current database name
            String databaseName;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT DATABASE()");
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    databaseName = rs.getString(1);
                } else {
                    throw new SQLException("Could not determine database name");
                }
            }

            // Query information_schema for Split-Verse tables
            String sql = "SELECT " +
                        "    TABLE_NAME, " +
                        "    CREATE_TIME, " +
                        "    TABLE_ROWS, " +
                        "    DATA_LENGTH, " +
                        "    INDEX_LENGTH " +
                        "FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = ? " +
                        "    AND TABLE_NAME LIKE 'scheduled_jobs_%' " +
                        "ORDER BY TABLE_NAME DESC";

            List<Map<String, Object>> tables = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, databaseName);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> table = new HashMap<>();
                        table.put("tableName", rs.getString("TABLE_NAME"));

                        Timestamp createTime = rs.getTimestamp("CREATE_TIME");
                        table.put("createTime", createTime != null ? createTime.toString() : null);

                        table.put("rowCount", rs.getLong("TABLE_ROWS"));
                        table.put("dataLength", rs.getLong("DATA_LENGTH"));
                        table.put("indexLength", rs.getLong("INDEX_LENGTH"));

                        // Calculate total size in MB
                        long totalBytes = rs.getLong("DATA_LENGTH") + rs.getLong("INDEX_LENGTH");
                        double totalMB = totalBytes / (1024.0 * 1024.0);
                        table.put("totalSizeMB", String.format("%.2f", totalMB));

                        // Extract date from table name (e.g., scheduled_jobs_20251115)
                        String tableName = rs.getString("TABLE_NAME");
                        if (tableName.length() >= 20) {
                            String dateStr = tableName.substring(15); // Extract YYYYMMDD
                            table.put("tableDate", dateStr);
                        }

                        tables.add(table);
                    }
                }
            }

            // Add metadata
            Map<String, Object> response = new HashMap<>();
            response.put("database", databaseName);
            response.put("tableCount", tables.size());
            response.put("tables", tables);

            ctx.json(response);

        } catch (SQLException e) {
            logger.error("Error fetching Split-Verse tables", e);
            ctx.status(500).result("Error fetching Split-Verse tables: " + e.getMessage());
        }
    }
}
