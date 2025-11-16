# Kafka Job Ingestion for Infinite Scheduler

## Overview

The Kafka Job Ingest feature allows you to schedule jobs through Kafka topics instead of REST API calls. This provides several significant advantages for distributed systems.

## Why Kafka-Based Ingestion?

### 1. **Automatic Load Balancing**
- Kafka consumer groups automatically distribute messages across multiple scheduler instances
- No need to implement client-side load balancing

### 2. **No Service Discovery Required**
- Producers don't need to know which scheduler instances are running
- Just publish to a Kafka topic
- Kafka handles routing to available consumers

###3. **Automatic Health Monitoring & Rebalancing**
- Kafka tracks consumer health automatically
- If a scheduler instance dies, Kafka rebalances partitions to healthy instances
- No manual intervention required

### 4. **Decoupled Architecture**
- Producers and consumers are completely decoupled
- Can scale producers and consumers independently
- Can deploy new scheduler versions without affecting producers

### 5. **Reliability**
- Kafka provides message persistence
- At-least-once delivery guarantee
- Dead Letter Queue (DLQ) for failed messages
- Idempotency key support to prevent duplicates

### 6. **High Throughput**
- Kafka can handle millions of messages per second
- Batch processing for efficiency
- Configurable poll size and timeout

## Architecture

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│   SMS Service   │       │  Payment Service│       │  SIP Service    │
│   (Producer)    │       │    (Producer)   │       │   (Producer)    │
└────────┬────────┘       └────────┬────────┘       └────────┬────────┘
         │                         │                         │
         └─────────────────────────┼─────────────────────────┘
                                   │
                          Publish to Kafka Topic
                      "scheduler.jobs.ingest"
                                   │
                                   ▼
                        ┌──────────────────────┐
                        │   Kafka Cluster      │
                        │  (Topic Partitions)  │
                        └──────────┬───────────┘
                                   │
                    Consumer Group: "infinite-scheduler-ingest"
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         │                         │                         │
         ▼                         ▼                         ▼
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│  Scheduler #1   │       │  Scheduler #2   │       │  Scheduler #3   │
│  (Consumer)     │       │  (Consumer)     │       │  (Consumer)     │
│  Partition 0,3  │       │  Partition 1,4  │       │  Partition 2,5  │
└────────┬────────┘       └────────┬────────┘       └────────┬────────┘
         │                         │                         │
         └─────────────────────────┼─────────────────────────┘
                                   │
                                   ▼
                        ┌──────────────────────┐
                        │  Quartz Scheduler    │
                        │    (MySQL)           │
                        └──────────────────────┘
```

## Quick Start

### 1. Configure Scheduler with Kafka Ingest

```java
import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.kafka.KafkaIngestConfig;

// Create scheduler with Kafka ingest enabled
MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
    // MySQL configuration
    .mysqlHost("10.10.199.165")
    .mysqlPort(3306)
    .mysqlDatabase("btcl_sms")
    .mysqlUsername("root")
    .mysqlPassword("password")

    // Enable Kafka ingest with custom configuration
    .withKafkaIngest(
        KafkaIngestConfig.builder()
            .bootstrapServers("10.10.199.83:9092")
            .topic("scheduler.jobs.ingest")
            .dlqTopic("scheduler.jobs.dlq")
            .groupId("infinite-scheduler-consumer-group")
            .maxPollRecords(500)
            .maxRetries(3)
            .enabled(true)
            .build()
    )
    .build();

// Register applications
manager.registerApp("sms", new SmsJobHandler());
manager.registerApp("sipcall", new SipCallJobHandler());
manager.registerApp("payment_gateway", new PaymentGatewayJobHandler());

// Start (includes Kafka consumer)
manager.startAll();
```

### 2. Send Job Scheduling Requests via Kafka

```java
import com.telcobright.scheduler.kafka.ScheduleJobRequest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// Create Kafka producer
Properties props = new Properties();
props.put("bootstrap.servers", "10.10.199.83:9092");
props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("acks", "all");

KafkaProducer<String, String> producer = new KafkaProducer<>(props);

// Create job data
Map<String, Object> smsData = new HashMap<>();
smsData.put("phoneNumber", "+8801712345678");
smsData.put("message", "Hello World!");
smsData.put("senderId", "MyApp");

// Create schedule request
ScheduleJobRequest request = new ScheduleJobRequest(
    "sms",                              // App name
    LocalDateTime.now().plusMinutes(5), // Execute in 5 minutes
    smsData
);
request.setPriority("normal");
request.setIdempotencyKey("unique-key-" + System.currentTimeMillis());

// Send to Kafka
ProducerRecord<String, String> record = new ProducerRecord<>(
    "scheduler.jobs.ingest",
    "sms-job-1",
    request.toJson()
);

producer.send(record, (metadata, error) -> {
    if (error != null) {
        System.err.println("Failed to send: " + error.getMessage());
    } else {
        System.out.println("Sent to partition " + metadata.partition() +
                         ", offset " + metadata.offset());
    }
});
```

## Configuration Options

### KafkaIngestConfig Builder

```java
KafkaIngestConfig config = KafkaIngestConfig.builder()
    // Required: Kafka broker addresses
    .bootstrapServers("localhost:9092")

    // Required: Topic to consume job requests from
    .topic("scheduler.jobs.ingest")

    // Optional: Dead Letter Queue topic for failed messages
    .dlqTopic("scheduler.jobs.dlq")

    // Optional: Consumer group ID (default: infinite-scheduler-ingest)
    .groupId("my-scheduler-group")

    // Optional: Max records per poll (default: 500)
    .maxPollRecords(1000)

    // Optional: Poll timeout in ms (default: 1000)
    .pollTimeoutMs(2000)

    // Optional: Enable auto-commit (default: false)
    .enableAutoCommit(false)

    // Optional: Max retries for failed messages (default: 3)
    .maxRetries(5)

    // Optional: Enable/disable consumer (default: true)
    .enabled(true)

    .build();
```

### MultiAppSchedulerManager Builder

```java
// Method 1: Full custom configuration
manager.withKafkaIngest(kafkaIngestConfig)

// Method 2: Simple configuration with defaults
manager.withKafkaIngest("localhost:9092", "scheduler.jobs.ingest")

// Method 3: Use all defaults (localhost:9092, scheduler.jobs.ingest)
manager.withKafkaIngest()
```

## Message Format

### ScheduleJobRequest JSON

```json
{
  "requestId": "auto-generated-uuid",
  "appName": "sms",
  "scheduledTime": "2025-11-03T22:00:00",
  "priority": "normal",
  "idempotencyKey": "sms-12345-unique",
  "jobData": {
    "phoneNumber": "+8801712345678",
    "message": "Hello World!",
    "senderId": "MyApp"
  }
}
```

### Fields

- **requestId**: Unique identifier (auto-generated if not provided)
- **appName**: Name of the registered application (required)
- **scheduledTime**: When to execute the job in ISO 8601 format (required)
- **priority**: Job priority: `high`, `normal`, `low` (optional)
- **idempotencyKey**: For duplicate detection (optional but recommended)
- **jobData**: Application-specific job data (required)

## Features

### 1. Idempotency

Prevent duplicate job scheduling with idempotency keys:

```java
request.setIdempotencyKey("order-" + orderId);
```

- Same key within 24 hours = job skipped
- Cache is in-memory (consider Redis for production cluster)

### 2. Dead Letter Queue (DLQ)

Failed messages are automatically sent to DLQ after max retries:

```java
.dlqTopic("scheduler.jobs.dlq")
.maxRetries(3)
```

DLQ messages include error metadata in headers:
- `original-topic`
- `original-partition`
- `original-offset`
- `error-message`
- `error-timestamp`

### 3. Retry with Exponential Backoff

Failed messages are retried with exponential backoff:
- Retry 1: 200ms delay
- Retry 2: 400ms delay
- Retry 3: 800ms delay
- Then send to DLQ

### 4. Manual Commit

By default, offsets are committed manually after successful processing:
- Ensures at-least-once delivery
- Failed messages can be retried
- No message loss on consumer crash

### 5. Consumer Metrics

Track consumer performance:

```java
KafkaJobIngestConsumer consumer = manager.getKafkaIngestConsumer();
Map<String, Long> metrics = consumer.getMetrics();

System.out.println("Messages received: " + metrics.get("messagesReceived"));
System.out.println("Messages processed: " + metrics.get("messagesProcessed"));
System.out.println("Messages failed: " + metrics.get("messagesFailed"));
System.out.println("Messages in DLQ: " + metrics.get("messagesSentToDLQ"));
System.out.println("Idempotency cache size: " + metrics.get("idempotencyCacheSize"));
```

## Scaling

### Horizontal Scaling

Run multiple scheduler instances:

```bash
# Instance 1
java -jar infinite-scheduler.jar

# Instance 2 (on different server)
java -jar infinite-scheduler.jar

# Instance 3 (on another server)
java -jar infinite-scheduler.jar
```

Kafka automatically:
- Assigns partitions to instances
- Rebalances on instance failure
- Ensures each message goes to exactly one instance

### Partition Strategy

For optimal load balancing:
- Create topic with 6+ partitions
- One partition can only be consumed by one instance
- More partitions = more parallelism

```bash
# Create topic with 12 partitions
kafka-topics.sh --create \
  --topic scheduler.jobs.ingest \
  --partitions 12 \
  --replication-factor 3 \
  --bootstrap-server localhost:9092
```

## Monitoring

### Health Check

```java
if (consumer.isRunning()) {
    System.out.println("Consumer is healthy");
}
```

### Kafka Consumer Lag

Monitor lag to detect processing issues:

```bash
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group infinite-scheduler-ingest \
  --describe
```

### Application Logs

Enable debug logging:

```xml
<logger name="com.telcobright.scheduler.kafka" level="DEBUG" />
```

## Best Practices

### 1. Always Use Idempotency Keys
```java
request.setIdempotencyKey("app-" + entityId + "-" + timestamp);
```

### 2. Monitor DLQ
Set up alerts when messages land in DLQ:
```bash
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic scheduler.jobs.dlq \
  --from-beginning
```

### 3. Tune Consumer Settings
For high throughput:
```java
.maxPollRecords(1000)
.pollTimeoutMs(500)
```

For low latency:
```java
.maxPollRecords(100)
.pollTimeoutMs(100)
```

### 4. Use Meaningful Keys
Kafka uses message keys for partition assignment:
```java
// Good: Related jobs go to same partition
ProducerRecord<>(topic, "customer-" + customerId, json)

// Bad: Random distribution
ProducerRecord<>(topic, UUID.randomUUID().toString(), json)
```

### 5. Set Proper Retention
```bash
kafka-configs.sh --bootstrap-server localhost:9092 \
  --entity-type topics \
  --entity-name scheduler.jobs.ingest \
  --alter --add-config retention.ms=86400000  # 24 hours
```

## Troubleshooting

### Consumer Not Starting

Check logs for:
```
KafkaJobIngestConsumer created with config: ...
Kafka ingest consumer started for topic: ...
```

If missing, ensure:
- Kafka config is not null
- `enabled` is true
- Kafka broker is reachable

### Messages Not Being Processed

1. Check consumer lag:
   ```bash
   kafka-consumer-groups.sh --describe ...
   ```

2. Check if app is registered:
   ```java
   manager.getRegisteredApps().contains("sms")
   ```

3. Verify message format is correct JSON

### High DLQ Rate

Common causes:
- Invalid JSON format
- Unknown app name
- Missing required fields
- Database connection issues

Check DLQ messages:
```bash
kafka-console-consumer.sh --topic scheduler.jobs.dlq --from-beginning
```

## Migration from REST to Kafka

### Gradual Migration

1. Enable both REST and Kafka:
   ```java
   // Kafka ingest
   manager.withKafkaIngest(...)

   // REST API (still works)
   JobStatusApi api = new JobStatusApi(dataSource);
   api.start(7070);
   ```

2. Migrate producers one by one:
   - SMS service → Kafka
   - Test thoroughly
   - Payment service → Kafka
   - Test thoroughly
   - SIP service → Kafka

3. Eventually remove REST API

### Comparison

| Feature | REST API | Kafka Ingest |
|---------|----------|--------------|
| Load Balancing | Client-side | Automatic |
| Service Discovery | Required | Not needed |
| Failover | Manual | Automatic |
| Throughput | Moderate | Very High |
| Latency | Low | Slightly higher |
| Complexity | Lower | Higher |
| Decoupling | Tight | Loose |

## Example: Complete Setup

See `KafkaIngestExample.java` for a complete working example with:
- MultiAppSchedulerManager setup
- Kafka producer simulation
- Multiple applications (SMS, SIPCall, Payment)
- Idempotency handling
- Error handling

Run it:
```bash
mvn clean package
java -jar target/infinite-scheduler-1.0.0.jar \
  -Dkafka.bootstrap.servers=10.10.199.83:9092 \
  -Ddb.host=10.10.199.165 \
  -Ddb.name=btcl_sms
```

## Summary

Kafka-based job ingestion provides:
- ✅ Automatic load balancing
- ✅ No service discovery needed
- ✅ Automatic failover
- ✅ High throughput
- ✅ Reliable delivery
- ✅ DLQ for failed messages
- ✅ Idempotency support
- ✅ Easy horizontal scaling

Perfect for distributed, high-scale deployments!
