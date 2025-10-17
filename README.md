# Infinite Scheduler

**High-Performance Job Scheduling Microservice**

Schedule millions to billions of jobs with precision and reliability using our Quarkus-based distributed job scheduler with Kafka/Redis publishing.

---

## Quick Start

### Start the Scheduler

```bash
# Configure database and messaging
export MYSQL_HOST=127.0.0.1
export MYSQL_DATABASE=scheduler
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Run with Quarkus
mvn quarkus:dev
```

### Schedule Your First Job

```bash
curl -X POST http://localhost:8080/api/scheduler/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "id": "job-001",
    "scheduledTime": "2025-10-18T15:30:00",
    "jobType": "sms_send",
    "jobName": "promotional",
    "phoneNumber": "+1234567890",
    "message": "Hello World!"
  }'
```

**Response:**
```json
{
  "status": "success",
  "message": "Job scheduled successfully",
  "jobId": "job-001"
}
```

The job will execute at exactly `15:30:00`, publishing to Kafka topic `sms_send-promotional` (or Redis stream `sms_send-promotional`).

---

## Table of Contents

- [Features](#features)
- [API Documentation](#api-documentation)
  - [Schedule Single Job](#schedule-single-job)
  - [Schedule Batch Jobs](#schedule-batch-jobs)
  - [Get Job Status](#get-job-status)
  - [Monitoring Endpoints](#monitoring-endpoints)
- [Configuration](#configuration)
- [Job Publishing](#job-publishing)
- [Examples](#examples)
- [Web UI](#web-ui)
- [Performance](#performance)
- [Deployment](#deployment)
- [Architecture](#architecture)

---

## Features

✅ **REST API**: Schedule and monitor jobs via HTTP
✅ **Massive Scale**: Handle billions of scheduled jobs efficiently
✅ **Dual Publishers**: Kafka and Redis support
✅ **Database-Level Filtering**: 90% reduction in data transfer
✅ **Web UI**: Real-time monitoring dashboard
✅ **UTC Timezone**: Consistent global scheduling
✅ **Automatic Cleanup**: Time-based partition management
✅ **High Availability**: Quartz RAM-based scheduling with MySQL persistence

---

## API Documentation

Base URL: `http://localhost:8080/api/scheduler`

### Schedule Single Job

**Endpoint:** `POST /api/scheduler/schedule`

**Request Body:**
```json
{
  "id": "unique-job-id",
  "scheduledTime": "2025-10-18T15:30:00",
  "jobType": "sms_send",
  "jobName": "promotional",
  "phoneNumber": "+1234567890",
  "message": "Special offer!"
}
```

**Required Fields:**
- `id` (string): Unique job identifier
- `scheduledTime` (ISO 8601): When to execute (UTC timezone)
- `jobType` (string): Job category (e.g., "sms_send", "email_send")
- `jobName` (string): Job name (e.g., "promotional", "transactional")

**Optional Fields:**
- Any additional fields (stored in `jobDataJson`)

**Response:**
```json
{
  "status": "success",
  "message": "Job scheduled successfully",
  "jobId": "unique-job-id"
}
```

**Error Response (400 Bad Request):**
```json
{
  "status": "error",
  "message": "Job 'jobName' is required"
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:8080/api/scheduler/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "id": "sms-12345",
    "scheduledTime": "2025-10-18T10:30:00",
    "jobType": "sms_send",
    "jobName": "promotional",
    "phoneNumber": "+1234567890",
    "message": "Don'\''t miss our 50% off sale!",
    "campaignId": "SUMMER2025"
  }'
```

---

### Schedule Batch Jobs

**Endpoint:** `POST /api/scheduler/schedule/batch`

**Request Body:**
```json
[
  {
    "id": "job-001",
    "scheduledTime": "2025-10-18T10:00:00",
    "jobType": "sms_send",
    "jobName": "promotional",
    "phoneNumber": "+1111111111",
    "message": "Message 1"
  },
  {
    "id": "job-002",
    "scheduledTime": "2025-10-18T10:05:00",
    "jobType": "sms_send",
    "jobName": "promotional",
    "phoneNumber": "+2222222222",
    "message": "Message 2"
  }
]
```

**Response:**
```json
{
  "status": "success",
  "scheduled": 2,
  "total": 2,
  "message": "Scheduled 2 out of 2 jobs successfully"
}
```

**Partial Success Example:**
```json
{
  "status": "partial",
  "scheduled": 8,
  "total": 10,
  "message": "Scheduled 8 out of 10 jobs successfully"
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:8080/api/scheduler/schedule/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "id": "batch-job-1",
      "scheduledTime": "2025-10-18T14:00:00",
      "jobType": "email_send",
      "jobName": "newsletter",
      "email": "user1@example.com",
      "subject": "Weekly Newsletter"
    },
    {
      "id": "batch-job-2",
      "scheduledTime": "2025-10-18T14:05:00",
      "jobType": "email_send",
      "jobName": "newsletter",
      "email": "user2@example.com",
      "subject": "Weekly Newsletter"
    }
  ]'
```

---

### Get Job Status

**Endpoint:** `GET /api/scheduler/status/{jobId}`

**Response:**
```json
{
  "jobId": "job-001",
  "message": "Status lookup not yet fully implemented"
}
```

*Note: Full status tracking coming in next release.*

---

### Monitoring Endpoints

#### Get System Statistics

**Endpoint:** `GET /api/monitoring/stats`

**Response:**
```json
{
  "totalJobs": 1523456,
  "activeJobs": 287543,
  "completedJobs": 1235913,
  "lookaheadFetcher": {
    "status": "running",
    "lastFetchTime": "2025-10-18T10:29:55Z",
    "lastFetchCount": 1523,
    "intervalSeconds": 25,
    "windowSeconds": 30
  },
  "publishers": {
    "type": "kafka",
    "status": "healthy"
  }
}
```

**cURL Example:**
```bash
curl http://localhost:8080/api/monitoring/stats | jq
```

#### Health Check

**Endpoint:** `GET /api/monitoring/health`

**Response:**
```json
{
  "status": "UP",
  "checks": {
    "database": "UP",
    "quartzScheduler": "UP",
    "publisher": "UP",
    "lookaheadFetcher": "UP"
  },
  "uptime": "2d 14h 23m"
}
```

---

## Configuration

### application.yml

```yaml
# Database Configuration
datasource:
  host: 127.0.0.1
  port: 3306
  database: scheduler
  username: root
  password: ${MYSQL_PASSWORD}

# Repository Configuration
repository:
  table-prefix: scheduled_jobs

# Scheduler Configuration
scheduler:
  fetch-interval: 25        # Fetch every 25 seconds
  lookahead-window: 30      # Look 30 seconds ahead
  publisher: kafka          # or redis

# Kafka Configuration (if using Kafka)
mp.messaging.outgoing.job-output:
  connector: smallrye-kafka
  bootstrap.servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  value.serializer: org.apache.kafka.common.serialization.StringSerializer

# Redis Configuration (if using Redis)
quarkus.redis.hosts: redis://${REDIS_HOST:localhost}:${REDIS_PORT:6379}
```

### Environment Variables

```bash
# Required
export MYSQL_HOST=127.0.0.1
export MYSQL_DATABASE=scheduler
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=your-password

# Kafka (if using Kafka publisher)
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Redis (if using Redis publisher)
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Optional
export SCHEDULER_FETCH_INTERVAL=25
export SCHEDULER_LOOKAHEAD_WINDOW=30
export SCHEDULER_PUBLISHER=kafka  # or redis
```

---

## Job Publishing

When a job executes, it's published to Kafka or Redis with the following details:

### Queue/Topic Naming

**Pattern:** `{jobType}-{jobName}`

**Examples:**
- `sms_send-promotional`
- `sms_send-transactional`
- `email_send-newsletter`
- `notification_send-alert`

### Kafka Publishing

**Topic:** `{jobType}-{jobName}`
**Key:** Job ID
**Value:** Complete job data as JSON

**Example Message:**
```json
{
  "id": "job-12345",
  "scheduledTime": "2025-10-18T10:30:00",
  "jobType": "sms_send",
  "jobName": "promotional",
  "phoneNumber": "+1234567890",
  "message": "Hello World!",
  "campaignId": "SUMMER2025"
}
```

**Consumer Example (Python):**
```python
from kafka import KafkaConsumer
import json

consumer = KafkaConsumer(
    'sms_send-promotional',
    bootstrap_servers='localhost:9092',
    value_deserializer=lambda m: json.loads(m.decode('utf-8'))
)

for message in consumer:
    job_data = message.value
    print(f"Processing SMS job: {job_data['id']}")
    send_sms(job_data['phoneNumber'], job_data['message'])
```

### Redis Publishing

**Stream:** `{jobType}-{jobName}`
**Fields:**
- `data`: Complete job data as JSON
- `jobId`: Job identifier
- `jobType`: Job type

**Consumer Example (Node.js):**
```javascript
const redis = require('redis');
const client = redis.createClient();

async function consumeJobs() {
  const results = await client.xRead(
    { key: 'sms_send-promotional', id: '$' },
    { BLOCK: 5000, COUNT: 10 }
  );

  for (const result of results) {
    const jobData = JSON.parse(result.message.data);
    console.log(`Processing job: ${jobData.id}`);
    await sendSMS(jobData.phoneNumber, jobData.message);
  }
}

consumeJobs();
```

---

## Examples

### Example 1: Schedule SMS

```bash
curl -X POST http://localhost:8080/api/scheduler/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "id": "sms-001",
    "scheduledTime": "2025-10-18T14:30:00",
    "jobType": "sms_send",
    "jobName": "promotional",
    "phoneNumber": "+1234567890",
    "message": "Flash sale: 50% off all items!",
    "senderId": "STORE",
    "priority": "high"
  }'
```

### Example 2: Schedule Email

```bash
curl -X POST http://localhost:8080/api/scheduler/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "id": "email-001",
    "scheduledTime": "2025-10-19T09:00:00",
    "jobType": "email_send",
    "jobName": "newsletter",
    "email": "subscriber@example.com",
    "subject": "Weekly Newsletter",
    "templateId": "newsletter-template-1",
    "variables": {
      "userName": "John Doe",
      "unsubscribeLink": "https://example.com/unsubscribe"
    }
  }'
```

### Example 3: Schedule Push Notification

```bash
curl -X POST http://localhost:8080/api/scheduler/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "id": "push-001",
    "scheduledTime": "2025-10-18T16:00:00",
    "jobType": "notification_send",
    "jobName": "alert",
    "userId": "user-12345",
    "title": "Order Shipped",
    "body": "Your order #12345 has been shipped!",
    "data": {
      "orderId": "12345",
      "trackingUrl": "https://tracking.example.com/12345"
    }
  }'
```

### Example 4: Batch Schedule (1000 Jobs)

```python
import requests
import json
from datetime import datetime, timedelta

def schedule_batch_jobs():
    base_time = datetime.utcnow() + timedelta(hours=1)
    jobs = []

    for i in range(1000):
        scheduled_time = base_time + timedelta(seconds=i * 10)
        jobs.append({
            "id": f"job-{i:04d}",
            "scheduledTime": scheduled_time.isoformat(),
            "jobType": "sms_send",
            "jobName": "campaign",
            "phoneNumber": f"+1{i:010d}",
            "message": f"Hello Customer {i}!"
        })

    response = requests.post(
        "http://localhost:8080/api/scheduler/schedule/batch",
        json=jobs
    )

    print(f"Response: {response.json()}")

schedule_batch_jobs()
```

---

## Web UI

Access the monitoring dashboard at: **http://localhost:8080/**

**Features:**
- Real-time job statistics
- Lookahead fetcher status
- System health indicators
- Recent job executions
- Auto-refresh every 5 seconds

**Screenshot:**
```
┌────────────────────────────────────────────────┐
│      Infinite Scheduler - Dashboard            │
├────────────────────────────────────────────────┤
│ Status: ● RUNNING                              │
│                                                 │
│ Jobs Statistics:                                │
│   Total Scheduled:     1,523,456               │
│   Active Jobs:           287,543               │
│   Completed Jobs:      1,235,913               │
│                                                 │
│ Lookahead Fetcher:                              │
│   Status:              ● Running                │
│   Last Fetch:          2025-10-18 10:29:55 UTC │
│   Jobs Fetched:        1,523                    │
│   Interval:            25 seconds               │
│   Window:              30 seconds               │
│                                                 │
│ Publisher:                                      │
│   Type:                Kafka                    │
│   Status:              ● Healthy                │
└────────────────────────────────────────────────┘
```

---

## Performance

### Capacity

| Metric | Single Instance | 4 Instances |
|--------|----------------|-------------|
| **Jobs/second** | 10,000 | 40,000 |
| **Active jobs (Quartz)** | 300,000 | 1,200,000 |
| **Memory usage** | ~2 GB | ~8 GB |
| **Database queries/min** | 2.4 (lookahead) | 9.6 (lookahead) |

### Database-Level Filtering

**Before (Java filtering):**
```
Fetched: 3,000,000 rows
Needed:    300,000 rows
Waste:   2,700,000 rows (90%)
Memory:     ~270 MB wasted
```

**After (Database filtering):**
```
Fetched: 300,000 rows
Needed:  300,000 rows
Waste:         0 rows
Memory:   ~27 MB
```

**Result:** **90% reduction** in data transfer and memory usage

---

## Deployment

### Docker Deployment

**Dockerfile:**
```dockerfile
FROM quay.io/quarkus/ubi-quarkus-native-image:22.3-java17 AS build
COPY --chown=quarkus:quarkus . /code/
WORKDIR /code
RUN mvn clean package -DskipTests

FROM registry.access.redhat.com/ubi9/openjdk-21:1.18
COPY --from=build /code/target/quarkus-app /deployments/
EXPOSE 8080
CMD ["java", "-jar", "/deployments/quarkus-run.jar"]
```

**Docker Compose:**
```yaml
version: '3.8'

services:
  infinite-scheduler:
    image: infinite-scheduler:latest
    ports:
      - "8080:8080"
    environment:
      MYSQL_HOST: mysql
      MYSQL_DATABASE: scheduler
      MYSQL_USERNAME: root
      MYSQL_PASSWORD: password
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      SCHEDULER_PUBLISHER: kafka
    depends_on:
      - mysql
      - kafka

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: scheduler
    volumes:
      - mysql_data:/var/lib/mysql

  kafka:
    image: confluentinc/cp-kafka:latest
    ports:
      - "9092:9092"
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092

  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

volumes:
  mysql_data:
```

**Start:**
```bash
docker-compose up -d
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: infinite-scheduler
spec:
  replicas: 4
  selector:
    matchLabels:
      app: infinite-scheduler
  template:
    metadata:
      labels:
        app: infinite-scheduler
    spec:
      containers:
      - name: scheduler
        image: infinite-scheduler:latest
        ports:
        - containerPort: 8080
        env:
        - name: MYSQL_HOST
          value: "mysql-service"
        - name: MYSQL_DATABASE
          value: "scheduler"
        - name: MYSQL_USERNAME
          valueFrom:
            secretKeyRef:
              name: mysql-secret
              key: username
        - name: MYSQL_PASSWORD
          valueFrom:
            secretKeyRef:
              name: mysql-secret
              key: password
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka-service:9092"
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /api/monitoring/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /api/monitoring/health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
```

---

## Architecture

For detailed architecture documentation, see [ARCHITECTURE.md](ARCHITECTURE.md).

**High-Level Overview:**
```
Client (REST API)
      ↓
InfiniteSchedulerService (Quarkus)
      ↓
Split-Verse Repository (MySQL)
      ↓
Lookahead Fetcher (25s cycle)
      ↓
Quartz Scheduler (RAM)
      ↓
Job Execution
      ↓
Kafka/Redis Publisher
      ↓
Consumer Applications
```

---

## Requirements

- **Java**: 21+
- **MySQL**: 8.0+
- **Kafka** or **Redis**: Latest stable version
- **Memory**: Minimum 2 GB RAM

---

## Development

### Build

```bash
mvn clean compile
```

### Test

```bash
mvn test
```

### Run in Dev Mode

```bash
mvn quarkus:dev
```

**Live Reload:** Changes to Java files auto-reload

---

## Support & Documentation

- **Architecture**: [ARCHITECTURE.md](ARCHITECTURE.md)
- **Quarkus Migration**: [QUARKUS_MIGRATION_SUMMARY.md](QUARKUS_MIGRATION_SUMMARY.md)
- **API Examples**: [QUARKUS_API_EXAMPLES.md](QUARKUS_API_EXAMPLES.md)
- **UI Integration**: [UI_INTEGRATION.md](UI_INTEGRATION.md)

---

## License

This project is part of the TelcoBright suite of libraries.

---

**Built with** [Quarkus](https://quarkus.io/) | [Split-verse](https://github.com/pialmmh/split-verse) | [Quartz Scheduler](http://www.quartz-scheduler.org/)
