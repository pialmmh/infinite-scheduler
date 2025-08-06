package com.telcobright.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Updates scheduled status in entity tables when jobs are scheduled/completed
 */
public class DatabaseStatusUpdater {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseStatusUpdater.class);
    private final DataSource dataSource;
    
    public DatabaseStatusUpdater(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Updates the scheduled column to 1 when a job is scheduled to Quartz
     */
    public void markAsScheduled(Long entityId, LocalDateTime scheduledTime, String tablePrefix) {
        String tableName = buildTableName(tablePrefix, scheduledTime);
        String sql = "UPDATE " + tableName + " SET scheduled = 1 WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, entityId);
            
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logger.debug("Marked entity {} as scheduled in table {}", entityId, tableName);
            } else {
                logger.warn("No rows updated when marking entity {} as scheduled in table {}", entityId, tableName);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to mark entity {} as scheduled in table {}: {}", entityId, tableName, e.getMessage());
        }
    }
    
    /**
     * Updates the scheduled column to 0 when a job is completed (for potential rescheduling)
     */
    public void markAsCompleted(Long entityId, LocalDateTime scheduledTime, String tablePrefix) {
        String tableName = buildTableName(tablePrefix, scheduledTime);
        String sql = "UPDATE " + tableName + " SET scheduled = 0, status = 'SENT' WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, entityId);
            
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logger.debug("Marked entity {} as completed in table {}", entityId, tableName);
            } else {
                logger.warn("No rows updated when marking entity {} as completed in table {}", entityId, tableName);
            }
            
        } catch (SQLException e) {
            logger.error("Failed to mark entity {} as completed in table {}: {}", entityId, tableName, e.getMessage());
        }
    }
    
    /**
     * Build table name from prefix and scheduled time
     * Scheduled time is now used for partitioning instead of created_at
     */
    private String buildTableName(String tablePrefix, LocalDateTime scheduledTime) {
        String dateStr = scheduledTime.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return tablePrefix + "_" + dateStr;
    }
}