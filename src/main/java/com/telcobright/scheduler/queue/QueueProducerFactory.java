package com.telcobright.scheduler.queue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing queue producers.
 */
public class QueueProducerFactory {

    private static final Map<String, QueueProducer> producers = new ConcurrentHashMap<>();

    /**
     * Get or create a queue producer for the given configuration.
     */
    public static QueueProducer getProducer(QueueConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("QueueConfig cannot be null");
        }

        String key = config.getQueueType() + ":" + config.getTopicName();

        return producers.computeIfAbsent(key, k -> {
            QueueProducer producer;

            switch (config.getQueueType()) {
                case KAFKA:
                    // For now, use mock producer for Kafka
                    producer = new MockQueueProducer();
                    break;
                case REDIS:
                    // For now, use mock producer for Redis
                    producer = new MockQueueProducer();
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported queue type: " + config.getQueueType());
            }

            producer.initialize(config);
            return producer;
        });
    }

    /**
     * Close all producers and clear the cache.
     */
    public static void closeAll() {
        producers.values().forEach(QueueProducer::close);
        producers.clear();
    }
}