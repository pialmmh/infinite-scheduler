package com.telcobright.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;

/**
 * Job history tracker for a specific application.
 * Each app has its own history table.
 */
public class AppJobHistoryTracker {

    private static final Logger logger = LoggerFactory.getLogger(AppJobHistoryTracker.class);
    private final DataSource dataSource;
    private final String tableName;
    private final String appName;

    public AppJobHistoryTracker(DataSource dataSource, String appName, String tableName) {
        this.dataSource = dataSource;
        this.appName = appName;
        this.tableName = tableName;
        createHistoryTableIfNotExists();
    }

    private void createHistoryTableIfNotExists() {
        String createTableSQL = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                job_id VARCHAR(255) NOT NULL,
                job_name VARCHAR(255) NOT NULL,
                job_group VARCHAR(255) NOT NULL,
                app_name VARCHAR(100) NOT NULL,
                entity_id VARCHAR(255),
                scheduled_time DATETIME,
                started_at DATETIME,
                completed_at DATETIME,
                status ENUM('SCHEDULED', 'STARTED', 'COMPLETED', 'FAILED') NOT NULL,
                error_message TEXT,
                execution_duration_ms BIGINT,
                job_data JSON,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_job_id (job_id),
                INDEX idx_job_name (job_name),
                INDEX idx_app_name (app_name),
                INDEX idx_status (status),
                INDEX idx_scheduled_time (scheduled_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """, tableName);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createTableSQL);
            logger.info("Job history table '{}' ensured to exist for app '{}'", tableName, appName);

        } catch (SQLException e) {
            logger.error("Failed to create job history table '{}' for app '{}'", tableName, appName, e);
            throw new RuntimeException("Failed to create job history table", e);
        }
    }

    public void recordJobScheduled(String jobId, String jobName, String jobGroup,
                                 String entityId, LocalDateTime scheduledTime, String jobDataJson) {
        String sql = String.format("""
            INSERT INTO %s
            (job_id, job_name, job_group, app_name, entity_id, scheduled_time, status, job_data)
            VALUES (?, ?, ?, ?, ?, ?, 'SCHEDULED', ?)
            """, tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, jobId);
            stmt.setString(2, jobName);
            stmt.setString(3, jobGroup);
            stmt.setString(4, appName);
            stmt.setString(5, entityId);
            stmt.setTimestamp(6, Timestamp.valueOf(scheduledTime));
            stmt.setString(7, jobDataJson);

            stmt.executeUpdate();
            logger.debug("Recorded job scheduled for app '{}': {}", appName, jobId);

        } catch (SQLException e) {
            logger.error("Failed to record job scheduled for app '{}': {}", appName, jobId, e);
        }
    }

    public void recordJobStarted(String jobId) {
        String sql = String.format("""
            UPDATE %s
            SET status = 'STARTED', started_at = NOW()
            WHERE job_id = ? AND status = 'SCHEDULED'
            """, tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, jobId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                logger.debug("Recorded job started for app '{}': {}", appName, jobId);
            } else {
                logger.warn("No scheduled job found to mark as started for app '{}': {}", appName, jobId);
            }

        } catch (SQLException e) {
            logger.error("Failed to record job started for app '{}': {}", appName, jobId, e);
        }
    }

    public void recordJobCompleted(String jobId, long executionTimeMs) {
        String sql = String.format("""
            UPDATE %s
            SET status = 'COMPLETED', completed_at = NOW(), execution_duration_ms = ?
            WHERE job_id = ? AND status = 'STARTED'
            """, tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, executionTimeMs);
            stmt.setString(2, jobId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                logger.debug("Recorded job completed for app '{}': {} (duration: {} ms)",
                    appName, jobId, executionTimeMs);
            } else {
                logger.warn("No started job found to mark as completed for app '{}': {}", appName, jobId);
            }

        } catch (SQLException e) {
            logger.error("Failed to record job completed for app '{}': {}", appName, jobId, e);
        }
    }

    public void recordJobFailed(String jobId, String errorMessage, long executionTimeMs) {
        String sql = String.format("""
            UPDATE %s
            SET status = 'FAILED', completed_at = NOW(), error_message = ?, execution_duration_ms = ?
            WHERE job_id = ? AND status = 'STARTED'
            """, tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, errorMessage != null ? errorMessage.substring(0, Math.min(errorMessage.length(), 1000)) : null);
            stmt.setLong(2, executionTimeMs);
            stmt.setString(3, jobId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                logger.debug("Recorded job failed for app '{}': {} (error: {})",
                    appName, jobId, errorMessage);
            } else {
                logger.warn("No started job found to mark as failed for app '{}': {}", appName, jobId);
            }

        } catch (SQLException e) {
            logger.error("Failed to record job failed for app '{}': {}", appName, jobId, e);
        }
    }

    public String getTableName() {
        return tableName;
    }

    public String getAppName() {
        return appName;
    }
}