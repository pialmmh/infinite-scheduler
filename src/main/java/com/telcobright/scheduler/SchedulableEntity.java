package com.telcobright.scheduler;

import com.telcobright.core.entity.ShardingEntity;
import java.time.LocalDateTime;

/**
 * Interface for entities that can be scheduled using InfiniteScheduler.
 * Extends ShardingEntity with LocalDateTime as the partition column (scheduled time).
 */
public interface SchedulableEntity extends ShardingEntity<LocalDateTime> {

    /**
     * Returns the scheduled execution time for this entity.
     * This is typically the same as the partition column value.
     */
    @Override
    LocalDateTime getPartitionColValue();

    /**
     * Convenience method to get scheduled time (alias for getPartitionColValue)
     */
    default LocalDateTime getScheduledTime() {
        return getPartitionColValue();
    }

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

    /**
     * Returns the name of the partition column used for sharding.
     * For schedulable entities, this is typically "scheduled_time" or similar.
     */
    @Override
    default String getPartitionColName() {
        return "scheduled_time";
    }

    /**
     * Returns additional data to be stored in the Quartz JobDataMap.
     * This data will be visible in the UI and available during job execution.
     * Override this method to provide entity-specific data for display.
     *
     * @return map of key-value pairs to store in JobDataMap
     */
    default java.util.Map<String, Object> getAdditionalJobData() {
        return new java.util.HashMap<>();
    }
}
