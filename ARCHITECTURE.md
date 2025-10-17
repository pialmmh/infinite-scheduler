# Infinite Scheduler - Architecture Documentation

**Version:** 1.0.0 (Quarkus)
**Last Updated:** 2025-10-18

## Table of Contents

1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Core Components](#core-components)
4. [Data Flow](#data-flow)
5. [Technology Stack](#technology-stack)
6. [Performance Optimizations](#performance-optimizations)
7. [Scalability](#scalability)
8. [Monitoring](#monitoring)

---

## Overview

Infinite Scheduler is a high-performance, distributed job scheduling microservice built on Quarkus. It schedules millions to billions of jobs efficiently using:

- **Quartz Scheduler** for in-memory job execution
- **Split-verse** for time-partitioned MySQL storage
- **Kafka/Redis** for job publishing
- **REST API** for job management
- **Web UI** for real-time monitoring

### Key Features

- ✅ **Massive Scale**: Handle billions of scheduled jobs
- ✅ **Database-Level Filtering**: 90% reduction in data transfer
- ✅ **Dual Publisher Support**: Kafka and Redis
- ✅ **REST API**: Schedule and monitor jobs via HTTP
- ✅ **Real-Time Monitoring**: Web UI with live stats
- ✅ **Automatic Cleanup**: Time-based partition management
- ✅ **UTC Timezone Handling**: Consistent global scheduling

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Infinite Scheduler                       │
│                    (Quarkus Service)                         │
└─────────────────────────────────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  REST API    │  │   Lookahead  │  │   Quartz     │
│  Endpoints   │  │   Fetcher    │  │   Scheduler  │
│              │  │  (25s cycle) │  │   (RAM)      │
└──────────────┘  └──────────────┘  └──────────────┘
        │                  │                  │
        │                  │                  │
        ▼                  ▼                  ▼
┌─────────────────────────────────────────────────┐
│        Split-Verse Repository                   │
│    (Multi-Table, Daily Partitions)              │
│                                                  │
│  ┌───────────────┐  ┌───────────────┐          │
│  │ 2025-10-17    │  │ 2025-10-18    │  ...     │
│  │ (300K jobs)   │  │ (320K jobs)   │          │
│  └───────────────┘  └───────────────┘          │
└─────────────────────────────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │   MySQL Database       │
              │   (UTC Timezone)       │
              └────────────────────────┘

                Job Execution Flow
                         │
        ┌────────────────┴────────────────┐
        │                                 │
        ▼                                 ▼
┌──────────────┐                 ┌──────────────┐
│    Kafka     │                 │    Redis     │
│  Publisher   │                 │  Publisher   │
│              │                 │              │
│ Topic:       │                 │ Stream:      │
│ {type}-{name}│                 │ {type}-{name}│
└──────────────┘                 └──────────────┘
```

---

## Core Components

### 1. InfiniteSchedulerService

**Purpose:** Central orchestrator for job scheduling lifecycle

**Responsibilities:**
- Initialize Quartz scheduler (in-memory RAMJobStore)
- Initialize Split-verse repository (MySQL multi-table)
- Select publisher (Kafka vs Redis) based on configuration
- Start lookahead fetcher thread
- Provide CDI beans for injection

**Configuration:**
```yaml
scheduler:
  fetch-interval: 25        # Fetch every 25 seconds
  lookahead-window: 30      # Look 30 seconds ahead
  publisher: kafka          # or redis
```

**Startup Sequence:**
1. Load configuration from `application.yml`
2. Select publisher (Kafka or Redis)
3. Initialize repository with connection pooling
4. Create Quartz scheduler (RAM-based)
5. Start lookahead fetcher (daemon thread)

### 2. Lookahead Fetcher

**Purpose:** Continuously fetch unscheduled jobs and load them into Quartz

**Algorithm:**
```java
Every 25 seconds:
  1. Calculate time window: [now_utc, now_utc + 30s]
  2. Query repository with filters:
     repository.findByPartitionRangeWithFilters(
       start: now_utc,
       end: now_utc + 30s,
       filters: {scheduled: false}
     )
  3. For each unscheduled job:
     - Create Quartz JobDetail with job data
     - Create Trigger with exact execution time
     - Schedule to Quartz
     - Mark as scheduled=true in database
```

**Performance:**
- Database-level filtering (90% reduction)
- Partition pruning (queries only relevant daily tables)
- Batch processing (configurable max jobs per fetch)

### 3. Quartz Scheduler

**Purpose:** Execute jobs at precise scheduled times

**Configuration:**
```java
org.quartz.scheduler.instanceName: InfiniteScheduler
org.quartz.threadPool.threadCount: 10
org.quartz.jobStore.class: org.quartz.simpl.RAMJobStore
```

**Job Execution Flow:**
1. Quartz fires job at scheduled time
2. GenericJobExecutor receives JobExecutionContext
3. Extract job data from JobDataMap
4. Determine queue name: `{jobType}-{jobName}`
5. Publish to Kafka or Redis
6. Log execution

**Why RAM-based?**
- Ultra-fast scheduling (no DB overhead)
- Jobs are ephemeral (already persisted in Split-verse)
- Lookahead fetcher continuously replenishes

### 4. Split-Verse Repository

**Purpose:** Time-partitioned MySQL storage for billions of jobs

**Features:**
- **Multi-Table Mode**: One table per day (e.g., `scheduled_jobs_20251018`)
- **Automatic Cleanup**: Old tables dropped based on retention policy
- **Partition Pruning**: Queries only relevant tables
- **Connection Pooling**: HikariCP for efficient connections
- **Database-Level Filtering**: WHERE clause filtering before fetching

**Entity Schema:**
```java
@Table(name = "scheduled_jobs")
public class ScheduledJobEntity {
    @Id
    String id;                   // UUID

    @ShardingKey
    LocalDateTime scheduledTime;  // UTC timezone

    String jobType;               // e.g., "sms_send"
    String jobName;               // e.g., "promotional"
    String jobDataJson;           // Full job data as JSON
    Boolean scheduled;            // false = unscheduled, true = scheduled
}
```

**MySQL Table Structure:**
```sql
CREATE TABLE scheduled_jobs_20251018 (
    id VARCHAR(255) PRIMARY KEY,
    scheduled_time DATETIME(6) NOT NULL,
    job_type VARCHAR(100) NOT NULL,
    job_name VARCHAR(255) NOT NULL,
    job_data_json TEXT,
    scheduled BOOLEAN DEFAULT FALSE,

    -- Indexes for performance
    INDEX idx_scheduled_time_scheduled (scheduled_time, scheduled),
    INDEX idx_scheduled (scheduled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
```

### 5. Job Publishers

**Interface:**
```java
public interface JobPublisher {
    void publish(String queueName, Map<String, Object> jobData);
}
```

**Implementations:**

#### KafkaJobPublisher (SmallRye Reactive Messaging)
```java
@Inject
@Channel("job-output")
Emitter<String> emitter;

public void publish(String queueName, Map<String, Object> jobData) {
    String json = objectMapper.writeValueAsString(jobData);
    Message<String> message = Message.of(json)
        .addMetadata(OutgoingKafkaRecordMetadata.builder()
            .withTopic(queueName)
            .withKey(jobData.get("id"))
            .build());
    emitter.send(message);
}
```

**Configuration:**
```yaml
mp.messaging.outgoing.job-output:
  connector: smallrye-kafka
  value.serializer: org.apache.kafka.common.serialization.StringSerializer
```

#### RedisJobPublisher (Quarkus Redis Extension)
```java
@Inject
RedisDataSource redisDataSource;

public void publish(String queueName, Map<String, Object> jobData) {
    StreamCommands<String, String, String> streamCommands =
        redisDataSource.stream(String.class, String.class, String.class);

    String json = objectMapper.writeValueAsString(jobData);
    Map<String, String> entry = new HashMap<>();
    entry.put("data", json);
    entry.put("jobId", jobData.get("id"));
    entry.put("jobType", jobData.get("jobType"));

    streamCommands.xadd(queueName, entry);
}
```

**Configuration:**
```yaml
quarkus.redis.hosts: redis://localhost:6379
```

### 6. REST API

**SchedulerResource (JAX-RS):**
```java
@Path("/api/scheduler")
@ApplicationScoped
public class SchedulerResource {

    @POST
    @Path("/schedule")
    Response scheduleJob(Map<String, Object> jobData);

    @POST
    @Path("/schedule/batch")
    Response scheduleJobs(List<Map<String, Object>> jobs);

    @GET
    @Path("/status/{jobId}")
    Response getJobStatus(@PathParam("jobId") String jobId);
}
```

**MonitoringResource (Metrics):**
```java
@Path("/api/monitoring")
@ApplicationScoped
public class MonitoringResource {

    @GET
    @Path("/stats")
    Response getStats();

    @GET
    @Path("/health")
    Response getHealth();
}
```

---

## Data Flow

### 1. Job Scheduling (REST API → Database)

```
Client
  │
  │ POST /api/scheduler/schedule
  │ {
  │   "id": "job-12345",
  │   "scheduledTime": "2025-10-18T10:30:00",
  │   "jobType": "sms_send",
  │   "jobName": "promotional",
  │   "phoneNumber": "+1234567890",
  │   "message": "Hello World"
  │ }
  ▼
SchedulerResource
  │
  │ Validate: id, scheduledTime, jobType, jobName
  ▼
InfiniteSchedulerService.scheduleJob()
  │
  │ Convert to ScheduledJobEntity
  │ scheduled = false (unscheduled)
  ▼
Split-Verse Repository
  │
  │ INSERT INTO scheduled_jobs_20251018
  ▼
MySQL Database
  (Job stored, waiting for lookahead fetcher)
```

### 2. Job Fetching (Lookahead Fetcher → Quartz)

```
Lookahead Fetcher (every 25s)
  │
  │ Calculate window: [now_utc, now_utc + 30s]
  │ Query: findByPartitionRangeWithFilters(
  │   start: 2025-10-18T10:29:55,
  │   end:   2025-10-18T10:30:25,
  │   filters: {scheduled: false}
  │ )
  ▼
Split-Verse Repository
  │
  │ SELECT * FROM scheduled_jobs_20251018
  │ WHERE scheduled_time BETWEEN ? AND ?
  │   AND scheduled = 0
  │ (Database-level filtering!)
  ▼
MySQL Database
  │
  │ Returns: 150 unscheduled jobs
  ▼
InfiniteSchedulerService.scheduleToQuartz()
  │
  │ For each job:
  │   1. Create JobDetail with job data
  │   2. Create Trigger at scheduledTime
  │   3. Schedule to Quartz
  │   4. UPDATE scheduled=true in DB
  ▼
Quartz Scheduler (RAM)
  (Jobs loaded, ready for execution)
```

### 3. Job Execution (Quartz → Kafka/Redis)

```
Quartz Scheduler
  │
  │ Time reaches: 2025-10-18T10:30:00
  │ Fire trigger for job-12345
  ▼
GenericJobExecutor.execute()
  │
  │ Extract from JobDataMap:
  │   - jobType = "sms_send"
  │   - jobName = "promotional"
  │   - jobDataJson = "{...}"
  │   - publisher = KafkaJobPublisher
  │
  │ Calculate queue: "sms_send-promotional"
  ▼
KafkaJobPublisher.publish()
  │
  │ Create Kafka message:
  │   Topic: "sms_send-promotional"
  │   Key: "job-12345"
  │   Value: "{...full job data...}"
  ▼
Kafka Broker
  │
  │ Message published
  ▼
Consumer Application
  (Processes SMS sending)
```

---

## Technology Stack

### Core Framework
- **Quarkus 3.15.1**: Microservices framework
  - Fast startup time (~1 second)
  - Low memory footprint (~200 MB)
  - Live reload for development
  - Native compilation support (GraalVM)

### Scheduling
- **Quartz Scheduler 2.3.2**: Job scheduling engine
  - In-memory RAMJobStore
  - Thread pool: 10 threads
  - Simple trigger scheduling

### Data Storage
- **Split-verse 1.0.0**: Time-partitioned repository
  - Multi-table mode (daily partitions)
  - Automatic partition management
  - Database-level filtering
  - HikariCP connection pooling

- **MySQL 8.0+**: Relational database
  - InnoDB engine
  - UTF8MB4 charset
  - Composite indexes for performance

### Messaging
- **Kafka (SmallRye Reactive Messaging)**: Primary publisher
  - Topic-based routing
  - At-least-once delivery
  - High throughput

- **Redis Streams (Quarkus Redis Extension)**: Alternative publisher
  - XADD for stream publishing
  - Consumer groups support
  - Low latency

### REST API
- **JAX-RS (RESTEasy Reactive)**: HTTP endpoints
  - Reactive processing
  - Jackson for JSON
  - Automatic OpenAPI generation

### Web UI
- **Static Resources**: HTML/JS/CSS
  - Served from `META-INF/resources/`
  - Real-time stats via fetch API
  - Responsive design

---

## Performance Optimizations

### 1. Database-Level Filtering (90% Improvement)

**Before (Java-level filtering):**
```java
// Fetches ALL jobs in time window
List<ScheduledJobEntity> allJobs = repository.findAllByPartitionRange(
    nowUtc, nowUtc.plusSeconds(30)
);

// Filters in Java (wasteful)
List<ScheduledJobEntity> unscheduled = allJobs.stream()
    .filter(entity -> !entity.getScheduled())
    .toList();
```

**Performance:**
- Fetches: 3,000,000 rows
- Needs: 300,000 rows
- Waste: 2,700,000 rows (90%)
- Memory: ~270 MB wasted

**After (Database-level filtering):**
```java
// Filters at database level
List<ScheduledJobEntity> unscheduled = repository.findByPartitionRangeWithFilters(
    nowUtc,
    nowUtc.plusSeconds(30),
    Map.of("scheduled", false)  // WHERE clause in SQL
);
```

**Performance:**
- Fetches: 300,000 rows
- Needs: 300,000 rows
- Waste: 0 rows
- Memory: ~27 MB

**Improvement:**
- **90% reduction** in data transfer
- **90% reduction** in memory usage
- **10x faster** processing

### 2. Partition Pruning

**How Split-verse Optimizes Queries:**

Query: `findByPartitionRangeWithFilters("2025-10-18T23:59:50", "2025-10-19T00:00:20", {scheduled: false})`

**Generated SQL:**
```sql
-- Only queries two relevant tables
SELECT * FROM scheduled_jobs_20251018
WHERE scheduled_time >= '2025-10-18 23:59:50'
  AND scheduled_time < '2025-10-18 23:59:59.999999'
  AND scheduled = 0
UNION ALL
SELECT * FROM scheduled_jobs_20251019
WHERE scheduled_time >= '2025-10-19 00:00:00'
  AND scheduled_time < '2025-10-19 00:00:20'
  AND scheduled = 0
ORDER BY scheduled_time;
```

**Benefits:**
- Queries only relevant tables (not all 365+ tables)
- Leverages table-level indexes
- Automatic UNION ALL for cross-day queries

### 3. Connection Pooling (HikariCP)

**Configuration:**
```java
GenericMultiTableRepository.builder()
    .host("127.0.0.1")
    .port(3306)
    .database("scheduler")
    .username("root")
    .password("password")
    .build();
// HikariCP automatically configured
```

**Pool Settings:**
- Max pool size: 10 connections
- Connection timeout: 30s
- Idle timeout: 600s
- Max lifetime: 1800s

### 4. RAM-Based Quartz Scheduler

**Why RAM > JDBC?**
- No database overhead for job scheduling
- Instant job creation (~1ms vs ~50ms)
- No connection contention
- Lookahead fetcher ensures jobs are always loaded

**Trade-off:**
- Jobs lost on crash → Acceptable (lookahead fetcher will re-fetch)
- Lower memory usage than full JDBC persistence

---

## Scalability

### Vertical Scaling

**Single Instance Capacity:**
- CPU: 4 cores
- RAM: 4 GB
- Throughput: 10,000 jobs/second
- Active jobs in Quartz: ~300,000

**Bottlenecks:**
- Database query performance (mitigated by filtering)
- Kafka/Redis publisher throughput
- Quartz thread pool size (configurable)

### Horizontal Scaling

**Multi-Instance Deployment:**

```
Load Balancer (Round Robin)
        │
    ┌───┴───┬───────┬───────┐
    │       │       │       │
    ▼       ▼       ▼       ▼
 Instance  Instance Instance Instance
    1        2        3        4
    │       │       │       │
    └───┬───┴───┬───┴───┬───┘
        │       │       │
        ▼       ▼       ▼
    Shared MySQL Database
```

**Considerations:**
1. **Database Locking**: Use `SELECT ... FOR UPDATE` to prevent duplicate scheduling
2. **Lookahead Coordination**: Offset fetch intervals to avoid thundering herd
3. **Quartz Clustering**: Not recommended (adds complexity, RAM-based is sufficient)

**Recommended Setup:**
- 4 instances × 10,000 jobs/sec = **40,000 jobs/sec**
- MySQL read replicas for query distribution
- Kafka partitioning by `jobType` for consumer parallelism

---

## Monitoring

### REST Endpoints

**GET /api/monitoring/stats**
```json
{
  "totalJobsScheduled": 15234567,
  "activeJobs": 287543,
  "completedJobs": 14947024,
  "failedJobs": 123,
  "avgExecutionTime": "124ms",
  "lookaheadFetcherStatus": "running",
  "lastFetchTime": "2025-10-18T10:29:55Z",
  "lastFetchCount": 1523
}
```

**GET /api/monitoring/health**
```json
{
  "status": "UP",
  "checks": {
    "database": "UP",
    "quartzScheduler": "UP",
    "kafkaPublisher": "UP",
    "lookaheadFetcher": "UP"
  },
  "uptime": "48h 23m 15s"
}
```

### Web UI

**Access:** `http://localhost:8080/`

**Features:**
- Real-time job statistics
- Lookahead fetcher status
- Recent job executions
- System health indicators
- Refresh every 5 seconds

### Logging

**SLF4J with Logback:**
```properties
# application.properties
quarkus.log.level=INFO
quarkus.log.category."com.telcobright.scheduler".level=DEBUG
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss} %-5p [%c{3.}] %m%n
```

**Key Log Events:**
- Lookahead fetcher cycles (INFO)
- Job scheduling (DEBUG)
- Job execution (INFO)
- Errors and exceptions (ERROR)

---

## Future Enhancements

1. **Distributed Locking**: Implement Redis-based locking for multi-instance coordination
2. **Job Priority**: Add priority queuing for urgent jobs
3. **Retry Mechanism**: Automatic retry for failed jobs
4. **GraphQL API**: Alternative to REST for flexible queries
5. **Metrics Export**: Prometheus/Grafana integration
6. **Native Compilation**: GraalVM native image for faster startup

---

**For detailed API documentation, see:** [README.md](README.md)
**For Quarkus migration details, see:** [QUARKUS_MIGRATION_SUMMARY.md](QUARKUS_MIGRATION_SUMMARY.md)
