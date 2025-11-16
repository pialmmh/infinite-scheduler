package com.telcobright.scheduler.examples;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Standalone simulation showing the SMS retry flow without requiring Kafka.
 * This demonstrates exactly what happens in the real system.
 */
public class SmsRetrySimulation {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        System.out.println("=============================================================");
        System.out.println("        SMS RETRY SCHEDULER - FLOW DEMONSTRATION");
        System.out.println("=============================================================\n");

        LocalDateTime now = LocalDateTime.now();

        // STEP 1: Simulate SMS failures in your service
        System.out.println("STEP 1: SMS Service - Failed SMS Messages");
        System.out.println("-------------------------------------------------------------");

        SmsFailure[] failures = {
            new SmsFailure(10001L, now.minusMinutes(5), "Network timeout", 1),
            new SmsFailure(10002L, now.minusMinutes(10), "Gateway error", 2),
            new SmsFailure(10003L, now.minusMinutes(15), "Rate limit exceeded", 3),
            new SmsFailure(10004L, now.minusMinutes(20), "Temporary unavailable", 1),
            new SmsFailure(10005L, now.minusMinutes(30), "Connection refused", 5)
        };

        for (SmsFailure failure : failures) {
            System.out.printf("❌ SMS FAILED - Campaign: %d, Created: %s, Reason: %s, Attempt: %d%n",
                failure.campaignTaskId,
                failure.createdOn.format(TIME_FORMATTER),
                failure.reason,
                failure.attemptNumber);
        }

        System.out.println("\n");

        // STEP 2: Producer sends retry requests to SMS_Retry topic
        System.out.println("STEP 2: Producer - Scheduling Retries via SMS_Retry Topic");
        System.out.println("-------------------------------------------------------------");

        RetryRequest[] retryRequests = {
            new RetryRequest(10001L, failures[0].createdOn, now.plusSeconds(10), 1),
            new RetryRequest(10002L, failures[1].createdOn, now.plusSeconds(30), 2),
            new RetryRequest(10003L, failures[2].createdOn, now.plusMinutes(1), 3),
            new RetryRequest(10004L, failures[3].createdOn, now.plusMinutes(2), 1),
            new RetryRequest(10005L, failures[4].createdOn, now.plusMinutes(3), 5)
        };

        for (RetryRequest request : retryRequests) {
            String json = request.toJson();
            System.out.printf("✅ SENT to SMS_Retry topic: %s%n", json);
            System.out.printf("   → Retry scheduled for: %s%n%n", request.retryTime.format(TIME_FORMATTER));
        }

        System.out.println();

        // STEP 3: Scheduler consumes from SMS_Retry and stores in MySQL
        System.out.println("STEP 3: Infinite Scheduler - Consuming from SMS_Retry");
        System.out.println("-------------------------------------------------------------");
        System.out.println("✅ Scheduler connected to SMS_Retry topic");
        System.out.println("✅ Consumer group: sms-retry-scheduler");
        System.out.println();

        for (RetryRequest request : retryRequests) {
            System.out.printf("📥 Consumed message: campaignTaskId=%d%n", request.campaignTaskId);
            System.out.printf("   → Parsing JSON...%n");
            System.out.printf("   → Validating request...%n");
            System.out.printf("   → Storing in MySQL (table: sms_retry_scheduled_jobs_%s)%n",
                request.retryTime.toLocalDate().toString().replace("-", ""));
            System.out.printf("   → Scheduling Quartz job for: %s%n", request.retryTime.format(TIME_FORMATTER));
            System.out.printf("   ✅ Job scheduled successfully%n%n");
        }

        System.out.println();

        // STEP 4: Simulate waiting for retry time
        System.out.println("STEP 4: Waiting for Retry Time...");
        System.out.println("-------------------------------------------------------------");
        System.out.println("⏰ Scheduler running... waiting for scheduled times...");
        System.out.println();

        // Simulate time passing and jobs executing
        for (int i = 0; i < retryRequests.length; i++) {
            RetryRequest request = retryRequests[i];

            System.out.printf("⏰ Time reached: %s%n", request.retryTime.format(TIME_FORMATTER));
            System.out.println("🔥 Triggering SmsRetryJobHandler...");

            // Simulate job execution
            Thread.sleep(500);

            System.out.printf("📤 Executing SMS retry job:%n");
            System.out.printf("   Campaign Task ID: %d%n", request.campaignTaskId);
            System.out.printf("   Created On: %s%n", request.createdOn.format(TIME_FORMATTER));
            System.out.printf("   Retry Time: %s%n", request.retryTime.format(TIME_FORMATTER));
            System.out.printf("   Actual Execution: %s%n", now.plusSeconds((i+1) * 2).format(TIME_FORMATTER));
            System.out.printf("   Retry Attempt: %d%n", request.attemptNumber);

            // Create output payload
            String outputPayload = createOutputPayload(request, now.plusSeconds((i+1) * 2));

            System.out.printf("%n✅ Publishing to SMS_Send topic:%n");
            System.out.printf("   %s%n", outputPayload);
            System.out.printf("   → Partition: 2, Offset: %d%n%n", 1000 + i);

            Thread.sleep(500);
        }

        System.out.println();

        // STEP 5: SMS Service consumes from SMS_Send
        System.out.println("STEP 5: SMS Service - Consuming from SMS_Send Topic");
        System.out.println("-------------------------------------------------------------");
        System.out.println("✅ SMS Service connected to SMS_Send topic");
        System.out.println("✅ Consumer group: sms-sender-service");
        System.out.println();

        for (int i = 0; i < retryRequests.length; i++) {
            RetryRequest request = retryRequests[i];

            System.out.printf("📥 Consumed from SMS_Send: campaignTaskId=%d%n", request.campaignTaskId);
            System.out.printf("   → Fetching SMS details from database...%n");
            System.out.printf("   → Phone: +880171234%04d%n", request.campaignTaskId);
            System.out.printf("   → Message: \"Test SMS for campaign %d\"%n", request.campaignTaskId);
            System.out.printf("   → Sending via SMS Gateway...%n");

            Thread.sleep(300);

            System.out.printf("   ✅ SMS SENT SUCCESSFULLY!%n");
            System.out.printf("   → Attempt %d succeeded%n%n", request.attemptNumber);
        }

        System.out.println();

        // Summary
        System.out.println("=============================================================");
        System.out.println("                    SUMMARY");
        System.out.println("=============================================================");
        System.out.println("✅ 5 failed SMS messages were scheduled for retry");
        System.out.println("✅ Retry requests sent to SMS_Retry topic");
        System.out.println("✅ Scheduler consumed and stored in MySQL");
        System.out.println("✅ Jobs executed at scheduled times");
        System.out.println("✅ Results published to SMS_Send topic");
        System.out.println("✅ SMS Service consumed and sent actual SMS");
        System.out.println();
        System.out.println("📊 Metrics:");
        System.out.println("   - Messages Received: 5");
        System.out.println("   - Messages Processed: 5");
        System.out.println("   - Messages Failed: 0");
        System.out.println("   - SMS Sent Successfully: 5");
        System.out.println();
        System.out.println("🎯 Success Rate: 100%");
        System.out.println("=============================================================");
    }

    static class SmsFailure {
        long campaignTaskId;
        LocalDateTime createdOn;
        String reason;
        int attemptNumber;

        SmsFailure(long id, LocalDateTime created, String reason, int attempt) {
            this.campaignTaskId = id;
            this.createdOn = created;
            this.reason = reason;
            this.attemptNumber = attempt;
        }
    }

    static class RetryRequest {
        long campaignTaskId;
        LocalDateTime createdOn;
        LocalDateTime retryTime;
        int attemptNumber;

        RetryRequest(long id, LocalDateTime created, LocalDateTime retry, int attempt) {
            this.campaignTaskId = id;
            this.createdOn = created;
            this.retryTime = retry;
            this.attemptNumber = attempt;
        }

        String toJson() {
            return String.format(
                "{\"appName\":\"sms_retry\",\"scheduledTime\":\"%s\",\"jobData\":{" +
                "\"campaignTaskId\":%d,\"createdOn\":\"%s\",\"retryTime\":\"%s\",\"retryAttempt\":%d}}",
                retryTime.format(TIME_FORMATTER),
                campaignTaskId,
                createdOn.format(TIME_FORMATTER),
                retryTime.format(TIME_FORMATTER),
                attemptNumber
            );
        }
    }

    static String createOutputPayload(RetryRequest request, LocalDateTime actualTime) {
        return String.format(
            "{\"campaignTaskId\":%d,\"createdOn\":\"%s\",\"scheduledRetryTime\":\"%s\"," +
            "\"actualExecutionTime\":\"%s\",\"retryAttempt\":%d}",
            request.campaignTaskId,
            request.createdOn.format(TIME_FORMATTER),
            request.retryTime.format(TIME_FORMATTER),
            actualTime.format(TIME_FORMATTER),
            request.attemptNumber
        );
    }
}
