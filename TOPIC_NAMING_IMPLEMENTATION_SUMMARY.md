# Topic Naming Convention - Implementation Summary

## What Was Implemented

Enforced strict Kafka topic naming convention for true multi-tenant isolation with fail-fast validation.

---

## Convention Enforced

### Pattern
```
{tenant}_{apptype}_scheduler_{direction}
```

### Examples
- **Link3 SMS Inbound**: `link3_sms_scheduler_in`
- **Link3 SMS Outbound**: `link3_sms_scheduler_out`
- **Link3 SMS DLQ**: `link3_sms_scheduler_dlq`
- **Example SMS Inbound**: `example_sms_scheduler_in`
- **Link3 Payment Inbound**: `link3_payment_scheduler_in`

---

## Implementation Components

### 1. TopicNamingConvention Class

**File**: `src/main/java/com/telcobright/scheduler/config/TopicNamingConvention.java`

**Purpose**: Define strict naming pattern and provide utility methods

**Key Methods**:
```java
// Generate topic names
String inbound = TopicNamingConvention.inbound("link3", "sms");
// Returns: "link3_sms_scheduler_in"

String outbound = TopicNamingConvention.outbound("link3", "sms");
// Returns: "link3_sms_scheduler_out"

String dlq = TopicNamingConvention.dlq("link3", "sms");
// Returns: "link3_sms_scheduler_dlq"

// Validate topic name
boolean valid = TopicNamingConvention.isValid("link3_sms_scheduler_in");
// Returns: true

boolean invalid = TopicNamingConvention.isValid("Job_Schedule_Link3_Dev");
// Returns: false

// Extract components
String tenant = TopicNamingConvention.extractTenant("link3_sms_scheduler_in");
// Returns: "link3"

String appType = TopicNamingConvention.extractAppType("link3_sms_scheduler_in");
// Returns: "sms"

String direction = TopicNamingConvention.getTopicType("link3_sms_scheduler_in");
// Returns: "in"
```

**Validation Rules**:
- Tenant: Lowercase alphanumeric with hyphens (`[a-z0-9-]+`)
- App Type: Lowercase alphanumeric only (`[a-z0-9]+`)
- Suffix: Must be exactly `_scheduler_in`, `_scheduler_out`, or `_scheduler_dlq`

---

### 2. TopicNamingValidator Class

**File**: `src/main/java/com/telcobright/scheduler/config/TopicNamingValidator.java`

**Purpose**: Fail-fast validation of all Kafka topic configurations

**What It Validates**:
1. **Pattern Compliance**: Each topic matches `{tenant}_{apptype}_scheduler_{in|out|dlq}`
2. **Tenant Matching**: Tenant in topic name matches configured tenant
3. **Direction Correctness**:
   - Incoming channels → Must use `_scheduler_in` topics
   - Outgoing channels → Must use `_scheduler_out` topics
   - DLQ topics → Must use `_scheduler_dlq` suffix

**Key Method**:
```java
TopicNamingValidator.validateAllTopics(tenant, profile, properties);
// Throws TopicValidationException if any topic is invalid
```

**Error Output Example**:
```
╔════════════════════════════════════════════════════════════════╗
║  ❌ TOPIC NAMING VALIDATION FAILED - SERVICE CANNOT START     ║
╚════════════════════════════════════════════════════════════════╝

Tenant: link3
Profile: dev

Errors:
  1. Invalid incoming topic for channel 'job-schedule': 'Job_Schedule_Link3_Dev'
  2. Invalid outgoing topic for channel 'sms-send': 'SMS_Send_Link3_Dev'

Required Pattern:
  Inbound:  {tenant}_{apptype}_scheduler_in
  Outbound: {tenant}_{apptype}_scheduler_out
  DLQ:      {tenant}_{apptype}_scheduler_dlq

Examples:
  - link3_sms_scheduler_in
  - link3_sms_scheduler_out
  - link3_sms_scheduler_dlq

Fix the topic names in: config/tenants/link3/dev/profile-dev.yml
```

---

### 3. TenantConfigSource Integration

**File**: `src/main/java/com/telcobright/scheduler/config/TenantConfigSource.java`

**Changes Made**:
```java
// After loading all properties from tenant YAML
System.out.println("[TenantConfigSource]   Validating topic naming convention...");
TopicNamingValidator.validateAllTopics(tenantName, tenantProfile, properties);
System.out.println("[TenantConfigSource]   ✅ All topic names are valid");
```

**Behavior**:
- Runs during early startup (before Quarkus finishes initialization)
- If validation fails → Throws `RuntimeException` → Service won't start
- If validation passes → Continues startup normally

---

### 4. Updated Tenant YAML Files

**All tenant profile YAMLs updated**:
- `config/tenants/link3/dev/profile-dev.yml`
- `config/tenants/link3/prod/profile-prod.yml`
- `config/tenants/link3/staging/profile-staging.yml`
- `config/tenants/example/dev/profile-dev.yml`

**Before** (Old Format - INVALID):
```yaml
mp:
  messaging:
    incoming:
      job-schedule:
        topic: "Job_Schedule_Link3_Dev"  # ❌ INVALID

    outgoing:
      sms-send:
        topic: "SMS_Send_Link3_Dev"  # ❌ INVALID
```

**After** (New Format - VALID):
```yaml
mp:
  messaging:
    incoming:
      job-schedule:
        topic: "link3_sms_scheduler_in"  # ✅ VALID
        failure-strategy: dead-letter-queue
        dead-letter-queue.topic: "link3_sms_scheduler_dlq"  # ✅ VALID

    outgoing:
      sms-send:
        topic: "link3_sms_scheduler_out"  # ✅ VALID
```

---

## Multi-Tenant Architecture

### Complete Isolation per Tenant

**Link3 Tenant**:
- Database: `scheduler_link3_dev` / `scheduler_link3_prod`
- Inbound Topic: `link3_sms_scheduler_in`
- Outbound Topic: `link3_sms_scheduler_out`
- DLQ Topic: `link3_sms_scheduler_dlq`

**Example Tenant**:
- Database: `scheduler_example_dev` / `scheduler_example_prod`
- Inbound Topic: `example_sms_scheduler_in`
- Outbound Topic: `example_sms_scheduler_out`
- DLQ Topic: `example_sms_scheduler_dlq`

### No Loop Refactoring Needed

**Current Model**: ✅ Single tenant per JVM instance (correct approach)

**Why?**
1. **Deployment Model**: Each tenant runs in separate Docker container/K8s pod
2. **SmallRye Limitation**: `@Incoming` channels are static, can't create multiple dynamically
3. **Production Best Practice**: Separate processes for tenant isolation

**Scaling**:
```
Container 1: Link3 Dev   → link3_sms_scheduler_* topics → scheduler_link3_dev DB
Container 2: Link3 Prod  → link3_sms_scheduler_* topics → scheduler_link3_prod DB
Container 3: Example Dev → example_sms_scheduler_* topics → scheduler_example_dev DB
```

---

## Kafka Message Flow

### Inbound: External Apps Schedule Jobs

```
External App (Link3 SMS Campaign Service)
         |
         | Produces to: link3_sms_scheduler_in
         v
Kafka Topic: link3_sms_scheduler_in
         |
         | Consumed by: SmallRye @Incoming("job-schedule")
         v
SmallRyeKafkaJobIngestConsumer
         |
         | Validates and processes
         v
MultiAppSchedulerManager.scheduleJob()
         |
         v
Quartz Job in scheduler_link3_dev DB
```

**Message Format**:
```json
{
  "requestId": "uuid-123",
  "appName": "sms_retry",
  "scheduledTime": "2025-11-15T22:00:00",
  "jobData": {
    "campaignTaskId": 12345,
    "phoneNumber": "+880123456789",
    "message": "Hello",
    "retryAttempt": 1
  }
}
```

### Outbound: Scheduler Publishes Results

```
Quartz Executes Job
         |
         v
SmallRyeSmsRetryJobHandler
         |
         | Publishes to: link3_sms_scheduler_out
         v
Kafka Topic: link3_sms_scheduler_out
         |
         | Consumed by: External SMS Gateway
         v
External App (Link3 SMS Gateway)
```

### Dead Letter Queue: Failed Messages

```
Message Processing Fails
         |
         v
message.nack(exception)
         |
         | SmallRye DLQ Strategy
         v
Kafka Topic: link3_sms_scheduler_dlq
         |
         | Monitored by: Operations Team
         v
Manual Review / Automated Retry
```

---

## External App Integration Guide

### Publishing Schedule Requests

**Configuration**:
```properties
# Kafka Producer
bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
topic.name=link3_sms_scheduler_in
acks=all
enable.idempotence=true
```

**Message Example**:
```java
Map<String, Object> scheduleRequest = Map.of(
    "requestId", UUID.randomUUID().toString(),
    "appName", "sms_retry",
    "scheduledTime", "2025-11-15T22:00:00",
    "jobData", Map.of(
        "campaignTaskId", 12345,
        "phoneNumber", "+880123456789",
        "message", "Hello"
    )
);

producer.send(new ProducerRecord<>(
    "link3_sms_scheduler_in",
    "task-12345",  // Key
    new Gson().toJson(scheduleRequest)
));
```

### Consuming Execution Results

**Configuration**:
```properties
# Kafka Consumer
bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
topic.name=link3_sms_scheduler_out
group.id=link3-sms-gateway
auto.offset.reset=earliest
```

**Consumer Example**:
```java
consumer.subscribe(Collections.singletonList("link3_sms_scheduler_out"));

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
    for (ConsumerRecord<String, String> record : records) {
        // Process execution result
        processExecutionResult(record.value());
    }
}
```

---

## Testing

### Build Verification

```bash
mvn clean compile -DskipTests
```

**Result**: ✅ BUILD SUCCESS

### Startup Validation Test

**Valid Configuration** (Current):
```
[TenantConfigSource] ════════════════════════════════════════════
[TenantConfigSource] Loading tenant configuration:
[TenantConfigSource]   Tenant:  link3
[TenantConfigSource]   Profile: dev
[TenantConfigSource]   Loaded 127 configuration properties
[TenantConfigSource]   Validating topic naming convention...
[TenantConfigSource]   ✅ All topic names are valid
[TenantConfigSource] ════════════════════════════════════════════
```

**Invalid Configuration** (If topics were wrong):
```
[TenantConfigSource] ERROR loading tenant config: Topic naming validation failed
╔════════════════════════════════════════════════════════════════╗
║  ❌ TOPIC NAMING VALIDATION FAILED - SERVICE CANNOT START     ║
╚════════════════════════════════════════════════════════════════╝
... detailed error messages ...
```

---

## Benefits

### ✅ True Multi-Tenant Isolation

Each tenant has:
- Isolated database: `scheduler_{tenant}_{profile}`
- Isolated topics: `{tenant}_{apptype}_scheduler_*`
- Separate consumer groups
- Independent scaling

### ✅ Fail-Fast Safety

- Invalid topics → Service won't start
- Clear error messages guide developers
- Prevents runtime errors from misconfiguration

### ✅ Consistent Naming

- All tenants follow same pattern
- Easy to understand and predict
- Simplifies operations and monitoring

### ✅ Future-Proof

- Easy to add new app types (payment, notification)
- Pattern scales to any number of tenants
- No code changes needed for new tenants

---

## Files Created

1. **`TopicNamingConvention.java`** - Naming pattern definition and utilities
2. **`TopicNamingValidator.java`** - Fail-fast validation logic
3. **`TOPIC_NAMING_CONVENTION.md`** - Comprehensive documentation
4. **`TOPIC_NAMING_IMPLEMENTATION_SUMMARY.md`** - This file

## Files Modified

1. **`TenantConfigSource.java`** - Added validation call
2. **`config/tenants/link3/dev/profile-dev.yml`** - Updated topic names
3. **`config/tenants/link3/prod/profile-prod.yml`** - Updated topic names
4. **`config/tenants/link3/staging/profile-staging.yml`** - Updated topic names
5. **`config/tenants/example/dev/profile-dev.yml`** - Updated topic names

---

## Next Steps for External Apps

### For App Developers

1. **Update Kafka Producers** to publish to `{tenant}_{apptype}_scheduler_in`
2. **Update Kafka Consumers** to consume from `{tenant}_{apptype}_scheduler_out`
3. **Monitor DLQ** topic `{tenant}_{apptype}_scheduler_dlq` for failures

### For Operations

1. **Create Kafka Topics** (if not auto-created):
   ```bash
   kafka-topics.sh --create \
     --bootstrap-server 10.10.199.20:9092 \
     --topic link3_sms_scheduler_in \
     --partitions 12 \
     --replication-factor 3

   kafka-topics.sh --create \
     --bootstrap-server 10.10.199.20:9092 \
     --topic link3_sms_scheduler_out \
     --partitions 12 \
     --replication-factor 3

   kafka-topics.sh --create \
     --bootstrap-server 10.10.199.20:9092 \
     --topic link3_sms_scheduler_dlq \
     --partitions 3 \
     --replication-factor 3
   ```

2. **Set Up Monitoring** for DLQ topics
3. **Configure Alerting** for validation failures

---

## Summary

✅ **Enforced Convention**: `{tenant}_{apptype}_scheduler_{in|out|dlq}`

✅ **Fail-Fast Validation**: Invalid topics prevent startup

✅ **Multi-Tenant Ready**: Database + Topic isolation

✅ **Production Safe**: Clear errors, no runtime surprises

✅ **External Apps**: Must use exact topic names

**The scheduler is now truly multi-tenant with enforced topic naming!** 🚀
