# Infinite Scheduler - Quarkus Standalone Container

## Overview

A standalone Quarkus application that schedules jobs via REST API and publishes them to **Kafka** or **Redis** topics at the scheduled time.

**Key Features:**
- ✅ REST API for scheduling jobs
- ✅ Map-based jobs (no entity classes)
- ✅ Split-verse for persistent storage
- ✅ Quartz for job scheduling
- ✅ Kafka & Redis publishers
- ✅ Topic-based routing (jobType → topic name)

---

## Architecture

```
External Service (API, Order Service, etc.)
    ↓
REST API: POST /api/schedule
    ↓
Insert Map to split-verse (MySQL)
    ↓
Lookahead Fetcher (every 25s)
    ↓
Schedule to Quartz
    ↓
Quartz fires at scheduledTime
    ↓
JobExecutor publishes to:
    - Kafka topic (jobType)
    - Redis stream (jobType)
    ↓
External Consumers
```

---

## Job Structure

### REST API Request:
```json
POST /api/schedule
{
  "id": "sms-123",
  "scheduledTime": "2025-10-17T10:30:00",
  "jobType": "sms_send",
  "phone": "+8801712345678",
  "message": "Hello World",
  "orderId": "ORD-12345"
}
```

### Required Fields:
- `id` (String) - Unique job ID
- `scheduledTime` (ISO 8601 DateTime) - When to execute
- `jobType` (String) - Topic/stream name for routing

### Optional Fields:
- `jobName` (String) - Display name (auto-generated if missing)
- Any custom fields (phone, message, orderId, etc.)

---

## Storage Schema

### Split-Verse Tables (Daily):
```sql
CREATE TABLE scheduled_jobs_2025_10_17 (
    id VARCHAR(255) PRIMARY KEY,
    scheduled_time DATETIME NOT NULL,
    job_type VARCHAR(100) NOT NULL,
    job_name VARCHAR(255),
    job_data_json TEXT,
    scheduled BOOLEAN DEFAULT FALSE,

    INDEX idx_scheduled_time (scheduled_time),
    INDEX idx_job_type (job_type),
    INDEX idx_scheduled (scheduled)
);
```

---

## Configuration

### application.yml:
```yaml
# Server
quarkus:
  http:
    port: 8080

# Scheduler settings
scheduler:
  fetch-interval: 25          # Lookahead interval (seconds)
  lookahead-window: 30        # Lookahead window (seconds)
  publisher: kafka            # "kafka" or "redis"

# MySQL (Quartz + split-verse)
datasource:
  host: 127.0.0.1
  port: 3306
  database: scheduler
  username: root
  password: 123456

# Split-verse
repository:
  table-prefix: scheduled_jobs

# Kafka
kafka:
  bootstrap:
    servers: localhost:9092

# Redis
quarkus:
  redis:
    hosts: redis://localhost:6379
```

---

## REST API

### Schedule Single Job
```bash
curl -X POST http://localhost:8080/api/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "id": "sms-123",
    "scheduledTime": "2025-10-17T10:30:00",
    "jobType": "sms_send",
    "phone": "+8801712345678",
    "message": "Your order is ready!"
  }'
```

**Response:**
```json
{
  "success": true,
  "jobId": "sms-123",
  "scheduledTime": "2025-10-17T10:30:00",
  "message": "Job scheduled successfully"
}
```

### Schedule Batch Jobs
```bash
curl -X POST http://localhost:8080/api/schedule/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "id": "sms-1",
      "scheduledTime": "2025-10-17T10:30:00",
      "jobType": "sms_send",
      "phone": "+880..."
    },
    {
      "id": "email-1",
      "scheduledTime": "2025-10-17T11:00:00",
      "jobType": "email_send",
      "to": "user@example.com"
    }
  ]'
```

**Response:**
```json
{
  "success": true,
  "scheduled": 2,
  "failed": 0,
  "message": "Scheduled 2 jobs successfully"
}
```

### Get Job Status
```bash
curl http://localhost:8080/api/jobs/{jobId}
```

---

## Publishers

### Kafka Publisher
- Publishes to Kafka topic = `jobType`
- Example: `jobType: "sms_send"` → Kafka topic `"sms_send"`
- Full job data sent as JSON message

```java
@ApplicationScoped
public class KafkaJobPublisher implements JobPublisher {

    @Inject
    @Channel("jobs-out")
    Emitter<String> emitter;

    @Override
    public void publish(String topic, Map<String, Object> jobData) {
        String json = objectMapper.writeValueAsString(jobData);

        // Send to Kafka with dynamic topic
        OutgoingKafkaRecordMetadata<String> metadata =
            OutgoingKafkaRecordMetadata.<String>builder()
                .withTopic(topic)
                .build();

        emitter.send(Message.of(json).addMetadata(metadata));
    }
}
```

### Redis Stream Publisher
- Publishes to Redis stream = `jobType`
- Example: `jobType: "sms_send"` → Redis stream `"sms_send"`
- Full job data sent as map

```java
@ApplicationScoped
public class RedisJobPublisher implements JobPublisher {

    @Inject
    RedisClient redisClient;

    @Override
    public void publish(String topic, Map<String, Object> jobData) {
        redisClient.xadd(
            Arrays.asList(topic, "*",
                "data", objectMapper.writeValueAsString(jobData)
            )
        );
    }
}
```

---

## External Consumers

### Kafka Consumer (separate service):
```java
@ApplicationScoped
public class SmsService {

    @Incoming("sms_send")
    public void processSms(String message) {
        Map<String, Object> job = objectMapper.readValue(message, Map.class);

        String phone = (String) job.get("phone");
        String msg = (String) job.get("message");

        smsSender.send(phone, msg);
    }
}
```

### Redis Stream Consumer (separate service):
```java
@ApplicationScoped
public class EmailService {

    @PostConstruct
    void init() {
        // Consume from email_send stream
        redisClient.xreadGroup(
            StreamConsumerGroupOptions.options("email-workers", "consumer-1"),
            Map.of("email_send", "$")
        ).subscribe().with(messages -> {
            messages.forEach(this::processEmail);
        });
    }
}
```

---

## Deployment

### Docker Compose:
```yaml
version: '3.8'

services:
  scheduler:
    image: infinite-scheduler:1.0.0
    ports:
      - "8080:8080"
    environment:
      SCHEDULER_FETCH_INTERVAL: 25
      SCHEDULER_LOOKAHEAD_WINDOW: 30
      SCHEDULER_PUBLISHER: kafka
      DATASOURCE_HOST: mysql
      DATASOURCE_DATABASE: scheduler
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - mysql
      - kafka

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: 123456
      MYSQL_DATABASE: scheduler
    ports:
      - "3306:3306"

  kafka:
    image: confluentinc/cp-kafka:latest
    ports:
      - "9092:9092"
    environment:
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092

  sms-service:
    image: sms-service:1.0.0
    depends_on:
      - kafka
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

---

## Component Design

### JobPublisher Interface:
```java
public interface JobPublisher {
    void publish(String topic, Map<String, Object> jobData);
}
```

### Quartz Job Executor:
```java
public class GenericJobExecutor implements Job {

    private JobPublisher publisher;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        String jobType = dataMap.getString("jobType");
        String jobDataJson = dataMap.getString("jobDataJson");

        Map<String, Object> jobData = objectMapper.readValue(jobDataJson, Map.class);

        // Publish to topic
        publisher.publish(jobType, jobData);

        logger.info("Published job {} to topic: {}", jobData.get("id"), jobType);
    }
}
```

### REST Controller:
```java
@Path("/api")
@ApplicationScoped
public class SchedulerResource {

    @Inject
    InfiniteScheduler scheduler;

    @POST
    @Path("/schedule")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response scheduleJob(Map<String, Object> job) {
        scheduler.scheduleJob(job);
        return Response.ok(Map.of(
            "success", true,
            "jobId", job.get("id"),
            "scheduledTime", job.get("scheduledTime")
        )).build();
    }

    @POST
    @Path("/schedule/batch")
    public Response scheduleJobs(List<Map<String, Object>> jobs) {
        int scheduled = scheduler.scheduleJobs(jobs);
        return Response.ok(Map.of(
            "success", true,
            "scheduled", scheduled
        )).build();
    }
}
```

---

## Benefits

✅ **Standalone Container** - Deploy anywhere
✅ **REST API** - Schedule from any service
✅ **Map-Based** - No entity classes needed
✅ **Topic Routing** - jobType → topic name
✅ **Dual Publishers** - Kafka or Redis
✅ **Scalable** - Horizontal scaling ready
✅ **Microservices** - Consumers are independent

---

## Migration Path

### From Old Library:
1. Deploy infinite-scheduler as container
2. Call REST API instead of repository.insert()
3. Consumers subscribe to Kafka/Redis topics
4. Remove entity classes from your code

---

**Status:** Design complete, ready for Quarkus implementation
**Date:** 2025-10-17
