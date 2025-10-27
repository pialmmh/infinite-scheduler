package com.telcobright.scheduler.queue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Mock queue producer that prints messages to console for testing.
 */
public class MockQueueProducer implements QueueProducer {

    private static final Logger logger = LoggerFactory.getLogger(MockQueueProducer.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private QueueConfig config;

    @Override
    public void initialize(QueueConfig config) {
        this.config = config;
        logger.info("🔧 MockQueueProducer initialized for topic: {} (simulating {})",
                   config.getTopicName(), config.getQueueType());
    }

    @Override
    public boolean send(String topicName, Map<String, Object> message) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("📤 MOCK QUEUE MESSAGE PRODUCED @ " + timestamp);
        System.out.println("=".repeat(80));
        System.out.println("Queue Type: " + (config != null ? config.getQueueType() : "UNKNOWN"));
        System.out.println("Topic Name: " + topicName);
        System.out.println("Broker: " + (config != null ? config.getBrokerAddress() : "N/A"));
        System.out.println("-".repeat(80));
        System.out.println("Message Payload:");
        System.out.println(GSON.toJson(message));
        System.out.println("=".repeat(80) + "\n");

        logger.info("✅ Message sent to mock queue '{}': {} fields",
                   topicName, message != null ? message.size() : 0);

        return true;
    }

    @Override
    public void close() {
        logger.info("🔌 MockQueueProducer closed");
    }

    @Override
    public QueueConfig.QueueType getType() {
        return config != null ? config.getQueueType() : QueueConfig.QueueType.KAFKA;
    }
}