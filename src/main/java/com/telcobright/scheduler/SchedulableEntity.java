package com.telcobright.scheduler;

import com.telcobright.core.entity.ShardingEntity;
import java.time.LocalDateTime;

/**
 * Interface for entities that can be scheduled.
 * Extends ShardingEntity with LocalDateTime as the partition column type.
 *
 * Note: All IDs must be String (externally generated like UUID)
 */
public interface SchedulableEntity extends ShardingEntity<LocalDateTime> {

    /**
     * Returns the scheduled time for this entity.
     * This should return the same value as getPartitionColValue() since
     * scheduled_time is typically the sharding key.
     */
    LocalDateTime getScheduledTime();

    /**
     * Sets the scheduled time for this entity.
     */
    void setScheduledTime(LocalDateTime scheduledTime);

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
     * Default implementation: partition column value is the scheduled time
     */
    @Override
    default LocalDateTime getPartitionColValue() {
        return getScheduledTime();
    }

    /**
     * Default implementation: set partition column value updates scheduled time
     */
    @Override
    default void setPartitionColValue(LocalDateTime value) {
        setScheduledTime(value);
    }
}