# Infinite Scheduler - Simplified Map-Based Architecture

## Overview

A simplified, map-based job scheduler that integrates with **split-verse** for storage and **omni-queue** for job execution via topic-based messaging.

**Key Principle:** Schedule simple Map<String, Object> jobs, not entities. No interfaces, no coupling.

---

## Architecture Flow

```
1. User inserts Map → split-verse repository
   ↓
2. Scheduler lookahead fetcher (every 25s)
   ↓
3. Schedules to Quartz
   ↓
4. Quartz fires at scheduledTime
   ↓
5. OmniQueueJobExecutor publishes to topic (jobType)
   ↓
6. External consumer subscribes to topic
   ↓
7. Consumer processes job
```

---

## Job Map Structure

### Required Fields:
```java
Map<String, Object> job = Map.of(
    "id", "job-123",                           // Unique job ID (String)
    "scheduledTime", LocalDateTime.now(),      // When to execute (LocalDateTime)
    "jobType", "sms_send"                      // Topic name for routing (String)
);
```

### Optional Fields:
```java
"jobName", "Send welcome SMS"                  // Display name (auto-generated if missing)
```

### Custom Fields (any key-value):
```java
"phone", "+8801712345678"
"message", "Hello World"
"orderId", "ORD-12345"
"retryCount", 0
// ... any other fields
```

---

## Storage Schema

### Split-Verse Tables (Daily):
```sql
CREATE TABLE scheduled_jobs_2025_10_17 (
    id VARCHAR(255) PRIMARY KEY,
    scheduled_time DATETIME NOT NULL,
    job_type VARCHAR(100) NOT NULL,
    job_name VARCHAR(255),
    job_data_json TEXT,                    -- Full map as JSON
    scheduled BOOLEAN DEFAULT FALSE,

    INDEX idx_scheduled_time (scheduled_time),
    INDEX idx_job_type (job_type),
    INDEX idx_scheduled (scheduled)
);
```

**Sharding Key:** `scheduled_time` (daily tables)

---

## OmniQueue Integration

### Supported Backends:
- ✅ **Kafka** (smallrye backend)
- ✅ **RabbitMQ** (rabbitmq backend)
- ✅ **Redis** (redis backend)

### Unsupported (throw exception):
- ❌ Vert.x EventBus
- ❌ BlockingQueue
- ❌ RabbitMQ-Memory
- ❌ SmallRye InMemory

### Topic Routing:
- Topic name = `jobType` field from job map
- Example: `jobType: "sms_send"` → publishes to topic `"sms_send"`

---

## Configuration

### Scheduler Config:
```java
SchedulerConfig config = SchedulerConfig.builder()
    // Scheduler settings
    .fetchInterval(25)
    .lookaheadWindow(30)

    // MySQL settings (for Quartz + split-verse)
    .mysqlHost("127.0.0.1")
    .mysqlPort(3306)
    .mysqlDatabase("scheduler")
    .mysqlUsername("root")
    .mysqlPassword("123456")

    // Split-verse settings
    .repositoryTablePrefix("scheduled_jobs")

    // OmniQueue backend selection
    .omniQueueBackend("rabbitmq")  // or "kafka" or "redis"

    .build();
```

### OmniQueue Config (application.yml):
```yaml
# For Kafka
omni-queue:
  channels:
    sms_send:
      backend: smallrye
      consumer-group: sms-workers
    email_send:
      backend: smallrye
      consumer-group: email-workers

mp:
  messaging:
    outgoing:
      sms_send-out:
        connector: smallrye-kafka
        topic: sms_send
        bootstrap.servers: localhost:9092

# For RabbitMQ
omni-queue:
  channels:
    sms_send:
      backend: rabbitmq
      consumer-group: sms-workers

mp:
  messaging:
    outgoing:
      sms_send-out:
        connector: smallrye-amqp
        address: sms_send
        host: localhost
        port: 5672

# For Redis
omni-queue:
  channels:
    sms_send:
      backend: redis
      consumer-group: sms-workers
      consumer-name: worker-1

quarkus:
  redis:
    hosts: redis://localhost:6379
```

---

## Usage Example

### 1. Initialize Scheduler:
```java
SchedulerConfig config = SchedulerConfig.builder()
    .mysqlHost("127.0.0.1")
    .mysqlDatabase("scheduler")
    .repositoryTablePrefix("scheduled_jobs")
    .omniQueueBackend("rabbitmq")
    .build();

InfiniteScheduler scheduler = new InfiniteScheduler(config);
scheduler.start();
```

### 2. Schedule Jobs (from any service):
```java
// Schedule SMS
Map<String, Object> smsJob = Map.of(
    "id", "sms-" + System.currentTimeMillis(),
    "scheduledTime", LocalDateTime.now().plusMinutes(5),
    "jobType", "sms_send",
    "phone", "+8801712345678",
    "message", "Your order is confirmed!",
    "orderId", "ORD-12345"
);
scheduler.scheduleJob(smsJob);

// Schedule Email
Map<String, Object> emailJob = Map.of(
    "id", "email-" + UUID.randomUUID(),
    "scheduledTime", LocalDateTime.now().plusHours(1),
    "jobType", "email_send",
    "to", "user@example.com",
    "subject", "Welcome",
    "body", "Welcome to our platform!"
);
scheduler.scheduleJob(emailJob);
```

### 3. External Consumers (separate microservices):
```java
// SMS Service (Container 2)
@ApplicationScoped
public class SmsService {

    @Inject
    OmniQueueService omniQueue;

    @Inject
    SmsSender smsSender;

    @PostConstruct
    void init() {
        omniQueue.subscribe("sms_send", MessageHandler.blockingPayload(data -> {
            Map<String, Object> job = (Map<String, Object>) data;

            String phone = (String) job.get("phone");
            String message = (String) job.get("message");

            smsSender.send(phone, message);
            logger.info("SMS sent: {}", job.get("id"));
        }));
    }
}

// Email Service (Container 3)
@ApplicationScoped
public class EmailService {

    @Inject
    OmniQueueService omniQueue;

    @PostConstruct
    void init() {
        omniQueue.subscribe("email_send", MessageHandler.blockingPayload(data -> {
            Map<String, Object> job = (Map<String, Object>) data;

            emailSender.send(
                (String) job.get("to"),
                (String) job.get("subject"),
                (String) job.get("body")
            );
        }));
    }
}
```

---

## Component Design

### ScheduledJobEntity (Internal Storage Only):
```java
@Table(name = "scheduled_jobs")
public class ScheduledJobEntity implements ShardingEntity<LocalDateTime> {

    @Id(autoGenerated = false)
    private String id;

    @ShardingKey
    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    @Column(name = "job_type")
    private String jobType;

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "job_data_json", columnDefinition = "TEXT")
    private String jobDataJson;

    @Column(name = "scheduled")
    private Boolean scheduled;

    // getters/setters, ShardingEntity methods
}
```

### OmniQueueJobExecutor (Quartz Job):
```java
public class OmniQueueJobExecutor implements Job {

    private final OmniQueueService omniQueue;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        // Get topic from jobType
        String topic = dataMap.getString("jobType");

        // Get full job data JSON
        String jobDataJson = dataMap.getString("jobDataJson");
        Map<String, Object> jobData = objectMapper.readValue(jobDataJson, Map.class);

        // Publish to omni-queue topic
        QueueProducer<Map> producer = omniQueue.getProducer(topic);
        producer.publishAndAwait(jobData);

        logger.info("Published job {} to topic: {}", jobData.get("id"), topic);
    }
}
```

### InfiniteScheduler API:
```java
public class InfiniteScheduler {

    // Constructor
    public InfiniteScheduler(SchedulerConfig config) { ... }

    // Schedule single job
    public void scheduleJob(Map<String, Object> job) { ... }

    // Schedule batch of jobs
    public void scheduleJobs(List<Map<String, Object>> jobs) { ... }

    // Start/stop
    public void start() { ... }
    public void stop() { ... }
}
```

---

## Deployment Architecture

```
┌─────────────────────────────────────────────────────┐
│  Container 1: Infinite Scheduler                    │
│  - Lookahead fetcher                                │
│  - Quartz scheduler                                 │
│  - OmniQueue producer                               │
│  - Publishes to topics                              │
└──────────────────┬──────────────────────────────────┘
                   │
                   ↓
┌─────────────────────────────────────────────────────┐
│  Message Broker (RabbitMQ / Kafka / Redis)          │
│  - Topics: sms_send, email_send, notification_send  │
└──────┬──────────────────────┬──────────────────┬────┘
       │                      │                  │
       ↓                      ↓                  ↓
┌──────────────┐    ┌──────────────┐   ┌──────────────┐
│ Container 2  │    │ Container 3  │   │ Container 4  │
│ SMS Service  │    │Email Service │   │Notif Service │
│              │    │              │   │              │
│ Subscribes:  │    │ Subscribes:  │   │ Subscribes:  │
│ "sms_send"   │    │ "email_send" │   │ "notif_send" │
└──────────────┘    └──────────────┘   └──────────────┘
```

---

## Benefits

✅ **No Entity Classes** - Just use Map<String, Object>
✅ **No Interfaces** - No SchedulableEntity to implement
✅ **Topic-based Routing** - jobType automatically routes to consumers
✅ **Complete Decoupling** - Scheduler doesn't know about handlers
✅ **Microservices Ready** - Each consumer is independent service
✅ **Multiple Backends** - Kafka for throughput, RabbitMQ for reliability, Redis for speed
✅ **Flexible** - Any job type, any custom fields
✅ **Scalable** - Scale consumers independently

---

## Limitations

❌ **No Retry Logic in Scheduler** - Consumers handle retries
❌ **Backend Restrictions** - Only Kafka, RabbitMQ, Redis supported
❌ **No Job Tracking** - Scheduler doesn't track completion (consumers do)
❌ **External OmniQueue Required** - Must configure omni-queue channels

---

## Migration from Old Design

### Old (Entity-based):
```java
SmsEntity entity = new SmsEntity();
entity.setId("123");
entity.setScheduledTime(LocalDateTime.now());
entity.setPhoneNumber("+880...");
repository.insert(entity);
```

### New (Map-based):
```java
Map<String, Object> job = Map.of(
    "id", "123",
    "scheduledTime", LocalDateTime.now(),
    "jobType", "sms_send",
    "phone", "+880..."
);
scheduler.scheduleJob(job);
```

---

**Status:** Design complete, ready for implementation
**Date:** 2025-10-17
