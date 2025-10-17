package com.telcobright.scheduler;

import java.util.Map;

/**
 * Interface for publishing scheduled jobs to message brokers.
 * Implementations publish to Kafka topics or Redis streams.
 */
public interface JobPublisher {

    /**
     * Publish a job to the specified topic/stream.
     *
     * @param topic The topic name (from jobType field)
     * @param jobData The complete job data as a map
     */
    void publish(String topic, Map<String, Object> jobData);
}
