# Infinite Scheduler - Architecture Diagram

## System Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                          INFINITE SCHEDULER SERVICE                                  │
│                         (Multi-App Architecture)                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                         │
                                         │
                    ┌────────────────────┴────────────────────┐
                    │                                          │
         ┌──────────▼──────────┐                  ┌───────────▼──────────┐
         │  Web UI & REST API  │                  │  MultiAppScheduler   │
         │   (Port 7070)       │                  │       Manager        │
         │                     │                  │                      │
         │  - index.html       │                  │  Orchestrates all    │
         │  - JobStatusApi     │◄─────────────────┤  application         │
         │                     │   Queries        │  schedulers          │
         │  GET /jobs/scheduled│                  │                      │
         │  GET /jobs/history  │                  └──────────┬───────────┘
         │  GET /jobs/stats    │                             │
         └─────────────────────┘                             │
                                                             │
                              ┌──────────────────────────────┼──────────────────────────────┐
                              │                              │                              │
                              │                              │                              │
                 ┌────────────▼────────────┐   ┌─────────────▼────────────┐   ┌───────────▼────────────┐
                 │  InfiniteAppScheduler   │   │  InfiniteAppScheduler    │   │  InfiniteAppScheduler  │
                 │       (SMS App)         │   │    (SIPCall App)         │   │   (Payment App)        │
                 │                         │   │                          │   │                        │
                 │  Config:                │   │  Config:                 │   │  Config:               │
                 │  - fetchInterval: 5s    │   │  - fetchInterval: 5s     │   │  - fetchInterval: 5s   │
                 │  - lookahead: 30s       │   │  - lookahead: 35s        │   │  - lookahead: 40s      │
                 │  - tablePrefix: sms_*   │   │  - tablePrefix: sipcall_*│   │  - tablePrefix: pay_*  │
                 │  - queueConfig          │   │  - queueConfig           │   │  - queueConfig         │
                 └────────────┬────────────┘   └─────────────┬────────────┘   └───────────┬────────────┘
                              │                              │                            │
                              │                              │                            │
         ┌────────────────────┼──────────────────────────────┼────────────────────────────┤
         │                    │                              │                            │
         │   Virtual Thread   │          Virtual Thread      │       Virtual Thread       │
         │   Fetcher (SMS)    │          Fetcher (SIPCall)   │       Fetcher (Payment)    │
         │   Every 5s         │          Every 5s            │       Every 5s             │
         │                    │                              │                            │
         └────────┬───────────┘          └──────┬────────────┘       └─────────┬──────────┘
                  │                             │                               │
                  │ Query                       │ Query                         │ Query
                  │ scheduled=0                 │ scheduled=0                   │ scheduled=0
                  │                             │                               │
         ┌────────▼──────────┐        ┌────────▼──────────┐         ┌─────────▼─────────┐
         │  ShardingRepository│        │  ShardingRepository│         │  ShardingRepository│
         │  (SMS Tables)      │        │  (SIPCall Tables)  │         │  (Payment Tables) │
         │                    │        │                    │         │                   │
         │  MultiTableRepo    │        │  MultiTableRepo    │         │  MultiTableRepo   │
         │  sms_2025_10_21    │        │  sipcall_2025_*    │         │  payment_2025_*   │
         │  sms_2025_10_22    │        │  ...               │         │  ...              │
         │  ...               │        │                    │         │                   │
         └────────┬───────────┘        └────────┬───────────┘         └─────────┬─────────┘
                  │                             │                               │
                  └─────────────────────────────┴───────────────────────────────┘
                                               │
                                               │ Entities fetched
                                               │
                              ┌────────────────▼────────────────┐
                              │     QUARTZ SCHEDULER            │
                              │     (Shared Instance)           │
                              │                                 │
                              │  - Job Store: MySQL JDBC        │
                              │  - Thread Pool: 10 workers      │
                              │  - Misfire: FIRE_ONCE_NOW       │
                              │  - Clustering: Supported        │
                              │                                 │
                              │  Tables:                        │
                              │  - QRTZ_JOB_DETAILS             │
                              │  - QRTZ_TRIGGERS                │
                              │  - QRTZ_FIRED_TRIGGERS          │
                              │  - QRTZ_SIMPLE_TRIGGERS         │
                              └────────────┬────────────────────┘
                                           │
                                           │ At scheduled time
                                           │ triggers execution
                                           │
                              ┌────────────▼────────────────┐
                              │       GenericJob            │
                              │  (Universal Executor)       │
                              │                             │
                              │  1. Extract queue config    │
                              │  2. Get queue producer      │
                              │  3. Build message payload   │
                              │  4. Send to topic           │
                              │  5. Mark as completed       │
                              └────────────┬────────────────┘
                                           │
                                           │ Produces message
                                           │
                              ┌────────────▼────────────────┐
                              │   QueueProducerFactory      │
                              │                             │
                              │  Creates & caches producers │
                              │  based on QueueType         │
                              └────────────┬────────────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                      │
         ┌──────────▼──────────┐  ┌───────▼────────┐  ┌─────────▼──────────┐
         │  MockQueueProducer  │  │ KafkaProducer  │  │  RedisProducer     │
         │   (CONSOLE)         │  │  (Not Impl)    │  │  (Not Impl)        │
         │                     │  │                │  │                    │
         │  Prints to console  │  │  TODO:         │  │  TODO:             │
         │  with formatting    │  │  Send to Kafka │  │  Send to Redis     │
         └─────────────────────┘  └────────────────┘  └────────────────────┘
                  │
                  │ Message output
                  ▼
         ┌─────────────────────┐
         │   CONSOLE OUTPUT    │
         │                     │
         │  Queue Type: CONSOLE│
         │  Topic: sms-notify  │
         │  Payload: {...}     │
         └─────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────┐
│                          SUPPORTING COMPONENTS                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘

         ┌─────────────────────┐          ┌──────────────────────┐
         │  JobCompletionListener│         │  QuartzCleanupService│
         │                     │          │                      │
         │  Listens to:        │          │  Virtual Thread      │
         │  - Job start        │          │  Runs every 60min    │
         │  - Job completion   │          │                      │
         │  - Job failure      │          │  Cleans:             │
         │                     │          │  - Completed jobs    │
         │  Updates:           │          │  - Orphaned triggers │
         │  - Job history DB   │          │  - Old fired records │
         └──────────┬──────────┘          └──────────┬───────────┘
                    │                                │
                    │ Writes                         │ Deletes
                    │                                │
                    ▼                                ▼
         ┌─────────────────────────────────────────────────────┐
         │           MySQL Database (scheduler)                │
         │                                                     │
         │  Job History Tables:                                │
         │  - sms_job_execution_history                        │
         │  - sipcall_job_execution_history                    │
         │  - payment_gateway_job_execution_history            │
         │                                                     │
         │  Quartz Tables:                                     │
         │  - QRTZ_JOB_DETAILS                                 │
         │  - QRTZ_TRIGGERS                                    │
         │  - QRTZ_FIRED_TRIGGERS                              │
         │  - QRTZ_SIMPLE_TRIGGERS                             │
         │  - QRTZ_CRON_TRIGGERS                               │
         │  - QRTZ_BLOB_TRIGGERS                               │
         │  - QRTZ_CALENDARS                                   │
         │  - QRTZ_PAUSED_TRIGGER_GRPS                         │
         │  - QRTZ_SCHEDULER_STATE                             │
         │  - QRTZ_LOCKS                                       │
         │                                                     │
         │  Repository Tables (per app):                       │
         │  - sms_scheduled_jobs_2025_10_21                    │
         │  - sms_scheduled_jobs_2025_10_22                    │
         │  - sipcall_scheduled_jobs_2025_10_21                │
         │  - payment_gateway_scheduled_jobs_2025_10_21        │
         │  ... (daily tables with auto-cleanup)               │
         └─────────────────────────────────────────────────────┘
```

## Data Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                            JOB LIFECYCLE FLOW                                 │
└──────────────────────────────────────────────────────────────────────────────┘

1. JOB CREATION
   ═══════════
   Application Code
        │
        │ INSERT entity with scheduled=0
        ▼
   Repository Table (sms_scheduled_jobs_2025_10_28)
   ┌─────────────────────────────────────────────┐
   │ id  | scheduled_time | scheduled | data     │
   │ 123 | 2025-10-28 ... | 0         | {...}    │
   └─────────────────────────────────────────────┘


2. JOB DISCOVERY (Every fetchInterval seconds)
   ════════════
   Virtual Thread Fetcher
        │
        │ SELECT * WHERE scheduled_time
        │   BETWEEN NOW() AND NOW() + lookahead
        │   AND (scheduled = 0 OR scheduled IS NULL)
        ▼
   Fetched Entities (scheduled=0)
        │
        │ UPDATE scheduled = 1  (mark as picked up)
        ▼
   Repository Table
   ┌─────────────────────────────────────────────┐
   │ id  | scheduled_time | scheduled | data     │
   │ 123 | 2025-10-28 ... | 1         | {...}    │
   └─────────────────────────────────────────────┘


3. QUARTZ JOB SCHEDULING
   ═════════════════════
   InfiniteAppScheduler
        │
        │ Create JobDetail with:
        │ - jobId: unique identifier
        │ - appName, entityId
        │ - jobDataJson (all entity data)
        │ - queueType, topicName, brokerAddress
        │
        │ Create Trigger with:
        │ - scheduled_time as fire time
        │ - MISFIRE_INSTRUCTION_FIRE_ONCE_NOW
        ▼
   Quartz JDBC Store (MySQL)
   ┌───────────────────────────────────────┐
   │ QRTZ_JOB_DETAILS                      │
   │ - job_name: sms-job-123               │
   │ - job_data: {...}                     │
   │                                       │
   │ QRTZ_TRIGGERS                         │
   │ - trigger_name: trigger-sms-job-123   │
   │ - next_fire_time: 1698465903000       │
   └───────────────────────────────────────┘


4. JOB EXECUTION (At scheduled_time)
   ═══════════════
   Quartz Scheduler
        │
        │ Fire trigger at next_fire_time
        ▼
   GenericJob.execute()
        │
        │ 1. Extract: appName, entityId, jobData
        │ 2. Extract: queueType, topicName, brokerAddress
        │ 3. Get QueueProducer from factory
        │ 4. Build message payload
        │ 5. Send to queue topic
        ▼
   QueueProducer (CONSOLE/KAFKA/REDIS)
        │
        │ Produce message to topic
        ▼
   Queue System / Console Output


5. JOB COMPLETION TRACKING
   ════════════════════════
   JobCompletionListener
        │
        │ Listen to job events:
        │ - jobWasExecuted()
        │ - jobExecutionVetoed()
        ▼
   Job History Table
   ┌──────────────────────────────────────────────────────┐
   │ sms_job_execution_history                            │
   │ - job_id: sms-job-123                                │
   │ - status: COMPLETED                                  │
   │ - started_at: 2025-10-28 04:05:03                    │
   │ - completed_at: 2025-10-28 04:05:03                  │
   │ - execution_duration_ms: 150                         │
   │ - job_data: {"queueType":"CONSOLE",...}              │
   └──────────────────────────────────────────────────────┘


6. AUTO CLEANUP (Every 60 minutes)
   ═══════════════
   QuartzCleanupService (Virtual Thread)
        │
        │ Delete from QRTZ_JOB_DETAILS
        │   WHERE not exists in QRTZ_TRIGGERS
        │
        │ Delete from QRTZ_TRIGGERS
        │   WHERE not exists in QRTZ_JOB_DETAILS
        │
        │ Delete from QRTZ_FIRED_TRIGGERS
        │   WHERE fired_time < NOW() - 24 hours
        ▼
   Clean Quartz Database


7. UI/API ACCESS (Anytime)
   ════════════════════
   Web Browser / curl
        │
        │ GET /api/jobs/scheduled
        │ GET /api/jobs/history
        │ GET /api/jobs/stats
        ▼
   JobStatusApi
        │
        │ Query job history tables
        │ + Extract queue config from job_data JSON
        ▼
   JSON Response to Client
```

## Message Flow

```
┌────────────────────────────────────────────────────────────────────┐
│                    MESSAGE PRODUCTION FLOW                         │
└────────────────────────────────────────────────────────────────────┘

Entity in Repository (scheduled=0)
        │
        ▼
    [Fetcher detects entity]
        │
        ▼
    [Schedule in Quartz with queue config]
        │
        ▼
    [Trigger fires at scheduled_time]
        │
        ▼
    [GenericJob.execute()]
        │
        │ Extract from JobDataMap:
        │ - appName: "sms"
        │ - entityId: "uuid"
        │ - queueType: "CONSOLE"
        │ - topicName: "sms-notifications"
        │ - brokerAddress: ""
        │ - jobDataJson: "{...}"
        │
        ▼
    [Build Message Payload]
        │
        │ {
        │   "jobId": "sms-job-123",
        │   "appName": "sms",
        │   "entityId": "uuid",
        │   "executionTime": "2025-10-28T04:05:03",
        │   "queueType": "CONSOLE",
        │   "topicName": "sms-notifications",
        │   "jobParams": {
        │     "phoneNumber": "+8801710000001",
        │     "message": "Your message",
        │     "priority": "NORMAL",
        │     ...
        │   }
        │ }
        │
        ▼
    [QueueProducerFactory.getProducer(queueConfig)]
        │
        ▼
    [MockQueueProducer.send(topic, message)]
        │
        ▼
    ┌─────────────────────────────────────────┐
    │         CONSOLE OUTPUT                  │
    │                                         │
    │  ════════════════════════════════       │
    │  📤 MOCK QUEUE MESSAGE PRODUCED         │
    │  ════════════════════════════════       │
    │  Queue Type: CONSOLE                    │
    │  Topic Name: sms-notifications          │
    │  Broker:                                │
    │  ────────────────────────────────       │
    │  Message Payload:                       │
    │  { ... full JSON ... }                  │
    │  ════════════════════════════════       │
    └─────────────────────────────────────────┘
        │
        ▼
    [Job marked COMPLETED in history]
```

## Component Responsibilities

### Core Components

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| **MultiAppSchedulerManager** | Orchestrates multiple app schedulers, manages shared Quartz instance | Java 21 |
| **InfiniteAppScheduler** | Per-app scheduler, manages fetcher thread and Quartz jobs | Java 21 Virtual Threads |
| **GenericJob** | Universal job executor, produces messages to queues | Quartz Job |
| **QueueProducerFactory** | Creates and caches queue producers by type | Factory Pattern |
| **MockQueueProducer** | Console-based mock for testing without real queues | Testing |
| **ShardingRepository** | Multi-table data source with auto-cleanup | partitioned-repo library |
| **JobCompletionListener** | Tracks job lifecycle, updates history | Quartz JobListener |
| **QuartzCleanupService** | Periodic cleanup of completed jobs | Virtual Thread |
| **JobStatusApi** | REST API for job monitoring | Javalin Web Framework |

### Data Components

| Component | Purpose | Storage |
|-----------|---------|---------|
| **Repository Tables** | Store scheduled entities (scheduled=0/1) | MySQL (daily tables) |
| **Quartz Tables** | Store job definitions and triggers | MySQL (JDBC JobStore) |
| **Job History Tables** | Track execution history and status | MySQL (per-app tables) |

### External Interfaces

| Interface | Purpose | Protocol |
|-----------|---------|----------|
| **Web UI** | Visual monitoring dashboard | HTTP (port 7070) |
| **REST API** | Programmatic access to job data | JSON over HTTP |
| **Queue Topics** | Message production targets | CONSOLE/KAFKA/REDIS |

## Threading Model

```
┌─────────────────────────────────────────────────────────────┐
│                    VIRTUAL THREAD POOL                      │
└─────────────────────────────────────────────────────────────┘

Main Thread
    │
    ├─► Virtual Thread: Fetcher (SMS App)
    │   └─► Runs every 5 seconds
    │       └─► Query repository
    │       └─► Schedule jobs in Quartz
    │
    ├─► Virtual Thread: Fetcher (SIPCall App)
    │   └─► Runs every 5 seconds
    │       └─► Query repository
    │       └─► Schedule jobs in Quartz
    │
    ├─► Virtual Thread: Fetcher (Payment App)
    │   └─► Runs every 5 seconds
    │       └─► Query repository
    │       └─► Schedule jobs in Quartz
    │
    ├─► Virtual Thread: Quartz Cleanup
    │   └─► Runs every 60 minutes
    │       └─► Delete completed jobs
    │       └─► Delete orphaned triggers
    │
    └─► Quartz Thread Pool (10 workers)
        └─► Worker-1: Execute GenericJob → Produce to queue
        └─► Worker-2: Execute GenericJob → Produce to queue
        └─► Worker-3: Execute GenericJob → Produce to queue
        └─► ... (10 workers total)
```

## Key Design Patterns

1. **Factory Pattern**: QueueProducerFactory creates appropriate queue producers
2. **Strategy Pattern**: Different queue producers (CONSOLE/KAFKA/REDIS) implement same interface
3. **Observer Pattern**: JobCompletionListener observes job lifecycle events
4. **Repository Pattern**: ShardingRepository abstracts data access
5. **Builder Pattern**: Configuration classes use builder pattern for flexibility
6. **Singleton Pattern**: MultiAppSchedulerManager maintains single Quartz instance

## Scalability Features

- **Virtual Threads**: Lightweight concurrency for thousands of fetch operations
- **Multi-Table Sharding**: Partitioned-repo distributes data across daily tables
- **Quartz Clustering**: Multiple scheduler instances can share job load
- **Auto-Cleanup**: Prevents database bloat from completed jobs
- **Queue-Based**: Decouples job scheduling from execution
- **Stateless Jobs**: GenericJob is stateless, scales horizontally
