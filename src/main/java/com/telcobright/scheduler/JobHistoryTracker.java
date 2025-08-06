package com.telcobright.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;

public class JobHistoryTracker {
    
    private static final Logger logger = LoggerFactory.getLogger(JobHistoryTracker.class);
    private final DataSource dataSource;
    
    public JobHistoryTracker(DataSource dataSource) {
        this.dataSource = dataSource;
        createHistoryTableIfNotExists();
    }
    
    private void createHistoryTableIfNotExists() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS job_execution_history (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                job_id VARCHAR(255) NOT NULL,
                job_name VARCHAR(255) NOT NULL,
                job_group VARCHAR(255) NOT NULL,
                entity_id VARCHAR(255),
                scheduled_time DATETIME,
                started_at DATETIME,
                completed_at DATETIME,
                status ENUM('SCHEDULED', 'STARTED', 'COMPLETED', 'FAILED') NOT NULL,
                error_message TEXT,
                execution_duration_ms BIGINT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_job_id (job_id),
                INDEX idx_job_name (job_name),
                INDEX idx_status (status),
                INDEX idx_scheduled_time (scheduled_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(createTableSQL);
            logger.info("Job history table ensured to exist");
            
        } catch (SQLException e) {
            logger.error("Failed to create job history table", e);
            throw new RuntimeException("Failed to create job history table", e);
        }
    }
    
    public void recordJobScheduled(String jobId, String jobName, String jobGroup, 
                                 String entityId, LocalDateTime scheduledTime) {
        String sql = """
            INSERT INTO job_execution_history 
            (job_id, job_name, job_group, entity_id, scheduled_time, status) 
            VALUES (?, ?, ?, ?, ?, 'SCHEDULED')
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, jobId);
            stmt.setString(2, jobName);
            stmt.setString(3, jobGroup);
            stmt.setString(4, entityId);
            stmt.setTimestamp(5, Timestamp.valueOf(scheduledTime));
            
            stmt.executeUpdate();
            logger.debug("Recorded job scheduled: {}", jobId);
            
        } catch (SQLException e) {
            logger.error("Failed to record job scheduled: {}", jobId, e);
        }
    }
    
    public void recordJobStarted(String jobId) {
        String sql = """
            UPDATE job_execution_history 
            SET status = 'STARTED', started_at = NOW() 
            WHERE job_id = ? AND status = 'SCHEDULED'
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, jobId);
            int updated = stmt.executeUpdate();
            
            if (updated > 0) {
                logger.debug("Recorded job started: {}", jobId);
            } else {
                logger.warn("No scheduled job found to mark as started: {}", jobId);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to record job started: {}", jobId, e);
        }
    }
    
    public void recordJobCompleted(String jobId) {
        String sql = """
            UPDATE job_execution_history 
            SET status = 'COMPLETED', completed_at = NOW(),
                execution_duration_ms = TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) / 1000
            WHERE job_id = ? AND status = 'STARTED'
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, jobId);
            int updated = stmt.executeUpdate();
            
            if (updated > 0) {
                logger.debug("Recorded job completed: {}", jobId);
            } else {
                logger.warn("No started job found to mark as completed: {}", jobId);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to record job completed: {}", jobId, e);
        }
    }
    
    public void recordJobFailed(String jobId, String errorMessage) {
        String sql = """
            UPDATE job_execution_history 
            SET status = 'FAILED', completed_at = NOW(), error_message = ?,
                execution_duration_ms = CASE 
                    WHEN started_at IS NOT NULL THEN TIMESTAMPDIFF(MICROSECOND, started_at, NOW()) / 1000
                    ELSE NULL 
                END
            WHERE job_id = ? AND status IN ('SCHEDULED', 'STARTED')
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, errorMessage);
            stmt.setString(2, jobId);
            int updated = stmt.executeUpdate();
            
            if (updated > 0) {
                logger.debug("Recorded job failed: {} - {}", jobId, errorMessage);
            } else {
                logger.warn("No active job found to mark as failed: {}", jobId);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to record job failed: {}", jobId, e);
        }
    }
    
    public void cleanupOldHistory(int daysToKeep) {
        String sql = "DELETE FROM job_execution_history WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, daysToKeep);
            int deleted = stmt.executeUpdate();
            
            if (deleted > 0) {
                logger.info("Cleaned up {} old job history records (older than {} days)", deleted, daysToKeep);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to cleanup old job history", e);
        }
    }
}