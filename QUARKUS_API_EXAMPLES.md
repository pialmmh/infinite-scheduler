# Infinite Scheduler - API Examples

## Prerequisites

- Quarkus application running on `http://localhost:8080`
- MySQL database accessible at `127.0.0.1:3306`
- Kafka running at `localhost:9092` (if using Kafka publisher)
- Redis running at `127.0.0.1:6379` (if using Redis publisher)

---

## Starting the Application

```bash
# Development mode (with hot reload)
mvn quarkus:dev

# Production mode
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

---

## API Endpoints

### 1. Health Check

```bash
curl http://localhost:8080/api/health
```

**Response:**
```json
{
  "status": "UP",
  "service": "Infinite Scheduler"
}
```

---

### 2. Schedule Single Job

**Endpoint:** `POST /api/schedule`

**Example 1: SMS Job**
```bash
curl -X POST http://localhost:8080/api/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "id": "sms-123",
    "scheduledTime": "2025-10-17T10:30:00",
    "jobType": "sms_send",
    "phone": "+8801712345678",
    "message": "Your order is ready for pickup!"
  }'
```

**Example 2: Email Job**
```bash
curl -X POST http://localhost:8080/api/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "id": "email-456",
    "scheduledTime": "2025-10-17T11:00:00",
    "jobType": "email_send",
    "to": "user@example.com",
    "subject": "Welcome to Our Service",
    "body": "Thank you for signing up!"
  }'
```

**Example 3: Notification Job with Custom Fields**
```bash
curl -X POST http://localhost:8080/api/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "id": "notif-789",
    "scheduledTime": "2025-10-17T12:00:00",
    "jobType": "push_notification",
    "jobName": "Order Confirmation",
    "userId": "USER-12345",
    "title": "Order Confirmed",
    "message": "Your order #ORD-789 has been confirmed",
    "orderId": "ORD-789",
    "priority": "high"
  }'
```

**Response:**
```json
{
  "success": true,
  "jobId": "sms-123",
  "scheduledTime": "2025-10-17T10:30:00",
  "message": "Job scheduled successfully"
}
```

**Error Response (Missing Required Field):**
```json
{
  "success": false,
  "message": "Job 'jobType' is required"
}
```

---

### 3. Schedule Batch Jobs

**Endpoint:** `POST /api/schedule/batch`

```bash
curl -X POST http://localhost:8080/api/schedule/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "id": "sms-1",
      "scheduledTime": "2025-10-17T10:30:00",
      "jobType": "sms_send",
      "phone": "+8801712345678",
      "message": "Order confirmed!"
    },
    {
      "id": "sms-2",
      "scheduledTime": "2025-10-17T10:31:00",
      "jobType": "sms_send",
      "phone": "+8801798765432",
      "message": "Order shipped!"
    },
    {
      "id": "email-1",
      "scheduledTime": "2025-10-17T11:00:00",
      "jobType": "email_send",
      "to": "user1@example.com",
      "subject": "Order Update",
      "body": "Your order has been shipped"
    }
  ]'
```

**Response:**
```json
{
  "success": true,
  "scheduled": 3,
  "failed": 0,
  "message": "Scheduled 3 out of 3 jobs successfully"
}
```

---

### 4. Get Job Status

**Endpoint:** `GET /api/jobs/{jobId}`

```bash
curl http://localhost:8080/api/jobs/sms-123
```

**Response:**
```json
{
  "jobId": "sms-123",
  "message": "Status lookup not yet fully implemented"
}
```

**Error Response (Job Not Found):**
```json
{
  "success": false,
  "message": "Job not found: sms-999"
}
```

---

## Job Data Structure

### Required Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique job identifier (must be unique) |
| `scheduledTime` | String (ISO 8601) | When to execute the job (e.g., "2025-10-17T10:30:00") |
| `jobType` | String | Topic/routing name for the job (e.g., "sms_send", "email_send") |

### Optional Fields

| Field | Type | Description |
|-------|------|-------------|
| `jobName` | String | Display name for the job (auto-generated if not provided) |

### Custom Fields

Any additional fields can be added to the job data map. They will be:
- Stored as JSON in the `job_data_json` column
- Published to the topic when the job executes

**Examples of custom fields:**
- SMS: `phone`, `message`, `senderId`, `campaignId`
- Email: `to`, `subject`, `body`, `from`, `attachments`
- Notification: `userId`, `title`, `message`, `priority`, `icon`

---

## Testing Workflow

### 1. Schedule a job for 1 minute from now

```bash
# Calculate timestamp (Linux/Mac)
SCHEDULED_TIME=$(date -u -d "+1 minute" +"%Y-%m-%dT%H:%M:%S")

curl -X POST http://localhost:8080/api/schedule \
  -H "Content-Type: application/json" \
  -d "{
    \"id\": \"test-$(date +%s)\",
    \"scheduledTime\": \"$SCHEDULED_TIME\",
    \"jobType\": \"test_job\",
    \"testData\": \"This is a test job\"
  }"
```

### 2. Monitor logs

```bash
# Watch the application logs
tail -f logs/infinite-scheduler.log
```

You should see:
1. Job saved to repository
2. Lookahead fetcher picks up the job
3. Job scheduled to Quartz
4. Job executes at scheduled time
5. Job published to Kafka/Redis topic

---

## Kafka Consumer Example

To consume jobs from Kafka topics:

```bash
# Install kcat (kafkacat)
# Ubuntu/Debian: sudo apt-get install kafkacat
# Mac: brew install kcat

# Listen to sms_send topic
kcat -C -b localhost:9092 -t sms_send
```

**Sample Output:**
```json
{
  "id": "sms-123",
  "scheduledTime": "2025-10-17T10:30:00",
  "jobType": "sms_send",
  "phone": "+8801712345678",
  "message": "Your order is ready for pickup!"
}
```

---

## Redis Consumer Example

To consume jobs from Redis streams:

```bash
# Redis CLI
redis-cli

# Read from sms_send stream
XREAD BLOCK 0 STREAMS sms_send 0

# Read with consumer group
XGROUP CREATE sms_send sms-workers $ MKSTREAM
XREADGROUP GROUP sms-workers consumer-1 BLOCK 0 STREAMS sms_send >
```

---

## Configuration

### Switch Between Kafka and Redis

Edit `application.yml`:

```yaml
scheduler:
  publisher: kafka    # or "redis"
```

### Adjust Fetch Interval

```yaml
scheduler:
  fetch-interval: 25          # Check every 25 seconds
  lookahead-window: 30        # Look 30 seconds ahead
```

---

## Database Tables

Jobs are stored in daily partitioned tables:

```sql
-- Example: Jobs scheduled for 2025-10-17
SELECT * FROM scheduled_jobs_2025_10_17;

-- Columns:
-- id VARCHAR(255) PRIMARY KEY
-- scheduled_time DATETIME NOT NULL
-- job_type VARCHAR(100) NOT NULL
-- job_name VARCHAR(255)
-- job_data_json TEXT
-- scheduled BOOLEAN DEFAULT FALSE
```

---

## Troubleshooting

### Job Not Scheduled

1. Check if job is in database:
   ```sql
   SELECT * FROM scheduled_jobs_2025_10_17 WHERE id = 'sms-123';
   ```

2. Check if `scheduled` flag is false
3. Check if `scheduled_time` is within lookahead window

### Job Not Published

1. Check Kafka/Redis connection
2. Verify publisher type in `application.yml`
3. Check application logs for errors

### Database Connection Error

Verify MySQL credentials in `application.yml`:
```yaml
datasource:
  host: 127.0.0.1
  port: 3306
  database: scheduler
  username: root
  password: 123456
```

---

## Advanced Examples

### Schedule Job with Retry Information

```bash
curl -X POST http://localhost:8080/api/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "id": "sms-retry-123",
    "scheduledTime": "2025-10-17T10:35:00",
    "jobType": "sms_send",
    "phone": "+8801712345678",
    "message": "Your order is ready!",
    "retryCount": 1,
    "originalJobId": "sms-123",
    "retryReason": "Network timeout"
  }'
```

### Schedule Job with Priority

```bash
curl -X POST http://localhost:8080/api/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "id": "urgent-email-789",
    "scheduledTime": "2025-10-17T10:32:00",
    "jobType": "email_send",
    "jobName": "Urgent: Payment Failed",
    "priority": "critical",
    "to": "admin@example.com",
    "subject": "URGENT: Payment Processing Failed",
    "body": "Payment failed for order #ORD-789"
  }'
```

---

**Last Updated:** 2025-10-17
