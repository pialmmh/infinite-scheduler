# Quartz Tables Guide - Infinite Scheduler

## Table Relationships and Job Lifecycle

### **Job Scheduling Flow:**
1. **InfiniteScheduler** fetches entities from repository
2. Creates **JobDetail** → inserted into `qrtz_job_details`
3. Creates **SimpleTrigger** → inserted into `qrtz_triggers` + `qrtz_simple_triggers`
4. Job waits in **WAITING** state until execution time
5. Scheduler acquires trigger → state becomes **ACQUIRED**
6. Job starts executing → record added to `qrtz_fired_triggers`
7. Job completes → all records removed (cleanup)

## Table Usage in Our System

### **Primary Tables (Always Used)**
- **qrtz_job_details**: Every scheduled SMS job
- **qrtz_triggers**: Every job's trigger with timing
- **qrtz_simple_triggers**: Simple one-time execution triggers
- **qrtz_fired_triggers**: Currently executing jobs
- **qrtz_scheduler_state**: Scheduler instance heartbeat
- **qrtz_locks**: Cluster coordination locks

### **Secondary Tables (Situational)**
- **qrtz_paused_trigger_grps**: If we pause job groups
- **qrtz_calendars**: If we exclude certain dates/times
- **qrtz_cron_triggers**: If we use cron expressions (we don't)
- **qrtz_blob_triggers**: If we use custom trigger types (we don't)
- **qrtz_simprop_triggers**: If we use property-based triggers (we don't)

## Monitoring Queries

### **1. Active Jobs Count**
```sql
SELECT COUNT(*) as ACTIVE_JOBS FROM qrtz_job_details;
```

### **2. Jobs by State**
```sql
SELECT TRIGGER_STATE, COUNT(*) as COUNT 
FROM qrtz_triggers 
GROUP BY TRIGGER_STATE;
```

### **3. Next 10 Jobs to Execute**
```sql
SELECT 
    t.JOB_NAME,
    FROM_UNIXTIME(t.NEXT_FIRE_TIME/1000) as NEXT_FIRE_TIME,
    t.TRIGGER_STATE
FROM qrtz_triggers t
WHERE t.TRIGGER_STATE = 'WAITING'
ORDER BY t.NEXT_FIRE_TIME
LIMIT 10;
```

### **4. Currently Executing Jobs**
```sql
SELECT 
    JOB_NAME,
    FROM_UNIXTIME(FIRED_TIME/1000) as STARTED_AT,
    STATE
FROM qrtz_fired_triggers
ORDER BY FIRED_TIME DESC;
```

### **5. Job Execution History (Recent)**
```sql
-- Note: Completed jobs are removed, so this shows recent activity
SELECT 
    JOB_NAME,
    FROM_UNIXTIME(PREV_FIRE_TIME/1000) as LAST_EXECUTED
FROM qrtz_triggers
WHERE PREV_FIRE_TIME IS NOT NULL
ORDER BY PREV_FIRE_TIME DESC
LIMIT 20;
```

### **6. Scheduler Cluster Status**
```sql
SELECT 
    INSTANCE_NAME,
    FROM_UNIXTIME(LAST_CHECKIN_TIME/1000) as LAST_CHECKIN,
    CASE 
        WHEN (UNIX_TIMESTAMP() * 1000 - LAST_CHECKIN_TIME) < 30000 
        THEN 'ACTIVE' 
        ELSE 'INACTIVE' 
    END as STATUS
FROM qrtz_scheduler_state;
```

## Understanding Job States

### **Trigger States:**
- **WAITING**: Job scheduled, waiting for execution time
- **ACQUIRED**: Trigger acquired by scheduler, about to execute
- **EXECUTING**: Job is currently running (also in qrtz_fired_triggers)
- **PAUSED**: Job/trigger group paused
- **BLOCKED**: Job blocked (usually by another running instance)
- **ERROR**: Error state (misfire or execution error)
- **COMPLETE**: Trigger completed (will be removed)

### **Fired Trigger States:**
- **ACQUIRED**: Job acquired but not yet started
- **EXECUTING**: Job currently running

## Cleanup Behavior

### **Automatic Cleanup (Default)**
- **Completed Jobs**: Removed from all tables immediately
- **One-time Triggers**: Removed after successful execution
- **Failed Jobs**: May remain depending on misfire policy

### **Manual Cleanup Queries**
```sql
-- Remove old completed triggers (if any remain)
DELETE FROM qrtz_simple_triggers 
WHERE TRIGGER_NAME NOT IN (SELECT TRIGGER_NAME FROM qrtz_triggers);

-- Remove orphaned job details (if any)
DELETE FROM qrtz_job_details 
WHERE JOB_NAME NOT IN (SELECT JOB_NAME FROM qrtz_triggers);
```

## Troubleshooting

### **Common Issues:**
1. **Jobs stuck in ACQUIRED**: Scheduler instance died
2. **Jobs stuck in WAITING**: Clock skew or past execution time
3. **No jobs visible**: They completed and were cleaned up (success!)
4. **Many BLOCKED jobs**: Concurrent execution prevented

### **Diagnostic Queries:**
```sql
-- Check for stuck jobs
SELECT * FROM qrtz_fired_triggers 
WHERE FIRED_TIME < (UNIX_TIMESTAMP() - 300) * 1000; -- Older than 5 minutes

-- Check scheduler health
SELECT 
    INSTANCE_NAME,
    (UNIX_TIMESTAMP() * 1000 - LAST_CHECKIN_TIME) / 1000 as SECONDS_SINCE_CHECKIN
FROM qrtz_scheduler_state;
```

## Performance Considerations

### **Indexes (Auto-created by Quartz)**
- Primary keys on all tables
- Indexes on trigger fire times
- Indexes on job names and groups
- Indexes on scheduler instance names

### **Optimization Tips**
- **Clean old data**: Remove completed job records periodically
- **Monitor lock contention**: Check qrtz_locks table
- **Tune connection pool**: Adjust HikariCP settings
- **Monitor trigger states**: Watch for stuck ACQUIRED states