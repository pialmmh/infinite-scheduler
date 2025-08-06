# Job Timing Verification

## Exact Schedule: 10 Jobs, 2 per Minute

### **Job Execution Timeline:**

| Job ID | Phone Number | Scheduled Time | Minute | Jobs in Minute |
|--------|--------------|----------------|---------|----------------|
| 1 | +1234567890 | now + 30s | 0:30 | **1st of 2** |
| 2 | +1234567891 | now + 1m | 1:00 | **2nd of 2** |
| 3 | +1234567892 | now + 1m 30s | 1:30 | **1st of 2** |
| 4 | +1234567893 | now + 2m | 2:00 | **2nd of 2** |
| 5 | +1234567894 | now + 2m 30s | 2:30 | **1st of 2** |
| 6 | +1234567895 | now + 3m | 3:00 | **2nd of 2** |
| 7 | +1234567896 | now + 3m 30s | 3:30 | **1st of 2** |
| 8 | +1234567897 | now + 4m | 4:00 | **2nd of 2** |
| 9 | +1234567898 | now + 4m 30s | 4:30 | **1st of 2** |
| 10 | +1234567899 | now + 5m | 5:00 | **2nd of 2** |

### **Verification Pattern:**
- **Total Jobs**: Exactly 10
- **Time Interval**: 30 seconds between each job
- **Jobs per Minute**: Exactly 2 jobs
- **Total Duration**: 5 minutes (4.5 minutes from first to last)
- **Pattern**: Job at :30 seconds, then job at :00 seconds of next minute

### **SQL Query to Verify Timing:**
```sql
-- Check job scheduling times
SELECT 
    JOB_NAME,
    FROM_UNIXTIME(NEXT_FIRE_TIME/1000) as SCHEDULED_TIME,
    NEXT_FIRE_TIME
FROM qrtz_triggers 
WHERE JOB_GROUP = 'entity-jobs'
ORDER BY NEXT_FIRE_TIME;
```

### **Monitor Output Pattern:**
You should see the monitoring dashboard show:
- **Total Jobs Scheduled**: 10
- **Active Jobs in Queue**: Decreasing from 10 to 0
- **Jobs Executed**: Increasing from 0 to 10
- **Upcoming Jobs**: Always showing next 2-5 jobs with 30-second intervals

### **Execution Log Pattern:**
```
2025-08-06 20:16:30 - Executing SMS job for entity ID: 1
2025-08-06 20:17:00 - Executing SMS job for entity ID: 2  
2025-08-06 20:17:30 - Executing SMS job for entity ID: 3
2025-08-06 20:18:00 - Executing SMS job for entity ID: 4
... and so on every 30 seconds
```