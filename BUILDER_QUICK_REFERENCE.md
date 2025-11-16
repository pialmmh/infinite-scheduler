# Infinite Scheduler - Builder Quick Reference

## 🎯 Single Builder Pattern - All Configurations

### Pattern 1: MySQL Only (Minimal)

```java
MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
    .mysqlHost("127.0.0.1")
    .mysqlPort(3306)
    .mysqlDatabase("scheduler")
    .mysqlUsername("root")
    .mysqlPassword("123456")
    .build();
```

### Pattern 2: MySQL + Simple Kafka

```java
MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
    // MySQL
    .mysqlHost("127.0.0.1")
    .mysqlPort(3306)
    .mysqlDatabase("scheduler")
    .mysqlUsername("root")
    .mysqlPassword("123456")
    // Kafka (simple)
    .withKafkaIngest("10.10.199.20:9092", "Job_Schedule")
    .build();
```

### Pattern 3: MySQL + Full Kafka Configuration

```java
MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
    // MySQL
    .mysqlHost("127.0.0.1")
    .mysqlPort(3306)
    .mysqlDatabase("scheduler")
    .mysqlUsername("root")
    .mysqlPassword("123456")
    // Kafka (full config)
    .withKafkaIngest(
        KafkaIngestConfig.builder()
            .bootstrapServers("10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092")
            .groupId("infinite-scheduler-group")
            .topic("Job_Schedule")
            .dlqTopic("Job_Schedule_DLQ")
            .maxPollRecords(500)
            .pollTimeoutMs(1000)
            .enableAutoCommit(false)
            .maxRetries(3)
            .enabled(true)
            .build()
    )
    .build();
```

### Pattern 4: Production with Environment Variables

```java
MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
    // MySQL from environment
    .mysqlHost(System.getenv("MYSQL_HOST"))
    .mysqlPort(Integer.parseInt(System.getenv("MYSQL_PORT")))
    .mysqlDatabase(System.getenv("MYSQL_DATABASE"))
    .mysqlUsername(System.getenv("MYSQL_USERNAME"))
    .mysqlPassword(System.getenv("MYSQL_PASSWORD"))
    // Kafka from environment
    .withKafkaIngest(
        KafkaIngestConfig.builder()
            .bootstrapServers(System.getenv("KAFKA_BROKERS"))
            .groupId(System.getenv("KAFKA_GROUP_ID"))
            .topic(System.getenv("KAFKA_INPUT_TOPIC"))
            .dlqTopic(System.getenv("KAFKA_DLQ_TOPIC"))
            .enabled(Boolean.parseBoolean(System.getenv("KAFKA_ENABLED")))
            .build()
    )
    .build();
```

---

## 📋 Required Parameters

| Parameter | Type | Required | Default | Example |
|-----------|------|----------|---------|---------|
| `mysqlHost` | String | ✅ Yes | - | `"127.0.0.1"` |
| `mysqlPort` | int | ⚠️ Optional | `3306` | `3306` |
| `mysqlDatabase` | String | ✅ Yes | - | `"scheduler"` |
| `mysqlUsername` | String | ✅ Yes | - | `"root"` |
| `mysqlPassword` | String | ✅ Yes | - | `"123456"` |

---

## 🔌 Kafka Configuration Options

### Option 1: No Kafka (Default)

```java
.build()  // No Kafka configuration
```

### Option 2: Default Kafka (localhost:9092)

```java
.withKafkaIngest()  // Uses localhost:9092, default topics
```

### Option 3: Custom Brokers & Topic

```java
.withKafkaIngest(
    "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092",
    "Job_Schedule"
)
```

### Option 4: Full Configuration

```java
.withKafkaIngest(
    KafkaIngestConfig.builder()
        .bootstrapServers("...")
        .groupId("...")
        .topic("...")
        .dlqTopic("...")
        .maxPollRecords(500)
        .pollTimeoutMs(1000)
        .enableAutoCommit(false)
        .maxRetries(3)
        .enabled(true)
        .build()
)
```

---

## 🚀 Complete Workflow

```java
// 1. BUILD
MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
    .mysqlHost("127.0.0.1")
    .mysqlDatabase("scheduler")
    .mysqlUsername("root")
    .mysqlPassword("123456")
    .withKafkaIngest("10.10.199.20:9092", "Job_Schedule")
    .build();

// 2. REGISTER APPS
manager.registerApp("sms_retry", new SmsRetryJobHandler(...));
manager.registerApp("payment", new PaymentJobHandler(...));

// 3. START
manager.startAll();

// 4. SHUTDOWN (optional, for graceful stop)
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    try {
        manager.stopAll();
    } catch (Exception e) {
        e.printStackTrace();
    }
}));
```

---

## 📦 Database Parameters Explained

### MySQL Host
```java
.mysqlHost("127.0.0.1")         // Local MySQL
.mysqlHost("10.10.199.10")      // Remote MySQL
.mysqlHost("mysql-service")     // Docker/Kubernetes service name
```

### MySQL Port
```java
.mysqlPort(3306)                // Default MySQL port
.mysqlPort(3307)                // Custom port
```

### MySQL Database
```java
.mysqlDatabase("scheduler")          // Database name
.mysqlDatabase("scheduler_prod")     // Environment-specific
.mysqlDatabase("tenant_a_scheduler") // Multi-tenant
```

### MySQL Credentials
```java
.mysqlUsername("root")                     // Username
.mysqlPassword("123456")                   // Password (hardcoded - not recommended)
.mysqlPassword(System.getenv("MYSQL_PASS")) // From environment (recommended)
```

---

## 🔐 Security Best Practices

### ❌ Bad (Hardcoded)

```java
.mysqlHost("10.10.199.10")
.mysqlUsername("root")
.mysqlPassword("my-secret-password")  // ❌ Never hardcode passwords!
```

### ✅ Good (Environment Variables)

```java
.mysqlHost(System.getenv("MYSQL_HOST"))
.mysqlUsername(System.getenv("MYSQL_USER"))
.mysqlPassword(System.getenv("MYSQL_PASSWORD"))
```

### ✅ Better (Property File)

```java
Properties props = loadSecureProperties("scheduler.properties");

.mysqlHost(props.getProperty("mysql.host"))
.mysqlUsername(props.getProperty("mysql.username"))
.mysqlPassword(props.getProperty("mysql.password"))
```

---

## 🌐 Multi-Environment Setup

### Development

```java
MultiAppSchedulerManager devManager = MultiAppSchedulerManager.builder()
    .mysqlHost("127.0.0.1")
    .mysqlDatabase("scheduler_dev")
    .mysqlUsername("root")
    .mysqlPassword("dev_password")
    .withKafkaIngest("localhost:9092", "Dev_Job_Schedule")
    .build();
```

### Staging

```java
MultiAppSchedulerManager stagingManager = MultiAppSchedulerManager.builder()
    .mysqlHost("10.10.199.10")
    .mysqlDatabase("scheduler_staging")
    .mysqlUsername("staging_user")
    .mysqlPassword(System.getenv("STAGING_MYSQL_PASS"))
    .withKafkaIngest("10.10.199.20:9092", "Staging_Job_Schedule")
    .build();
```

### Production

```java
MultiAppSchedulerManager prodManager = MultiAppSchedulerManager.builder()
    .mysqlHost("10.10.199.10")
    .mysqlDatabase("scheduler_prod")
    .mysqlUsername("prod_user")
    .mysqlPassword(System.getenv("PROD_MYSQL_PASS"))
    .withKafkaIngest(
        KafkaIngestConfig.builder()
            .bootstrapServers("10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092")
            .groupId("scheduler-prod-group")
            .topic("Prod_Job_Schedule")
            .dlqTopic("Prod_Job_Schedule_DLQ")
            .maxPollRecords(1000)
            .maxRetries(5)
            .build()
    )
    .build();
```

---

## ✅ Validation

The builder automatically validates:

```java
// ❌ Missing host
MultiAppSchedulerManager.builder()
    .mysqlDatabase("scheduler")
    .build();
// Throws: IllegalArgumentException("mysqlHost is required")

// ❌ Missing database
MultiAppSchedulerManager.builder()
    .mysqlHost("localhost")
    .build();
// Throws: IllegalArgumentException("mysqlDatabase is required")

// ❌ Missing username
MultiAppSchedulerManager.builder()
    .mysqlHost("localhost")
    .mysqlDatabase("scheduler")
    .build();
// Throws: IllegalArgumentException("mysqlUsername is required")

// ❌ Missing password
MultiAppSchedulerManager.builder()
    .mysqlHost("localhost")
    .mysqlDatabase("scheduler")
    .mysqlUsername("root")
    .build();
// Throws: IllegalArgumentException("mysqlPassword is required")

// ✅ Valid configuration
MultiAppSchedulerManager.builder()
    .mysqlHost("localhost")
    .mysqlDatabase("scheduler")
    .mysqlUsername("root")
    .mysqlPassword("password")
    .build();
// Success!
```

---

## 📝 Examples

### Example Files

| File | Description |
|------|-------------|
| `MinimalBuilderExample.java` | MySQL only (no Kafka) |
| `SimpleBuilderExample.java` | MySQL + Kafka with full config |
| `MultiAppSchedulerWithUI.java` | Multi-app with Web UI |
| `SmsRetrySchedulerExample.java` | SMS retry use case |

### Run Examples

```bash
# Minimal example (MySQL only)
java -cp target/infinite-scheduler-1.0.0.jar \
  com.telcobright.scheduler.examples.MinimalBuilderExample

# Full example (MySQL + Kafka)
java -cp target/infinite-scheduler-1.0.0.jar \
  com.telcobright.scheduler.examples.SimpleBuilderExample

# Multi-app with Web UI
java -cp target/infinite-scheduler-1.0.0.jar \
  com.telcobright.scheduler.examples.MultiAppSchedulerWithUI
```

---

## 🎯 Summary

**Single Builder Configuration** = Complete Setup in One Statement

```java
MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
    .mysqlHost("...")          // ✅ Required
    .mysqlPort(3306)           // ⚠️  Optional (default: 3306)
    .mysqlDatabase("...")      // ✅ Required
    .mysqlUsername("...")      // ✅ Required
    .mysqlPassword("...")      // ✅ Required
    .withKafkaIngest(...)      // ⚠️  Optional (no Kafka by default)
    .build();
```

**That's it!** All database and Kafka configuration in a single fluent builder.

---

## 📚 Documentation

- **Full Guide**: `BUILDER_CONFIGURATION_GUIDE.md`
- **Quarkus Config**: `QUARKUS_CONFIGURATION_GUIDE.md`
- **SmallRye Guide**: `SMALLRYE_KAFKA_CONFIGURATION.md`
- **Examples**: `src/main/java/com/telcobright/scheduler/examples/`

**Questions?** See `BUILDER_CONFIGURATION_GUIDE.md` for detailed examples and best practices.
