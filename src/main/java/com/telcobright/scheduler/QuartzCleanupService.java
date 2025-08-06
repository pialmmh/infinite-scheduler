package com.telcobright.scheduler;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

public class QuartzCleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(QuartzCleanupService.class);
    
    private final DataSource dataSource;
    private final SchedulerConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread cleanupThread;
    
    public QuartzCleanupService(DataSource dataSource, SchedulerConfig config) {
        this.dataSource = dataSource;
        this.config = config;
    }
    
    public void start() {
        if (config.isAutoCleanupCompletedJobs() && running.compareAndSet(false, true)) {
            logger.info("Starting Quartz cleanup service with interval: {} minutes", 
                config.getCleanupIntervalMinutes());
            
            cleanupThread = Thread.ofVirtual()
                .name("quartz-cleanup")
                .start(this::runCleanup);
        }
    }
    
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping Quartz cleanup service...");
            
            if (cleanupThread != null) {
                cleanupThread.interrupt();
            }
            
            logger.info("Quartz cleanup service stopped");
        }
    }
    
    private void runCleanup() {
        logger.info("Quartz cleanup thread started");
        
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                performCleanup();
                
                // Sleep for the configured interval
                long sleepMs = config.getCleanupIntervalMinutes() * 60L * 1000L;
                Thread.sleep(sleepMs);
                
            } catch (InterruptedException e) {
                logger.info("Cleanup thread interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in cleanup thread", e);
                try {
                    Thread.sleep(30000); // Wait 30 seconds before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        logger.info("Quartz cleanup thread stopped");
    }
    
    private void performCleanup() {
        try (Connection conn = dataSource.getConnection()) {
            int cleanedJobs = cleanupCompletedJobs(conn);
            int cleanedTriggers = cleanupOrphanedTriggers(conn);
            int cleanedFiredTriggers = cleanupOldFiredTriggers(conn);
            
            if (cleanedJobs > 0 || cleanedTriggers > 0 || cleanedFiredTriggers > 0) {
                logger.info("Cleanup completed: {} jobs, {} triggers, {} fired triggers removed",
                    cleanedJobs, cleanedTriggers, cleanedFiredTriggers);
            } else {
                logger.debug("Cleanup completed: no old records found");
            }
            
        } catch (SQLException e) {
            logger.error("Failed to perform Quartz cleanup", e);
        }
    }
    
    private int cleanupCompletedJobs(Connection conn) throws SQLException {
        // Remove job details that have no associated triggers (completed jobs)
        String sql = """
            DELETE jd FROM QRTZ_JOB_DETAILS jd
            LEFT JOIN QRTZ_TRIGGERS t ON jd.SCHED_NAME = t.SCHED_NAME 
                AND jd.JOB_NAME = t.JOB_NAME 
                AND jd.JOB_GROUP = t.JOB_GROUP
            WHERE jd.SCHED_NAME = 'InfiniteScheduler' 
                AND t.TRIGGER_NAME IS NULL
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                logger.debug("Cleaned up {} completed job details", deleted);
            }
            return deleted;
        }
    }
    
    private int cleanupOrphanedTriggers(Connection conn) throws SQLException {
        // Remove triggers that have no associated job details
        String sql = """
            DELETE t FROM QRTZ_TRIGGERS t
            LEFT JOIN QRTZ_JOB_DETAILS jd ON t.SCHED_NAME = jd.SCHED_NAME 
                AND t.JOB_NAME = jd.JOB_NAME 
                AND t.JOB_GROUP = jd.JOB_GROUP
            WHERE t.SCHED_NAME = 'InfiniteScheduler' 
                AND jd.JOB_NAME IS NULL
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                logger.debug("Cleaned up {} orphaned triggers", deleted);
            }
            return deleted;
        }
    }
    
    private int cleanupOldFiredTriggers(Connection conn) throws SQLException {
        // Remove fired trigger records older than 1 day
        long oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L);
        
        String sql = """
            DELETE FROM QRTZ_FIRED_TRIGGERS 
            WHERE SCHED_NAME = 'InfiniteScheduler' 
                AND FIRED_TIME < ?
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, oneDayAgo);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                logger.debug("Cleaned up {} old fired trigger records", deleted);
            }
            return deleted;
        }
    }
    
    public boolean isRunning() {
        return running.get();
    }
}