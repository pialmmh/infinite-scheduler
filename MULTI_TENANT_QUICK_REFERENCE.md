# Multi-Tenant Configuration - Quick Reference

## TL;DR

```bash
# 1. Select tenant and profile
export SCHEDULER_TENANT_NAME=example
export SCHEDULER_TENANT_PROFILE=dev

# 2. Run
mvn quarkus:dev
```

---

## Folder Structure

```
src/main/resources/config/tenants/
├── example/dev/profile-dev.yml
├── example/prod/profile-prod.yml
├── tenant1/dev/profile-dev.yml
└── tenant1/prod/profile-prod.yml
```

---

## Configuration Priority

1. System Properties (`-Dscheduler.tenant.name=example`)
2. Environment Variables (`SCHEDULER_TENANT_NAME=example`)
3. Tenant YAML (`config/tenants/example/dev/profile-dev.yml`)
4. application.properties (fallback only)

---

## Select Tenant

### Method 1: Environment Variables (Recommended)
```bash
export SCHEDULER_TENANT_NAME=example
export SCHEDULER_TENANT_PROFILE=dev
mvn quarkus:dev
```

### Method 2: System Properties
```bash
mvn quarkus:dev -Dscheduler.tenant.name=example -Dscheduler.tenant.profile=dev
```

### Method 3: application.properties
```properties
scheduler.tenant.name=${SCHEDULER_TENANT_NAME:example}
scheduler.tenant.profile=${SCHEDULER_TENANT_PROFILE:dev}
```

---

## Create New Tenant

```bash
# 1. Create folders
mkdir -p src/main/resources/config/tenants/my-tenant/{dev,prod}

# 2. Copy example config
cp config/tenants/example/dev/profile-dev.yml \
   config/tenants/my-tenant/dev/

# 3. Edit my-tenant/dev/profile-dev.yml
# - Change tenant name
# - Change database name
# - Change Kafka topics
# - Change ports

# 4. Use it
export SCHEDULER_TENANT_NAME=my-tenant
export SCHEDULER_TENANT_PROFILE=dev
mvn quarkus:dev
```

---

## Profile YAML Template

```yaml
infinite-scheduler:
  tenant:
    name: "my-tenant"
    profile: "dev"
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

  repository:
    database: "scheduler_mytenant_dev"
    table-prefix: "scheduled_jobs"
    retention-days: 7

  fetcher:
    interval-seconds: 25
    lookahead-seconds: 30
    max-jobs-per-fetch: 1000

  web:
    port: 7070

quarkus:
  http:
    port: 7070

  datasource:
    db-kind: mysql
    username: "root"
    password: "123456"
    jdbc:
      url: "jdbc:mysql://127.0.0.1:3306/scheduler_mytenant_dev?useSSL=false"

mp:
  messaging:
    incoming:
      job-schedule:
        connector: smallrye-kafka
        topic: "Job_Schedule_MyTenant_Dev"
    outgoing:
      sms-send:
        connector: smallrye-kafka
        topic: "SMS_Send_MyTenant_Dev"
```

---

## Production Deployment

### Docker
```bash
docker run \
  -e SCHEDULER_TENANT_NAME=example \
  -e SCHEDULER_TENANT_PROFILE=prod \
  -e MYSQL_PASSWORD=secure_password \
  -p 7070:7070 \
  infinite-scheduler:1.0.0
```

### Kubernetes
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: scheduler-example-prod
spec:
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
        - name: MYSQL_PASSWORD
          valueFrom:
            secretKeyRef:
              name: scheduler-secrets
              key: mysql-password
```

### Systemd Service
```ini
[Unit]
Description=Infinite Scheduler - Example Tenant (Production)
After=network.target

[Service]
Type=simple
User=scheduler
Environment="SCHEDULER_TENANT_NAME=example"
Environment="SCHEDULER_TENANT_PROFILE=prod"
Environment="MYSQL_PASSWORD=from_vault_or_secrets"
ExecStart=/usr/bin/java -jar /opt/infinite-scheduler/app.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

---

## Multi-Tenant Deployment

### Same Host, Different Ports
```bash
# Terminal 1: Tenant example on port 7070
SCHEDULER_TENANT_NAME=example \
SCHEDULER_TENANT_PROFILE=prod \
java -jar target/app.jar

# Terminal 2: Tenant1 on port 7071
SCHEDULER_TENANT_NAME=tenant1 \
SCHEDULER_TENANT_PROFILE=prod \
java -jar target/app.jar
```

### Kubernetes (Multiple Tenants)
```bash
# Deploy example tenant
kubectl apply -f deployments/example-prod.yaml

# Deploy tenant1
kubectl apply -f deployments/tenant1-prod.yaml
```

---

## Troubleshooting

### Check what config is loaded
```bash
mvn quarkus:dev 2>&1 | grep -A 10 "Tenant Instance"

# Expected output:
#   ┌──────────────────────────────────────┐
#   │ Tenant Instance                      │
#   ├──────────────────────────────────────┤
#   │ Tenant:   example
#   │ Profile:  dev
#   └──────────────────────────────────────┘
```

### Verify tenant YAML exists
```bash
ls -la src/main/resources/config/tenants/example/dev/profile-dev.yml
```

### Test database connection
```bash
# Get credentials from tenant YAML
mysql -h 127.0.0.1 -u root -p123456 -D scheduler_example_dev -e "SELECT 1;"
```

---

## Common Patterns

### Dev/Staging/Prod for Single Tenant
```properties
# Development
scheduler.tenant.name=example
scheduler.tenant.profile=dev

# Staging
scheduler.tenant.name=example
scheduler.tenant.profile=staging

# Production
scheduler.tenant.name=example
scheduler.tenant.profile=prod
```

### Multiple Tenants in Production
```properties
# Tenant: example
scheduler.tenant.name=example
scheduler.tenant.profile=prod

# Tenant: tenant1
scheduler.tenant.name=tenant1
scheduler.tenant.profile=prod
```

---

## Security Best Practices

### Use Environment Variables for Passwords
```yaml
# In profile YAML
mysql:
  password: "${MYSQL_PASSWORD:change_me}"

# At runtime
export MYSQL_PASSWORD=secure_password
```

### Separate Databases per Tenant
```yaml
# Tenant: example
mysql:
  database: "scheduler_example_prod"

# Tenant: tenant1
mysql:
  database: "scheduler_tenant1_prod"
```

### Unique Kafka Topics per Tenant
```yaml
# Tenant: example
kafka:
  ingest:
    topic: "Job_Schedule_Example"

# Tenant: tenant1
kafka:
  ingest:
    topic: "Job_Schedule_Tenant1"
```

---

## Files

- **Full Guide:** `MULTI_TENANT_CONFIGURATION.md`
- **Quarkus Guide:** `QUARKUS_STARTUP_GUIDE.md`
- **Quick Start:** `QUICK_START_QUARKUS.md`
- **Example Configs:**
  - `config/tenants/example/dev/profile-dev.yml`
  - `config/tenants/example/prod/profile-prod.yml`
  - `config/tenants/tenant1/dev/profile-dev.yml`

---

## Summary

**Configuration Path:**
```
config/tenants/{tenant-name}/{profile}/profile-{profile}.yml
```

**Select Tenant:**
```bash
export SCHEDULER_TENANT_NAME=example
export SCHEDULER_TENANT_PROFILE=dev
```

**Run:**
```bash
mvn quarkus:dev
```

**That's it!** 🚀
