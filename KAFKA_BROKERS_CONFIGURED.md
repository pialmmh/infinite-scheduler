# Kafka Brokers Configuration - Summary

## ✅ Configuration Complete

The Infinite Scheduler is now configured as a **Quarkus application** with Kafka brokers:

```
10.10.199.20:9092
10.10.198.20:9092
10.10.197.20:9092
```

---

## 📁 Files Created/Modified

### 1. Configuration Files

#### `src/main/resources/application.properties`
Complete Quarkus configuration with:
- ✅ Kafka brokers (production cluster)
- ✅ MySQL datasource
- ✅ Scheduler settings
- ✅ Web UI configuration
- ✅ Profile-specific overrides (dev, prod, test)

**Production Brokers**:
```properties
%prod.kafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
```

### 2. Configuration Classes

#### `com.telcobright.scheduler.config.KafkaConfigProperties`
Quarkus @ConfigMapping for Kafka settings:
- Bootstrap servers
- Consumer configuration
- Producer configuration

#### `com.telcobright.scheduler.config.SchedulerConfigProperties`
Quarkus @ConfigMapping for scheduler settings:
- Kafka ingest configuration
- Repository settings
- Fetcher configuration
- Cleanup settings
- Quartz configuration
- Web UI settings

#### `com.telcobright.scheduler.config.KafkaIngestConfigFactory`
Factory bean for creating KafkaIngestConfig from Quarkus configuration.

### 3. Example Application

#### `com.telcobright.scheduler.examples.QuarkusSchedulerExample`
Complete Quarkus application demonstrating:
- Configuration injection via @Inject
- Multi-app scheduler with Kafka ingest
- SMS retry handler registration
- Production-ready setup

### 4. Documentation

#### `QUARKUS_CONFIGURATION_GUIDE.md`
Comprehensive guide covering:
- Profile-based configuration
- Environment variable overrides
- Multi-tenant setup
- Docker/LXC deployment
- Troubleshooting

---

## 🚀 Quick Start

### Development (Local Kafka)

```bash
# Run in dev mode
mvn quarkus:dev

# Access Web UI
open http://localhost:7070/index.html
```

### Production (Configured Brokers)

```bash
# Build
mvn clean package -DskipTests

# Run with production profile
java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=prod
```

**Expected Output**:
```
=============================================================
  INFINITE SCHEDULER - QUARKUS APPLICATION
=============================================================

📋 Configuration:
  Kafka Bootstrap Servers: 10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
  Consumer Group: infinite-scheduler-group
  Ingest Topic: Job_Schedule
  DLQ Topic: Job_Schedule_DLQ
  Kafka Ingest Enabled: true
  Web UI Port: 7070

✅ Kafka Ingest Configured:
   - Bootstrap Servers: 10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
   - Consumer Group: infinite-scheduler-group
   - Topic: Job_Schedule
   - DLQ Topic: Job_Schedule_DLQ

✅ Registered SMS Retry Handler:
   - App Name: sms_retry
   - Output Topic: SMS_Send
   - Kafka Brokers: 10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092

=============================================================
  ✅ SCHEDULER STARTED SUCCESSFULLY
=============================================================
  Web UI: http://0.0.0.0:7070/index.html
  Kafka Ingest: ENABLED
  Press Ctrl+C to stop
=============================================================
```

---

## 🔧 Configuration Profiles

### Default (Development)
```properties
kafka.bootstrap.servers=localhost:9092
scheduler.kafka.ingest.enabled=false
```

### Production
```properties
%prod.kafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
%prod.scheduler.kafka.ingest.enabled=true
```

### Test
```properties
%test.kafka.bootstrap.servers=localhost:9092
%test.scheduler.kafka.ingest.enabled=false
```

---

## 📊 Kafka Topics

### Input Topic: Job_Schedule
Schedule jobs by sending messages to this topic:

```json
{
  "appName": "sms_retry",
  "scheduledTime": "2025-11-14 20:30:00",
  "jobData": {
    "campaignTaskId": 12345,
    "createdOn": "2025-11-14 20:00:00",
    "retryTime": "2025-11-14 20:30:00",
    "retryAttempt": 1
  }
}
```

### Output Topic: SMS_Send
Job execution results published here:

```json
{
  "campaignTaskId": 12345,
  "createdOn": "2025-11-14 20:00:00",
  "scheduledRetryTime": "2025-11-14 20:30:00",
  "actualExecutionTime": "2025-11-14 20:30:02",
  "retryAttempt": 1
}
```

### DLQ Topic: Job_Schedule_DLQ
Failed messages sent here after max retries.

---

## 🐳 Docker Deployment

```bash
# Build Docker image
docker build -f src/main/docker/Dockerfile.jvm -t infinite-scheduler:1.0.0 .

# Run with production brokers
docker run \
  -e KAFKA_BOOTSTRAP_SERVERS="10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092" \
  -e QUARKUS_PROFILE=prod \
  -p 7070:7070 \
  infinite-scheduler:1.0.0
```

---

## 📦 LXC Deployment

```bash
# Copy JAR to container
lxc file push target/quarkus-app/quarkus-run.jar infinite-scheduler/app/

# Run in container
lxc exec infinite-scheduler -- java \
  -jar /app/quarkus-run.jar \
  -Dquarkus.profile=prod \
  -Dkafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
```

---

## 🔍 Verification

### 1. Check Kafka Connection

```bash
# Test broker connectivity
telnet 10.10.199.20 9092
telnet 10.10.198.20 9092
telnet 10.10.197.20 9092
```

### 2. Send Test Job

```bash
# Produce test message to Job_Schedule topic
kafka-console-producer.sh \
  --bootstrap-server 10.10.199.20:9092 \
  --topic Job_Schedule \
  --property "parse.key=true" \
  --property "key.separator=:"

# Enter message:
test-1:{"appName":"sms_retry","scheduledTime":"2025-11-14 21:00:00","jobData":{"campaignTaskId":99999,"createdOn":"2025-11-14 20:00:00","retryTime":"2025-11-14 21:00:00","retryAttempt":1}}
```

### 3. Monitor Logs

```bash
# Watch scheduler logs
tail -f logs/infinite-scheduler.log | grep -E "Kafka|Job|Schedule"
```

### 4. Check Web UI

```bash
# View scheduled jobs
curl http://localhost:7070/api/jobs/scheduled | jq

# View job history
curl http://localhost:7070/api/jobs/history | jq

# View statistics
curl http://localhost:7070/api/jobs/stats | jq
```

---

## 🎯 Next Steps

1. **Build the project**:
   ```bash
   mvn clean package -DskipTests
   ```

2. **Run with production profile**:
   ```bash
   java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=prod
   ```

3. **Verify Kafka connection** in logs:
   ```
   ✅ Kafka Ingest Configured:
      - Bootstrap Servers: 10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
   ```

4. **Access Web UI**:
   ```
   http://localhost:7070/index.html
   ```

5. **Send test job** to Kafka topic and watch it execute

---

## 📚 Documentation

- **Configuration Guide**: `QUARKUS_CONFIGURATION_GUIDE.md`
- **SMS Retry Guide**: `SMS_RETRY_QUICK_START.md`
- **Web UI Guide**: `WEB_UI_GUIDE.md`
- **Testing Guide**: `TESTING_GUIDE.md`

---

## ✅ Summary

- ✅ **Kafka Brokers**: `10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092`
- ✅ **Quarkus Configuration**: Complete with profiles
- ✅ **Type-Safe Config**: @ConfigMapping classes
- ✅ **CDI Integration**: @Inject support
- ✅ **Profile Support**: dev, prod, test
- ✅ **Environment Overrides**: All properties configurable via env vars
- ✅ **Multi-Tenant Ready**: Separate configs per tenant
- ✅ **Docker/LXC Compatible**: Easy containerization
- ✅ **Web UI**: Real-time job monitoring on port 7070
- ✅ **Split-Verse**: Time-based table partitioning enabled

**Status**: Ready for production deployment! 🚀
