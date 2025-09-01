# Architecture Overview

Understanding how **Infinite Scheduler** works under the hood.

## Core Components

### 1. InfiniteScheduler
The main orchestrator that manages the entire lifecycle:
- Creates and manages the partitioned repository
- Runs the virtual thread fetcher
- Integrates with Quartz scheduler
- Handles job lifecycle events

### 2. SchedulerConfig  
Centralized configuration builder:
- MySQL connection settings
- Repository configuration
- Scheduler behavior settings
- Auto-table creation options

### 3. Virtual Thread Fetcher
Java 21 virtual thread that runs continuously:
- Wakes up every `fetchInterval` seconds
- Queries partitioned-repo for upcoming jobs  
- Filters and schedules new jobs to Quartz
- Minimal resource overhead

### 4. Partitioned Repository Integration
Seamless integration with partitioned-repo:
- Creates daily tables with hourly partitions
- Efficient querying by scheduled_time (sharding key)
- Automatic table cleanup based on retention policies

### 5. Quartz Integration
Native Quartz scheduler integration:
- MySQL JobStore for persistence
- Clustering support for high availability
- Misfire handling and recovery
- Thread pool management

## Data Flow Diagram

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Application   │───▶│ Partitioned-Repo │───▶│     MySQL       │
│   Inserts Jobs  │    │   (scheduled=0)   │    │ sms_20250807... │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │                         ▲
                                ▼                         │
┌─────────────────┐    ┌──────────────────┐              │
│ Virtual Thread  │───▶│     Fetcher      │──────────────┘
│   (25s cycle)   │    │ Query scheduled=0│
└─────────────────┘    └──────────────────┘
                                │
                                ▼ scheduled=1
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│  Quartz Jobs    │◄───│   Job Scheduler  │───▶│     MySQL       │
│  (in memory +   │    │  (InfiniteScheduler) │    │  QRTZ_* tables  │
│   database)     │    └──────────────────┘    └─────────────────┘
└─────────────────┘
         │
         ▼ execution time
┌─────────────────┐
│   Job Executor  │
│  (Your Logic)   │
└─────────────────┘
```

## Scheduled Flag Workflow

The `scheduled` boolean flag prevents duplicate scheduling:

### State Transitions

```
┌─────────────────┐
│  Entity Created │
│   scheduled=0   │ ◄─── Application inserts job
└─────────┬───────┘
          │
          ▼
┌─────────────────┐
│ Fetcher Queries │
│WHERE scheduled=0│ ◄─── Virtual thread queries repository  
└─────────┬───────┘
          │
          ▼
┌─────────────────┐
│   Job Picked    │
│   scheduled=1   │ ◄─── Immediately set in database
└─────────┬───────┘
          │
          ▼
┌─────────────────┐
│ Quartz Takes    │
│     Over        │ ◄─── No more partitioned-repo updates
└─────────────────┘
```

### Key Benefits

1. **No Duplicates**: `scheduled=1` jobs never fetched again
2. **Immediate Marking**: Set as soon as picked up, before Quartz scheduling
3. **Simple Logic**: Only two states: available (0) and picked up (1)
4. **Performance**: Efficient database queries with WHERE clause

## Timeline - Fetch Intervals & Lookahead

```
🕒 TIMELINE - Fetch: 25s, Lookahead: 30s

Time:     0s    5s    10s   15s   20s   25s   30s   35s   40s   45s   50s   55s
          |     |     |     |     |     |     |     |     |     |     |     |
          
🔍 Fetch #1 |────────────────────────────────────|
          Jobs for: [0s → 30s]                  |
                                            |
                               🔍 Fetch #2 |────────────────────────────────────|
                                          Jobs for: [25s → 55s]
                                                         |
                                          🔍 Fetch #3 |────────────────────────────────────|
                                                     Jobs for: [50s → 80s]

📅 Job Scheduling:
Job A (t=10s) ──────────────────●          ← Scheduled in Fetch #1
Job B (t=20s) ────────────────────────────● ← Scheduled in Fetch #1  
Job C (t=35s) ─────────────────────────────────────────● ← Scheduled in Fetch #2
Job D (t=45s) ───────────────────────────────────────────────────● ← Scheduled in Fetch #2

⚡ Execution:
t=10s: Job A executes ✓
t=20s: Job B executes ✓
t=35s: Job C executes ✓ 
t=45s: Job D executes ✓
```

**Key Insights:**
- **Overlap (5s)**: `lookaheadWindow(30) > fetchInterval(25)` ensures no missed jobs
- **Safety Buffer**: Extra time handles system delays
- **Efficient Queries**: Partitioned tables make time-range queries fast

## Partitioned Tables Structure

### Daily Tables with Hourly Partitions

```sql
-- Example: sms_20250807 (August 7, 2025)
CREATE TABLE scheduler.sms_20250807 (
    id BIGINT AUTO_INCREMENT,
    scheduled_time DATETIME NOT NULL,
    phone_number VARCHAR(20),
    message TEXT,
    scheduled BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'PENDING',
    PRIMARY KEY (id, scheduled_time)  -- Composite key required for partitioning
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
PARTITION BY RANGE (HOUR(scheduled_time)) (
    PARTITION h00 VALUES LESS THAN (1),   -- 00:xx hours
    PARTITION h01 VALUES LESS THAN (2),   -- 01:xx hours  
    PARTITION h02 VALUES LESS THAN (3),   -- 02:xx hours
    -- ... up to h23
    PARTITION h23 VALUES LESS THAN (24)   -- 23:xx hours
);
```

### Benefits

1. **Efficient Queries**: Time-range queries use partition pruning
2. **Automatic Cleanup**: Old daily tables dropped automatically  
3. **High Performance**: Each daily table partitioned by hour
4. **Scalability**: Handles billions of jobs across time

## Virtual Threads Architecture

### Why Virtual Threads?

```java
// Traditional approach - expensive OS threads
Thread fetcherThread = new Thread(this::runFetcher);

// Virtual threads - lightweight, efficient
Thread fetcherThread = Thread.ofVirtual()
    .name("scheduler-fetcher")
    .start(this::runFetcher);
```

### Benefits

1. **Low Resource Usage**: Minimal memory and CPU overhead
2. **High Concurrency**: Can create millions of virtual threads
3. **Blocking Operations**: Safe to block on I/O operations
4. **Java 21**: Uses latest JVM virtual thread implementation

### Fetcher Loop

```java
private void runFetcher() {
    while (running.get() && !Thread.currentThread().isInterrupted()) {
        try {
            fetchAndScheduleJobs();
            Thread.sleep(config.getFetchIntervalSeconds() * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        } catch (Exception e) {
            logger.error("Error in fetcher thread", e);
            Thread.sleep(5000); // Back-off on error
        }
    }
}
```

## Quartz Integration Details

### JobStore Configuration

```java
// MySQL-based JobStore for persistence
props.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
props.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
props.setProperty("org.quartz.jobStore.dataSource", "myDS");
props.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
props.setProperty("org.quartz.jobStore.isClustered", "true"); // For multi-node
```

### Job Lifecycle Events

```java
// Job listener tracks execution
JobListener jobListener = new JobListener() {
    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        // Job starting - record metrics
    }
    
    @Override 
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException ex) {
        if (ex == null) {
            // Success - record completion
        } else {
            // Failure - record error
        }
    }
};
```

### Misfire Handling

```java
// Default policy: Fire once when recovered
Trigger trigger = TriggerBuilder.newTrigger()
    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
        .withMisfireHandlingInstructionFireNow())
    .build();
```

## Performance Characteristics

### Expected Performance
- **Job Creation**: 10,000+ jobs/second
- **Job Execution**: 5,000+ jobs/second per node
- **Memory Usage**: < 2GB for millions of active jobs
- **Database Queries**: Sub-second with proper indexing

### Scaling Factors
1. **MySQL Performance**: Proper indexing and partitioning
2. **Thread Pool Size**: Match to workload characteristics
3. **Fetch Interval**: Balance between latency and throughput
4. **Batch Size**: Optimize for database performance

### Monitoring Points
- Jobs fetched per cycle
- Jobs scheduled per second
- Jobs executed per second  
- Job execution failures
- Database query performance
- Memory usage and GC

---

**Next:** Learn about [Configuration](Configuration) options and [Performance Monitoring](Performance-Monitoring).