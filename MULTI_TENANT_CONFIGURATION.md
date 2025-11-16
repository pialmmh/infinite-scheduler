# Multi-Tenant Configuration Guide

## Overview

The Infinite Scheduler supports **multi-tenant deployments** with separate configuration per tenant and environment. This allows you to run multiple isolated scheduler instances for different clients, teams, or use cases - each with its own database, Kafka topics, and settings.

## Architecture

### Configuration Structure

```
src/main/resources/
├── application.properties          # Tenant selection only
└── config/
    └── tenants/
        ├── example/               # Tenant: example
        │   ├── dev/              # Profile: development
        │   │   └── profile-dev.yml
        │   ├── prod/             # Profile: production
        │   │   └── profile-prod.yml
        │   └── staging/          # Profile: staging
        │       └── profile-staging.yml
        ├── tenant1/              # Tenant: tenant1
        │   ├── dev/
        │   │   └── profile-dev.yml
        │   └── prod/
        │       └── profile-prod.yml
        └── {your-tenant}/        # Add your own tenants
            ├── dev/
            ├── prod/
            └── staging/
```

### Configuration Loading Priority

Configuration is loaded in the following order (highest to lowest priority):

1. **System Properties** (`-Dscheduler.tenant.name=example`)
2. **Environment Variables** (`SCHEDULER_TENANT_NAME=example`)
3. **Tenant Profile YAML** (`config/tenants/example/dev/profile-dev.yml`)
4. **application.properties** (fallback defaults only)

---

## Quick Start

### 1. Select Tenant and Profile

**Edit `application.properties`:**
```properties
# Default tenant for local development
scheduler.tenant.name=${SCHEDULER_TENANT_NAME:example}
scheduler.tenant.profile=${SCHEDULER_TENANT_PROFILE:dev}
```

### 2. Start Quarkus

```bash
# Development mode (uses defaults from application.properties)
mvn quarkus:dev

# Override tenant at runtime
SCHEDULER_TENANT_NAME=tenant1 SCHEDULER_TENANT_PROFILE=prod mvn quarkus:dev

# Production mode
export SCHEDULER_TENANT_NAME=example
export SCHEDULER_TENANT_PROFILE=prod
java -jar target/quarkus-app/quarkus-run.jar
```

### 3. Verify Configuration

On startup, you'll see:
```
╔════════════════════════════════════════════════════════════════╗
║     INFINITE SCHEDULER - QUARKUS STARTUP                       ║
╚════════════════════════════════════════════════════════════════╝

📋 Configuration:
  ┌─────────────────────────────────────────────────────────────┐
  │ Tenant Instance                                             │
  ├─────────────────────────────────────────────────────────────┤
  │ Tenant:   example
  │ Profile:  dev
  └─────────────────────────────────────────────────────────────┘
```

---

## Tenant Profile Configuration

### Profile YAML Structure

Each tenant profile is a complete configuration file:

**Example: `config/tenants/example/dev/profile-dev.yml`**
```yaml
infinite-scheduler:
  tenant:
    name: "example"
    profile: "dev"
    description: "Example tenant for development"
    enabled: true

  # MySQL Database Configuration
  mysql:
    host: "127.0.0.1"
    port: 3306
    database: "scheduler_example_dev"
    username: "root"
    password: "123456"

  # Kafka Configuration
  kafka:
    bootstrap-servers: "localhost:9092"

    ingest:
      enabled: false
      topic: "Job_Schedule_Example_Dev"
      dlq-topic: "Job_Schedule_Example_DLQ_Dev"
      max-retries: 3

    consumer:
      group-id: "infinite-scheduler-example-dev"
      enable-auto-commit: false
      auto-offset-reset: "earliest"
      max-poll-records: 500

  # Repository Configuration (Split-Verse)
  repository:
    database: "scheduler_example_dev"
    table-prefix: "scheduled_jobs"
    retention-days: 7

  # Fetcher Configuration
  fetcher:
    enabled: true
    interval-seconds: 25
    lookahead-seconds: 30
    max-jobs-per-fetch: 1000

  # Cleanup Configuration
  cleanup:
    enabled: true
    interval-hours: 24
    batch-size: 500

  # Quartz Scheduler
  quartz:
    thread-pool-size: 10
    misfire-threshold-ms: 60000

  # Web UI
  web:
    enabled: true
    host: "0.0.0.0"
    port: 7070

  # Monitoring
  monitoring:
    metrics-enabled: true
    health-enabled: true
    logging-level: "DEBUG"
    log-file: "/var/log/infinite-scheduler/example-dev.log"

# Quarkus Configuration
quarkus:
  http:
    port: 7070

  datasource:
    db-kind: mysql
    username: "root"
    password: "123456"
    jdbc:
      url: "jdbc:mysql://127.0.0.1:3306/scheduler_example_dev?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
      min-size: 2
      max-size: 20
      initial-size: 2

  log:
    level: DEBUG
    category:
      "com.telcobright.scheduler": DEBUG
      "org.quartz": INFO

# SmallRye Reactive Messaging
mp:
  messaging:
    incoming:
      job-schedule:
        connector: smallrye-kafka
        topic: "Job_Schedule_Example_Dev"
        bootstrap.servers: "localhost:9092"
        group.id: "infinite-scheduler-example-dev"

    outgoing:
      sms-send:
        connector: smallrye-kafka
        topic: "SMS_Send_Example_Dev"
        bootstrap.servers: "localhost:9092"
```

---

## Creating a New Tenant

### Step 1: Create Tenant Folder Structure

```bash
cd src/main/resources/config/tenants
mkdir -p my-tenant/{dev,prod,staging}
```

### Step 2: Create Profile Files

Create `my-tenant/dev/profile-dev.yml`:
```yaml
infinite-scheduler:
  tenant:
    name: "my-tenant"
    profile: "dev"
    description: "My Tenant Development"
    enabled: true

  mysql:
    host: "127.0.0.1"
    port: 3306
    database: "scheduler_mytenant_dev"
    username: "root"
    password: "123456"

  kafka:
    bootstrap-servers: "localhost:9092"
    ingest:
      enabled: false
      topic: "Job_Schedule_MyTenant_Dev"
      dlq-topic: "Job_Schedule_MyTenant_DLQ_Dev"

  # ... (copy other sections from example tenant)
```

Create `my-tenant/prod/profile-prod.yml`:
```yaml
infinite-scheduler:
  tenant:
    name: "my-tenant"
    profile: "prod"
    description: "My Tenant Production"
    enabled: true

  mysql:
    host: "10.10.199.12"
    port: 3306
    database: "scheduler_mytenant_prod"
    username: "mytenant_user"
    password: "${MYTENANT_MYSQL_PASSWORD:change_me}"

  kafka:
    bootstrap-servers: "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092"
    ingest:
      enabled: true
      topic: "Job_Schedule_MyTenant"
      dlq-topic: "Job_Schedule_MyTenant_DLQ"

  # ... (production settings)
```

### Step 3: Select Your Tenant

```bash
export SCHEDULER_TENANT_NAME=my-tenant
export SCHEDULER_TENANT_PROFILE=dev
mvn quarkus:dev
```

---

## Environment-Specific Configuration

### Development Profile

**Purpose:** Local development and testing

**Characteristics:**
- Local MySQL (`127.0.0.1:3306`)
- Local/disabled Kafka
- Debug logging
- Small thread pools
- Short data retention (7 days)
- Lower resource limits

**Example Settings:**
```yaml
infinite-scheduler:
  mysql:
    host: "127.0.0.1"
    database: "scheduler_tenant_dev"

  kafka:
    bootstrap-servers: "localhost:9092"
    ingest:
      enabled: false  # Disabled for dev

  fetcher:
    max-jobs-per-fetch: 1000  # Lower limit

  quartz:
    thread-pool-size: 10  # Smaller pool

  repository:
    retention-days: 7  # Short retention
```

### Staging Profile

**Purpose:** Pre-production testing

**Characteristics:**
- Staging database server
- Staging Kafka cluster
- Debug logging (for troubleshooting)
- Medium resource allocation
- Medium data retention (15 days)

**Example Settings:**
```yaml
infinite-scheduler:
  mysql:
    host: "10.10.198.10"
    database: "scheduler_tenant_staging"

  kafka:
    bootstrap-servers: "10.10.198.20:9092,10.10.197.20:9092"
    ingest:
      enabled: true

  fetcher:
    max-jobs-per-fetch: 5000

  quartz:
    thread-pool-size: 15

  repository:
    retention-days: 15
```

### Production Profile

**Purpose:** Production deployment

**Characteristics:**
- Production database servers
- Production Kafka cluster
- INFO/WARN logging
- Large thread pools
- Full data retention (30+ days)
- High resource limits
- Connection pooling optimizations
- Password from environment variables

**Example Settings:**
```yaml
infinite-scheduler:
  mysql:
    host: "10.10.199.10"
    database: "scheduler_tenant_prod"
    username: "tenant_user"
    password: "${MYSQL_PASSWORD:change_me}"

  kafka:
    bootstrap-servers: "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092"
    ingest:
      enabled: true

  fetcher:
    max-jobs-per-fetch: 10000

  quartz:
    thread-pool-size: 20

  repository:
    retention-days: 30

  monitoring:
    logging-level: "INFO"
```

---

## Runtime Configuration Override

### Via Environment Variables

```bash
# Select tenant and profile
export SCHEDULER_TENANT_NAME=example
export SCHEDULER_TENANT_PROFILE=prod

# Override specific settings (if needed)
export MYSQL_PASSWORD=secure_production_password
export TENANT1_MYSQL_PASSWORD=tenant1_secure_password

# Run
java -jar target/quarkus-app/quarkus-run.jar
```

### Via System Properties

```bash
java -jar target/quarkus-app/quarkus-run.jar \
  -Dscheduler.tenant.name=tenant1 \
  -Dscheduler.tenant.profile=prod \
  -DMYSQL_PASSWORD=secure_password
```

### Via Docker/Kubernetes

**Docker:**
```bash
docker run \
  -e SCHEDULER_TENANT_NAME=example \
  -e SCHEDULER_TENANT_PROFILE=prod \
  -e MYSQL_PASSWORD=secure_password \
  -p 7070:7070 \
  infinite-scheduler:1.0.0
```

**Kubernetes Deployment:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: scheduler-example-prod
spec:
  template:
    spec:
      containers:
      - name: infinite-scheduler
        image: infinite-scheduler:1.0.0
        env:
        - name: SCHEDULER_TENANT_NAME
          value: "example"
        - name: SCHEDULER_TENANT_PROFILE
          value: "prod"
        - name: MYSQL_PASSWORD
          valueFrom:
            secretKeyRef:
              name: scheduler-secrets
              key: mysql-password
```

---

## Multi-Tenant Deployment Scenarios

### Scenario 1: Single Tenant, Multiple Environments

Run the same tenant in different environments:

```bash
# Development
SCHEDULER_TENANT_NAME=example SCHEDULER_TENANT_PROFILE=dev mvn quarkus:dev

# Staging
SCHEDULER_TENANT_NAME=example SCHEDULER_TENANT_PROFILE=staging java -jar target/app.jar

# Production
SCHEDULER_TENANT_NAME=example SCHEDULER_TENANT_PROFILE=prod java -jar target/app.jar
```

### Scenario 2: Multiple Tenants, Same Environment

Run different tenants in the same environment (e.g., all in production):

```bash
# Tenant: example
SCHEDULER_TENANT_NAME=example SCHEDULER_TENANT_PROFILE=prod java -jar target/app.jar

# Tenant: tenant1
SCHEDULER_TENANT_NAME=tenant1 SCHEDULER_TENANT_PROFILE=prod java -jar target/app.jar
```

**Note:** Each instance needs:
- Different database
- Different Kafka topics
- Different HTTP port (configured in tenant YAML)

### Scenario 3: Kubernetes Multi-Tenant Deployment

Deploy multiple tenants as separate pods:

```yaml
# example-prod-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: scheduler-example-prod
spec:
  replicas: 1
  template:
    spec:
      containers:
      - name: scheduler
        image: infinite-scheduler:1.0.0
        env:
        - name: SCHEDULER_TENANT_NAME
          value: "example"
        - name: SCHEDULER_TENANT_PROFILE
          value: "prod"
---
# tenant1-prod-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: scheduler-tenant1-prod
spec:
  replicas: 1
  template:
    spec:
      containers:
      - name: scheduler
        image: infinite-scheduler:1.0.0
        env:
        - name: SCHEDULER_TENANT_NAME
          value: "tenant1"
        - name: SCHEDULER_TENANT_PROFILE
          value: "prod"
```

---

## Configuration Best Practices

### 1. Security

**Never commit passwords to Git:**
```yaml
# Use environment variable placeholders
mysql:
  password: "${MYSQL_PASSWORD:change_me}"
```

**Use Kubernetes Secrets:**
```yaml
env:
- name: MYSQL_PASSWORD
  valueFrom:
    secretKeyRef:
      name: scheduler-secrets
      key: mysql-password
```

### 2. Database Isolation

**Each tenant should have its own database:**
```yaml
# Tenant: example
mysql:
  database: "scheduler_example_prod"

# Tenant: tenant1
mysql:
  database: "scheduler_tenant1_prod"
```

### 3. Kafka Topic Naming

**Use tenant-specific topics:**
```yaml
# Tenant: example
kafka:
  ingest:
    topic: "Job_Schedule_Example"
    dlq-topic: "Job_Schedule_Example_DLQ"

# Tenant: tenant1
kafka:
  ingest:
    topic: "Job_Schedule_Tenant1"
    dlq-topic: "Job_Schedule_Tenant1_DLQ"
```

### 4. Port Assignment

**Assign unique ports per tenant (if running on same host):**
```yaml
# Tenant: example
web:
  port: 7070

# Tenant: tenant1
web:
  port: 7071
```

### 5. Log File Separation

**Separate log files per tenant:**
```yaml
# Tenant: example
monitoring:
  log-file: "/var/log/infinite-scheduler/example-prod.log"

# Tenant: tenant1
monitoring:
  log-file: "/var/log/infinite-scheduler/tenant1-prod.log"
```

---

## Troubleshooting

### Problem: Tenant config not loading

**Check:**
1. Tenant name and profile are set correctly
2. YAML file exists at correct path
3. YAML syntax is valid
4. Check startup logs for `[TenantConfigSource]` messages

**Verify:**
```bash
# Check startup logs
mvn quarkus:dev 2>&1 | grep TenantConfigSource

# Expected output:
# [TenantConfigSource] ════════════════════════════════════════════
# [TenantConfigSource] Loading tenant configuration:
# [TenantConfigSource]   Tenant:  example
# [TenantConfigSource]   Profile: dev
# [TenantConfigSource]   Loaded 127 configuration properties
# [TenantConfigSource] ════════════════════════════════════════════
```

### Problem: Wrong configuration being used

**Solution:** Check configuration priority
```bash
# System properties override everything
java -Dscheduler.tenant.name=example -jar target/app.jar

# Environment variables override YAML
export SCHEDULER_TENANT_NAME=example

# YAML is used if no overrides
# application.properties is fallback only
```

### Problem: Database connection fails

**Check:**
1. Database credentials in tenant YAML
2. Database exists
3. Network connectivity
4. Connection URL format

**Test connection:**
```bash
# From tenant YAML
mysql -h 10.10.199.10 -u tenant_user -p scheduler_tenant_prod
```

---

## Migration from Non-Tenant Config

If you have existing `application.properties` configuration:

### Before (application.properties):
```properties
scheduler.mysql.host=127.0.0.1
scheduler.mysql.database=scheduler
kafka.bootstrap.servers=localhost:9092
```

### After (Tenant YAML):

1. Create tenant folder: `config/tenants/default/dev/`
2. Create `profile-dev.yml` with your settings
3. Update `application.properties`:
```properties
scheduler.tenant.name=default
scheduler.tenant.profile=dev
```

---

## Summary

**Multi-tenant configuration enables:**
- ✅ **Isolation:** Each tenant has separate database, topics, logs
- ✅ **Flexibility:** Different settings per tenant and environment
- ✅ **Scalability:** Run multiple tenant instances independently
- ✅ **Security:** Secrets via environment variables
- ✅ **Maintainability:** Clear separation of configuration

**Configuration hierarchy:**
```
System Properties > Environment Variables > Tenant YAML > application.properties
```

**Tenant structure:**
```
config/tenants/{tenant-name}/{profile}/profile-{profile}.yml
```

**For more information, see:**
- `QUICK_START_QUARKUS.md` - Quick start guide
- `QUARKUS_STARTUP_GUIDE.md` - Complete Quarkus integration guide
- Example configurations in `config/tenants/example/` and `config/tenants/tenant1/`
