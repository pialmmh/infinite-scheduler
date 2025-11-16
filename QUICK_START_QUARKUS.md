# Quick Start - Quarkus Automatic Startup

## ⚡ 3-Step Quick Start

### 1️⃣ Configure `application.properties`

```properties
# MySQL Database
scheduler.mysql.host=127.0.0.1
scheduler.mysql.port=3306
scheduler.mysql.database=scheduler
scheduler.mysql.username=root
scheduler.mysql.password=123456

# Kafka (Optional - set enabled=false to disable)
kafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
scheduler.kafka.ingest.enabled=true
scheduler.kafka.ingest.topic=Job_Schedule
```

### 2️⃣ Run Quarkus

```bash
# Development mode (with hot reload)
mvn quarkus:dev

# Or production mode
mvn clean package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

### 3️⃣ That's It! ✅

The scheduler automatically starts with Quarkus.

**Access**:
- Web UI: http://localhost:7070/index.html
- Health: http://localhost:7070/q/health
- Metrics: http://localhost:7070/q/metrics

---

## 📋 Configuration Properties Reference

### Required (MySQL)

```properties
scheduler.mysql.host=127.0.0.1
scheduler.mysql.port=3306
scheduler.mysql.database=scheduler
scheduler.mysql.username=root
scheduler.mysql.password=123456
```

### Optional (Kafka)

```properties
# Kafka brokers
kafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092

# Kafka topics
scheduler.kafka.ingest.topic=Job_Schedule
scheduler.kafka.ingest.dlq.topic=Job_Schedule_DLQ

# Enable/disable Kafka
scheduler.kafka.ingest.enabled=true  # Set to false to disable Kafka
```

---

## 🌐 Environment-Specific Configuration

### Development (Default)

```bash
mvn quarkus:dev
```

Uses `%dev.*` properties from `application.properties`:
- MySQL: `localhost:3306/scheduler_dev`
- Kafka: Disabled

### Production

```bash
# Set password via environment variable
export MYSQL_PASSWORD=your_secure_password

# Run with production profile
java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=prod
```

Uses `%prod.*` properties:
- MySQL: `10.10.199.10:3306/scheduler_prod`
- Kafka: Enabled with production brokers

---

## 🔧 Override via Environment Variables

```bash
# Override any property
export SCHEDULER_MYSQL_HOST=10.10.199.10
export SCHEDULER_MYSQL_DATABASE=my_scheduler
export SCHEDULER_MYSQL_USERNAME=my_user
export SCHEDULER_MYSQL_PASSWORD=my_password
export KAFKA_BOOTSTRAP_SERVERS=10.10.199.20:9092

# Run
java -jar target/quarkus-app/quarkus-run.jar
```

---

## 📝 Add Your Own Job Handler

### 1. Create Handler Class

```java
package com.telcobright.scheduler.handlers;

import com.telcobright.scheduler.handler.JobHandler;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class MyJobHandler implements JobHandler {

    @Override
    public void execute(Map<String, Object> jobData) throws Exception {
        // Your job logic here
        String taskId = (String) jobData.get("taskId");
        System.out.println("Executing task: " + taskId);
    }

    @Override
    public boolean validate(Map<String, Object> jobData) {
        return jobData.containsKey("taskId");
    }

    @Override
    public String getName() {
        return "MyJobHandler";
    }
}
```

### 2. Register in Startup Bean

Edit `InfiniteSchedulerStartup.java`:

```java
@Inject
MyJobHandler myJobHandler;

private void registerApplicationHandlers() {
    schedulerManager.registerApp("sms_retry", smsRetryHandler);
    schedulerManager.registerApp("my_app", myJobHandler);  // Add this
}
```

### 3. Restart

```bash
mvn quarkus:dev  # Auto-reloads in dev mode
```

---

## 🎯 Expected Output

```
╔════════════════════════════════════════════════════════════════╗
║     INFINITE SCHEDULER - QUARKUS STARTUP                       ║
╚════════════════════════════════════════════════════════════════╝

📋 Configuration:
  ┌─────────────────────────────────────────────────────────────┐
  │ MySQL Database                                              │
  ├─────────────────────────────────────────────────────────────┤
  │ Host:     127.0.0.1:3306
  │ Database: scheduler
  │ Username: root
  └─────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────┐
  │ Kafka Configuration                                         │
  ├─────────────────────────────────────────────────────────────┤
  │ Brokers:      10.10.199.20:9092,10.10.198.20:9092...
  │ Group ID:     infinite-scheduler-group
  │ Input Topic:  Job_Schedule
  │ DLQ Topic:    Job_Schedule_DLQ
  │ Consumer:     SmallRye Reactive Messaging
  └─────────────────────────────────────────────────────────────┘

📋 Creating scheduler manager...
✅ Kafka ingest configured
📝 Registering application handlers...
✅ Registered: sms_retry → SmallRyeSmsRetryJobHandler
📝 Total applications registered: 1

╔════════════════════════════════════════════════════════════════╗
║          ✅ SCHEDULER STARTED SUCCESSFULLY                     ║
╠════════════════════════════════════════════════════════════════╣
║                                                                ║
║  🌐 Web UI:        http://0.0.0.0:7070/index.html             ║
║  📥 Kafka Ingest:  ENABLED                                     ║
║     Topic:         Job_Schedule                                ║
║     Type:          SmallRye Reactive Messaging                 ║
║                                                                ║
║  🎯 Registered Apps:                                           ║
║     • sms_retry                                                ║
║                                                                ║
║  ⚡ Features:                                                   ║
║     • Quartz Scheduler (MySQL persistence)                    ║
║     • Split-Verse (time-based partitioning)                   ║
║     • Virtual Threads (Java 21)                               ║
║     • Automatic table cleanup                                 ║
║     • Web UI for monitoring                                   ║
║     • SmallRye Reactive Messaging                             ║
║     • Automatic DLQ on failure                                ║
║                                                                ║
╚════════════════════════════════════════════════════════════════╝
```

---

## 🧪 Send Test Job

```bash
# Send message to Kafka
kafka-console-producer.sh \
  --bootstrap-server 10.10.199.20:9092 \
  --topic Job_Schedule \
  --property "parse.key=true" \
  --property "key.separator=:"

# Enter message:
test-1:{"appName":"sms_retry","scheduledTime":"2025-11-14 21:00:00","jobData":{"campaignTaskId":12345,"createdOn":"2025-11-14 20:00:00","retryTime":"2025-11-14 21:00:00","retryAttempt":1}}
```

**Expected Logs**:
```
📥 Received message - Partition: 0, Offset: 123, Key: test-1
✅ Job scheduled successfully - App: sms_retry, Time: 2025-11-14 21:00:00
🔥 Executing SMS retry job: Campaign Task ID: 12345
📤 Published to SMS_Send - Campaign: 12345, Attempt: 1
```

---

## 🛑 Stop Scheduler

Press `Ctrl+C` in dev mode, or send `SIGTERM` in production.

**Expected**:
```
🛑 Stopping Infinite Scheduler...
✅ Infinite Scheduler stopped successfully
```

---

## 📚 Full Documentation

- **Complete Configuration**: `QUARKUS_STARTUP_GUIDE.md`
- **Builder Pattern**: `BUILDER_CONFIGURATION_GUIDE.md`
- **SmallRye Kafka**: `SMALLRYE_KAFKA_CONFIGURATION.md`
- **Web UI Guide**: `WEB_UI_GUIDE.md`

---

## ✅ Summary

**Automatic Startup**:
1. Configure `application.properties`
2. Run `mvn quarkus:dev`
3. Scheduler starts automatically! ✅

**No manual code needed** - everything is configured via properties file!

**Configuration File**: `src/main/resources/application.properties`

**Startup Bean**: `src/main/java/com/telcobright/scheduler/startup/InfiniteSchedulerStartup.java`

**Just start Quarkus and go!** 🚀
