package com.telcobright.scheduler.queue;

/**
 * Configuration for queue-based job execution.
 */
public class QueueConfig {

    public enum QueueType {
        KAFKA,
        REDIS
    }

    private final QueueType queueType;
    private final String topicName;
    private final String brokerAddress; // Kafka brokers or Redis host:port

    public QueueConfig(QueueType queueType, String topicName, String brokerAddress) {
        this.queueType = queueType;
        this.topicName = topicName;
        this.brokerAddress = brokerAddress;
    }

    public QueueType getQueueType() {
        return queueType;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getBrokerAddress() {
        return brokerAddress;
    }

    @Override
    public String toString() {
        return "QueueConfig{" +
                "queueType=" + queueType +
                ", topicName='" + topicName + '\'' +
                ", brokerAddress='" + brokerAddress + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private QueueType queueType;
        private String topicName;
        private String brokerAddress;

        public Builder queueType(QueueType queueType) {
            this.queueType = queueType;
            return this;
        }

        public Builder topicName(String topicName) {
            this.topicName = topicName;
            return this;
        }

        public Builder brokerAddress(String brokerAddress) {
            this.brokerAddress = brokerAddress;
            return this;
        }

        public QueueConfig build() {
            if (queueType == null || topicName == null || brokerAddress == null) {
                throw new IllegalArgumentException("QueueType, topicName and brokerAddress are required");
            }
            return new QueueConfig(queueType, topicName, brokerAddress);
        }
    }
}