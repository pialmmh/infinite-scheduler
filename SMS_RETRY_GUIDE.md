# SMS Retry Scheduling with Infinite Scheduler

Complete guide for scheduling failed SMS retries using Kafka topics.

## Architecture Overview

```
┌─────────────────┐
│  SMS Service    │
│  (Your App)     │
└────────┬────────┘
         │ 1. SMS Failed
         │
         ▼
    ┌────────────────┐
    │  SMS_Retry     │  ◄── Input Topic
    │  Kafka Topic   │
    └────────┬───────┘
             │
             │ 2. Consumed by Scheduler
             ▼
    ┌─────────────────────┐
    │ Infinite Scheduler  │
    │  (Kafka Consumer)   │
    │                     │
    │ - Schedules job     │
    │ - Stores in MySQL   │
    │ - Waits for retry   │
    │   time              │
    └─────────┬───────────┘
              │
              │ 3. At retryTime, execute job
              ▼
    ┌─────────────────────┐
    │ SmsRetryJobHandler  │
    └─────────┬───────────┘
              │
              │ 4. Publish to output topic
              ▼
    ┌────────────────┐
    │  SMS_Send      │  ◄── Output Topic
    │  Kafka Topic   │
    └────────┬───────┘
             │
             │ 5. Consume and send SMS
             ▼
    ┌─────────────────┐
    │  SMS Service    │
    │  (Actual Send)  │
    └─────────────────┘
```

## Topics

### Input Topic: `SMS_Retry`
Failed SMS jobs are sent here for scheduling.

**Payload Format:**
```json
{
  "appName": "sms_retry",
  "scheduledTime": "2025-11-13 15:30:00",
  "jobData": {
    "campaignTaskId": 12345,
    "createdOn": "2025-11-13 15:00:00",
    "retryTime": "2025-11-13 15:30:00",
    "retryAttempt": 1,
    "failureReason": "Network timeout"
  }
}
```

### Output Topic: `SMS_Send`
When retry time arrives, the job publishes here.

**Payload Format:**
```json
{
  "campaignTaskId": 12345,
  "createdOn": "2025-11-13 15:00:00",
  "scheduledRetryTime": "2025-11-13 15:30:00",
  "actualExecutionTime": "2025-11-13 15:30:02",
  "retryAttempt": 1
}
```

## Setup Guide

### 1. Start the Scheduler

```java
// Create scheduler manager with Kafka ingest
MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
    .mysqlHost("127.0.0.1")
    .mysqlPort(3306)
    .mysqlDatabase("scheduler")
    .mysqlUsername("root")
    .mysqlPassword("password")
    .withKafkaIngest(
        KafkaIngestConfig.builder()
            .bootstrapServers("localhost:9092")
            .topic("SMS_Retry")              // Listen to SMS_Retry
            .dlqTopic("SMS_Retry_DLQ")       // DLQ for failures
            .groupId("sms-retry-scheduler")
            .maxPollRecords(500)
            .enabled(true)
            .build()
    )
    .build();

// Register SMS retry job handler
SmsRetryJobHandler handler = new SmsRetryJobHandler(
    "localhost:9092",  // Kafka bootstrap servers
    "SMS_Send"         // Output topic
);
manager.registerApp("sms_retry", handler);

// Start the scheduler
manager.startAll();
```

### 2. Send Retry Requests (Producer)

In your SMS service, when an SMS fails, schedule a retry:

```java
// Create producer (once, reuse it)
SmsRetryProducer producer = new SmsRetryProducer("localhost:9092");

// When SMS fails, schedule retry
producer.scheduleRetry(
    campaignTaskId,           // 12345L
    originalCreatedTime,      // When SMS was first created
    retryTime,                // When to retry (e.g., now + 10 minutes)
    retryAttemptNumber,       // 1, 2, 3, etc.
    failureReason             // "Network timeout"
);

// Close when done (app shutdown)
producer.close();
```

### 3. Consume Retry Results

Your SMS service should consume from `SMS_Send` topic:

```java
// Kafka consumer configuration
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("group.id", "sms-sender-service");
props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Collections.singletonList("SMS_Send"));

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
    for (ConsumerRecord<String, String> record : records) {
        // Parse JSON payload
        JSONObject payload = new JSONObject(record.value());

        Long campaignTaskId = payload.getLong("campaignTaskId");
        int retryAttempt = payload.getInt("retryAttempt");

        // Send the actual SMS
        sendSms(campaignTaskId);
    }
}
```

## Complete Integration Example

### Step 1: Your SMS Service (When SMS Fails)

```java
public class SmsService {

    private final SmsRetryProducer retryProducer;

    public SmsService(String kafkaBootstrapServers) {
        this.retryProducer = new SmsRetryProducer(kafkaBootstrapServers);
    }

    public void sendSms(Long campaignTaskId, String phoneNumber, String message) {
        try {
            // Attempt to send SMS
            smsGateway.send(phoneNumber, message);

        } catch (NetworkException e) {
            // SMS failed - schedule retry
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime retryTime = calculateRetryTime(e, attemptNumber);

            retryProducer.scheduleRetry(
                campaignTaskId,
                now,
                retryTime,
                attemptNumber,
                e.getMessage()
            );

            logger.info("SMS failed, scheduled retry at {}", retryTime);
        }
    }

    private LocalDateTime calculateRetryTime(Exception e, int attempt) {
        // Exponential backoff: 1 min, 5 min, 15 min, 30 min, 1 hour
        int[] delays = {1, 5, 15, 30, 60};
        int minutes = delays[Math.min(attempt - 1, delays.length - 1)];
        return LocalDateTime.now().plusMinutes(minutes);
    }
}
```

### Step 2: Run the Scheduler

```bash
# Compile and package
mvn clean package -DskipTests

# Run the scheduler
java -cp target/infinite-scheduler-1.0.0.jar \
  com.telcobright.scheduler.examples.SmsRetrySchedulerExample \
  -Dkafka.bootstrap.servers=localhost:9092 \
  -Ddb.host=127.0.0.1 \
  -Ddb.name=scheduler \
  -Ddb.user=root \
  -Ddb.password=123456
```

### Step 3: SMS Service Consumes Results

```java
public class SmsRetryConsumer {

    private final KafkaConsumer<String, String> consumer;

    public SmsRetryConsumer(String kafkaBootstrapServers) {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaBootstrapServers);
        props.put("group.id", "sms-sender-service");
        props.put("key.deserializer", "StringDeserializer");
        props.put("value.deserializer", "StringDeserializer");

        this.consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("SMS_Send"));
    }

    public void start() {
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

            for (ConsumerRecord<String, String> record : records) {
                processRetry(record.value());
            }
        }
    }

    private void processRetry(String jsonPayload) {
        // Parse payload
        JSONObject data = new JSONObject(jsonPayload);
        Long campaignTaskId = data.getLong("campaignTaskId");
        int retryAttempt = data.getInt("retryAttempt");

        // Fetch SMS details from database
        SmsTask task = database.getSmsTask(campaignTaskId);

        // Attempt to send
        try {
            smsGateway.send(task.getPhoneNumber(), task.getMessage());
            logger.info("✅ SMS retry successful: taskId={}", campaignTaskId);

        } catch (Exception e) {
            logger.error("❌ SMS retry failed: taskId={}", campaignTaskId);

            // Schedule another retry if not max attempts
            if (retryAttempt < MAX_RETRIES) {
                scheduleNextRetry(task, retryAttempt + 1);
            }
        }
    }
}
```

## Running the Demo

### Prerequisites
1. **Kafka** running on localhost:9092
2. **MySQL** running on 127.0.0.1:3306 with database `scheduler`

### Create Kafka Topics
```bash
# Create SMS_Retry topic
kafka-topics.sh --create \
  --topic SMS_Retry \
  --partitions 6 \
  --replication-factor 1 \
  --bootstrap-server localhost:9092

# Create SMS_Send topic
kafka-topics.sh --create \
  --topic SMS_Send \
  --partitions 6 \
  --replication-factor 1 \
  --bootstrap-server localhost:9092

# Create DLQ topic
kafka-topics.sh --create \
  --topic SMS_Retry_DLQ \
  --partitions 3 \
  --replication-factor 1 \
  --bootstrap-server localhost:9092
```

### Run the Example
```bash
# Compile
mvn clean compile

# Run
java -cp target/infinite-scheduler-1.0.0.jar \
  com.telcobright.scheduler.examples.SmsRetrySchedulerExample
```

**What happens:**
1. Scheduler starts and listens to `SMS_Retry` topic
2. Demo sends 5 failed SMS retry requests to `SMS_Retry`
3. Scheduler schedules them for retry at specified times
4. At retry time, jobs execute and publish to `SMS_Send`
5. Your SMS service consumes from `SMS_Send` and sends actual SMS

## Message Format Details

### Sending Retry Request to SMS_Retry

You need to send a `ScheduleJobRequest` JSON to the `SMS_Retry` topic:

```java
import com.telcobright.scheduler.kafka.ScheduleJobRequest;

// Create request
ScheduleJobRequest request = new ScheduleJobRequest(
    "sms_retry",                    // appName (must match registered app)
    retryTime,                      // LocalDateTime - when to execute
    jobData                         // Map<String, Object> - your data
);

// Set optional fields
request.setPriority("high");        // Optional: "high", "normal", "low"
request.setIdempotencyKey("task-12345-retry-1");  // Prevent duplicates

// Convert to JSON and send to Kafka
String json = request.toJson();
producer.send(new ProducerRecord<>("SMS_Retry", json));
```

**Or use the provided SmsRetryProducer** (recommended):
```java
SmsRetryProducer producer = new SmsRetryProducer("localhost:9092");
producer.scheduleRetry(campaignTaskId, createdOn, retryTime);
```

## Production Configuration

### High Availability Setup

**Multiple Scheduler Instances:**
```bash
# Server 1
java -jar scheduler.jar -Dinstance.id=scheduler-1

# Server 2
java -jar scheduler.jar -Dinstance.id=scheduler-2

# Server 3
java -jar scheduler.jar -Dinstance.id=scheduler-3
```

All instances:
- Use same consumer group ID
- Kafka automatically distributes partitions
- Automatic rebalancing on failure
- No coordination needed

### Tuning Parameters

```java
KafkaIngestConfig.builder()
    .bootstrapServers("kafka1:9092,kafka2:9092,kafka3:9092")
    .topic("SMS_Retry")
    .dlqTopic("SMS_Retry_DLQ")
    .groupId("sms-retry-scheduler-prod")
    .maxPollRecords(1000)           // Increase for higher throughput
    .pollTimeoutMs(1000)
    .maxRetries(3)                  // Retry failed messages 3 times
    .enabled(true)
    .build()
```

## Monitoring

### Check Consumer Lag
```bash
kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group sms-retry-scheduler \
  --describe
```

### Check DLQ for Failed Messages
```bash
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic SMS_Retry_DLQ \
  --from-beginning
```

### Application Metrics

The scheduler provides metrics via `getKafkaIngestConsumer().getMetrics()`:
- `messagesReceived` - Total messages consumed
- `messagesProcessed` - Successfully scheduled jobs
- `messagesFailed` - Failed processing attempts
- `messagesSentToDLQ` - Messages sent to DLQ after max retries

## Benefits

✅ **Automatic Load Balancing** - Kafka handles partition distribution
✅ **No Service Discovery** - Producers just send to topic
✅ **Scalable** - Add more scheduler instances anytime
✅ **Reliable** - MySQL persistence + Kafka durability
✅ **Idempotency** - Duplicate retry requests are handled
✅ **DLQ Support** - Failed messages don't block processing
✅ **Metrics** - Full visibility into processing pipeline

## Troubleshooting

### Jobs Not Executing

1. Check scheduler is running and consuming:
```bash
kafka-consumer-groups.sh --describe --group sms-retry-scheduler \
  --bootstrap-server localhost:9092
```

2. Check for messages in DLQ:
```bash
kafka-console-consumer.sh --topic SMS_Retry_DLQ \
  --bootstrap-server localhost:9092 --from-beginning
```

3. Check MySQL for scheduled jobs:
```sql
SELECT * FROM sms_retry_scheduled_jobs_YYYYMMDD
ORDER BY scheduled_time DESC LIMIT 10;
```

### Messages Going to DLQ

Check the DLQ message headers for error details:
```bash
kafka-console-consumer.sh --topic SMS_Retry_DLQ \
  --bootstrap-server localhost:9092 \
  --property print.headers=true \
  --from-beginning
```

Common reasons:
- Invalid JSON format
- Missing required fields (appName, scheduledTime)
- Unregistered app name

## Files Created

**Producer:**
- `SmsRetryProducer.java` - Send retry requests to Kafka

**Job Handler:**
- `SmsRetryJobHandler.java` - Execute retry and publish to SMS_Send

**Example:**
- `SmsRetrySchedulerExample.java` - Complete working example

**This Guide:**
- `SMS_RETRY_GUIDE.md` - Documentation
