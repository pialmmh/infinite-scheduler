# Kafka Topic Naming Convention

## Overview

The Infinite Scheduler enforces a strict Kafka topic naming convention for multi-tenant isolation and consistency. **All topic names are validated at startup** - if any topic name is invalid, the service will **fail-fast** and refuse to start.

---

## Naming Pattern

### Convention

```
{tenant}_{apptype}_scheduler_{direction}
```

Where:
- **`{tenant}`**: Tenant name (lowercase alphanumeric with hyphens, e.g., `link3`, `example`)
- **`{apptype}`**: Application type (lowercase alphanumeric only, e.g., `sms`, `payment`, `notification`)
- **`{direction}`**: Topic direction:
  - `in` - Inbound topic for scheduling requests
  - `out` - Outbound topic for execution results
  - `dlq` - Dead Letter Queue for failed messages

### Examples

#### Link3 Tenant - SMS Application

| Topic Purpose | Topic Name |
|--------------|------------|
| Inbound (schedule requests) | `link3_sms_scheduler_in` |
| Outbound (execution results) | `link3_sms_scheduler_out` |
| Dead Letter Queue | `link3_sms_scheduler_dlq` |

#### Link3 Tenant - Payment Application

| Topic Purpose | Topic Name |
|--------------|------------|
| Inbound (schedule requests) | `link3_payment_scheduler_in` |
| Outbound (execution results) | `link3_payment_scheduler_out` |
| Dead Letter Queue | `link3_payment_scheduler_dlq` |

#### Example Tenant - SMS Application

| Topic Purpose | Topic Name |
|--------------|------------|
| Inbound (schedule requests) | `example_sms_scheduler_in` |
| Outbound (execution results) | `example_sms_scheduler_out` |
| Dead Letter Queue | `example_sms_scheduler_dlq` |

---

## Kafka Message Flow

### Inbound: External Apps → Infinite Scheduler

```
External App (Link3 SMS Campaign Service)
         |
         | Publishes ScheduleJobRequest JSON
         v
Topic: link3_sms_scheduler_in
         |
         | SmallRye @Incoming("job-schedule")
         v
SmallRyeKafkaJobIngestConsumer
         |
         | Parse & Validate
         v
MultiAppSchedulerManager.scheduleJob()
         |
         v
Quartz Job scheduled in scheduler_link3_dev DB
```

**Message Format:**
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

### Outbound: Infinite Scheduler → External Apps

```
Quartz executes scheduled job
         |
         v
SmallRyeSmsRetryJobHandler.execute()
         |
         | Process job logic
         v
SmallRyeSmsRetryPublisher.publishRetryResult()
         |
         | @Channel("sms-send")
         v
Topic: link3_sms_scheduler_out
         |
         | External app consumes
         v
External App (Link3 SMS Gateway)
```

### Dead Letter Queue: Failed Messages

```
SmallRyeKafkaJobIngestConsumer receives message
         |
         | Processing fails
         v
message.nack(exception)
         |
         | SmallRye failure-strategy: dead-letter-queue
         v
Topic: link3_sms_scheduler_dlq
         |
         | Monitor/retry service consumes
         v
Manual review or automated retry
```

---

## Configuration in Tenant YAML

### Link3 Dev Profile

**File:** `config/tenants/link3/dev/profile-dev.yml`

```yaml
mp:
  messaging:
    incoming:
      job-schedule:
        connector: smallrye-kafka
        topic: "link3_sms_scheduler_in"  # Inbound topic
        bootstrap.servers: "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092"
        group.id: "infinite-scheduler-link3-dev"
        enable.auto.commit: false
        auto.offset.reset: earliest
        max.poll.records: 500
        # Dead Letter Queue configuration
        failure-strategy: dead-letter-queue
        dead-letter-queue.topic: "link3_sms_scheduler_dlq"

    outgoing:
      sms-send:
        connector: smallrye-kafka
        topic: "link3_sms_scheduler_out"  # Outbound topic
        bootstrap.servers: "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092"
        acks: all
        enable.idempotence: true
```

---

## Validation Rules

### Startup Validation

During startup, `TenantConfigSource` validates **ALL** topic names:

1. **Pattern Validation**: Each topic must match `{tenant}_{apptype}_scheduler_{in|out|dlq}`
2. **Tenant Matching**: Tenant in topic name must match configured tenant
3. **Direction Validation**:
   - Incoming channels → Must use `_scheduler_in` topics
   - Outgoing channels → Must use `_scheduler_out` topics
   - DLQ topics → Must use `_scheduler_dlq` suffix

### Fail-Fast Behavior

**If ANY topic name is invalid, the service WILL NOT START.**

Example error output:

```
╔════════════════════════════════════════════════════════════════╗
║  ❌ TOPIC NAMING VALIDATION FAILED - SERVICE CANNOT START     ║
╚════════════════════════════════════════════════════════════════╝

Tenant: link3
Profile: dev

Errors:
  1. Invalid incoming topic for channel 'job-schedule': 'Job_Schedule_Link3_Dev'
  2. Invalid outgoing topic for channel 'sms-send': 'SMS_Send_Link3_Dev'
  3. Topic tenant mismatch for channel 'job-schedule': topic 'example_sms_scheduler_in' has tenant 'example' but expected 'link3'

Required Pattern:
  Inbound:  {tenant}_{apptype}_scheduler_in
  Outbound: {tenant}_{apptype}_scheduler_out
  DLQ:      {tenant}_{apptype}_scheduler_dlq

Examples:
  - link3_sms_scheduler_in
  - link3_sms_scheduler_out
  - link3_sms_scheduler_dlq
  - link3_payment_scheduler_in

Fix the topic names in: config/tenants/link3/dev/profile-dev.yml
```

---

## External App Integration

### For External Apps Publishing Schedule Requests

**Configuration Required:**

1. **Topic Name**: Use `{tenant}_{apptype}_scheduler_in`
   - Example: `link3_sms_scheduler_in`

2. **Message Format**: JSON with required fields
   ```json
   {
     "requestId": "unique-id",
     "appName": "sms_retry",
     "scheduledTime": "2025-11-15T22:00:00",
     "jobData": { ... }
   }
   ```

3. **Kafka Producer Configuration**:
   ```properties
   bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
   acks=all
   enable.idempotence=true
   ```

### For External Apps Consuming Execution Results

**Configuration Required:**

1. **Topic Name**: Use `{tenant}_{apptype}_scheduler_out`
   - Example: `link3_sms_scheduler_out`

2. **Kafka Consumer Configuration**:
   ```properties
   bootstrap.servers=10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
   group.id=link3-sms-gateway
   auto.offset.reset=earliest
   ```

### Monitoring Dead Letter Queue

**Configuration Required:**

1. **Topic Name**: Use `{tenant}_{apptype}_scheduler_dlq`
   - Example: `link3_sms_scheduler_dlq`

2. **Consumer Setup**: Monitor DLQ for failed messages
   ```bash
   # Kafka console consumer example
   kafka-console-consumer.sh \
     --bootstrap-server 10.10.199.20:9092 \
     --topic link3_sms_scheduler_dlq \
     --group dlq-monitor
   ```

---

## Multi-Tenant Isolation

### Topic Isolation by Tenant

Each tenant has completely isolated topics:

**Link3:**
- `link3_sms_scheduler_in`
- `link3_sms_scheduler_out`
- `link3_sms_scheduler_dlq`

**Example:**
- `example_sms_scheduler_in`
- `example_sms_scheduler_out`
- `example_sms_scheduler_dlq`

### Database Isolation

Combined with database isolation:
- Link3 Dev → Database: `scheduler_link3_dev` + Topics: `link3_sms_scheduler_*`
- Example Dev → Database: `scheduler_example_dev` + Topics: `example_sms_scheduler_*`

### Profile Separation

**Note:** All profiles (dev/prod/staging) of the same tenant use the **same topic names**:
- Link3 Dev → `link3_sms_scheduler_in`
- Link3 Prod → `link3_sms_scheduler_in` (same topic)
- Link3 Staging → `link3_sms_scheduler_in` (same topic)

**Why?** Each environment runs in a separate container/deployment with different:
- Database: `scheduler_link3_dev` vs `scheduler_link3_prod`
- Consumer Group ID: `infinite-scheduler-link3-dev` vs `infinite-scheduler-link3-prod`
- Kafka Bootstrap Servers (optionally different clusters)

---

## Adding New App Types

### Example: Adding Payment Scheduler

1. **Update Tenant YAML** (`config/tenants/link3/dev/profile-dev.yml`):

```yaml
mp:
  messaging:
    incoming:
      payment-schedule:  # New channel
        connector: smallrye-kafka
        topic: "link3_payment_scheduler_in"  # Follows convention
        bootstrap.servers: "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092"
        group.id: "infinite-scheduler-link3-dev-payment"
        failure-strategy: dead-letter-queue
        dead-letter-queue.topic: "link3_payment_scheduler_dlq"

    outgoing:
      payment-result:  # New channel
        connector: smallrye-kafka
        topic: "link3_payment_scheduler_out"  # Follows convention
        bootstrap.servers: "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092"
```

2. **Create Consumer** (`PaymentScheduleConsumer.java`):

```java
@ApplicationScoped
public class PaymentScheduleConsumer {

    @Incoming("payment-schedule")
    @Blocking
    public CompletionStage<Void> consumePaymentSchedule(Message<String> message) {
        // Handle payment scheduling
        return message.ack();
    }
}
```

3. **Create Publisher** (`PaymentResultPublisher.java`):

```java
@ApplicationScoped
public class PaymentResultPublisher {

    @Inject
    @Channel("payment-result")
    Emitter<String> paymentEmitter;

    public void publishResult(Map<String, Object> result) {
        // Publish payment result
    }
}
```

4. **Validation**: Topic names automatically validated at startup ✅

---

## Troubleshooting

### Common Validation Errors

#### 1. Invalid Topic Format

**Error:**
```
Invalid incoming topic for channel 'job-schedule': 'Job_Schedule_Link3_Dev'
```

**Fix:**
```yaml
# Before (WRONG)
topic: "Job_Schedule_Link3_Dev"

# After (CORRECT)
topic: "link3_sms_scheduler_in"
```

#### 2. Tenant Mismatch

**Error:**
```
Topic tenant mismatch: topic 'example_sms_scheduler_in' has tenant 'example' but expected 'link3'
```

**Fix:** Ensure topic tenant matches `application.properties`:
```properties
tenants[0].name=link3  # Must match topic prefix
```

#### 3. Wrong Direction Suffix

**Error:**
```
Incoming channel 'job-schedule' must use _scheduler_in topic, but got: 'link3_sms_scheduler_out' (type: out)
```

**Fix:** Use correct suffix for direction:
```yaml
# Incoming channels use _in suffix
incoming:
  job-schedule:
    topic: "link3_sms_scheduler_in"  # Not _out

# Outgoing channels use _out suffix
outgoing:
  sms-send:
    topic: "link3_sms_scheduler_out"  # Not _in
```

### Validation Bypass (NOT Recommended)

**There is NO way to bypass validation.** This is intentional to enforce consistency across all tenants and environments.

If you need a different naming scheme, you must:
1. Update `TopicNamingConvention.java` to change the pattern
2. Update `TopicNamingValidator.java` to match new rules
3. Rebuild and redeploy

---

## Implementation Details

### Classes Involved

1. **`TopicNamingConvention`** (`src/main/java/com/telcobright/scheduler/config/TopicNamingConvention.java`)
   - Defines strict naming pattern
   - Provides utility methods for generation and validation
   - Extracts tenant/apptype from topic names

2. **`TopicNamingValidator`** (`src/main/java/com/telcobright/scheduler/config/TopicNamingValidator.java`)
   - Validates all Kafka topic configurations
   - Runs during `TenantConfigSource` initialization
   - Throws `TopicValidationException` on failure

3. **`TenantConfigSource`** (`src/main/java/com/telcobright/scheduler/config/TenantConfigSource.java`)
   - Loads tenant YAML configuration
   - Calls `TopicNamingValidator.validateAllTopics()`
   - Fails startup if validation fails

### Validation Flow

```
Application Startup
         |
         v
TenantConfigSource.loadTenantConfiguration()
         |
         v
Load tenant YAML from config/tenants/{tenant}/{profile}/profile-{profile}.yml
         |
         v
Flatten YAML properties
         |
         v
TopicNamingValidator.validateAllTopics()
         |
         ├─→ Validate incoming topics (mp.messaging.incoming.*.topic)
         ├─→ Validate outgoing topics (mp.messaging.outgoing.*.topic)
         ├─→ Validate DLQ topics (*.dead-letter-queue.topic)
         |
         v
All valid? → Continue startup ✅
Any invalid? → Throw exception, fail startup ❌
```

---

## Summary

✅ **Enforced Convention:** `{tenant}_{apptype}_scheduler_{in|out|dlq}`

✅ **Fail-Fast Validation:** Invalid topics prevent service startup

✅ **Multi-Tenant Isolation:** Each tenant has isolated topics and databases

✅ **Clear Error Messages:** Detailed guidance when validation fails

✅ **Consistent Naming:** All tenants, profiles, and app types follow same pattern

**Example Topic Names:**
```
link3_sms_scheduler_in
link3_sms_scheduler_out
link3_sms_scheduler_dlq
link3_payment_scheduler_in
example_sms_scheduler_in
```

**External apps must configure their Kafka producers/consumers to use these exact topic names.**
