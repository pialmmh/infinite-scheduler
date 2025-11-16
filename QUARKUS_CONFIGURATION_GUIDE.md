# Quarkus Configuration Guide - Infinite Scheduler

## Overview

The Infinite Scheduler is integrated with Quarkus configuration system, allowing you to configure all aspects through `application.properties` or environment variables.

---

## Kafka Broker Configuration

### Production Brokers (Configured)

```properties
# application.properties (Production Profile)
%prod.kafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
```

### Environment Variable Override

```bash
# Override via environment variable
export KAFKA_BOOTSTRAP_SERVERS="10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092"

# Run with production profile
java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=prod
```

### Docker/LXC Container Configuration

```bash
# Run container with Kafka brokers
docker run \
  -e KAFKA_BOOTSTRAP_SERVERS="10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092" \
  -e QUARKUS_PROFILE=prod \
  -p 7070:7070 \
  infinite-scheduler:1.0.0
```

---

## Configuration Profiles

### 1. Development Profile (Default)

**File**: `application.properties`

```properties
%dev.kafka.bootstrap.servers=localhost:9092
%dev.quarkus.datasource.jdbc.url=jdbc:mysql://127.0.0.1:3306/scheduler
%dev.scheduler.kafka.ingest.enabled=false
%dev.scheduler.web.enabled=true
%dev.quarkus.log.level=DEBUG
```

**Usage**:
```bash
# Run in dev mode
mvn quarkus:dev

# Or with explicit profile
java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=dev
```

### 2. Production Profile

**File**: `application.properties`

```properties
%prod.kafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
%prod.scheduler.kafka.ingest.enabled=true
%prod.scheduler.web.enabled=true
%prod.quarkus.log.level=INFO
%prod.quarkus.log.console.color=false
```

**Usage**:
```bash
# Build for production
mvn clean package -DskipTests -Dquarkus.profile=prod

# Run production
java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=prod
```

### 3. Test Profile

**File**: `application.properties`

```properties
%test.kafka.bootstrap.servers=localhost:9092
%test.quarkus.datasource.jdbc.url=jdbc:mysql://127.0.0.1:3306/scheduler_test
%test.scheduler.kafka.ingest.enabled=false
%test.scheduler.fetcher.enabled=false
%test.scheduler.cleanup.enabled=false
%test.quarkus.log.level=WARN
```

**Usage**:
```bash
# Run tests
mvn test -Dquarkus.profile=test
```

---

## Complete Configuration Reference

### Kafka Configuration

```properties
# Bootstrap Servers
kafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092

# Consumer Settings
kafka.consumer.group.id=infinite-scheduler-group
kafka.consumer.enable.auto.commit=false
kafka.consumer.auto.offset.reset=earliest
kafka.consumer.max.poll.records=500
kafka.consumer.session.timeout.ms=30000
kafka.consumer.heartbeat.interval.ms=10000

# Producer Settings
kafka.producer.acks=all
kafka.producer.retries=3
kafka.producer.max.in.flight.requests.per.connection=1
kafka.producer.enable.idempotence=true
kafka.producer.compression.type=snappy
kafka.producer.batch.size=16384
kafka.producer.linger.ms=10

# Job Ingest Configuration
scheduler.kafka.ingest.topic=Job_Schedule
scheduler.kafka.ingest.dlq.topic=Job_Schedule_DLQ
scheduler.kafka.ingest.enabled=true
scheduler.kafka.ingest.max.retries=3
scheduler.kafka.ingest.poll.timeout.ms=1000
```

### MySQL Configuration

```properties
# DataSource
quarkus.datasource.db-kind=mysql
quarkus.datasource.jdbc.url=jdbc:mysql://127.0.0.1:3306/scheduler?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
quarkus.datasource.username=root
quarkus.datasource.password=123456

# Connection Pool
quarkus.datasource.jdbc.min-size=10
quarkus.datasource.jdbc.max-size=50
quarkus.datasource.jdbc.initial-size=10
quarkus.datasource.jdbc.acquisition-timeout=30s
quarkus.datasource.jdbc.leak-detection-interval=60s
```

### Scheduler Configuration

```properties
# Repository (Split-Verse)
scheduler.repository.database=scheduler
scheduler.repository.table.prefix=scheduled_jobs
scheduler.repository.retention.days=30

# Fetcher
scheduler.fetcher.interval.seconds=25
scheduler.fetcher.lookahead.seconds=30
scheduler.fetcher.max.jobs.per.fetch=10000
scheduler.fetcher.enabled=true

# Cleanup
scheduler.cleanup.enabled=true
scheduler.cleanup.interval.hours=24
scheduler.cleanup.batch.size=1000

# Quartz
scheduler.quartz.thread.pool.size=20
scheduler.quartz.misfire.threshold.ms=60000
```

### Web UI Configuration

```properties
# Web UI
scheduler.web.enabled=true
scheduler.web.port=7070
scheduler.web.host=0.0.0.0

# Quarkus HTTP
quarkus.http.port=7070
quarkus.http.host=0.0.0.0
```

---

## Using Configuration in Code

### Injecting Configuration

```java
import com.telcobright.scheduler.config.KafkaConfigProperties;
import com.telcobright.scheduler.config.SchedulerConfigProperties;
import jakarta.inject.Inject;

@ApplicationScoped
public class MySchedulerService {

    @Inject
    KafkaConfigProperties kafkaConfig;

    @Inject
    SchedulerConfigProperties schedulerConfig;

    public void start() {
        String brokers = kafkaConfig.bootstrapServers();
        // "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092"

        boolean kafkaEnabled = schedulerConfig.kafka().ingest().enabled();
        String topic = schedulerConfig.kafka().ingest().topic();

        logger.info("Kafka Brokers: {}", brokers);
        logger.info("Ingest Topic: {}", topic);
        logger.info("Kafka Ingest Enabled: {}", kafkaEnabled);
    }
}
```

### Using KafkaIngestConfigFactory

```java
import com.telcobright.scheduler.config.KafkaIngestConfigFactory;
import com.telcobright.scheduler.kafka.KafkaIngestConfig;
import jakarta.inject.Inject;

@ApplicationScoped
public class SchedulerInitializer {

    @Inject
    KafkaIngestConfigFactory configFactory;

    public void initializeKafka() {
        if (configFactory.isEnabled()) {
            KafkaIngestConfig config = configFactory.createConfig();

            logger.info("Kafka Bootstrap Servers: {}", config.getBootstrapServers());
            logger.info("Consumer Group: {}", config.getGroupId());
            logger.info("Ingest Topic: {}", config.getTopic());
            logger.info("DLQ Topic: {}", config.getDlqTopic());

            // Use config to start Kafka consumer
            startKafkaConsumer(config);
        }
    }
}
```

---

## Environment Variable Overrides

All properties can be overridden via environment variables using the Quarkus naming convention:
- Replace `.` with `_`
- Convert to UPPERCASE

### Examples

```bash
# Kafka brokers
export KAFKA_BOOTSTRAP_SERVERS="10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092"

# MySQL connection
export QUARKUS_DATASOURCE_JDBC_URL="jdbc:mysql://10.10.199.10:3306/scheduler"
export QUARKUS_DATASOURCE_USERNAME="scheduler_user"
export QUARKUS_DATASOURCE_PASSWORD="secure_password"

# Scheduler settings
export SCHEDULER_KAFKA_INGEST_ENABLED="true"
export SCHEDULER_KAFKA_INGEST_TOPIC="Job_Schedule"
export SCHEDULER_KAFKA_INGEST_DLQ_TOPIC="Job_Schedule_DLQ"

# Web UI
export SCHEDULER_WEB_PORT="8080"
export QUARKUS_HTTP_PORT="8080"

# Run application
java -jar target/quarkus-app/quarkus-run.jar
```

---

## Multi-Tenant Configuration

### Tenant-Specific Configuration Files

Following the routesphere pattern:

```
src/main/resources/
├── application.properties          # Global defaults
├── tenant-a/
│   ├── application-dev.properties  # Tenant A Dev
│   └── application-prod.properties # Tenant A Prod
└── tenant-b/
    ├── application-dev.properties  # Tenant B Dev
    └── application-prod.properties # Tenant B Prod
```

### Tenant A - Production

**File**: `src/main/resources/tenant-a/application-prod.properties`

```properties
# Tenant A - Production Kafka Brokers
kafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092

# Tenant A - Database
quarkus.datasource.jdbc.url=jdbc:mysql://10.10.199.10:3306/scheduler_tenant_a
quarkus.datasource.username=tenant_a_user
quarkus.datasource.password=tenant_a_password

# Tenant A - Topics
scheduler.kafka.ingest.topic=TenantA_Job_Schedule
scheduler.kafka.ingest.dlq.topic=TenantA_Job_Schedule_DLQ
```

### Tenant B - Production

**File**: `src/main/resources/tenant-b/application-prod.properties`

```properties
# Tenant B - Production Kafka Brokers
kafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092

# Tenant B - Database
quarkus.datasource.jdbc.url=jdbc:mysql://10.10.199.11:3306/scheduler_tenant_b
quarkus.datasource.username=tenant_b_user
quarkus.datasource.password=tenant_b_password

# Tenant B - Topics
scheduler.kafka.ingest.topic=TenantB_Job_Schedule
scheduler.kafka.ingest.dlq.topic=TenantB_Job_Schedule_DLQ
```

---

## Running with Specific Configuration

### Local Development

```bash
# Start with dev profile (uses localhost Kafka)
mvn quarkus:dev
```

### Production Deployment

```bash
# Build
mvn clean package -DskipTests

# Run with production profile
java -jar target/quarkus-app/quarkus-run.jar \
  -Dquarkus.profile=prod \
  -Dkafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
```

### Docker Container

```bash
# Build Docker image
docker build -f src/main/docker/Dockerfile.jvm -t infinite-scheduler:1.0.0 .

# Run with production Kafka brokers
docker run \
  -e KAFKA_BOOTSTRAP_SERVERS="10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092" \
  -e QUARKUS_PROFILE=prod \
  -e QUARKUS_DATASOURCE_JDBC_URL="jdbc:mysql://10.10.199.10:3306/scheduler" \
  -e QUARKUS_DATASOURCE_USERNAME="root" \
  -e QUARKUS_DATASOURCE_PASSWORD="123456" \
  -p 7070:7070 \
  infinite-scheduler:1.0.0
```

### LXC Container

```bash
# Run in LXC with production config
lxc exec infinite-scheduler -- java \
  -jar /app/infinite-scheduler.jar \
  -Dquarkus.profile=prod \
  -Dkafka.bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092 \
  -Dquarkus.http.host=0.0.0.0
```

---

## Configuration Validation

### Startup Validation

The scheduler validates configuration on startup:

```
✅ Kafka Bootstrap Servers: 10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
✅ Consumer Group: infinite-scheduler-group
✅ Ingest Topic: Job_Schedule
✅ DLQ Topic: Job_Schedule_DLQ
✅ MySQL URL: jdbc:mysql://127.0.0.1:3306/scheduler
✅ Web UI: http://0.0.0.0:7070/index.html
✅ Kafka Ingest: ENABLED
✅ Fetcher: ENABLED
✅ Cleanup: ENABLED
```

### Health Check Endpoints

```bash
# Check application health
curl http://localhost:7070/q/health

# Check Kafka connection
curl http://localhost:7070/q/health/ready

# Check metrics
curl http://localhost:7070/q/metrics
```

---

## Troubleshooting

### Issue: Cannot Connect to Kafka Brokers

**Solution**: Verify network connectivity and broker addresses

```bash
# Test connectivity to each broker
telnet 10.10.199.20 9092
telnet 10.10.198.20 9092
telnet 10.10.197.20 9092

# Check Kafka cluster status
kafka-broker-api-versions.sh --bootstrap-server 10.10.199.20:9092
```

### Issue: Configuration Not Loading

**Solution**: Check profile and property names

```bash
# Enable debug logging for config
java -jar target/quarkus-app/quarkus-run.jar \
  -Dquarkus.log.category."io.smallrye.config".level=DEBUG

# Print all configuration
java -jar target/quarkus-app/quarkus-run.jar \
  -Dquarkus.config-tracking.enabled=true
```

### Issue: MySQL Connection Fails

**Solution**: Verify MySQL connection string and credentials

```bash
# Test MySQL connection
mysql -h 127.0.0.1 -P 3306 -u root -p123456 -D scheduler -e "SELECT 1;"

# Check if database exists
mysql -h 127.0.0.1 -P 3306 -u root -p123456 -e "SHOW DATABASES LIKE 'scheduler';"
```

---

## Summary

✅ **Kafka Brokers Configured**: `10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092`
✅ **Profile-Based Configuration**: dev, prod, test
✅ **Environment Variable Support**: Override any property
✅ **Multi-Tenant Ready**: Separate config per tenant
✅ **Docker/LXC Compatible**: Easy containerization
✅ **Type-Safe Configuration**: Quarkus @ConfigMapping

**Next Steps**:
1. Build the project: `mvn clean package`
2. Run with production profile: `java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=prod`
3. Verify Kafka connection in logs
4. Access Web UI: http://localhost:7070/index.html
