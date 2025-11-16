# Quarkus Startup Bean - Configuration Guide

## Overview

The Infinite Scheduler now uses **automatic Quarkus startup** with configuration from `application.properties`.

When Quarkus starts, the scheduler automatically:
- ✅ Reads configuration from properties file
- ✅ Creates and configures the scheduler
- ✅ Registers application handlers (via CDI injection)
- ✅ Starts all schedulers (Quartz, fetchers, Kafka)
- ✅ Gracefully shuts down on application stop

---

## 🎯 How It Works

### Startup Flow

```
Quarkus Application Starts
        ↓
InfiniteSchedulerStartup @Observes StartupEvent
        ↓
Read Configuration (application.properties)
        ↓
Create MultiAppSchedulerManager
        ↓
Register Application Handlers (CDI injection)
        ↓
Start All Schedulers
        ↓
✅ Scheduler Running
```

### Shutdown Flow

```
Quarkus Application Stops (Ctrl+C or SIGTERM)
        ↓
InfiniteSchedulerStartup @Observes ShutdownEvent
        ↓
Stop All Schedulers
        ↓
✅ Graceful Shutdown Complete
```

---

## 📋 Configuration File

### Location
```
src/main/resources/application.properties
```

### Complete Configuration

```properties
# ===============================================
# Infinite Scheduler Configuration
# ===============================================

# MySQL Database Configuration
scheduler.mysql.host=127.0.0.1
scheduler.mysql.port=3306
scheduler.mysql.database=scheduler
scheduler.mysql.username=root
scheduler.mysql.password=123456

# Kafka Bootstrap Servers
kafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092

# Kafka Consumer Configuration
kafka.consumer.group.id=infinite-scheduler-group
kafka.consumer.enable.auto.commit=false
kafka.consumer.auto.offset.reset=earliest
kafka.consumer.max.poll.records=500

# SmallRye Reactive Messaging - Incoming Channel
mp.messaging.incoming.job-schedule.connector=smallrye-kafka
mp.messaging.incoming.job-schedule.topic=Job_Schedule
mp.messaging.incoming.job-schedule.bootstrap.servers=${kafka.bootstrap.servers}
mp.messaging.incoming.job-schedule.group.id=${kafka.consumer.group.id}
mp.messaging.incoming.job-schedule.failure-strategy=dead-letter-queue

# SmallRye Reactive Messaging - Outgoing Channel
mp.messaging.outgoing.sms-send.connector=smallrye-kafka
mp.messaging.outgoing.sms-send.topic=SMS_Send
mp.messaging.outgoing.sms-send.bootstrap.servers=${kafka.bootstrap.servers}
mp.messaging.outgoing.sms-send.acks=all
mp.messaging.outgoing.sms-send.enable.idempotence=true

# Scheduler Configuration
scheduler.kafka.ingest.topic=Job_Schedule
scheduler.kafka.ingest.dlq.topic=Job_Schedule_DLQ
scheduler.kafka.ingest.enabled=true
scheduler.kafka.ingest.max.retries=3

scheduler.repository.database=scheduler
scheduler.repository.table.prefix=scheduled_jobs
scheduler.repository.retention.days=30

scheduler.fetcher.interval.seconds=25
scheduler.fetcher.lookahead.seconds=30
scheduler.fetcher.enabled=true

scheduler.web.enabled=true
scheduler.web.port=7070
scheduler.web.host=0.0.0.0
```

---

## 🌐 Profile-Based Configuration

### Development Profile

```properties
# Development overrides
%dev.scheduler.mysql.host=127.0.0.1
%dev.scheduler.mysql.database=scheduler_dev
%dev.scheduler.mysql.password=123456

%dev.kafka.bootstrap.servers=localhost:9092
%dev.scheduler.kafka.ingest.enabled=false
%dev.quarkus.log.level=DEBUG
```

**Run**:
```bash
mvn quarkus:dev
```

### Production Profile

```properties
# Production overrides
%prod.scheduler.mysql.host=10.10.199.10
%prod.scheduler.mysql.database=scheduler_prod
%prod.scheduler.mysql.username=scheduler_user
%prod.scheduler.mysql.password=${MYSQL_PASSWORD:change_me_in_production}

%prod.kafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
%prod.scheduler.kafka.ingest.enabled=true
%prod.quarkus.log.level=INFO
```

**Run**:
```bash
# Set environment variable
export MYSQL_PASSWORD=secure_production_password

# Run with production profile
java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=prod
```

### Test Profile

```properties
# Test overrides
%test.scheduler.mysql.database=scheduler_test
%test.scheduler.kafka.ingest.enabled=false
%test.scheduler.fetcher.enabled=false
%test.scheduler.cleanup.enabled=false
```

**Run**:
```bash
mvn test
```

---

## 🚀 Running the Scheduler

### Option 1: Development Mode (Hot Reload)

```bash
mvn quarkus:dev
```

**Output**:
```
╔════════════════════════════════════════════════════════════════╗
║     INFINITE SCHEDULER - QUARKUS STARTUP                       ║
╚════════════════════════════════════════════════════════════════╝

📋 Configuration:
  ┌─────────────────────────────────────────────────────────────┐
  │ MySQL Database                                              │
  ├─────────────────────────────────────────────────────────────┤
  │ Host:     127.0.0.1:3306
  │ Database: scheduler_dev
  │ Username: root
  └─────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────┐
  │ Kafka: Disabled                                             │
  └─────────────────────────────────────────────────────────────┘

📋 Creating scheduler manager...
✅ Kafka ingest disabled
📝 Registering application handlers...
✅ Registered: sms_retry → SmallRyeSmsRetryJobHandler
📝 Total applications registered: 1

╔════════════════════════════════════════════════════════════════╗
║          ✅ SCHEDULER STARTED SUCCESSFULLY                     ║
╠════════════════════════════════════════════════════════════════╣
║                                                                ║
║  🌐 Web UI:        http://0.0.0.0:7070/index.html             ║
║  📥 Kafka Ingest:  DISABLED                                    ║
║                                                                ║
║  🎯 Registered Apps:                                           ║
║     • sms_retry                                                ║
║                                                                ║
╚════════════════════════════════════════════════════════════════╝
```

### Option 2: Production Mode

```bash
# Build
mvn clean package -DskipTests

# Run with production profile
export MYSQL_PASSWORD=your_secure_password
java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=prod
```

### Option 3: Docker/Container

```bash
# Build Docker image
docker build -f src/main/docker/Dockerfile.jvm -t infinite-scheduler:1.0.0 .

# Run container
docker run \
  -e MYSQL_PASSWORD=secure_password \
  -e QUARKUS_PROFILE=prod \
  -p 7070:7070 \
  infinite-scheduler:1.0.0
```

### Option 4: Override via Environment Variables

```bash
# Override configuration via environment variables
export SCHEDULER_MYSQL_HOST=10.10.199.10
export SCHEDULER_MYSQL_DATABASE=my_scheduler
export SCHEDULER_MYSQL_USERNAME=my_user
export SCHEDULER_MYSQL_PASSWORD=my_password
export KAFKA_BOOTSTRAP_SERVERS=10.10.199.20:9092

# Run
java -jar target/quarkus-app/quarkus-run.jar
```

---

## 🔧 Startup Bean Explained

### Location
```
src/main/java/com/telcobright/scheduler/startup/InfiniteSchedulerStartup.java
```

### Key Components

#### 1. Configuration Injection

```java
@ApplicationScoped
public class InfiniteSchedulerStartup {

    @Inject
    InfiniteSchedulerProperties schedulerProps;  // scheduler.mysql.*

    @Inject
    KafkaConfigProperties kafkaProps;            // kafka.*

    @Inject
    SchedulerConfigProperties schedulerConfig;   // scheduler.kafka.*, scheduler.repository.*

    @Inject
    SmallRyeSmsRetryJobHandler smsRetryHandler; // CDI-injected handler
}
```

#### 2. Automatic Startup

```java
void onStart(@Observes StartupEvent event) {
    // 1. Print configuration
    printConfiguration();

    // 2. Create scheduler manager from properties
    schedulerManager = createSchedulerManager();

    // 3. Register handlers (via CDI injection)
    registerApplicationHandlers();

    // 4. Start all schedulers
    schedulerManager.startAll();

    // 5. Print startup summary
    printStartupSummary();
}
```

#### 3. Graceful Shutdown

```java
void onStop(@Observes ShutdownEvent event) {
    if (schedulerManager != null) {
        schedulerManager.stopAll();
        logger.info("✅ Scheduler stopped successfully");
    }
}
```

#### 4. Dynamic Configuration

```java
private MultiAppSchedulerManager createSchedulerManager() {
    MultiAppSchedulerManager.Builder builder = MultiAppSchedulerManager.builder()
        // Read from properties
        .mysqlHost(schedulerProps.mysql().host())
        .mysqlPort(schedulerProps.mysql().port())
        .mysqlDatabase(schedulerProps.mysql().database())
        .mysqlUsername(schedulerProps.mysql().username())
        .mysqlPassword(schedulerProps.mysql().password());

    // Add Kafka if enabled
    if (schedulerConfig.kafka().ingest().enabled()) {
        builder.withKafkaIngest(createKafkaConfig());
    }

    return builder.build();
}
```

---

## 📝 Adding New Application Handlers

### Step 1: Create Handler

```java
@ApplicationScoped
public class MyCustomJobHandler implements JobHandler {

    @Inject
    SmallRyeSmsRetryPublisher publisher;  // Can inject other beans

    @Override
    public void execute(Map<String, Object> jobData) throws Exception {
        // Your job logic
    }

    @Override
    public boolean validate(Map<String, Object> jobData) {
        return jobData.containsKey("required_field");
    }

    @Override
    public String getName() {
        return "MyCustomJobHandler";
    }
}
```

### Step 2: Register in Startup Bean

```java
@ApplicationScoped
public class InfiniteSchedulerStartup {

    @Inject
    SmallRyeSmsRetryJobHandler smsRetryHandler;

    @Inject
    MyCustomJobHandler myCustomHandler;  // Inject your handler

    private void registerApplicationHandlers() {
        schedulerManager.registerApp("sms_retry", smsRetryHandler);
        schedulerManager.registerApp("my_custom_app", myCustomHandler);  // Register it

        logger.info("✅ Registered: my_custom_app → MyCustomJobHandler");
    }
}
```

### Step 3: Restart Quarkus

```bash
# In dev mode, hot reload is automatic
# In prod mode, rebuild and restart
mvn clean package && java -jar target/quarkus-app/quarkus-run.jar
```

---

## 🔐 Security Best Practices

### 1. Use Environment Variables for Passwords

```properties
# application.properties
%prod.scheduler.mysql.password=${MYSQL_PASSWORD:change_me}
```

```bash
# Set environment variable
export MYSQL_PASSWORD=secure_password_here
```

### 2. Use Kubernetes Secrets (for K8s deployment)

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: scheduler-secrets
type: Opaque
data:
  mysql-password: <base64-encoded-password>
```

```yaml
# deployment.yaml
env:
  - name: MYSQL_PASSWORD
    valueFrom:
      secretKeyRef:
        name: scheduler-secrets
        key: mysql-password
```

### 3. Use Vault or External Secret Store

```properties
# Configure Quarkus Vault extension
quarkus.vault.url=http://vault:8200
quarkus.vault.authentication.userpass.username=app-user

# Reference secrets
scheduler.mysql.password=${vault:secret/scheduler#mysql-password}
```

---

## 🧪 Testing

### Unit Test

```java
@QuarkusTest
public class SchedulerStartupTest {

    @Inject
    InfiniteSchedulerStartup startup;

    @Test
    public void testSchedulerManagerCreated() {
        assertNotNull(startup.getSchedulerManager());
        assertTrue(startup.getSchedulerManager().getRegisteredApps().contains("sms_retry"));
    }
}
```

### Integration Test

```bash
# Start with test profile
mvn test -Dquarkus.profile=test
```

---

## 📊 Monitoring

### Health Check

```bash
curl http://localhost:7070/q/health
```

**Response**:
```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Database connection",
      "status": "UP"
    },
    {
      "name": "Kafka connection",
      "status": "UP"
    }
  ]
}
```

### Metrics

```bash
curl http://localhost:7070/q/metrics
```

### Web UI

```
http://localhost:7070/index.html
```

---

## 🐛 Troubleshooting

### Issue: Scheduler doesn't start

**Check logs**:
```bash
tail -f logs/infinite-scheduler.log
```

**Verify configuration**:
```bash
# Check if properties are loaded
java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.log.category."io.smallrye.config".level=DEBUG
```

### Issue: MySQL connection fails

**Test connection**:
```bash
mysql -h 127.0.0.1 -P 3306 -u root -p123456 -D scheduler -e "SELECT 1;"
```

**Check properties**:
```properties
scheduler.mysql.host=127.0.0.1     # Correct host?
scheduler.mysql.database=scheduler  # Database exists?
scheduler.mysql.username=root       # Correct username?
```

### Issue: Kafka not connecting

**Test Kafka**:
```bash
kafka-broker-api-versions.sh --bootstrap-server 10.10.199.20:9092
```

**Check properties**:
```properties
kafka.bootstrap.servers=10.10.199.20:9092  # Correct brokers?
scheduler.kafka.ingest.enabled=true        # Kafka enabled?
```

---

## ✅ Summary

**Automatic Startup with Quarkus**:

✅ **Configuration** - All settings in `application.properties`
✅ **Automatic Start** - Scheduler starts with Quarkus application
✅ **Graceful Shutdown** - Clean shutdown on application stop
✅ **CDI Integration** - Handlers injected via dependency injection
✅ **Profile Support** - dev, prod, test profiles
✅ **Environment Variables** - Override any property via env vars
✅ **Type-Safe Config** - Compile-time validation with @ConfigMapping
✅ **No Manual Code** - No need to write startup logic

**Just start Quarkus and the scheduler automatically starts!**

```bash
mvn quarkus:dev  # Development
# or
java -jar target/quarkus-app/quarkus-run.jar  # Production
```

**Configuration File**:
```
src/main/resources/application.properties
```

**Startup Bean**:
```
src/main/java/com/telcobright/scheduler/startup/InfiniteSchedulerStartup.java
```

**That's it!** The scheduler is now fully integrated with Quarkus lifecycle. 🎉
