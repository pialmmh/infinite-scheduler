package com.telcobright.scheduler;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Global job listener that tracks job execution completion and failure.
 */
public class JobCompletionListener implements JobListener {

    private static final Logger logger = LoggerFactory.getLogger(JobCompletionListener.class);
    private final DataSource dataSource;

    public JobCompletionListener(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String getName() {
        return "JobCompletionListener";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        String jobId = context.getJobDetail().getKey().getName();
        String appName = context.getJobDetail().getJobDataMap().getString("appName");

        if (appName == null) return;

        String historyTable = appName + "_job_execution_history";
        updateJobStatus(historyTable, jobId, "STARTED", null);
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        // Not used
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        String jobId = context.getJobDetail().getKey().getName();
        String appName = context.getJobDetail().getJobDataMap().getString("appName");

        if (appName == null) return;

        String historyTable = appName + "_job_execution_history";

        if (jobException != null) {
            // Job failed
            updateJobStatus(historyTable, jobId, "FAILED", jobException.getMessage());
        } else {
            // Job completed successfully
            updateJobStatus(historyTable, jobId, "COMPLETED", null);
        }
    }

    private void updateJobStatus(String tableName, String jobId, String status, String errorMessage) {
        String sql = null;

        if ("STARTED".equals(status)) {
            sql = String.format(
                "UPDATE %s SET status = 'STARTED', started_at = NOW() " +
                "WHERE job_id = ? AND status = 'SCHEDULED'",
                tableName
            );
        } else if ("COMPLETED".equals(status)) {
            sql = String.format(
                "UPDATE %s SET status = 'COMPLETED', completed_at = NOW(), " +
                "execution_duration_ms = TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) / 1000 " +
                "WHERE job_id = ? AND status = 'STARTED'",
                tableName
            );
        } else if ("FAILED".equals(status)) {
            sql = String.format(
                "UPDATE %s SET status = 'FAILED', completed_at = NOW(), " +
                "execution_duration_ms = TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) / 1000, " +
                "error_message = ? " +
                "WHERE job_id = ? AND status IN ('SCHEDULED', 'STARTED')",
                tableName
            );
        }

        if (sql == null) return;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if ("FAILED".equals(status)) {
                stmt.setString(1, errorMessage != null ?
                    errorMessage.substring(0, Math.min(errorMessage.length(), 1000)) : null);
                stmt.setString(2, jobId);
            } else {
                stmt.setString(1, jobId);
            }

            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logger.debug("Updated job '{}' status to {}", jobId, status);
            }

        } catch (SQLException e) {
            logger.error("Failed to update job status: {}", e.getMessage());
        }
    }
}
