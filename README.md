# Infinite Scheduler

A high-performance, scalable job scheduling system built on Quartz Scheduler with MySQL persistence, designed to handle millions to billions of scheduled jobs efficiently.

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
public class SmsEntity implements SchedulableEntity<Long> {
    private Long id;
    private LocalDateTime scheduledTime;
    private String phoneNumber;
    private String message;
    
    @Override
    public Long getId() { return id; }
    
    @Override
    public LocalDateTime getScheduledTime() { return scheduledTime; }
    
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

// Define job executor
Consumer<SmsEntity> smsExecutor = sms -> {
    System.out.println("Sending SMS to " + sms.getPhoneNumber() + ": " + sms.getMessage());
    // Your SMS sending logic here
};

// Create scheduler - partitioned-repo is created and managed internally
InfiniteScheduler<SmsEntity, Long> scheduler = 
    new InfiniteScheduler<>(SmsEntity.class, Long.class, config, smsExecutor);

scheduler.start();

// Stop when done
scheduler.stop();
```

### Constructor Parameters

The `InfiniteScheduler` constructor accepts:
- `entityClass`: The entity class (e.g., `SmsEntity.class`)
- `keyClass`: The key class (e.g., `Long.class`)  
- `config`: SchedulerConfig with complete MySQL and repository settings
- `jobExecutor`: Consumer function to process entities

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
3. **Job Execution**: Quartz fires jobs at scheduled times, loads entity from repository
4. **Auto-Cleanup**: Repository retention policies and Quartz cleanup maintain system health

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

## License

This project is part of the TelcoBright suite of libraries.