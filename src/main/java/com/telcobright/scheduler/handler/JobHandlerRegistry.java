package com.telcobright.scheduler.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Registry for job handlers.
 * Maps application names to their respective job handlers.
 */
public class JobHandlerRegistry {
    private static final Logger logger = LoggerFactory.getLogger(JobHandlerRegistry.class);
    private static final Map<String, JobHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Register a job handler for an application.
     * @param appName Application name
     * @param handler Job handler implementation
     */
    public static void register(String appName, JobHandler handler) {
        handlers.put(appName, handler);
        logger.info("Registered job handler for app '{}': {}", appName, handler.getName());
    }

    /**
     * Get the job handler for an application.
     * @param appName Application name
     * @return Job handler or null if not found
     */
    public static JobHandler getHandler(String appName) {
        return handlers.get(appName);
    }

    /**
     * Check if a handler exists for an application.
     * @param appName Application name
     * @return true if handler exists
     */
    public static boolean hasHandler(String appName) {
        return handlers.containsKey(appName);
    }

    /**
     * Get all registered application names.
     * @return Set of application names
     */
    public static Map<String, JobHandler> getAllHandlers() {
        return new ConcurrentHashMap<>(handlers);
    }

    /**
     * Clear all registered handlers.
     * Useful for testing.
     */
    public static void clear() {
        handlers.clear();
        logger.info("Cleared all job handlers from registry");
    }
}