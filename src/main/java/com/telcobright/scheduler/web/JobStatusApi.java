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
            String sql = "SELECT id, job_id, job_name, job_group, app_name, entity_id, scheduled_time, status, created_at " +
                        "FROM sms_job_execution_history " +
                        "WHERE status = 'SCHEDULED' OR status = 'STARTED' " +
                        "UNION ALL " +
                        "SELECT id, job_id, job_name, job_group, app_name, entity_id, scheduled_time, status, created_at " +
                        "FROM sipcall_job_execution_history " +
                        "WHERE status = 'SCHEDULED' OR status = 'STARTED' " +
                        "UNION ALL " +
                        "SELECT id, job_id, job_name, job_group, app_name, entity_id, scheduled_time, status, created_at " +
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
                        "completed_at, status, error_message, execution_duration_ms " +
                        "FROM sms_job_execution_history " +
                        "WHERE status IN ('COMPLETED', 'FAILED') " +
                        "UNION ALL " +
                        "SELECT id, job_id, job_name, job_group, app_name, entity_id, scheduled_time, started_at, " +
                        "completed_at, status, error_message, execution_duration_ms " +
                        "FROM sipcall_job_execution_history " +
                        "WHERE status IN ('COMPLETED', 'FAILED') " +
                        "UNION ALL " +
                        "SELECT id, job_id, job_name, job_group, app_name, entity_id, scheduled_time, started_at, " +
                        "completed_at, status, error_message, execution_duration_ms " +
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
}
