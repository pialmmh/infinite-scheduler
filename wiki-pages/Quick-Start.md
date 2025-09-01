# Quick Start Guide

Get **Infinite Scheduler** up and running in 5 minutes!

## Prerequisites

- **Java 21+** (for Virtual Threads support)
- **MySQL 8.0+** or **MariaDB 10.6+**
- **Maven 3.6+**

## 1. Add Dependencies

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
    <dependency>
        <groupId>com.telcobright.db</groupId>
        <artifactId>partitioned-repo</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

## 2. Database Setup

**Option A: Automatic (Recommended)**
- Scheduler creates all tables automatically on first startup
- Just ensure your database exists with proper permissions

**Option B: Manual**
```sql
-- Create database
CREATE DATABASE scheduler;

-- Import Quartz tables (optional - auto-created by default)
mysql -u root -p scheduler < quartz-mysql-schema.sql
```

## 3. Create Your Entity

```java
@Table(name = "sms_schedules")
public class SmsEntity implements SchedulableEntity<Long> {
    @Id
    @Column(name = "id")
    private Long id;
    
    @ShardingKey  // Used for partitioning by partitioned-repo
    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "message") 
    private String message;
    
    @Column(name = "scheduled")  // Tracks if job is picked up
    private Boolean scheduled;
    
    // Required methods
    @Override
    public Long getId() { return id; }
    
    @Override
    public LocalDateTime getScheduledTime() { return scheduledTime; }
    
    @Override
    public Boolean getScheduled() { return scheduled; }
    
    @Override
    public void setScheduled(Boolean scheduled) { this.scheduled = scheduled; }
    
    // Constructors, getters, setters...
    public SmsEntity() {}
    
    public SmsEntity(Long id, LocalDateTime scheduledTime, String phoneNumber, String message) {
        this.id = id;
        this.scheduledTime = scheduledTime;
        this.phoneNumber = phoneNumber;
        this.message = message;
        this.scheduled = false; // Initially not scheduled
    }
    
    // Getters and setters...
}
```

## 4. Create Job Executor

```java
public class SmsJob implements Job {
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String entityId = context.getJobDetail().getJobDataMap().getString("entityId");
        String scheduledTime = context.getJobDetail().getJobDataMap().getString("scheduledTime");
        
        System.out.println("🚀 Executing SMS job for entity: " + entityId + " at " + scheduledTime);
        
        // Your SMS sending logic here
        // You can load the full entity from the repository if needed
    }
}
```

## 5. Configure and Start Scheduler

```java
public class SchedulerExample {
    public static void main(String[] args) throws Exception {
        // Configure scheduler
        SchedulerConfig config = SchedulerConfig.builder()
            .fetchInterval(30)              // Check every 30 seconds
            .lookaheadWindow(360)           // Look 6 minutes ahead
            .mysqlHost("127.0.0.1")         
            .mysqlPort(3306)               
            .mysqlDatabase("scheduler")     
            .mysqlUsername("root")          
            .mysqlPassword("your_password") 
            .repositoryDatabase("scheduler") 
            .repositoryTablePrefix("sms")   // Creates tables like sms_20250807
            .maxJobsPerFetch(10000)        
            .autoCreateTables(true)         // Auto-create Quartz tables
            .build();
        
        // Create scheduler
        InfiniteScheduler<SmsEntity, Long> scheduler = 
            new InfiniteScheduler<>(SmsEntity.class, Long.class, config, SmsJob.class);
        
        // Insert some test jobs
        createTestJobs(scheduler);
        
        // Start scheduler
        scheduler.start();
        System.out.println("📅 Scheduler started! Jobs will execute based on their scheduled_time");
        
        // Keep running
        Thread.sleep(600000); // Run for 10 minutes
        scheduler.stop();
    }
    
    private static void createTestJobs(InfiniteScheduler<SmsEntity, Long> scheduler) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 1; i <= 5; i++) {
            SmsEntity sms = new SmsEntity(
                (long) i,
                now.plusMinutes(i),           // Schedule 1, 2, 3, 4, 5 minutes from now
                "+8801712345" + (678 + i),    // Phone numbers
                "Test SMS #" + i + " scheduled for " + now.plusMinutes(i)
            );
            
            scheduler.getRepository().insert(sms);
            System.out.println("✅ Inserted SMS job #" + i + " for " + sms.getScheduledTime());
        }
    }
}
```

## 6. Run and Verify

1. **Compile and run:**
   ```bash
   mvn clean compile exec:java -Dexec.mainClass="your.package.SchedulerExample"
   ```

2. **Watch the logs:**
   ```
   📅 Scheduler started! Jobs will execute based on their scheduled_time
   ✅ Inserted SMS job #1 for 2025-08-07T20:30:00
   🔍 Fetching entities from 2025-08-07T20:29:00 to 2025-08-07T20:35:00
   📊 Found 5 total entities from database
   📋 Found 5 candidate entities in time range
   ✅ Marked entity 1 as scheduled=1 in database
   🚀 Executing SMS job for entity: 1 at 2025-08-07T20:30:00
   ```

3. **Check database:**
   ```sql
   -- Check partitioned tables created
   SHOW TABLES LIKE 'sms_%';
   
   -- Check job data
   SELECT * FROM sms_20250807 WHERE scheduled = 1;
   
   -- Check Quartz tables
   SELECT * FROM QRTZ_JOB_DETAILS;
   ```

## 🎉 Success!

You now have a working Infinite Scheduler! The system will:
- ✅ Create partitioned tables automatically
- ✅ Fetch jobs with `scheduled=0` 
- ✅ Set `scheduled=1` when picked up
- ✅ Execute jobs at their exact scheduled time
- ✅ Never schedule the same job twice

## Next Steps

- **[Configuration Guide](Configuration)** - Learn all configuration options
- **[Architecture](Architecture)** - Understand how it works
- **[Examples](Examples)** - More complex examples and patterns
- **[Troubleshooting](Troubleshooting)** - Common issues and solutions

---

**Need help?** Check the [Troubleshooting](Troubleshooting) guide or open an issue on GitHub.