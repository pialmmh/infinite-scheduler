# Infinite Scheduler - Single Builder Configuration Guide

## Overview

The **MultiAppSchedulerManager** provides a fluent builder API for complete scheduler configuration in a single, chainable call.

---

## 🎯 Complete Builder Example

### Basic Configuration (MySQL Only)

```java
MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
    // MySQL Database Configuration (Required)
    .mysqlHost("127.0.0.1")
    .mysqlPort(3306)
    .mysqlDatabase("scheduler")
    .mysqlUsername("root")
    .mysqlPassword("123456")
    .build();

// Register applications
manager.registerApp("sms_retry", new SmsRetryJobHandler(...));
manager.registerApp("payment", new PaymentJobHandler(...));

// Start all schedulers
manager.startAll();
```

### Full Configuration (MySQL + Kafka)

```java
MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
    // === MySQL Configuration (Required) ===
    .mysqlHost("127.0.0.1")                    // MySQL host
    .mysqlPort(3306)                           // MySQL port (default: 3306)
    .mysqlDatabase("scheduler")                // Database name
    .mysqlUsername("root")                     // Database user
    .mysqlPassword("123456")                   // Database password

    // === Kafka Configuration (Optional) ===
    .withKafkaIngest(
        KafkaIngestConfig.builder()
            .bootstrapServers("10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092")
            .groupId("infinite-scheduler-group")
            .topic("Job_Schedule")             // Input topic
            .dlqTopic("Job_Schedule_DLQ")      // Dead letter queue
            .maxPollRecords(500)               // Max records per poll
            .pollTimeoutMs(1000)               // Poll timeout
            .enableAutoCommit(false)           // Manual commit
            .maxRetries(3)                     // Retry attempts
            .enabled(true)                     // Enable Kafka
            .build()
    )
    .build();

// Register applications
manager.registerApp("sms_retry", smsHandler);
manager.registerApp("payment", paymentHandler);

// Start everything (Quartz, fetchers, Kafka consumer)
manager.startAll();
```

---

## 📋 Configuration Parameters

### MySQL Configuration (Required)

| Method | Type | Default | Description |
|--------|------|---------|-------------|
| `mysqlHost(String)` | String | - | **Required**. MySQL server hostname or IP |
| `mysqlPort(int)` | int | `3306` | MySQL server port |
| `mysqlDatabase(String)` | String | - | **Required**. Database name |
| `mysqlUsername(String)` | String | - | **Required**. Database username |
| `mysqlPassword(String)` | String | - | **Required**. Database password |

**Example**:
```java
.mysqlHost("10.10.199.10")
.mysqlPort(3306)
.mysqlDatabase("scheduler")
.mysqlUsername("scheduler_user")
.mysqlPassword("secure_password_123")
```

### Kafka Configuration (Optional)

#### Option 1: Simple Kafka (Default Settings)

```java
.withKafkaIngest()  // Uses localhost:9092, default topics
```

#### Option 2: Quick Kafka (Custom Brokers & Topic)

```java
.withKafkaIngest(
    "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092",
    "Job_Schedule"
)
```

#### Option 3: Full Kafka Configuration

```java
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
```

### Kafka Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `bootstrapServers` | String | `localhost:9092` | Kafka broker addresses (comma-separated) |
| `groupId` | String | `infinite-scheduler-ingest` | Kafka consumer group ID |
| `topic` | String | `scheduler.jobs.ingest` | Input topic for job scheduling requests |
| `dlqTopic` | String | `scheduler.jobs.dlq` | Dead letter queue for failed messages |
| `maxPollRecords` | int | `500` | Max records per poll |
| `pollTimeoutMs` | int | `1000` | Poll timeout in milliseconds |
| `enableAutoCommit` | boolean | `false` | Auto-commit offsets (false = manual) |
| `maxRetries` | int | `3` | Max retry attempts for failed messages |
| `enabled` | boolean | `true` | Enable/disable Kafka ingestion |

---

## 🚀 Usage Examples

### Example 1: Development Setup (Local MySQL, No Kafka)

```java
import com.telcobright.scheduler.MultiAppSchedulerManager;

public class DevScheduler {
    public static void main(String[] args) throws Exception {
        MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
            .mysqlHost("127.0.0.1")
            .mysqlPort(3306)
            .mysqlDatabase("scheduler_dev")
            .mysqlUsername("root")
            .mysqlPassword("123456")
            .build();

        // Register SMS retry handler
        manager.registerApp("sms_retry", new SmsRetryJobHandler());

        // Start
        manager.startAll();

        System.out.println("✅ Development Scheduler Started");
        System.out.println("   MySQL: 127.0.0.1:3306/scheduler_dev");
        System.out.println("   Kafka: Disabled");

        // Keep running
        Thread.sleep(Long.MAX_VALUE);
    }
}
```

### Example 2: Production Setup (Remote MySQL + Kafka Cluster)

```java
import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.kafka.KafkaIngestConfig;

public class ProdScheduler {
    public static void main(String[] args) throws Exception {
        // Production Kafka brokers
        String kafkaBrokers = "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092";

        MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
            // Production MySQL
            .mysqlHost("10.10.199.10")
            .mysqlPort(3306)
            .mysqlDatabase("scheduler_prod")
            .mysqlUsername("scheduler_user")
            .mysqlPassword(System.getenv("MYSQL_PASSWORD"))  // From env

            // Production Kafka
            .withKafkaIngest(
                KafkaIngestConfig.builder()
                    .bootstrapServers(kafkaBrokers)
                    .groupId("scheduler-prod-group")
                    .topic("Prod_Job_Schedule")
                    .dlqTopic("Prod_Job_Schedule_DLQ")
                    .maxPollRecords(1000)
                    .pollTimeoutMs(2000)
                    .enableAutoCommit(false)
                    .maxRetries(5)
                    .enabled(true)
                    .build()
            )
            .build();

        // Register handlers
        manager.registerApp("sms_retry", new SmsRetryJobHandler(
            kafkaBrokers,
            "Prod_SMS_Send"
        ));
        manager.registerApp("payment", new PaymentJobHandler(
            kafkaBrokers,
            "Prod_Payment_Process"
        ));
        manager.registerApp("notification", new NotificationJobHandler(
            kafkaBrokers,
            "Prod_Notification_Send"
        ));

        // Start all
        manager.startAll();

        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║    PRODUCTION SCHEDULER STARTED SUCCESSFULLY           ║");
        System.out.println("╠════════════════════════════════════════════════════════╣");
        System.out.println("║  MySQL:  10.10.199.10:3306/scheduler_prod            ║");
        System.out.println("║  Kafka:  " + kafkaBrokers.substring(0, 40) + "...║");
        System.out.println("║  Apps:   sms_retry, payment, notification             ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                manager.stopAll();
                System.out.println("✅ Scheduler stopped gracefully");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        // Keep running
        Thread.sleep(Long.MAX_VALUE);
    }
}
```

### Example 3: Multi-Tenant Setup

```java
public class MultiTenantScheduler {
    public static void main(String[] args) throws Exception {
        // Tenant A Configuration
        MultiAppSchedulerManager tenantA = createScheduler(
            "10.10.199.10", "scheduler_tenant_a", "tenant_a_user", "pass_a",
            "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092",
            "TenantA_Job_Schedule", "TenantA_Job_DLQ"
        );
        tenantA.registerApp("sms_retry", new SmsRetryJobHandler(...));
        tenantA.startAll();

        // Tenant B Configuration
        MultiAppSchedulerManager tenantB = createScheduler(
            "10.10.199.11", "scheduler_tenant_b", "tenant_b_user", "pass_b",
            "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092",
            "TenantB_Job_Schedule", "TenantB_Job_DLQ"
        );
        tenantB.registerApp("sms_retry", new SmsRetryJobHandler(...));
        tenantB.startAll();

        System.out.println("✅ Multi-Tenant Scheduler Started");
        System.out.println("   Tenant A: 10.10.199.10/scheduler_tenant_a");
        System.out.println("   Tenant B: 10.10.199.11/scheduler_tenant_b");
    }

    private static MultiAppSchedulerManager createScheduler(
            String mysqlHost, String database, String username, String password,
            String kafkaBrokers, String topic, String dlqTopic) throws Exception {

        return MultiAppSchedulerManager.builder()
            .mysqlHost(mysqlHost)
            .mysqlPort(3306)
            .mysqlDatabase(database)
            .mysqlUsername(username)
            .mysqlPassword(password)
            .withKafkaIngest(
                KafkaIngestConfig.builder()
                    .bootstrapServers(kafkaBrokers)
                    .groupId(database + "-group")
                    .topic(topic)
                    .dlqTopic(dlqTopic)
                    .build()
            )
            .build();
    }
}
```

### Example 4: Environment-Based Configuration

```java
public class EnvBasedScheduler {
    public static void main(String[] args) throws Exception {
        // Read from environment variables
        String env = System.getenv("ENVIRONMENT");  // dev, staging, prod

        MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
            .mysqlHost(getEnv("MYSQL_HOST", "127.0.0.1"))
            .mysqlPort(Integer.parseInt(getEnv("MYSQL_PORT", "3306")))
            .mysqlDatabase(getEnv("MYSQL_DATABASE", "scheduler"))
            .mysqlUsername(getEnv("MYSQL_USERNAME", "root"))
            .mysqlPassword(getEnv("MYSQL_PASSWORD", "123456"))
            .withKafkaIngest(
                KafkaIngestConfig.builder()
                    .bootstrapServers(getEnv("KAFKA_BROKERS", "localhost:9092"))
                    .groupId(getEnv("KAFKA_GROUP_ID", "scheduler-" + env))
                    .topic(getEnv("KAFKA_INPUT_TOPIC", "Job_Schedule"))
                    .dlqTopic(getEnv("KAFKA_DLQ_TOPIC", "Job_Schedule_DLQ"))
                    .maxPollRecords(Integer.parseInt(getEnv("KAFKA_MAX_POLL", "500")))
                    .enabled(Boolean.parseBoolean(getEnv("KAFKA_ENABLED", "true")))
                    .build()
            )
            .build();

        manager.registerApp("sms_retry", new SmsRetryJobHandler(
            getEnv("KAFKA_BROKERS", "localhost:9092"),
            getEnv("SMS_OUTPUT_TOPIC", "SMS_Send")
        ));

        manager.startAll();

        System.out.println("✅ Scheduler Started - Environment: " + env);
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }
}
```

### Example 5: Docker/LXC Configuration

```java
public class ContainerScheduler {
    public static void main(String[] args) throws Exception {
        // Container-optimized configuration
        MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
            // MySQL from linked container or external service
            .mysqlHost(System.getenv("MYSQL_HOST"))  // e.g., "mysql-service"
            .mysqlPort(3306)
            .mysqlDatabase("scheduler")
            .mysqlUsername(System.getenv("MYSQL_USER"))
            .mysqlPassword(System.getenv("MYSQL_PASSWORD"))

            // Kafka from environment
            .withKafkaIngest(
                KafkaIngestConfig.builder()
                    .bootstrapServers(System.getenv("KAFKA_BROKERS"))
                    .groupId("scheduler-container-" + System.getenv("HOSTNAME"))
                    .topic(System.getenv("KAFKA_INPUT_TOPIC"))
                    .dlqTopic(System.getenv("KAFKA_DLQ_TOPIC"))
                    .maxPollRecords(1000)
                    .build()
            )
            .build();

        // Register apps
        String kafkaBrokers = System.getenv("KAFKA_BROKERS");
        manager.registerApp("sms_retry", new SmsRetryJobHandler(
            kafkaBrokers,
            System.getenv("SMS_OUTPUT_TOPIC")
        ));

        manager.startAll();

        System.out.println("✅ Container Scheduler Started");
        System.out.println("   Container: " + System.getenv("HOSTNAME"));
        System.out.println("   MySQL: " + System.getenv("MYSQL_HOST"));
        System.out.println("   Kafka: " + System.getenv("KAFKA_BROKERS"));

        // Health check endpoint (optional)
        startHealthCheckServer(8080);

        Thread.sleep(Long.MAX_VALUE);
    }

    private static void startHealthCheckServer(int port) {
        // Simple HTTP server for container health checks
        // Implementation omitted for brevity
    }
}
```

---

## 🔧 Configuration Best Practices

### 1. **Security**

```java
// ❌ Bad: Hardcoded passwords
.mysqlPassword("my-secret-password-123")

// ✅ Good: Environment variables
.mysqlPassword(System.getenv("MYSQL_PASSWORD"))

// ✅ Good: Property files (encrypted)
.mysqlPassword(loadFromSecureConfig("mysql.password"))
```

### 2. **Connection Pooling**

The builder automatically configures HikariCP with optimal settings:
- **Max Pool Size**: 10 connections
- **Min Idle**: 2 connections
- **Connection Timeout**: 30 seconds
- **Validation Query**: `SELECT 1`

### 3. **Kafka Consumer Groups**

```java
// ❌ Bad: Same group ID for different environments
.groupId("infinite-scheduler-group")

// ✅ Good: Environment-specific group IDs
.groupId("scheduler-" + environment + "-group")  // scheduler-prod-group
```

### 4. **Error Handling**

```java
try {
    MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
        .mysqlHost("10.10.199.10")
        .mysqlDatabase("scheduler")
        .mysqlUsername("root")
        .mysqlPassword(getPassword())
        .withKafkaIngest(kafkaBrokers, "Job_Schedule")
        .build();

    manager.registerApp("sms_retry", handler);
    manager.startAll();

} catch (IllegalArgumentException e) {
    logger.error("Invalid configuration: {}", e.getMessage());
    System.exit(1);
} catch (SchedulerException e) {
    logger.error("Failed to start scheduler: {}", e.getMessage());
    System.exit(1);
}
```

### 5. **Graceful Shutdown**

```java
MultiAppSchedulerManager manager = ...;
manager.startAll();

Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    try {
        logger.info("Shutting down scheduler...");
        manager.stopAll();
        logger.info("Scheduler stopped successfully");
    } catch (Exception e) {
        logger.error("Error during shutdown", e);
    }
}));
```

---

## 📊 Configuration Validation

The builder validates all required parameters:

```java
// Missing required parameters throws IllegalArgumentException
MultiAppSchedulerManager.builder()
    .mysqlHost("")  // ❌ Empty host
    .build();       // Throws: "mysqlHost is required"

MultiAppSchedulerManager.builder()
    .mysqlHost("localhost")
    .mysqlDatabase("")  // ❌ Empty database
    .build();           // Throws: "mysqlDatabase is required"

MultiAppSchedulerManager.builder()
    .mysqlHost("localhost")
    .mysqlDatabase("scheduler")
    // ❌ Missing username
    .build();  // Throws: "mysqlUsername is required"
```

---

## 🎯 Complete Configuration Template

```java
import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.kafka.KafkaIngestConfig;

public class SchedulerBootstrap {

    public static void main(String[] args) throws Exception {

        MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()

            // ===============================================
            // MySQL Configuration (Required)
            // ===============================================
            .mysqlHost(getConfig("mysql.host", "127.0.0.1"))
            .mysqlPort(getIntConfig("mysql.port", 3306))
            .mysqlDatabase(getConfig("mysql.database", "scheduler"))
            .mysqlUsername(getConfig("mysql.username", "root"))
            .mysqlPassword(getConfig("mysql.password", "123456"))

            // ===============================================
            // Kafka Configuration (Optional)
            // ===============================================
            .withKafkaIngest(
                KafkaIngestConfig.builder()
                    .bootstrapServers(getConfig("kafka.brokers",
                        "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092"))
                    .groupId(getConfig("kafka.group.id", "infinite-scheduler-group"))
                    .topic(getConfig("kafka.input.topic", "Job_Schedule"))
                    .dlqTopic(getConfig("kafka.dlq.topic", "Job_Schedule_DLQ"))
                    .maxPollRecords(getIntConfig("kafka.max.poll.records", 500))
                    .pollTimeoutMs(getIntConfig("kafka.poll.timeout.ms", 1000))
                    .enableAutoCommit(getBoolConfig("kafka.auto.commit", false))
                    .maxRetries(getIntConfig("kafka.max.retries", 3))
                    .enabled(getBoolConfig("kafka.enabled", true))
                    .build()
            )
            .build();

        // ===============================================
        // Register Applications
        // ===============================================
        String kafkaBrokers = getConfig("kafka.brokers", "localhost:9092");

        manager.registerApp("sms_retry",
            new SmsRetryJobHandler(kafkaBrokers, "SMS_Send"));

        manager.registerApp("payment",
            new PaymentJobHandler(kafkaBrokers, "Payment_Process"));

        // ===============================================
        // Start All Schedulers
        // ===============================================
        manager.startAll();

        logger.info("✅ Infinite Scheduler Started Successfully");
        logger.info("   MySQL: {}:{}/{}",
            getConfig("mysql.host"),
            getIntConfig("mysql.port", 3306),
            getConfig("mysql.database"));
        logger.info("   Kafka: {}", kafkaBrokers);
        logger.info("   Apps: {}", manager.getRegisteredApps());

        // ===============================================
        // Graceful Shutdown
        // ===============================================
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                manager.stopAll();
                logger.info("✅ Scheduler stopped gracefully");
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

        // Keep running
        Thread.sleep(Long.MAX_VALUE);
    }

    // Helper methods
    private static String getConfig(String key, String defaultValue) {
        return System.getProperty(key, System.getenv(key.replace(".", "_").toUpperCase()));
    }

    private static int getIntConfig(String key, int defaultValue) {
        String value = getConfig(key, null);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    private static boolean getBoolConfig(String key, boolean defaultValue) {
        String value = getConfig(key, null);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
}
```

---

## ✅ Summary

**Single Builder Configuration** provides:

✅ **Fluent API** - Chain all configuration in one statement
✅ **Type Safety** - Compile-time parameter validation
✅ **Defaults** - Sensible defaults for all optional parameters
✅ **Validation** - Runtime validation of required parameters
✅ **Flexibility** - Multiple Kafka configuration options
✅ **Separation** - Clear separation between MySQL and Kafka config
✅ **Environment-Ready** - Easy integration with env vars and property files

**Example Usage**:
```java
MultiAppSchedulerManager manager = MultiAppSchedulerManager.builder()
    .mysqlHost("10.10.199.10")
    .mysqlDatabase("scheduler")
    .mysqlUsername("root")
    .mysqlPassword("pass")
    .withKafkaIngest("10.10.199.20:9092", "Job_Schedule")
    .build();
```

**That's it!** All scheduler configuration in a single, readable, type-safe builder pattern.
