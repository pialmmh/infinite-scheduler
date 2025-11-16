package com.telcobright.scheduler.kafka;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScheduleJobRequest.
 */
class ScheduleJobRequestTest {

    @Test
    void testDefaultConstructor() {
        ScheduleJobRequest request = new ScheduleJobRequest();

        assertNotNull(request.getRequestId(), "Request ID should be auto-generated");
        assertNotNull(request.getJobData(), "Job data should be initialized");
        assertTrue(request.getJobData().isEmpty(), "Job data should be empty");
    }

    @Test
    void testParameterizedConstructor() {
        LocalDateTime scheduledTime = LocalDateTime.of(2025, 11, 3, 22, 0);
        Map<String, Object> jobData = new HashMap<>();
        jobData.put("key1", "value1");
        jobData.put("key2", 123);

        ScheduleJobRequest request = new ScheduleJobRequest("sms", scheduledTime, jobData);

        assertNotNull(request.getRequestId());
        assertEquals("sms", request.getAppName());
        assertEquals(scheduledTime, request.getScheduledTime());
        assertEquals(2, request.getJobData().size());
        assertEquals("value1", request.getJobData().get("key1"));
        assertEquals(123, request.getJobData().get("key2"));
    }

    @Test
    void testValidation_Success() {
        ScheduleJobRequest request = new ScheduleJobRequest(
            "sms",
            LocalDateTime.now(),
            new HashMap<>()
        );

        assertDoesNotThrow(request::validate);
    }

    @Test
    void testValidation_MissingAppName() {
        ScheduleJobRequest request = new ScheduleJobRequest();
        request.setScheduledTime(LocalDateTime.now());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            request::validate
        );
        assertTrue(exception.getMessage().contains("appName"));
    }

    @Test
    void testValidation_EmptyAppName() {
        ScheduleJobRequest request = new ScheduleJobRequest();
        request.setAppName("   ");
        request.setScheduledTime(LocalDateTime.now());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            request::validate
        );
        assertTrue(exception.getMessage().contains("appName"));
    }

    @Test
    void testValidation_MissingScheduledTime() {
        ScheduleJobRequest request = new ScheduleJobRequest();
        request.setAppName("sms");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            request::validate
        );
        assertTrue(exception.getMessage().contains("scheduledTime"));
    }

    @Test
    void testValidation_NullJobData() {
        ScheduleJobRequest request = new ScheduleJobRequest();
        request.setAppName("sms");
        request.setScheduledTime(LocalDateTime.now());
        request.setJobData(null);

        // Should not throw - validation initializes empty map
        assertDoesNotThrow(request::validate);
        assertNotNull(request.getJobData());
    }

    @Test
    void testToJobDataMap() {
        LocalDateTime scheduledTime = LocalDateTime.of(2025, 11, 3, 22, 30);
        Map<String, Object> originalData = new HashMap<>();
        originalData.put("phoneNumber", "+8801712345678");
        originalData.put("message", "Test message");

        ScheduleJobRequest request = new ScheduleJobRequest("sms", scheduledTime, originalData);
        request.setPriority("high");
        request.setIdempotencyKey("test-key-123");

        Map<String, Object> jobDataMap = request.toJobDataMap();

        // Original data should be present
        assertEquals("+8801712345678", jobDataMap.get("phoneNumber"));
        assertEquals("Test message", jobDataMap.get("message"));

        // Additional fields should be added
        assertEquals(scheduledTime, jobDataMap.get("scheduledTime"));
        assertNotNull(jobDataMap.get("requestId"));
        assertEquals("test-key-123", jobDataMap.get("idempotencyKey"));
        assertEquals("high", jobDataMap.get("priority"));
    }

    @Test
    void testToJobDataMap_WithoutOptionalFields() {
        LocalDateTime scheduledTime = LocalDateTime.now();
        Map<String, Object> originalData = new HashMap<>();
        originalData.put("key", "value");

        ScheduleJobRequest request = new ScheduleJobRequest("sms", scheduledTime, originalData);

        Map<String, Object> jobDataMap = request.toJobDataMap();

        assertEquals("value", jobDataMap.get("key"));
        assertEquals(scheduledTime, jobDataMap.get("scheduledTime"));
        assertNotNull(jobDataMap.get("requestId"));
        assertNull(jobDataMap.get("idempotencyKey"));
        assertNull(jobDataMap.get("priority"));
    }

    @Test
    void testJsonSerialization() {
        LocalDateTime scheduledTime = LocalDateTime.of(2025, 11, 3, 22, 0);
        Map<String, Object> jobData = new HashMap<>();
        jobData.put("phoneNumber", "+8801712345678");
        jobData.put("message", "Hello World");

        ScheduleJobRequest request = new ScheduleJobRequest("sms", scheduledTime, jobData);
        request.setPriority("normal");
        request.setIdempotencyKey("unique-123");

        String json = request.toJson();

        assertNotNull(json);
        assertTrue(json.contains("\"appName\":\"sms\""));
        assertTrue(json.contains("\"phoneNumber\":\"+8801712345678\""));
        assertTrue(json.contains("\"message\":\"Hello World\""));
        assertTrue(json.contains("\"priority\":\"normal\""));
        assertTrue(json.contains("\"idempotencyKey\":\"unique-123\""));
    }

    @Test
    void testJsonDeserialization() {
        String json = "{" +
            "\"requestId\":\"test-request-id\"," +
            "\"appName\":\"sms\"," +
            "\"scheduledTime\":\"2025-11-03T22:00:00\"," +
            "\"priority\":\"high\"," +
            "\"idempotencyKey\":\"test-key\"," +
            "\"jobData\":{" +
                "\"phoneNumber\":\"+8801712345678\"," +
                "\"message\":\"Test\"" +
            "}" +
        "}";

        ScheduleJobRequest request = ScheduleJobRequest.fromJson(json);

        assertEquals("test-request-id", request.getRequestId());
        assertEquals("sms", request.getAppName());
        assertEquals(LocalDateTime.of(2025, 11, 3, 22, 0), request.getScheduledTime());
        assertEquals("high", request.getPriority());
        assertEquals("test-key", request.getIdempotencyKey());
        assertEquals("+8801712345678", request.getJobData().get("phoneNumber"));
        assertEquals("Test", request.getJobData().get("message"));
    }

    @Test
    void testJsonRoundTrip() {
        LocalDateTime scheduledTime = LocalDateTime.of(2025, 11, 3, 22, 0);
        Map<String, Object> jobData = new HashMap<>();
        jobData.put("key1", "value1");
        jobData.put("key2", 42);

        ScheduleJobRequest original = new ScheduleJobRequest("payment", scheduledTime, jobData);
        original.setPriority("low");
        original.setIdempotencyKey("round-trip-test");

        String json = original.toJson();
        ScheduleJobRequest deserialized = ScheduleJobRequest.fromJson(json);

        assertEquals(original.getRequestId(), deserialized.getRequestId());
        assertEquals(original.getAppName(), deserialized.getAppName());
        assertEquals(original.getScheduledTime(), deserialized.getScheduledTime());
        assertEquals(original.getPriority(), deserialized.getPriority());
        assertEquals(original.getIdempotencyKey(), deserialized.getIdempotencyKey());
        assertEquals(original.getJobData().get("key1"), deserialized.getJobData().get("key1"));
    }

    @Test
    void testSettersAndGetters() {
        ScheduleJobRequest request = new ScheduleJobRequest();

        request.setRequestId("custom-id");
        assertEquals("custom-id", request.getRequestId());

        request.setAppName("sipcall");
        assertEquals("sipcall", request.getAppName());

        LocalDateTime time = LocalDateTime.now();
        request.setScheduledTime(time);
        assertEquals(time, request.getScheduledTime());

        Map<String, Object> data = new HashMap<>();
        data.put("test", "value");
        request.setJobData(data);
        assertEquals("value", request.getJobData().get("test"));

        request.setPriority("high");
        assertEquals("high", request.getPriority());

        request.setIdempotencyKey("key-123");
        assertEquals("key-123", request.getIdempotencyKey());
    }

    @Test
    void testToString() {
        ScheduleJobRequest request = new ScheduleJobRequest(
            "sms",
            LocalDateTime.of(2025, 11, 3, 22, 0),
            new HashMap<>()
        );
        request.setPriority("normal");

        String toString = request.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("sms"));
        assertTrue(toString.contains("2025-11-03T22:00"));
        assertTrue(toString.contains("normal"));
    }
}
