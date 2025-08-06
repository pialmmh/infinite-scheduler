# Scheduled Flag - Duplicate Prevention System

## Overview

The **Scheduled Flag** is a robust duplicate prevention mechanism that ensures each job is only scheduled to Quartz once, preventing duplicate job execution even if the fetcher encounters the same entity multiple times.

## How It Works

### **1. SchedulableEntity Interface**
Every schedulable entity now implements a `scheduled` flag:

```java
public interface SchedulableEntity<TKey> extends ShardingEntity<TKey> {
    Boolean getScheduled();
    void setScheduled(Boolean scheduled);
    // ... other methods
}
```

### **2. Dual-Layer Protection**

#### **A. In-Memory Tracking (Primary)**
- **scheduledJobIds Set**: Thread-safe HashSet tracks scheduled job IDs
- **Fast Lookup**: O(1) performance for duplicate detection
- **Memory Efficient**: Only stores job IDs, not full entities

#### **B. Entity Flag (Secondary)**
- **Entity Level**: Each entity has `scheduled` boolean field
- **Persistence Ready**: Can be saved to database if repository supports updates
- **Visual Indicator**: Easy to see which entities are scheduled

### **3. Lifecycle Flow**

```
1. Entity Created → scheduled = false
2. Fetcher Finds Entity → Check scheduledJobIds.contains(jobId)
3. Not Scheduled → Add to Quartz + Add to scheduledJobIds + Set scheduled = true
4. Already Scheduled → Skip entity (no duplicate)
5. Job Completes → Remove from scheduledJobIds (allow re-scheduling if needed)
```

## Implementation Details

### **Entity Changes**
```java
@Table(name = "sms_schedules")
public class SmsEntity implements SchedulableEntity<Long> {
    
    @Column(name = "scheduled")
    private Boolean scheduled;
    
    @Override
    public Boolean getScheduled() {
        return scheduled;
    }
    
    @Override
    public void setScheduled(Boolean scheduled) {
        this.scheduled = scheduled;
    }
    
    // Constructor sets scheduled = false initially
    public SmsEntity(...) {
        this.scheduled = false;
        // ... other initialization
    }
}
```

### **Scheduler Logic**
```java
// Filter out already scheduled jobs
List<TEntity> entities = recentEntities.stream()
    .filter(entity -> {
        String jobId = entity.getJobId();
        return scheduledTime != null && 
               !scheduledTime.isBefore(now) && 
               !scheduledTime.isAfter(endTime) &&
               !scheduledJobIds.contains(jobId); // ← Duplicate prevention
    })
    .limit(config.getMaxJobsPerFetch())
    .toList();
```

```java
// Mark as scheduled when adding to Quartz
quartzScheduler.scheduleJob(jobDetail, trigger);
scheduledJobIds.add(jobId);           // ← In-memory tracking
entity.setScheduled(true);            // ← Entity flag
```

```java
// Clean up when job completes
@Override
public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    String jobName = context.getJobDetail().getKey().getName();
    scheduledJobIds.remove(jobName);   // ← Remove from memory
    // ... handle success/failure
}
```

## Benefits

### **🚫 Prevents Duplicate Jobs**
- **No Double Execution**: Same job never runs twice
- **Resource Protection**: Prevents wasted CPU/memory from duplicates
- **Data Integrity**: Ensures SMS/notifications sent only once

### **⚡ High Performance**
- **O(1) Lookup**: HashSet provides constant-time duplicate detection
- **Minimal Memory**: Only stores job IDs, not full entity objects
- **Thread Safe**: Synchronized set handles concurrent access

### **🔄 Automatic Cleanup**
- **Job Completion**: Removes job ID when execution finishes
- **Memory Efficient**: Prevents memory leaks from accumulating IDs
- **Re-scheduling Ready**: Allows same entity to be rescheduled later

### **💾 Database Column Ready**
- **Schema Addition**: Add `scheduled BOOLEAN DEFAULT FALSE` column
- **Migration Safe**: Works with existing data (NULL treated as FALSE)
- **Reporting Ready**: Can query database for scheduled vs pending jobs

## Database Schema Update

To add the scheduled column to your entity tables:

```sql
-- Add scheduled column to SMS table
ALTER TABLE sms_schedules_YYYYMMDD 
ADD COLUMN scheduled BOOLEAN DEFAULT FALSE;

-- Query scheduled vs unscheduled jobs
SELECT 
    COUNT(*) as total,
    SUM(CASE WHEN scheduled = TRUE THEN 1 ELSE 0 END) as scheduled_jobs,
    SUM(CASE WHEN scheduled = FALSE OR scheduled IS NULL THEN 1 ELSE 0 END) as pending_jobs
FROM sms_schedules_20250806;
```

## Testing the Feature

### **1. Create Test Entities**
```java
// All entities start with scheduled = false
SmsEntity sms1 = new SmsEntity(1L, scheduledTime, "+123456789", "Test message");
System.out.println(sms1.getScheduled()); // false

scheduler.getRepository().insert(sms1);
```

### **2. Verify Duplicate Prevention**
```java
// First fetch - job gets scheduled
scheduler.fetchAndScheduleJobs(); // sms1 scheduled, scheduledJobIds contains job-1-...

// Second fetch - same job skipped
scheduler.fetchAndScheduleJobs(); // sms1 skipped, no duplicate

// Check entity state
System.out.println(sms1.getScheduled()); // true
```

### **3. Monitor Logs**
Look for these log messages:
```
DEBUG - Scheduled job for entity: 1 (jobId: job-1-2025-08-06T20:30:00) at 2025-08-06T20:30:00 - marked as scheduled
DEBUG - Job job-1-2025-08-06T20:30:00 executed successfully and removed from scheduled set
```

## Advanced Usage

### **Manual Schedule Control**
```java
// Prevent specific entity from being scheduled
entity.setScheduled(true);

// Allow re-scheduling of completed job
scheduledJobIds.remove(entity.getJobId());
entity.setScheduled(false);
```

### **Bulk Status Check**
```java
// Check how many jobs are scheduled in memory
int scheduledCount = scheduler.getScheduledJobIds().size();
System.out.println("Currently scheduled jobs: " + scheduledCount);
```

### **Custom Duplicate Logic**
If you need custom duplicate detection logic, override `getJobId()`:
```java
@Override
public String getJobId() {
    // Custom logic for job ID generation
    return "custom-" + getId() + "-" + getPhoneNumber();
}
```

## Troubleshooting

### **Jobs Not Being Scheduled**
- **Check scheduled flag**: Verify entity.getScheduled() is false
- **Check memory set**: scheduledJobIds might contain the job ID
- **Clear memory**: Restart scheduler to clear in-memory tracking

### **Memory Usage Growing**
- **Job completion**: Ensure jobs are completing successfully
- **Cleanup logic**: Verify jobWasExecuted() is removing job IDs
- **Monitor size**: Track scheduledJobIds.size() over time

### **Database Column Issues**
- **NULL handling**: NULL scheduled values are treated as FALSE
- **Migration**: Add DEFAULT FALSE to handle existing records
- **Indexing**: Consider adding index on scheduled column for queries

## Performance Impact

- **Filtering**: Minimal overhead, O(1) HashSet lookup
- **Memory**: ~24 bytes per scheduled job ID (Java String overhead)
- **CPU**: Negligible impact on scheduling performance
- **Database**: No additional queries required for duplicate detection

This scheduled flag system provides robust, efficient duplicate prevention while maintaining high performance and memory efficiency.