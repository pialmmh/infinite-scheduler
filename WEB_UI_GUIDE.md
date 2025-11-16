# Infinite Scheduler - Web UI & Monitoring Guide

## ✅ YES! The Web UI is Fully Functional

The infinite-scheduler includes a **complete web-based monitoring UI** with real-time job tracking and statistics.

---

## 🌐 Starting the Web UI

### Option 1: With Multi-App Scheduler
```bash
java -cp target/infinite-scheduler-1.0.0.jar \
  com.telcobright.scheduler.examples.MultiAppSchedulerWithUI \
  -Ddb.host=127.0.0.1 \
  -Ddb.port=3306 \
  -Ddb.name=scheduler \
  -Ddb.user=root \
  -Ddb.password=123456 \
  -Dweb.port=7070
```

### Option 2: Standalone Web UI
```java
// In your application
DataSource dataSource = createDataSource();
JobStatusApi api = new JobStatusApi(dataSource);
api.start(7070);  // Start on port 7070
```

**Access the UI:**
- **Web UI**: http://localhost:7070/index.html
- **REST API**: http://localhost:7070/api/*

---

## 📊 Web UI Features

### Dashboard View

The UI provides a **3-column responsive dashboard**:

#### Left Column: Statistics Cards
- **Total Jobs** - Count of all jobs
- **Scheduled** - Currently queued jobs
- **Completed** - Successfully finished jobs
- **Failed** - Jobs that encountered errors
- **Running** - Currently executing jobs

#### Middle Column: Scheduled Jobs Panel
- Real-time list of upcoming jobs
- Shows next 100 scheduled jobs
- Auto-refreshes every 5 seconds
- Displays:
  - Job ID
  - Job Name
  - App Name (sms, sipcall, payment_gateway)
  - Scheduled Time
  - Status
  - Queue Type & Topic

#### Right Column: Job History Panel
- Recent job execution history
- Last 100 completed/failed jobs
- Auto-refreshes every 5 seconds
- Displays:
  - Job ID
  - Job Name
  - App Name
  - Execution Time
  - Duration (ms)
  - Status (Success/Failed)
  - Error messages (if failed)

---

## 🔌 REST API Endpoints

### 1. Get Scheduled Jobs
```http
GET /api/jobs/scheduled
```

**Response:**
```json
[
  {
    "id": 123,
    "jobId": "sms-job-1",
    "jobName": "Send SMS",
    "jobGroup": "DEFAULT",
    "appName": "sms",
    "entityId": "ent-456",
    "scheduledTime": "2025-11-14 16:30:00",
    "status": "SCHEDULED",
    "createdAt": "2025-11-14 16:00:00",
    "queueType": "CONSOLE",
    "topicName": "sms-notifications",
    "brokerAddress": ""
  }
]
```

### 2. Get Job History
```http
GET /api/jobs/history
```

**Response:**
```json
[
  {
    "id": 456,
    "jobId": "sms-job-2",
    "jobName": "Send SMS",
    "appName": "sms",
    "scheduledTime": "2025-11-14 16:00:00",
    "startedAt": "2025-11-14 16:00:01",
    "completedAt": "2025-11-14 16:00:02",
    "status": "COMPLETED",
    "executionDurationMs": 1234,
    "errorMessage": null,
    "queueType": "CONSOLE"
  }
]
```

### 3. Get Job Statistics
```http
GET /api/jobs/stats
```

**Response:**
```json
{
  "totalJobs": 1245,
  "statusCounts": {
    "SCHEDULED": 50,
    "RUNNING": 5,
    "COMPLETED": 1150,
    "FAILED": 40
  }
}
```

---

## 🎨 UI Screenshots (What You'll See)

### Header Section
```
╔════════════════════════════════════════════════════════════════╗
║              INFINITE SCHEDULER - JOB MONITOR                  ║
║            Real-time Job Scheduling & Execution                ║
╚════════════════════════════════════════════════════════════════╝
```

### Statistics Cards
```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ TOTAL JOBS   │  │  SCHEDULED   │  │  COMPLETED   │
│     1,245    │  │      50      │  │    1,150     │
└──────────────┘  └──────────────┘  └──────────────┘

┌──────────────┐  ┌──────────────┐
│   FAILED     │  │   RUNNING    │
│      40      │  │       5      │
└──────────────┘  └──────────────┘
```

### Scheduled Jobs Panel
```
╔══════════════════════════════════════════════════════════╗
║  📅 SCHEDULED JOBS                           [50]       ║
╠══════════════════════════════════════════════════════════╣
║                                                          ║
║  📱 sms-job-123                                         ║
║      App: sms                                           ║
║      Schedule: 2025-11-14 16:30:00                     ║
║      Status: SCHEDULED                                  ║
║      Queue: CONSOLE → sms-notifications                ║
║                                                          ║
║  📞 sipcall-job-456                                     ║
║      App: sipcall                                       ║
║      Schedule: 2025-11-14 16:35:00                     ║
║      Status: SCHEDULED                                  ║
║      Queue: CONSOLE → sipcall-queue                    ║
║                                                          ║
╚══════════════════════════════════════════════════════════╝
```

### Job History Panel
```
╔══════════════════════════════════════════════════════════╗
║  📊 JOB HISTORY                              [100]      ║
╠══════════════════════════════════════════════════════════╣
║                                                          ║
║  ✅ payment-job-789                                     ║
║      App: payment_gateway                               ║
║      Completed: 2025-11-14 16:29:45                    ║
║      Duration: 234ms                                    ║
║      Status: SUCCESS                                    ║
║                                                          ║
║  ❌ sms-job-012                                         ║
║      App: sms                                           ║
║      Completed: 2025-11-14 16:28:30                    ║
║      Duration: 567ms                                    ║
║      Status: FAILED                                     ║
║      Error: Network timeout                            ║
║                                                          ║
╚══════════════════════════════════════════════════════════╝
```

---

## 🔄 Auto-Refresh

- **Scheduled Jobs**: Refreshes every 5 seconds
- **Job History**: Refreshes every 5 seconds
- **Statistics**: Updates every 5 seconds

No manual refresh needed - watch jobs flow through in real-time!

---

## 🧪 Testing the UI

### 1. Start the Scheduler with UI
```bash
mvn clean package -DskipTests

java -cp target/infinite-scheduler-1.0.0.jar \
  com.telcobright.scheduler.examples.MultiAppSchedulerWithUI
```

You'll see:
```
✅ Registered SMS application → Console output: sms-notifications
✅ Registered SIPCall application → Console output: sipcall-queue
✅ Registered Payment Gateway application → Console output: payment-transactions
✅ All application schedulers started
🌐 Web UI: http://localhost:7070/index.html
⏰ Press Ctrl+C to stop

╔════════════════════════════════════════════════════════════════════╗
║  ✅ INFINITE SCHEDULER STARTED SUCCESSFULLY                        ║
║                                                                    ║
║  Multi-App Architecture: 3 apps (SMS, SIPCall, Payment)           ║
║  Queue Type: CONSOLE (Mock for testing)                           ║
║  Web UI: http://localhost:7070/index.html                         ║
║  REST API: http://localhost:7070/api/*                            ║
║  Database: 127.0.0.1:3306/scheduler                               ║
║  Status: RUNNING - Ready to schedule jobs                         ║
╚════════════════════════════════════════════════════════════════════╝
```

### 2. Open Browser
Navigate to: **http://localhost:7070/index.html**

### 3. Watch Jobs Flow
The demo automatically creates:
- **SMS jobs**: Every 5 seconds
- **SIP Call jobs**: Every 8 seconds
- **Payment jobs**: Every 12 seconds

Watch them appear in:
1. **Scheduled Jobs** panel (waiting to execute)
2. **Statistics** update in real-time
3. **Job History** panel (after execution)

---

## 📈 Monitoring Production Jobs

### Using curl to check API
```bash
# Get scheduled jobs
curl http://localhost:7070/api/jobs/scheduled | jq

# Get job history
curl http://localhost:7070/api/jobs/history | jq

# Get statistics
curl http://localhost:7070/api/jobs/stats | jq
```

### Integration with Monitoring Tools

#### Prometheus Metrics (Future Enhancement)
```java
// Expose metrics endpoint
app.get("/metrics", ctx -> {
    Map<String, Object> metrics = new HashMap<>();
    metrics.put("jobs_scheduled", getScheduledCount());
    metrics.put("jobs_completed", getCompletedCount());
    metrics.put("jobs_failed", getFailedCount());
    metrics.put("jobs_running", getRunningCount());
    ctx.json(metrics);
});
```

#### Grafana Dashboard
Create dashboards using the REST API endpoints as data sources.

---

## 🎯 Customizing the UI

### Changing Port
```bash
# Use custom port
java -cp target/infinite-scheduler-1.0.0.jar \
  com.telcobright.scheduler.examples.MultiAppSchedulerWithUI \
  -Dweb.port=8080
```

### Adding Custom Endpoints
```java
JobStatusApi api = new JobStatusApi(dataSource);
api.start(7070);

// Add custom endpoint
api.getApp().get("/api/custom", ctx -> {
    // Your custom logic
});
```

### Styling
The UI uses:
- **Gradient Header**: Purple gradient (customizable in CSS)
- **Responsive Grid**: 3-column layout
- **Color Scheme**:
  - Primary: #667eea (purple)
  - Success: Green badges
  - Error: Red badges
  - Background: #f5f7fa

Edit `/src/main/resources/public/index.html` to customize.

---

## 🔧 Troubleshooting

### UI Not Loading
```bash
# Check if server started
curl http://localhost:7070/api/jobs/stats

# Expected: JSON response
# If error: Check port is not in use
```

### No Jobs Showing
1. **Check Database Connection**: Verify MySQL credentials
2. **Check Tables Exist**: Look for `*_job_execution_history` tables
3. **Check Scheduler Running**: Jobs won't appear if scheduler isn't started

### API Errors
```bash
# Check logs
tail -f logs/infinite-scheduler.log

# Common issues:
# - Database connection failed
# - Tables don't exist (run scheduler first to create them)
# - Wrong credentials
```

---

## 📊 Performance

- **Lightweight**: Javalin framework (fast HTTP server)
- **Efficient Queries**: Limited to 100 most recent jobs
- **Auto-refresh**: Client-side polling (5s interval)
- **Responsive**: Works on desktop, tablet, mobile

---

## 🚀 Production Deployment

### Standalone UI Server
```java
public class MonitoringServer {
    public static void main(String[] args) {
        // Create datasource to your production DB
        DataSource dataSource = createProductionDataSource();

        // Start monitoring UI only (no scheduler)
        JobStatusApi api = new JobStatusApi(dataSource);
        api.start(7070);

        System.out.println("Monitoring UI started on port 7070");
    }
}
```

### Reverse Proxy Setup (Nginx)
```nginx
location /scheduler/ {
    proxy_pass http://localhost:7070/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection 'upgrade';
    proxy_set_header Host $host;
    proxy_cache_bypass $http_upgrade;
}
```

Access at: `http://yourdomain.com/scheduler/`

---

## ✅ Summary

**YES, the Web UI is fully functional!**

✅ **Real-time Dashboard** - Live job monitoring
✅ **REST API** - 3 comprehensive endpoints
✅ **Auto-refresh** - No manual refresh needed
✅ **Multi-app Support** - Track all apps in one place
✅ **Job History** - See execution results
✅ **Statistics** - Quick overview of system health
✅ **Responsive Design** - Works on all devices

**Start it now:**
```bash
java -cp target/infinite-scheduler-1.0.0.jar \
  com.telcobright.scheduler.examples.MultiAppSchedulerWithUI
```

Then open: **http://localhost:7070/index.html**
