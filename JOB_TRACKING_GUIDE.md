# Complete Job Tracking Guide

## How to Track All Jobs - Processed or Not

The Infinite Scheduler now provides **complete job lifecycle tracking** through a dedicated history table that persists all job states.

### **Job States:**
- **SCHEDULED**: Job created and waiting for execution time
- **STARTED**: Job execution has begun  
- **COMPLETED**: Job finished successfully
- **FAILED**: Job execution failed with error

## **Method 1: Comprehensive History Tracking (Recommended)**

### **Query All Jobs Status:**
```bash
mysql -h 127.0.0.1 -P 3306 -u root -p123456 scheduler < track_all_jobs.sql
```

### **Quick Status Check:**
```sql
-- Summary of all jobs
SELECT 
    status,
    COUNT(*) as count,
    CASE 
        WHEN status = 'COMPLETED' THEN 'PROCESSED ✓'
        WHEN status = 'FAILED' THEN 'PROCESSED ✗'  
        WHEN status = 'STARTED' THEN 'PROCESSING...'
        WHEN status = 'SCHEDULED' THEN 'NOT PROCESSED'
    END as processing_status
FROM job_execution_history 
GROUP BY status;
```

### **Individual Job Tracking:**
```sql
-- Track specific job by ID
SELECT 
    job_id,
    entity_id,
    status,
    scheduled_time,
    started_at,
    completed_at,
    execution_duration_ms,
    error_message
FROM job_execution_history 
WHERE job_id = 'job-1-2025-08-06T20:21:30'
ORDER BY created_at DESC;
```

## **Method 2: Real-time Quartz Tables**

### **Current Active Jobs (Not Yet Processed):**
```sql
SELECT 
    'NOT PROCESSED' as STATUS,
    jd.JOB_NAME as JOB_ID,
    t.TRIGGER_STATE,
    FROM_UNIXTIME(t.NEXT_FIRE_TIME/1000) as SCHEDULED_TIME
FROM qrtz_job_details jd 
JOIN qrtz_triggers t ON jd.JOB_NAME = t.JOB_NAME 
ORDER BY t.NEXT_FIRE_TIME;
```

### **Currently Executing Jobs:**
```sql
SELECT 
    'PROCESSING' as STATUS,
    JOB_NAME as JOB_ID,
    FROM_UNIXTIME(FIRED_TIME/1000) as STARTED_AT
FROM qrtz_fired_triggers
ORDER BY FIRED_TIME DESC;
```

## **Method 3: Monitor Dashboard**

The real-time monitoring dashboard shows live statistics:

```
║ Total Jobs Scheduled: 10                                                         ║
║ Active Jobs in Queue: 3                                                          ║  
║ Jobs Executed:        7                                                          ║
║ Jobs Failed:          0                                                          ║
```

**Interpretation:**
- **Total Jobs Scheduled**: 10 jobs created
- **Active Jobs in Queue**: 3 jobs not yet processed
- **Jobs Executed**: 7 jobs successfully processed
- **Jobs Failed**: 0 jobs failed processing

## **Key SQL Queries for Job Tracking**

### **1. All Jobs with Processing Status:**
```sql
SELECT 
    job_id,
    entity_id,
    CASE 
        WHEN status = 'SCHEDULED' THEN 'NOT PROCESSED'
        WHEN status = 'STARTED' THEN 'PROCESSING'
        WHEN status = 'COMPLETED' THEN 'PROCESSED (Success)'
        WHEN status = 'FAILED' THEN 'PROCESSED (Failed)'
    END as PROCESSING_STATUS,
    scheduled_time,
    completed_at,
    execution_duration_ms
FROM job_execution_history 
ORDER BY created_at DESC;
```

### **2. Jobs Not Yet Processed:**
```sql
SELECT job_id, entity_id, scheduled_time 
FROM job_execution_history 
WHERE status = 'SCHEDULED'
ORDER BY scheduled_time;
```

### **3. Successfully Processed Jobs:**
```sql
SELECT job_id, entity_id, completed_at, execution_duration_ms
FROM job_execution_history 
WHERE status = 'COMPLETED'
ORDER BY completed_at DESC;
```

### **4. Failed Jobs:**
```sql
SELECT job_id, entity_id, error_message, completed_at
FROM job_execution_history 
WHERE status = 'FAILED'
ORDER BY completed_at DESC;
```

### **5. Performance Statistics:**
```sql
SELECT 
    COUNT(*) as TOTAL_JOBS,
    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as SUCCESS_COUNT,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as FAILED_COUNT,
    SUM(CASE WHEN status IN ('SCHEDULED', 'STARTED') THEN 1 ELSE 0 END) as PENDING_COUNT,
    AVG(execution_duration_ms) as AVG_EXECUTION_TIME_MS
FROM job_execution_history;
```

## **Database Tables Structure**

### **job_execution_history table:**
```sql
CREATE TABLE job_execution_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id VARCHAR(255) NOT NULL,           -- Unique job identifier
    job_name VARCHAR(255) NOT NULL,         -- Job name from Quartz  
    job_group VARCHAR(255) NOT NULL,        -- Job group
    entity_id VARCHAR(255),                 -- Original entity ID
    scheduled_time DATETIME,                -- When job should execute
    started_at DATETIME,                    -- When job actually started
    completed_at DATETIME,                  -- When job finished
    status ENUM('SCHEDULED', 'STARTED', 'COMPLETED', 'FAILED'),
    error_message TEXT,                     -- Error details if failed
    execution_duration_ms BIGINT,           -- How long job took to run
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### **Key Features:**
- **Complete Lifecycle**: Tracks from scheduling to completion
- **Persistent History**: Jobs remain in table after execution
- **Performance Metrics**: Execution duration tracking
- **Error Tracking**: Full error message capture
- **Timeline Analysis**: Created/started/completed timestamps

## **Monitoring Workflow**

### **Development/Testing:**
1. **Run demo**: `mvn test -Dtest=MonitoringDemoTest`
2. **Watch monitor**: See real-time dashboard updates
3. **Check history**: `mysql ... < track_all_jobs.sql`
4. **Verify processing**: Count COMPLETED vs SCHEDULED

### **Production:**
1. **Monitor dashboard**: Real-time job statistics
2. **Daily reports**: Query job_execution_history for trends
3. **Alert on failures**: Monitor FAILED status jobs
4. **Performance analysis**: Track execution_duration_ms

### **Troubleshooting:**
1. **Stuck jobs**: Check jobs in STARTED state for too long
2. **Failed jobs**: Query error_message for failure patterns
3. **Performance issues**: Analyze execution_duration_ms
4. **Missing jobs**: Compare expected vs actual job counts

## **Cleanup and Maintenance**

The system includes automatic cleanup:
```java
// Clean up history older than 30 days
historyTracker.cleanupOldHistory(30);
```

**Manual cleanup:**
```sql
-- Remove old history (older than 30 days)
DELETE FROM job_execution_history 
WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
```

This comprehensive tracking system gives you **complete visibility** into every job's lifecycle, from creation to completion or failure.