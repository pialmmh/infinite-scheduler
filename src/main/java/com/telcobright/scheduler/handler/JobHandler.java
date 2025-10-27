package com.telcobright.scheduler.handler;

import java.util.Map;

/**
 * Interface for app-specific job handlers.
 * Each application (sms, sipcall, payment_gateway) implements this interface
 * to define how its jobs should be executed.
 */
public interface JobHandler {
    /**
     * Execute the job with the given data.
     * @param jobData Map containing all job parameters
     * @throws Exception if job execution fails
     */
    void execute(Map<String, Object> jobData) throws Exception;

    /**
     * Validate that required fields are present in job data.
     * @param jobData Map to validate
     * @return true if valid, false otherwise
     */
    default boolean validate(Map<String, Object> jobData) {
        return true;
    }

    /**
     * Get the handler name for logging purposes.
     * @return handler name
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}