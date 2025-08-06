# Infinite Scheduler - High-Performance Distributed Job Scheduler

## Project Overview

**Infinite Scheduler** is a high-performance, scalable job scheduling system built on Quartz Scheduler with MySQL persistence, designed to handle millions to billions of scheduled jobs efficiently. It integrates with the **partitioned-repo** library's ShardingRepository interface for optimal data management and automatic cleanup.

## Core Requirements

### 1. **Quartz Scheduler Foundation with MySQL Persistence**
- **Base Technology:** Quartz Scheduler with JDBC JobStore (MySQL)
- **Persistence:** All job data, triggers, and state stored in MySQL for durability
- **Misfire Handling:** Default misfire instruction: **MISFIRE_INSTRUCTION_FIRE_ONCE_NOW**
  - Failed jobs automatically retry once when scheduler recovers
  - Prevents job accumulation during system downtime
- **Clustering:** Support for multi-node scheduler clustering for high availability

### 2. **ShardingRepository Integration**
- **Data Source:** Uses `ShardingRepository<TEntity, TKey>` interface from **partitioned-repo** library
- **Repository Types:** Support for both:
  - `GenericMultiTableRepository<T, K>` for high-volume, short-retention data
  - `GenericPartitionedTableRepository<T, K>` for structured, long-retention data
- **Entity Types:** Generic support for any entity type (SmsEntity, OrderEntity, NotificationEntity, etc.)
- **Type Safety:** Full compile-time type safety with generic parameters

### 3. **Virtual Thread-Based Fetcher**
- **Fetcher Thread:** Single virtual thread running continuously
- **Fetch Interval:** Configurable interval `n` seconds (default: 25 seconds)
- **Lookahead Window:** Configurable window `n1` seconds (default: 30 seconds)
- **Constraint:** `n1 > n` (lookahead must be greater than fetch interval)
- **Data Query:** Fetches entities from ShardingRepository for time range `[now, now + n1]`

### 4. **Dynamic Job Scheduling**
- **Job Creation:** For each fetched entity, create and schedule a Quartz job
- **Execution Time:** Schedule job to execute at entity's specified execution time
- **Persistence:** All jobs persisted to MySQL immediately upon creation
- **Job Data:** Include entity data and metadata in JobDataMap
- **Failure Recovery:** Failed jobs automatically recovered from database on scheduler restart

### 5. **Massive Scale Handling**
- **Volume:** Designed to handle millions to billions of scheduled jobs
- **Performance:** Optimized for high-throughput job creation and execution
- **Memory Management:** Efficient memory usage to prevent OOM issues
- **Auto-Cleanup:** Leverage partitioned-repo's automatic partition/table cleanup
- **Backlog Prevention:** Completed jobs automatically cleaned by repository retention policies

## Technical Architecture

### **Core Components**

#### 1. **InfiniteScheduler**
```java
public class InfiniteScheduler<TEntity, TKey> {
    private final ShardingRepository<TEntity, TKey> repository;
    private final Scheduler quartzScheduler;
    private final SchedulerConfig config;
    private final VirtualThread fetcherThread;
}
```

#### 2. **SchedulerConfig**
```java
public class SchedulerConfig {
    private final int fetchIntervalSeconds;      // n - fetch every 25 seconds
    private final int lookaheadWindowSeconds;    // n1 - fetch for next 30 seconds
    private final String quartzDataSource;       // MySQL connection for Quartz
    private final int maxJobsPerFetch;           // Limit jobs per fetch cycle
    private final MisfireInstruction misfirePolicy;
}
```

#### 3. **EntityJobExecutor**
```java
@Component
public class EntityJobExecutor<TEntity> implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // Execute the actual business logic for the entity
        TEntity entity = (TEntity) context.getJobDetail().getJobDataMap().get("entity");
        // Business logic execution here
    }
}
```

### **Data Flow**

1. **Fetcher Thread (Virtual Thread):**
   - Runs every `n` seconds (25s default)
   - Queries ShardingRepository: `findAllByDateRange(now, now + n1)`
   - Processes fetched entities in batches

2. **Job Scheduling:**
   - For each entity, create JobDetail and Trigger
   - Set execution time based on entity's schedule time
   - Persist job to MySQL via Quartz JDBC JobStore
   - Apply misfire policy: FIRE_ONCE_NOW

3. **Job Execution:**
   - Quartz fires jobs at scheduled times
   - EntityJobExecutor processes business logic
   - Job completion triggers cleanup

4. **Auto-Cleanup:**
   - Repository's retention policies automatically clean old data
   - Quartz's built-in cleanup removes executed jobs
   - System remains stable even with billions of jobs

### **Misfire Handling Strategy**

```java
// Default misfire instruction
public static final int DEFAULT_MISFIRE_INSTRUCTION = 
    CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW;

// Configuration
.withMisfireHandlingInstruction(MISFIRE_INSTRUCTION_FIRE_ONCE_NOW)
```

**Benefits:**
- **Recovery:** Jobs missed during downtime execute once when system recovers
- **No Accumulation:** Prevents massive job backlog during outages
- **Resource Protection:** Avoids system overload from accumulated jobs

## Implementation Phases

### **Phase 1: Core Scheduler Framework**
- [ ] Set up Quartz Scheduler with MySQL JDBC JobStore
- [ ] Create InfiniteScheduler generic class with ShardingRepository integration
- [ ] Implement basic virtual thread fetcher
- [ ] Configure misfire handling policies
- [ ] Add basic job execution framework

### **Phase 2: Advanced Features**
- [ ] Implement configurable fetch intervals and lookahead windows
- [ ] Add batch processing for high-volume job creation
- [ ] Implement job priority and categorization
- [ ] Add monitoring and metrics collection
- [ ] Create management APIs for scheduler control

### **Phase 3: Production Optimization**
- [ ] Add clustering support for high availability
- [ ] Implement advanced error handling and retry policies
- [ ] Add comprehensive logging and observability
- [ ] Performance tuning for massive scale
- [ ] Documentation and examples

### **Phase 4: Integration & Testing**
- [ ] Create example integrations with SMS, Order, and Notification entities
- [ ] Load testing with millions of jobs
- [ ] Failover and recovery testing
- [ ] Production deployment guides

## Configuration Examples

### **Basic SMS Scheduling**
```java
// Repository for SMS entities
ShardingRepository<SmsEntity, Long> smsRepo = 
    GenericMultiTableRepository.<SmsEntity, Long>builder(SmsEntity.class, Long.class)
        .database("messaging")
        .tablePrefix("sms")
        .build();

// Scheduler configuration
SchedulerConfig config = SchedulerConfig.builder()
    .fetchInterval(25)        // Fetch every 25 seconds
    .lookaheadWindow(30)      // Look 30 seconds ahead
    .quartzDataSource("jdbc:mysql://localhost:3306/scheduler")
    .maxJobsPerFetch(10000)   // Process up to 10K jobs per fetch
    .build();

// Create infinite scheduler
InfiniteScheduler<SmsEntity, Long> scheduler = 
    new InfiniteScheduler<>(smsRepo, config);

scheduler.start();
```

### **Production Configuration**
```java
SchedulerConfig productionConfig = SchedulerConfig.builder()
    .fetchInterval(15)                    // More frequent fetching
    .lookaheadWindow(45)                  // Larger lookahead window
    .quartzDataSource("jdbc:mysql://prod-db:3306/scheduler?useSSL=true")
    .maxJobsPerFetch(50000)               // Higher throughput
    .misfireInstruction(MISFIRE_INSTRUCTION_FIRE_ONCE_NOW)
    .clusteringEnabled(true)              // Enable clustering
    .threadPoolSize(20)                   // Quartz thread pool
    .batchSize(1000)                      // Batch job creation
    .build();
```

## Performance Characteristics

### **Expected Performance**
- **Job Creation Rate:** 10,000+ jobs/second
- **Job Execution Rate:** 5,000+ jobs/second per node
- **Memory Usage:** < 2GB for millions of active jobs
- **Database Storage:** Efficient with automatic cleanup
- **Scalability:** Linear scaling with additional nodes

### **Resource Requirements**
- **CPU:** Virtual threads minimize CPU overhead
- **Memory:** Bounded memory usage regardless of job count
- **Database:** MySQL with appropriate indexing and partitioning
- **Network:** Minimal network usage for job coordination

## Integration Points

### **With partitioned-repo Library**
- Import as Maven dependency: `com.telcobright:partitioned-repo:1.0.0`
- Use ShardingRepository interface for all data operations
- Leverage automatic table/partition management
- Benefit from HikariCP connection pooling

### **Entity Requirements**
```java
@Table(name = "sms_schedules")
public class SmsScheduleEntity {
    @Id
    @Column(name = "id")
    private Long id;
    
    @ShardingKey  // Required for partitioning
    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "message")
    private String message;
    
    // ... other fields
}
```

## Monitoring & Observability

### **Key Metrics**
- Jobs scheduled per second
- Jobs executed per second
- Jobs failed/retried
- Fetch cycle duration
- Repository query performance
- Memory and CPU utilization

### **Logging**
- Structured logging with correlation IDs
- Job lifecycle tracking
- Performance metrics
- Error reporting and alerting

## Benefits

### **Scalability**
- Handle billions of jobs without performance degradation
- Linear scaling with additional scheduler nodes
- Automatic cleanup prevents disk space issues

### **Reliability**
- MySQL persistence ensures job durability
- Misfire handling prevents job loss
- Clustering provides high availability

### **Performance**
- Virtual threads minimize resource overhead
- Batch processing optimizes database operations
- ShardingRepository provides efficient data access

### **Maintainability**
- Generic design supports any entity type
- Automatic cleanup eliminates manual maintenance
- Comprehensive monitoring and logging

---

## Dependencies

### **Required Libraries**
- **Quartz Scheduler:** `org.quartz-scheduler:quartz:2.3.2`
- **MySQL Connector:** `mysql:mysql-connector-java:8.0.33`
- **HikariCP:** `com.zaxxer:HikariCP:5.0.1`
- **partitioned-repo:** `com.telcobright:partitioned-repo:1.0.0` (local)

### **Java Requirements**
- **Java Version:** 21+ (for Virtual Threads)
- **Memory:** Minimum 4GB heap for production
- **Database:** MySQL 8.0+ or MariaDB 10.6+

---

**Note:** This project will be created as a sibling to the partitioned-repo project and will heavily integrate with its ShardingRepository interface for optimal performance and automatic data lifecycle management.