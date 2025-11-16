package com.telcobright.scheduler.kafka;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KafkaIngestConfig.
 */
class KafkaIngestConfigTest {

    @Test
    void testDefaultBuilder() {
        KafkaIngestConfig config = KafkaIngestConfig.builder().build();

        assertEquals("localhost:9092", config.getBootstrapServers());
        assertEquals("infinite-scheduler-ingest", config.getGroupId());
        assertEquals("scheduler.jobs.ingest", config.getTopic());
        assertEquals("scheduler.jobs.dlq", config.getDlqTopic());
        assertEquals(500, config.getMaxPollRecords());
        assertEquals(1000, config.getPollTimeoutMs());
        assertFalse(config.isEnableAutoCommit());
        assertEquals(3, config.getMaxRetries());
        assertTrue(config.isEnabled());
    }

    @Test
    void testCustomBuilder() {
        KafkaIngestConfig config = KafkaIngestConfig.builder()
            .bootstrapServers("kafka1:9092,kafka2:9092")
            .groupId("custom-group")
            .topic("custom.topic")
            .dlqTopic("custom.dlq")
            .maxPollRecords(1000)
            .pollTimeoutMs(2000)
            .enableAutoCommit(true)
            .maxRetries(5)
            .enabled(false)
            .build();

        assertEquals("kafka1:9092,kafka2:9092", config.getBootstrapServers());
        assertEquals("custom-group", config.getGroupId());
        assertEquals("custom.topic", config.getTopic());
        assertEquals("custom.dlq", config.getDlqTopic());
        assertEquals(1000, config.getMaxPollRecords());
        assertEquals(2000, config.getPollTimeoutMs());
        assertTrue(config.isEnableAutoCommit());
        assertEquals(5, config.getMaxRetries());
        assertFalse(config.isEnabled());
    }

    @Test
    void testValidation_MissingBootstrapServers() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> KafkaIngestConfig.builder()
                .bootstrapServers(null)
                .build()
        );
        assertTrue(exception.getMessage().contains("bootstrapServers"));
    }

    @Test
    void testValidation_EmptyBootstrapServers() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> KafkaIngestConfig.builder()
                .bootstrapServers("   ")
                .build()
        );
        assertTrue(exception.getMessage().contains("bootstrapServers"));
    }

    @Test
    void testValidation_MissingTopic() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> KafkaIngestConfig.builder()
                .topic(null)
                .build()
        );
        assertTrue(exception.getMessage().contains("topic"));
    }

    @Test
    void testValidation_EmptyTopic() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> KafkaIngestConfig.builder()
                .topic("")
                .build()
        );
        assertTrue(exception.getMessage().contains("topic"));
    }

    @Test
    void testToKafkaProperties() {
        KafkaIngestConfig config = KafkaIngestConfig.builder()
            .bootstrapServers("kafka1:9092")
            .groupId("test-group")
            .topic("test.topic")
            .maxPollRecords(100)
            .enableAutoCommit(true)
            .build();

        Properties props = config.toKafkaProperties();

        assertEquals("kafka1:9092", props.getProperty("bootstrap.servers"));
        assertEquals("test-group", props.getProperty("group.id"));
        assertEquals("org.apache.kafka.common.serialization.StringDeserializer",
            props.getProperty("key.deserializer"));
        assertEquals("org.apache.kafka.common.serialization.StringDeserializer",
            props.getProperty("value.deserializer"));
        assertEquals("true", props.getProperty("enable.auto.commit"));
        assertEquals("100", props.getProperty("max.poll.records"));
        assertEquals("earliest", props.getProperty("auto.offset.reset"));
        assertEquals("30000", props.getProperty("session.timeout.ms"));
        assertEquals("10000", props.getProperty("heartbeat.interval.ms"));
    }

    @Test
    void testToKafkaProperties_DisableAutoCommit() {
        KafkaIngestConfig config = KafkaIngestConfig.builder()
            .enableAutoCommit(false)
            .build();

        Properties props = config.toKafkaProperties();

        assertEquals("false", props.getProperty("enable.auto.commit"));
    }

    @Test
    void testBuilderChaining() {
        KafkaIngestConfig config = KafkaIngestConfig.builder()
            .bootstrapServers("kafka:9092")
            .groupId("group1")
            .topic("topic1")
            .dlqTopic("dlq1")
            .maxPollRecords(200)
            .pollTimeoutMs(500)
            .enableAutoCommit(true)
            .maxRetries(2)
            .enabled(true)
            .build();

        assertNotNull(config);
        assertEquals("kafka:9092", config.getBootstrapServers());
        assertEquals("group1", config.getGroupId());
        assertEquals("topic1", config.getTopic());
    }

    @Test
    void testToString() {
        KafkaIngestConfig config = KafkaIngestConfig.builder()
            .bootstrapServers("kafka:9092")
            .topic("test.topic")
            .build();

        String toString = config.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("kafka:9092"));
        assertTrue(toString.contains("test.topic"));
        assertTrue(toString.contains("KafkaIngestConfig"));
    }

    @Test
    void testMultipleBootstrapServers() {
        KafkaIngestConfig config = KafkaIngestConfig.builder()
            .bootstrapServers("kafka1:9092,kafka2:9092,kafka3:9092")
            .build();

        assertEquals("kafka1:9092,kafka2:9092,kafka3:9092", config.getBootstrapServers());

        Properties props = config.toKafkaProperties();
        assertEquals("kafka1:9092,kafka2:9092,kafka3:9092", props.getProperty("bootstrap.servers"));
    }

    @Test
    void testNullDlqTopic() {
        KafkaIngestConfig config = KafkaIngestConfig.builder()
            .dlqTopic(null)
            .build();

        assertNull(config.getDlqTopic());
    }

    @Test
    void testEmptyDlqTopic() {
        KafkaIngestConfig config = KafkaIngestConfig.builder()
            .dlqTopic("")
            .build();

        assertEquals("", config.getDlqTopic());
    }
}
