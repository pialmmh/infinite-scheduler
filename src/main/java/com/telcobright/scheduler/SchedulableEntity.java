package com.telcobright.scheduler;

import com.telcobright.db.entity.ShardingEntity;
import java.time.LocalDateTime;

public interface SchedulableEntity<TKey> extends ShardingEntity<TKey> {
    
    /**
     * Returns the unique identifier for this entity
     */
    TKey getId();
    
    LocalDateTime getScheduledTime();
    
    /**
     * Returns whether this entity has been scheduled to Quartz.
     * Used to prevent duplicate scheduling of the same job.
     * 
     * @return true if job has been scheduled to Quartz
     */
    Boolean getScheduled();
    
    /**
     * Sets the scheduled flag to indicate this entity has been scheduled to Quartz.
     * 
     * @param scheduled true when job is scheduled to Quartz
     */
    void setScheduled(Boolean scheduled);
    
    /**
     * Returns a unique job ID for this schedulable entity.
     * This ID is used by Quartz to identify jobs and prevent duplicates.
     * Default implementation combines entity ID with scheduled time for uniqueness.
     * 
     * @return unique job identifier
     */
    default String getJobId() {
        return "job-" + getId() + "-" + getScheduledTime().toString();
    }
}