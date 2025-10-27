package com.telcobright.scheduler.queue;

import java.util.Map;

/**
 * Interface for producing messages to different queue systems.
 */
public interface QueueProducer {

    /**
     * Send a message to the configured queue/topic.
     *
     * @param topicName The topic/queue name to send to
     * @param message The message payload
     * @return true if successful, false otherwise
     */
    boolean send(String topicName, Map<String, Object> message);

    /**
     * Initialize the producer with configuration.
     *
     * @param config Queue configuration
     */
    void initialize(QueueConfig config);

    /**
     * Close the producer and release resources.
     */
    void close();

    /**
     * Get the type of this producer.
     */
    QueueConfig.QueueType getType();
}