# Infinite Scheduler

A high-performance, scalable job scheduling system built on Quartz Scheduler with MySQL persistence, designed to handle millions to billions of scheduled jobs efficiently.

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Quick Start](#quick-start)
  - [1. Dependencies](#1-dependencies)
  - [2. Database Setup](#2-database-setup)
  - [3. Create Your Entity](#3-create-your-entity)
  - [4. Set Up the Scheduler](#4-set-up-the-scheduler)
  - [Constructor Parameters](#constructor-parameters)
- [Configuration Options](#configuration-options)
  - [SchedulerConfig Builder](#schedulerconfig-builder)
  - [Important Constraints](#important-constraints)
- [Production Configuration](#production-configuration)
- [Architecture](#architecture)
  - [Core Components](#core-components)
  - [Data Flow](#data-flow)
  - [Scheduled Flag Workflow](#scheduled-flag-workflow)
  - [Misfire Handling](#misfire-handling)
  - [Timeline Diagram](#timeline-diagram---how-fetch-interval--lookahead-work)
  - [Detailed How It Works](#detailed-how-it-works)
  - [Duplicate Detection & Cleanup](#duplicate-detection--cleanup)
- [How to Run](#how-to-run)
  - [Option 1: Command Line (Maven)](#option-1-command-line-maven)
  - [Option 2: IntelliJ IDEA](#option-2-intellij-idea)
  - [Option 3: Background Process](#option-3-background-process)
  - [Access the Web UI](#access-the-web-ui)
- [Multi-App Architecture](#multi-app-architecture)
  - [AppConfig Example](#appconfig-example)
- [Queue-Based Execution Model](#queue-based-execution-model)
  - [Queue Types](#queue-types)
  - [Queue Configuration](#queue-configuration)
  - [Message Payload](#message-payload)
  - [Universal Job Execution](#universal-job-execution)
- [REST API Usage](#rest-api-usage)
  - [Get Scheduled Jobs](#get-scheduled-jobs)
  - [Get Job History](#get-job-history)
  - [Get Job Statistics](#get-job-statistics)
  - [API Endpoints Summary](#api-endpoints-summary)
  - [Using with Scripts](#using-with-scripts)
- [Testing with Console Output](#testing-with-console-output)
- [Implementing Real Queue Producers](#implementing-real-queue-producers)
- [Examples](#examples)
- [Performance & Monitoring](#performance--monitoring)
- [Troubleshooting](#troubleshooting)

## Overview

**Infinite Scheduler** integrates with the **partitioned-repo** library's ShardingRepository interface for optimal data management and automatic cleanup. It uses Java 21 virtual threads for efficient resource utilization and provides a simple, type-safe API for scheduling entity-based jobs.

## Key Features

- **High Performance**: Handle millions to billions of jobs with efficient virtual thread-based fetching
- **MySQL Persistence**: All job data stored in MySQL with automatic recovery
- **Auto Table Creation**: Automatically creates Quartz tables on first startup if they don't exist
- **Unique Job IDs**: Each job has a unique identifier preventing duplicates across fetch cycles
- **Smart Duplicate Detection**: Only new jobs are scheduled, existing jobs are skipped automatically
- **Automatic Cleanup**: Completed jobs are automatically removed from Quartz database
- **Misfire Handling**: Default policy `MISFIRE_INSTRUCTION_FIRE_ONCE_NOW` prevents job accumulation
- **Clustering Support**: Multi-node scheduler clustering for high availability
- **Generic Design**: Type-safe support for any entity implementing `SchedulableEntity<TKey>`
- **Virtual Threads**: Java 21 virtual threads for optimal resource utilization

## Quick Start

### 1. Dependencies

Add to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>telcobright-partitioned-repo</id>
        <url>https://pialmmh.github.io/partitioned-repo</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.telcobright</groupId>
        <artifactId>infinite-scheduler</artifactId>
        <version>1.0.0</version>
    </dependency>
    <!-- Partitioned-repo dependency -->
    <dependency>
        <groupId>com.telcobright.db</groupId>
        <artifactId>partitioned-repo</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

### 2. Database Setup

**Option A: Automatic Setup (Default)**

The scheduler automatically creates all required Quartz tables on first startup. Simply ensure your database exists and the connection string has proper permissions.

**Option B: Manual Setup**

If you prefer to create tables manually or need custom table prefixes:

```bash
mysql -u your_user -p your_database < src/main/resources/sql/quartz-mysql-schema.sql
```

Then disable auto-creation in config:
```java
.autoCreateTables(false)  // Disable automatic table creation
```

### 3. Create Your Entity

```java
@Table(name = "sms_schedules")
public class SmsEntity implements SchedulableEntity<Long> {
    @Id
    @Column(name = "id")
    private Long id;
    
    @ShardingKey  // Partitioned-repo uses this for partitioning
    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "message")
    private String message;
    
    @Column(name = "scheduled")  // Important: tracks if job is picked up
    private Boolean scheduled;
    
    @Override
    public Long getId() { return id; }
    
    @Override
    public LocalDateTime getScheduledTime() { return scheduledTime; }
    
    @Override
    public Boolean getScheduled() { return scheduled; }
    
    @Override
    public void setScheduled(Boolean scheduled) { this.scheduled = scheduled; }
    
    // Optional: Override for custom unique job ID
    // Default: "job-" + getId() + "-" + getScheduledTime()
    @Override 
    public String getJobId() {
        return "sms-" + getId() + "-" + getScheduledTime().toEpochSecond(ZoneOffset.UTC);
    }
    
    // ... other fields and methods
}
```

### 4. Set Up the Scheduler

```java
// Configure scheduler with MySQL credentials and repository settings (single configuration)
SchedulerConfig config = SchedulerConfig.builder()
    .fetchInterval(25)              // Fetch every 25 seconds
    .lookaheadWindow(30)            // Look 30 seconds ahead
    .mysqlHost("127.0.0.1")         // MySQL host
    .mysqlPort(3306)               // MySQL port (default: 3306)
    .mysqlDatabase("scheduler")     // Database name (shared by Quartz tables and partitioned-repo)
    .mysqlUsername("root")          // MySQL username
    .mysqlPassword("123456")        // MySQL password
    .repositoryDatabase("scheduler") // Repository database (same as main database)
    .repositoryTablePrefix("sms")    // Repository table prefix
    .maxJobsPerFetch(10000)         // Process up to 10K jobs per fetch
    .autoCreateTables(true)         // Auto-create Quartz tables
    .autoCleanupCompletedJobs(true) // Auto-cleanup completed jobs
    .build();

// Define job executor implementing Quartz Job interface
public class SmsJob implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String entityId = context.getJobDetail().getJobDataMap().getString("entityId");
        System.out.println("Executing SMS job for entity: " + entityId);
        // Your SMS sending logic here
    }
}

// Create scheduler - partitioned-repo is created and managed internally
InfiniteScheduler<SmsEntity, Long> scheduler = 
    new InfiniteScheduler<>(SmsEntity.class, Long.class, config, SmsJob.class);

scheduler.start();

// Stop when done
scheduler.stop();
```

### Constructor Parameters

The `InfiniteScheduler` constructor accepts:
- `entityClass`: The entity class (e.g., `SmsEntity.class`)
- `keyClass`: The key class (e.g., `Long.class`)  
- `config`: SchedulerConfig with complete MySQL and repository settings
- `jobClass`: Quartz Job implementation class (e.g., `SmsJob.class`)

**Key Benefits**:
- **Single Configuration**: All settings specified once in `SchedulerConfig`
- **Hidden Complexity**: Partitioned-repo is created and managed internally
- **Unified Database**: Quartz tables and partitioned-repo tables share the same MySQL database
- **Automatic Management**: Repository lifecycle managed by the scheduler

## Configuration Options

### SchedulerConfig Builder

```java
SchedulerConfig config = SchedulerConfig.builder()
    .fetchInterval(25)                    // Fetch interval in seconds (default: 25)
    .lookaheadWindow(30)                  // Lookahead window in seconds (default: 30)
    .mysqlHost("127.0.0.1")               // MySQL host (required)
    .mysqlPort(3306)                      // MySQL port (default: 3306)
    .mysqlDatabase("scheduler")           // MySQL database name (required)
    .mysqlUsername("root")                // MySQL username (required)
    .mysqlPassword("123456")              // MySQL password (required)
    .repositoryDatabase("scheduler")      // Repository database name (required)
    .repositoryTablePrefix("sms")         // Repository table prefix (required)
    .maxJobsPerFetch(10000)               // Max jobs per fetch cycle (default: 10000)
    .clusteringEnabled(true)              // Enable clustering (default: false)
    .threadPoolSize(20)                   // Quartz thread pool size (default: 10)
    .batchSize(1000)                      // Batch processing size (default: 1000)
    .autoCreateTables(true)               // Auto-create Quartz tables (default: true)
    .autoCleanupCompletedJobs(true)       // Auto-cleanup completed jobs (default: true)
    .cleanupIntervalMinutes(60)           // Cleanup every 60 minutes (default: 60)
    .misfireInstruction(MISFIRE_INSTRUCTION_FIRE_ONCE_NOW) // Misfire policy
    .build();
```

### Important Constraints

- **Lookahead Window**: Must be greater than fetch interval (`lookaheadWindow > fetchInterval`)
- **Fetch Interval**: Must be positive
- **Thread Pool**: Must be positive
- **MySQL Credentials**: Host, database, username, and password are required
- **Repository Settings**: Repository database and table prefix are required

## Production Configuration

```java
SchedulerConfig productionConfig = SchedulerConfig.builder()
    .fetchInterval(15)                    // More frequent fetching
    .lookaheadWindow(45)                  // Larger lookahead window
    .mysqlHost("127.0.0.1")               // MySQL host
    .mysqlPort(3306)                      // MySQL port
    .mysqlDatabase("scheduler")           // Database name
    .mysqlUsername("root")                // MySQL username
    .mysqlPassword("123456")              // MySQL password
    .repositoryDatabase("scheduler")      // Repository database (same as main)
    .repositoryTablePrefix("notifications") // Repository table prefix
    .maxJobsPerFetch(50000)               // Higher throughput
    .clusteringEnabled(true)              // Enable clustering
    .threadPoolSize(20)                   // More threads
    .batchSize(1000)                      // Batch processing
    .build();
```

## Architecture

### Core Components

1. **InfiniteScheduler**: Main scheduler class managing the lifecycle
2. **SchedulerConfig**: Configuration builder for scheduler settings
3. **EntityJobExecutor**: Quartz job executor that handles entity processing
4. **SchedulableEntity**: Interface for entities that can be scheduled
5. **QuartzTableManager**: Automatic table creation and verification on startup

### Data Flow

1. **Virtual Thread Fetcher**: Runs every `n` seconds, queries repository for upcoming jobs
2. **Job Scheduling**: Creates Quartz jobs for each entity, persists to MySQL
3. **Job Execution**: Quartz fires jobs at scheduled times
4. **Auto-Cleanup**: Repository retention policies and Quartz cleanup maintain system health

### Scheduled Flag Workflow

The `scheduled` boolean flag in your entity tracks whether a job has been picked up:

1. **Initial State**: Entity inserted with `scheduled=0` (false) - not yet picked up
2. **Fetching**: Scheduler queries for entities where `scheduled=0` or `null`
3. **Scheduling**: When picked up, immediately sets `scheduled=1` in database
4. **Quartz Takes Over**: Job is now Quartz's responsibility, `scheduled=1` remains forever
5. **No Further Updates**: Once `scheduled=1`, the partitioned-repo is never updated again

**Key Points:**
- `scheduled=0`: Job available for pickup
- `scheduled=1`: Job picked up by scheduler, now managed by Quartz
- Jobs with `scheduled=1` are never fetched again
- This prevents duplicate scheduling across fetch cycles

### Misfire Handling

The scheduler uses `MISFIRE_INSTRUCTION_FIRE_ONCE_NOW` by default:
- Jobs missed during downtime execute once when system recovers
- Prevents massive job backlog accumulation
- Protects system resources during recovery

### Timeline Diagram - How Fetch Interval & Lookahead Work

```
🕒 INFINITE SCHEDULER TIMELINE - Fetch Interval: 25s, Lookahead: 30s

Time:     0s    5s    10s   15s   20s   25s   30s   35s   40s   45s   50s   55s   60s
          |     |     |     |     |     |     |     |     |     |     |     |     |
          
🔍 Fetch #1 |────────────────────────────────────|
          Jobs found for: [0s → 30s]             |
                                              |
                                              |
                               🔍 Fetch #2 |────────────────────────────────────|
                                              Jobs found for: [25s → 55s]
                                                             |
                                                             |
                                              🔍 Fetch #3 |────────────────────────────────────|
                                                             Jobs found for: [50s → 80s]

📅 Job Timeline:
Job A (scheduled at 10s) ──────────────────●
Job B (scheduled at 20s) ────────────────────────────●
Job C (scheduled at 35s) ─────────────────────────────────────────●
Job D (scheduled at 45s) ───────────────────────────────────────────────────●

🎯 Scheduling Events:
- Fetch #1 (t=0s):  Schedules Job A, Job B (ready for execution at t=10s, t=20s)
- Fetch #2 (t=25s): Schedules Job C (ready for execution at t=35s)
- Fetch #3 (t=50s): No new jobs in [50s→55s], but covers [55s→80s] window

⚡ Execution Events:
t=10s: Job A executes ✓
t=20s: Job B executes ✓  
t=35s: Job C executes ✓
t=45s: Job D executes ✓

📝 Key Insights:
• Lookahead (30s) > Fetch Interval (25s) = 5s overlap ensures no jobs are missed
• Virtual thread fetcher runs every 25s with minimal resource usage  
• Jobs are pre-scheduled in Quartz, ensuring precise execution timing
• Overlap window (5s) provides safety buffer for system delays
```

### Detailed How It Works

**🔍 Fetch Cycle Process:**
1. **Virtual Thread Wakes Up:** Every 25 seconds (configurable)
2. **Query Repository:** `findAllByDateRange(now, now + 30s)`
3. **Create Quartz Jobs:** For each entity found
4. **Persist to MySQL:** Jobs stored immediately
5. **Sleep:** Thread sleeps until next cycle

**⏰ Timing Guarantees:**
- **No Missed Jobs:** Lookahead > Fetch Interval
- **No Duplicates:** Quartz checks existing jobs
- **Precise Execution:** Quartz handles exact timing
- **System Recovery:** Jobs persist through restarts

**🔧 Configuration Impact:**
- **Fetch Interval ↓:** More frequent checks, higher CPU
- **Fetch Interval ↑:** Less frequent checks, larger batches
- **Lookahead ↑:** Better job discovery, more memory
- **Lookahead ↓:** Risk of missing jobs if too close

**⚠️ Critical Rule:** `lookaheadWindow > fetchInterval` (enforced by validation)

### Duplicate Detection & Cleanup

**🔍 Unique Job IDs:**
- Each job gets a unique ID from `entity.getJobId()`
- Default format: `"job-" + entity.getId() + "-" + scheduledTime`
- Override `getJobId()` in your entity for custom IDs

**🚫 Duplicate Prevention:**
- Quartz checks if job ID already exists before scheduling
- Duplicate jobs are automatically skipped with debug log
- Prevents job multiplication during overlapping fetch cycles

**🧹 Automatic Cleanup Process:**
1. **Completed Jobs:** Removes job details with no associated triggers
2. **Orphaned Triggers:** Removes triggers with no associated job details  
3. **Old Fired Records:** Removes fired trigger history older than 24 hours
4. **Virtual Thread:** Cleanup runs in dedicated virtual thread every 60 minutes (configurable)

**💡 Cleanup Benefits:**
- Prevents database bloat from completed jobs
- Maintains optimal query performance
- Automatic maintenance with zero manual intervention
- Configurable interval and enable/disable option

## Performance Characteristics

- **Job Creation Rate**: 10,000+ jobs/second
- **Job Execution Rate**: 5,000+ jobs/second per node
- **Memory Usage**: < 2GB for millions of active jobs
- **Scalability**: Linear scaling with additional nodes

## Monitoring

The scheduler provides structured logging with key metrics:
- Jobs scheduled per fetch cycle
- Fetch cycle duration
- Job execution success/failure rates
- Repository query performance

## Examples

See the `examples` package for complete working examples:
- SMS scheduling with `SmsEntity`
- Notification scheduling with `NotificationEntity`
- Production configuration examples

## Building

```bash
mvn clean compile
mvn test
mvn package
```

## Requirements

- **Java**: 21+ (for Virtual Threads)
- **MySQL**: 8.0+ or MariaDB 10.6+
- **Memory**: Minimum 4GB heap for production
- **partitioned-repo**: Available via Maven repository (automatically downloaded)

## How to Run

### Option 1: Command Line (Maven)

```bash
# Compile the project
mvn clean compile

# Run the scheduler with UI
mvn exec:java -Dexec.mainClass="com.telcobright.scheduler.examples.MultiAppSchedulerWithUI"
```

### Option 2: IntelliJ IDEA

1. **Open Project**: File → Open → Navigate to project directory
2. **Navigate to**: `src/main/java/com/telcobright/scheduler/examples/MultiAppSchedulerWithUI.java`
3. **Run**: Right-click → Run 'MultiAppSchedulerWithUI.main()'
   - Or click the green ▶️ icon next to the main method

### Option 3: Background Process

```bash
# Run in background
nohup mvn exec:java -Dexec.mainClass="com.telcobright.scheduler.examples.MultiAppSchedulerWithUI" -Dexec.cleanupDaemonThreads=false > scheduler.log 2>&1 &

# Check logs
tail -f scheduler.log

# Stop the process
pkill -f "MultiAppSchedulerWithUI"
```

### Access the Web UI

Once running, open your browser:
```
http://localhost:7070/index.html
```

The UI shows:
- **Scheduled Jobs**: Currently scheduled jobs waiting to execute
- **Job History**: Completed and failed jobs with execution details
- **Job Stats**: Real-time statistics and counts
- **Queue Configuration**: Queue type, topic name, and broker address for each job

## Multi-App Architecture

The scheduler supports multiple independent applications in a single instance:

```java
// Create the multi-app manager
MultiAppSchedulerManager manager = new MultiAppSchedulerManager(dataSource);

// Register multiple applications
manager.registerApp(smsConfig, new SmsJobHandler());
manager.registerApp(sipCallConfig, new SipCallJobHandler());
manager.registerApp(paymentConfig, new PaymentGatewayJobHandler());

// Start all schedulers
manager.startAll();

// Each app has its own:
// - Repository (with table prefix)
// - Fetch thread
// - Queue configuration
// - Job history table
```

### AppConfig Example

```java
AppConfig smsConfig = AppConfig.builder("sms")
    .tablePrefix("sms_scheduled_jobs")
    .historyTableName("sms_job_execution_history")
    .fetchIntervalSeconds(5)
    .lookaheadWindowSeconds(30)
    .queueConfig(queueConfig)  // Queue configuration
    .build();
```

## Queue-Based Execution Model

Jobs **do not execute business logic** directly. Instead, they **produce messages to queue topics**.

### Queue Types

- **CONSOLE**: Mock/testing output (prints to console)
- **KAFKA**: Apache Kafka topics (requires implementation)
- **REDIS**: Redis queues (requires implementation)

### Queue Configuration

```java
// Console output (for testing)
QueueConfig queueConfig = QueueConfig.builder()
    .queueType(QueueConfig.QueueType.CONSOLE)
    .topicName("sms-notifications")
    .brokerAddress("")  // Empty for console
    .build();

// Kafka (requires KafkaQueueProducer implementation)
QueueConfig kafkaConfig = QueueConfig.builder()
    .queueType(QueueConfig.QueueType.KAFKA)
    .topicName("sms-notifications")
    .brokerAddress("localhost:9092")
    .build();

// Redis (requires RedisQueueProducer implementation)
QueueConfig redisConfig = QueueConfig.builder()
    .queueType(QueueConfig.QueueType.REDIS)
    .topicName("sipcall-queue")
    .brokerAddress("localhost:6379")
    .build();
```

### Message Payload

When a job executes, it produces a message to the configured topic:

```json
{
  "jobId": "sms-job-123",
  "appName": "sms",
  "entityId": "uuid-here",
  "executionTime": "2025-10-28T04:05:03.925087875",
  "queueType": "CONSOLE",
  "topicName": "sms-notifications",
  "jobParams": {
    "phoneNumber": "+8801710000001",
    "message": "Your message here",
    "priority": "NORMAL",
    "scheduledTime": "2025-10-28T04:05:03.901939568"
  }
}
```

### Universal Job Execution

All jobs use the same execution logic (`GenericJob.java`):

1. Extract queue configuration from job data
2. Get queue producer from factory
3. Build message payload with job parameters
4. Send message to topic
5. Return success/failure

**No app-specific code** - just message production!

## REST API Usage

### Get Scheduled Jobs

```bash
# Get all scheduled jobs (up to 100)
curl http://localhost:7070/api/jobs/scheduled | jq

# Response
[
  {
    "id": 123,
    "jobId": "sms-job-5",
    "jobName": "sms-job-5",
    "jobGroup": "sms",
    "appName": "sms",
    "entityId": "uuid-here",
    "scheduledTime": "2025-10-28 04:10:15.0",
    "status": "SCHEDULED",
    "queueType": "CONSOLE",
    "topicName": "sms-notifications",
    "brokerAddress": "",
    "createdAt": "2025-10-28 04:05:03.0"
  }
]
```

### Get Job History

```bash
# Get recent completed/failed jobs
curl http://localhost:7070/api/jobs/history?limit=10 | jq

# Response
[
  {
    "id": 456,
    "jobId": "payment-job-3",
    "jobName": "payment-job-3",
    "jobGroup": "payment_gateway",
    "appName": "payment_gateway",
    "entityId": "uuid-here",
    "scheduledTime": "2025-10-28 04:05:17.0",
    "startedAt": "2025-10-28 04:05:17.0",
    "completedAt": "2025-10-28 04:05:18.0",
    "executionDurationMs": 150,
    "status": "COMPLETED",
    "errorMessage": null,
    "queueType": "CONSOLE",
    "topicName": "payment-transactions",
    "brokerAddress": ""
  }
]
```

### Get Job Statistics

```bash
# Get overall job statistics
curl http://localhost:7070/api/jobs/stats | jq

# Response
{
  "totalJobs": 1547,
  "statusCounts": {
    "SCHEDULED": 12,
    "COMPLETED": 1520,
    "FAILED": 15
  }
}
```

### API Endpoints Summary

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/jobs/scheduled` | GET | Get all scheduled and started jobs |
| `/api/jobs/history?limit=N` | GET | Get completed/failed jobs (default limit: 100) |
| `/api/jobs/stats` | GET | Get job statistics by status |

### Using with Scripts

```bash
# Monitor scheduled jobs count
watch -n 2 'curl -s http://localhost:7070/api/jobs/scheduled | jq "length"'

# Get failed jobs only
curl -s http://localhost:7070/api/jobs/history | jq '.[] | select(.status=="FAILED")'

# Export job history to file
curl -s http://localhost:7070/api/jobs/history?limit=1000 > job-history.json

# Check if specific job completed
curl -s http://localhost:7070/api/jobs/history | jq '.[] | select(.jobId=="sms-job-123")'
```

## Testing with Console Output

The system includes a mock queue producer for testing without Kafka/Redis:

```java
// Jobs will print to console
QueueConfig consoleConfig = QueueConfig.builder()
    .queueType(QueueConfig.QueueType.CONSOLE)
    .topicName("test-topic")
    .brokerAddress("")
    .build();
```

**Console Output:**
```
================================================================================
📤 MOCK QUEUE MESSAGE PRODUCED @ 04:05:03.925
================================================================================
Queue Type: CONSOLE
Topic Name: sms-notifications
Broker:
--------------------------------------------------------------------------------
Message Payload:
{
  "executionTime": "2025-10-28T04:05:03.925087875",
  "jobId": "sms-job-1",
  "appName": "sms",
  "queueType": "CONSOLE",
  "topicName": "sms-notifications",
  "jobParams": { ... }
}
================================================================================
```

## Implementing Real Queue Producers

To implement Kafka or Redis producers:

1. **Create Producer Class** (e.g., `KafkaQueueProducer.java`):
```java
public class KafkaQueueProducer implements QueueProducer {
    private KafkaProducer<String, String> producer;

    @Override
    public void initialize(QueueConfig config) {
        // Initialize Kafka producer with config.getBrokerAddress()
    }

    @Override
    public boolean send(String topicName, Map<String, Object> message) {
        // Send message to Kafka topic
    }

    @Override
    public void close() {
        // Close Kafka producer
    }

    @Override
    public QueueConfig.QueueType getType() {
        return QueueConfig.QueueType.KAFKA;
    }
}
```

2. **Update QueueProducerFactory**:
```java
case KAFKA:
    producer = new KafkaQueueProducer();  // Use real implementation
    break;
```

## Troubleshooting

### Jobs Not Executing

**Check Quartz tables:**
```sql
SELECT COUNT(*) FROM QRTZ_JOB_DETAILS;
SELECT COUNT(*) FROM QRTZ_TRIGGERS;
```

**Check job history:**
```sql
SELECT * FROM sms_job_execution_history ORDER BY created_at DESC LIMIT 10;
```

### Clean Test Data

```sql
-- Clean Quartz tables
DELETE FROM QRTZ_SIMPLE_TRIGGERS;
DELETE FROM QRTZ_TRIGGERS;
DELETE FROM QRTZ_JOB_DETAILS;

-- Clean job history
DELETE FROM sms_job_execution_history;
DELETE FROM sipcall_job_execution_history;
DELETE FROM payment_gateway_job_execution_history;
```

### Port Already in Use

If port 7070 is in use:
```bash
# Find process using port 7070
lsof -i :7070

# Kill the process
kill -9 <PID>
```

## License

This project is part of the TelcoBright suite of libraries.