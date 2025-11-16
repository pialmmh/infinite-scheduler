# SMS Retry Scheduler - Quick Start Guide

## What Just Happened?

The simulation showed the complete SMS retry flow:

1. **5 SMS messages failed** in your service (Network timeout, Gateway error, etc.)
2. **Producer sent retry requests** to `SMS_Retry` topic
3. **Scheduler consumed messages** and stored in MySQL
4. **At retry time**, jobs executed and published to `SMS_Send` topic
5. **SMS Service consumed** from `SMS_Send` and sent actual SMS
6. **100% success rate** - all retries succeeded!

---

## How to Use in Your Project

### 1️⃣ Start the Scheduler (One-time setup)

```java
// In your scheduler service/application
MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
    .mysqlHost("127.0.0.1")
    .mysqlDatabase("scheduler")
    .mysqlUsername("root")
    .mysqlPassword("password")
    .withKafkaIngest(
        KafkaIngestConfig.builder()
            .bootstrapServers("localhost:9092")
            .topic("SMS_Retry")          // Listen here for retry requests
            .dlqTopic("SMS_Retry_DLQ")
            .groupId("sms-retry-scheduler")
            .build()
    )
    .build();

// Register handler
SmsRetryJobHandler handler = new SmsRetryJobHandler(
    "localhost:9092",
    "SMS_Send"                           // Publish results here
);
manager.registerApp("sms_retry", handler);
manager.startAll();
```

### 2️⃣ Send Retry Requests (In Your SMS Service)

```java
// When SMS fails, schedule a retry
SmsRetryProducer producer = new SmsRetryProducer("localhost:9092");

// Calculate retry time (exponential backoff recommended)
LocalDateTime retryTime = LocalDateTime.now().plusMinutes(10);

// Schedule the retry
producer.scheduleRetry(
    campaignTaskId,      // 12345L
    createdOn,           // When SMS was originally created
    retryTime,           // When to retry
    attemptNumber,       // 1, 2, 3, etc.
    failureReason        // "Network timeout"
);

producer.close();
```

**Example with Exponential Backoff:**
```java
public void handleSmsFailure(Long campaignTaskId, String reason, int attempt) {
    // Exponential backoff: 1min, 5min, 15min, 30min, 1hr
    int[] delayMinutes = {1, 5, 15, 30, 60};
    int delay = delayMinutes[Math.min(attempt - 1, delayMinutes.length - 1)];

    LocalDateTime retryTime = LocalDateTime.now().plusMinutes(delay);

    producer.scheduleRetry(
        campaignTaskId,
        LocalDateTime.now(),
        retryTime,
        attempt,
        reason
    );

    logger.info("Scheduled retry #{} for campaign {} at {}",
        attempt, campaignTaskId, retryTime);
}
```

### 3️⃣ Consume Results (In Your SMS Service)

```java
// Listen to SMS_Send topic
KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Collections.singletonList("SMS_Send"));

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

    for (ConsumerRecord<String, String> record : records) {
        JSONObject payload = new JSONObject(record.value());

        Long campaignTaskId = payload.getLong("campaignTaskId");
        int retryAttempt = payload.getInt("retryAttempt");

        // Fetch SMS details
        SmsTask task = database.findByCampaignTaskId(campaignTaskId);

        // Send actual SMS
        try {
            smsGateway.send(task.getPhoneNumber(), task.getMessage());
            logger.info("✅ SMS retry succeeded: {}", campaignTaskId);

            // Update status
            database.markAsSent(campaignTaskId);

        } catch (Exception e) {
            logger.error("❌ SMS retry failed: {}", campaignTaskId);

            // Schedule next retry if not max attempts
            if (retryAttempt < MAX_RETRIES) {
                handleSmsFailure(campaignTaskId, e.getMessage(), retryAttempt + 1);
            } else {
                database.markAsFailed(campaignTaskId);
            }
        }
    }
}
```

---

## Message Formats

### Input: SMS_Retry Topic

```json
{
  "appName": "sms_retry",
  "scheduledTime": "2025-11-14 15:30:00",
  "jobData": {
    "campaignTaskId": 12345,
    "createdOn": "2025-11-14 15:00:00",
    "retryTime": "2025-11-14 15:30:00",
    "retryAttempt": 1,
    "failureReason": "Network timeout"
  }
}
```

### Output: SMS_Send Topic

```json
{
  "campaignTaskId": 12345,
  "createdOn": "2025-11-14 15:00:00",
  "scheduledRetryTime": "2025-11-14 15:30:00",
  "actualExecutionTime": "2025-11-14 15:30:02",
  "retryAttempt": 1
}
```

---

## Architecture Diagram

```
┌──────────────┐
│ SMS Service  │
│ (Fails)      │
└──────┬───────┘
       │ 1. SMS Failed
       ▼
   producer.scheduleRetry()
       │
       ▼
┌─────────────────┐
│  SMS_Retry      │ ◄── Kafka Topic (Input)
│  Topic          │
└────────┬────────┘
         │ 2. Scheduler consumes
         ▼
┌──────────────────┐
│ Infinite         │
│ Scheduler        │
│                  │
│ - Stores MySQL   │
│ - Waits for time │
└────────┬─────────┘
         │ 3. At retryTime
         ▼
┌──────────────────┐
│ SmsRetryJob      │
│ Handler          │
└────────┬─────────┘
         │ 4. Publishes result
         ▼
┌─────────────────┐
│  SMS_Send       │ ◄── Kafka Topic (Output)
│  Topic          │
└────────┬────────┘
         │ 5. Consumes
         ▼
┌──────────────┐
│ SMS Service  │
│ (Sends SMS)  │
└──────────────┘
```

---

## Running the Demo

```bash
# Compile
mvn clean compile

# Run simulation (no Kafka required)
java -cp target/classes com.telcobright.scheduler.examples.SmsRetrySimulation

# Run full demo (requires Kafka and MySQL)
java -cp target/infinite-scheduler-1.0.0.jar \
  com.telcobright.scheduler.examples.SmsRetrySchedulerExample
```

---

## Key Files

| File | Purpose |
|------|---------|
| `SmsRetryJobHandler.java` | Executes retry, publishes to SMS_Send |
| `SmsRetryProducer.java` | Helper to send retry requests |
| `SmsRetrySchedulerExample.java` | Full working example |
| `SmsRetrySimulation.java` | Standalone demo (no deps) |

---

## Benefits

✅ **Decoupled** - Producer doesn't need to know scheduler instances
✅ **Scalable** - Add more scheduler instances anytime
✅ **Reliable** - MySQL persistence + Kafka durability
✅ **Automatic Load Balancing** - Kafka handles partition distribution
✅ **No Service Discovery** - Just send to topic
✅ **Idempotency** - Duplicate retry requests handled
✅ **DLQ Support** - Failed messages don't block processing

---

## Production Checklist

- [ ] Create Kafka topics: `SMS_Retry`, `SMS_Send`, `SMS_Retry_DLQ`
- [ ] Configure topic partitions (recommend 6 partitions)
- [ ] Set up MySQL database for scheduler
- [ ] Deploy scheduler instances (2-3 for HA)
- [ ] Configure SMS service producer
- [ ] Configure SMS service consumer
- [ ] Set up monitoring (Kafka lag, metrics)
- [ ] Test with failed SMS scenarios
- [ ] Configure max retry attempts
- [ ] Set up alerting for DLQ messages

---

## Need Help?

- **Full Documentation**: `SMS_RETRY_GUIDE.md`
- **Architecture Details**: See guide for deep dive
- **Troubleshooting**: Check guide's troubleshooting section
- **Monitoring**: Guide includes monitoring setup

---

## Quick Test

```bash
# 1. Create topics
kafka-topics.sh --create --topic SMS_Retry --partitions 6 \
  --bootstrap-server localhost:9092

kafka-topics.sh --create --topic SMS_Send --partitions 6 \
  --bootstrap-server localhost:9092

# 2. Start scheduler
java -cp target/infinite-scheduler-1.0.0.jar \
  com.telcobright.scheduler.examples.SmsRetrySchedulerExample

# 3. Watch logs for:
#    - "Scheduled 5 SMS retries"
#    - "Executing SMS retry job"
#    - "Sent to SMS_Send topic"
```

---

**🎉 You're all set!** The library handles scheduling, persistence, execution, and publishing automatically.
