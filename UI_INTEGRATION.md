# Infinite Scheduler - UI Integration

**Date:** 2025-10-17
**Status:** ✅ Complete

---

## Overview

Successfully integrated the existing monitoring UI with the new Quarkus-based scheduler. The UI provides real-time monitoring of scheduled jobs and execution history.

---

## Components Integrated

### 1. Static UI File
**Location:** `src/main/resources/META-INF/resources/index.html`
- Copied from `src/main/resources/web/index.html`
- Quarkus automatically serves static content from `META-INF/resources`
- Accessible at `http://localhost:8080/index.html` or `http://localhost:8080/`

### 2. Monitoring API (`MonitoringResource.java`)
Created REST endpoints to support the UI:

#### **GET /api/summary**
Returns scheduler statistics:
```json
{
  "scheduledNext1Hour": 42,
  "completedLast1Hour": 0,
  "totalExecuted": 0
}
```

#### **GET /api/jobs?limit=20&offset=0**
Returns paginated list of scheduled jobs:
```json
{
  "jobs": [
    {
      "jobName": "sms-123",
      "jobGroup": "sms_send",
      "nextFireTime": 1729142400000,
      "triggerState": "NORMAL",
      "additionalData": {
        "phone": "+8801712345678",
        "message": "Your order is ready!",
        "scheduledTime": "2025-10-17T10:30:00"
      }
    }
  ]
}
```

#### **GET /api/history?limit=20&offset=0**
Returns execution history (currently empty - requires history tracking):
```json
{
  "jobs": []
}
```

### 3. Quartz Scheduler CDI Producer
**Modified:** `InfiniteSchedulerService.java`
- Added `@Produces` method to expose Quartz Scheduler for CDI injection
- Allows `MonitoringResource` to inject and query the scheduler

---

## UI Features

### Dashboard Layout
- **Left Column:** Summary statistics
  - Next 1 Hour: Count of jobs scheduled in next hour
  - Last 1 Hour: Completed jobs (requires history tracking)
  - All Time: Total executed jobs (requires history tracking)

- **Middle Panel:** Scheduled Jobs
  - Real-time list of scheduled jobs
  - Job details with parameters
  - Pagination (20 jobs per page)
  - Status badges (NORMAL, PAUSED, etc.)

- **Right Panel:** Execution History
  - Completed job history
  - Execution duration
  - Error messages (if failed)
  - Pagination

### Features
- Auto-refresh every 5 seconds
- Modal popup for viewing all job parameters
- Pagination controls
- Parameter count badges
- Color-coded status indicators

---

## Access the UI

### Start Quarkus
```bash
mvn quarkus:dev
```

### Open Browser
```
http://localhost:8080/
```
or
```
http://localhost:8080/index.html
```

---

## How It Works

### Data Flow
```
Browser
  ↓ (every 5s)
GET /api/summary
GET /api/jobs?limit=20&offset=0
GET /api/history?limit=20&offset=0
  ↓
MonitoringResource
  ↓ (queries)
Quartz Scheduler (injected)
  ↓
Returns job data
  ↓
JavaScript renders UI
```

### Job Listing Logic
1. MonitoringResource queries all JobKeys from Quartz
2. Sorts jobs by next fire time
3. Applies pagination (offset + limit)
4. Builds job info with:
   - Job name and group
   - Next fire time
   - Trigger state
   - Additional parameters from JobDataMap
5. Returns JSON to UI

### Summary Statistics
1. Gets all JobKeys from Quartz
2. Filters jobs by next fire time (within 1 hour)
3. Counts matching jobs
4. Returns statistics (history tracking TODO)

---

## Limitations & Future Enhancements

### Current Limitations
1. **No History Tracking** - Execution history panel is empty
   - Requires persistent history storage
   - Need to track job completions, failures, durations

2. **No Real-time Updates** - Uses polling (5s interval)
   - Could be improved with WebSockets or Server-Sent Events

3. **Limited Job Details** - Only shows simple types from JobDataMap
   - Complex objects are filtered out
   - Could parse jobDataJson for full details

### Future Enhancements
1. **Add History Tracking**
   ```java
   @Entity
   public class JobHistory {
       @Id String jobId;
       LocalDateTime startTime;
       LocalDateTime endTime;
       String status;
       String errorMessage;
       Long durationMs;
   }
   ```

2. **WebSocket Support**
   - Real-time job updates without polling
   - Instant notification of job completions

3. **Enhanced Filtering**
   - Filter by job type
   - Search by job ID
   - Filter by status

4. **Job Actions**
   - Pause/resume jobs
   - Delete scheduled jobs
   - Re-schedule failed jobs

5. **Charts & Graphs**
   - Job execution timeline
   - Success/failure rates
   - Performance metrics

---

## API Endpoint Summary

| Endpoint | Method | Description | Status |
|----------|--------|-------------|--------|
| `/` or `/index.html` | GET | Monitoring UI | ✅ Working |
| `/api/schedule` | POST | Schedule single job | ✅ Working |
| `/api/schedule/batch` | POST | Schedule batch jobs | ✅ Working |
| `/api/jobs/{jobId}` | GET | Get job status | ✅ Working |
| `/api/health` | GET | Health check | ✅ Working |
| `/api/summary` | GET | Dashboard statistics | ✅ Working |
| `/api/jobs` | GET | List scheduled jobs | ✅ Working |
| `/api/history` | GET | Execution history | ⏳ Empty (TODO) |

---

## Testing the UI

### 1. Start Quarkus
```bash
mvn quarkus:dev
```

### 2. Schedule Test Jobs
```bash
# Schedule a job for 1 minute from now
curl -X POST http://localhost:8080/api/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test-'$(date +%s)'",
    "scheduledTime": "'$(date -u -d "+1 minute" +"%Y-%m-%dT%H:%M:%S")'",
    "jobType": "test_job",
    "phone": "+8801712345678",
    "message": "Test message",
    "orderId": "ORD-123"
  }'
```

### 3. Open UI
```
http://localhost:8080/
```

### 4. Verify
- ✅ Summary shows scheduled count
- ✅ Middle panel displays the test job
- ✅ Job details include phone, message, orderId
- ✅ Next fire time is displayed
- ✅ Status badge shows NORMAL

---

## Files Modified/Created

### Created
- `src/main/resources/META-INF/resources/index.html` - UI frontend
- `src/main/java/com/telcobright/scheduler/resource/MonitoringResource.java` - UI backend API

### Modified
- `src/main/java/com/telcobright/scheduler/InfiniteSchedulerService.java` - Added Scheduler producer

---

## Code Examples

### Query Scheduled Jobs
```java
@GET
@Path("/jobs")
public Map<String, Object> getJobs(
        @QueryParam("limit") @DefaultValue("20") int limit,
        @QueryParam("offset") @DefaultValue("0") int offset) {

    Set<JobKey> allJobKeys = quartzScheduler.getJobKeys(GroupMatcher.anyGroup());

    // Sort by next fire time, apply pagination
    List<Map<String, Object>> jobs = jobKeysList.stream()
        .skip(offset)
        .limit(limit)
        .map(this::buildJobInfo)
        .collect(Collectors.toList());

    return Map.of("jobs", jobs);
}
```

### Build Job Info
```java
private Map<String, Object> buildJobInfo(JobKey jobKey) {
    JobDetail jobDetail = quartzScheduler.getJobDetail(jobKey);

    Map<String, Object> jobInfo = new HashMap<>();
    jobInfo.put("jobName", jobDetail.getKey().getName());

    // Get trigger info
    List<? extends Trigger> triggers = quartzScheduler.getTriggersOfJob(jobKey);
    if (!triggers.isEmpty()) {
        jobInfo.put("nextFireTime", triggers.get(0).getNextFireTime().getTime());
    }

    // Get job parameters
    JobDataMap dataMap = jobDetail.getJobDataMap();
    Map<String, Object> additionalData = new HashMap<>();
    for (String key : dataMap.getKeys()) {
        additionalData.put(key, dataMap.get(key));
    }
    jobInfo.put("additionalData", additionalData);

    return jobInfo;
}
```

---

## Deployment

### Development Mode (with UI)
```bash
mvn quarkus:dev
```
Access at: `http://localhost:8080/`

### Production Build
```bash
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```
Access at: `http://localhost:8080/`

### Docker Deployment
```dockerfile
FROM quay.io/quarkus/quarkus-micro-image:2.0
COPY target/quarkus-app /work
EXPOSE 8080
CMD ["java", "-jar", "/work/quarkus-app/quarkus-run.jar"]
```

---

## Benefits

✅ **Real-time Monitoring** - Live view of scheduled jobs
✅ **User-Friendly** - Clean, intuitive interface
✅ **Pagination** - Handles large job lists efficiently
✅ **Auto-refresh** - Always up-to-date (5s interval)
✅ **Detailed View** - Modal popup for all job parameters
✅ **Zero Configuration** - Works out of the box with Quarkus
✅ **Static Content** - No separate frontend server needed

---

**Status:** ✅ UI Integration Complete
**Next:** Test with real jobs and consider adding history tracking
