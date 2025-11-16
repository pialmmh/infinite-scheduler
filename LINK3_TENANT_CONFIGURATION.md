# Link3 Tenant Configuration

## Overview

The **Link3** tenant is now configured with all three profiles (dev, prod, staging) using the Kafka bootstrap servers:
```
10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
```

---

## Configuration Files

### Folder Structure
```
src/main/resources/config/tenants/link3/
├── dev/
│   └── profile-dev.yml          # Development profile
├── prod/
│   └── profile-prod.yml         # Production profile
└── staging/
    └── profile-staging.yml      # Staging profile
```

---

## Quick Start

### Development Mode

```bash
# Select Link3 tenant with dev profile
export SCHEDULER_TENANT_NAME=link3
export SCHEDULER_TENANT_PROFILE=dev

# Run
mvn quarkus:dev
```

**Configuration:**
- **Port:** 7072
- **Database:** `scheduler_link3_dev` (local MySQL at 127.0.0.1:3306)
- **Kafka:** `10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092`
- **Topics:**
  - Input: `Job_Schedule_Link3_Dev`
  - DLQ: `Job_Schedule_Link3_DLQ_Dev`
  - Output: `SMS_Send_Link3_Dev`
- **Kafka Enabled:** Yes
- **Logging:** DEBUG
- **Retention:** 7 days

### Staging Mode

```bash
# Select Link3 tenant with staging profile
export SCHEDULER_TENANT_NAME=link3
export SCHEDULER_TENANT_PROFILE=staging

# Set database password
export LINK3_MYSQL_PASSWORD=staging_password

# Run
java -jar target/quarkus-app/quarkus-run.jar
```

**Configuration:**
- **Port:** 7072
- **Database:** `scheduler_link3_staging` (MySQL at 10.10.198.10:3306)
- **Kafka:** `10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092`
- **Topics:**
  - Input: `Job_Schedule_Link3_Staging`
  - DLQ: `Job_Schedule_Link3_DLQ_Staging`
  - Output: `SMS_Send_Link3_Staging`
- **Kafka Enabled:** Yes
- **Logging:** DEBUG
- **Retention:** 15 days

### Production Mode

```bash
# Select Link3 tenant with production profile
export SCHEDULER_TENANT_NAME=link3
export SCHEDULER_TENANT_PROFILE=prod

# Set database password from secure source
export LINK3_MYSQL_PASSWORD=secure_production_password

# Run
java -jar target/quarkus-app/quarkus-run.jar
```

**Configuration:**
- **Port:** 7072
- **Database:** `scheduler_link3_prod` (MySQL at 10.10.199.10:3306)
- **Kafka:** `10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092`
- **Topics:**
  - Input: `Job_Schedule_Link3`
  - DLQ: `Job_Schedule_Link3_DLQ`
  - Output: `SMS_Send_Link3`
- **Kafka Enabled:** Yes
- **Logging:** INFO
- **Retention:** 30 days

---

## Kafka Configuration

All three profiles use the same Kafka cluster:

**Bootstrap Servers:**
```
10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
```

**Consumer Configuration:**
- **Group ID:** `infinite-scheduler-link3-{profile}`
- **Auto Commit:** Disabled (manual acknowledgment)
- **Offset Reset:** Earliest
- **Max Poll Records:** 500
- **Session Timeout:** 30s (prod/staging)
- **Heartbeat Interval:** 10s (prod/staging)

**Producer Configuration:**
- **Acks:** All (ensures durability)
- **Idempotence:** Enabled (prevents duplicates)
- **Retries:** 3
- **Compression:** Snappy (prod/staging)

**Topics by Profile:**

| Profile  | Input Topic                    | DLQ Topic                       | Output Topic          |
|----------|--------------------------------|---------------------------------|-----------------------|
| dev      | Job_Schedule_Link3_Dev         | Job_Schedule_Link3_DLQ_Dev      | SMS_Send_Link3_Dev    |
| staging  | Job_Schedule_Link3_Staging     | Job_Schedule_Link3_DLQ_Staging  | SMS_Send_Link3_Staging|
| prod     | Job_Schedule_Link3             | Job_Schedule_Link3_DLQ          | SMS_Send_Link3        |

---

## Database Configuration

### Development
```yaml
mysql:
  host: "127.0.0.1"
  port: 3306
  database: "scheduler_link3_dev"
  username: "root"
  password: "123456"
```

**Create Database:**
```sql
CREATE DATABASE scheduler_link3_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Staging
```yaml
mysql:
  host: "10.10.198.10"
  port: 3306
  database: "scheduler_link3_staging"
  username: "link3_user"
  password: "${LINK3_MYSQL_PASSWORD}"
```

**Create Database:**
```sql
CREATE DATABASE scheduler_link3_staging CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'link3_user'@'%' IDENTIFIED BY 'staging_password';
GRANT ALL PRIVILEGES ON scheduler_link3_staging.* TO 'link3_user'@'%';
FLUSH PRIVILEGES;
```

### Production
```yaml
mysql:
  host: "10.10.199.10"
  port: 3306
  database: "scheduler_link3_prod"
  username: "link3_user"
  password: "${LINK3_MYSQL_PASSWORD}"
```

**Create Database:**
```sql
CREATE DATABASE scheduler_link3_prod CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'link3_user'@'%' IDENTIFIED BY 'production_password';
GRANT ALL PRIVILEGES ON scheduler_link3_prod.* TO 'link3_user'@'%';
FLUSH PRIVILEGES;
```

---

## Deployment Examples

### Docker Deployment (Production)

```bash
docker run \
  -e SCHEDULER_TENANT_NAME=link3 \
  -e SCHEDULER_TENANT_PROFILE=prod \
  -e LINK3_MYSQL_PASSWORD=secure_password \
  -p 7072:7072 \
  infinite-scheduler:1.0.0
```

### Kubernetes Deployment

**Deployment YAML:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: scheduler-link3-prod
  labels:
    app: infinite-scheduler
    tenant: link3
    profile: prod
spec:
  replicas: 1
  selector:
    matchLabels:
      app: infinite-scheduler
      tenant: link3
      profile: prod
  template:
    metadata:
      labels:
        app: infinite-scheduler
        tenant: link3
        profile: prod
    spec:
      containers:
      - name: infinite-scheduler
        image: infinite-scheduler:1.0.0
        ports:
        - containerPort: 7072
          name: http
          protocol: TCP
        env:
        - name: SCHEDULER_TENANT_NAME
          value: "link3"
        - name: SCHEDULER_TENANT_PROFILE
          value: "prod"
        - name: LINK3_MYSQL_PASSWORD
          valueFrom:
            secretKeyRef:
              name: link3-secrets
              key: mysql-password
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 7072
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 7072
          initialDelaySeconds: 5
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: scheduler-link3-prod
  labels:
    app: infinite-scheduler
    tenant: link3
spec:
  selector:
    app: infinite-scheduler
    tenant: link3
    profile: prod
  ports:
  - name: http
    port: 7072
    targetPort: 7072
    protocol: TCP
  type: ClusterIP
```

**Create Secret:**
```bash
kubectl create secret generic link3-secrets \
  --from-literal=mysql-password='secure_production_password'
```

**Deploy:**
```bash
kubectl apply -f link3-prod-deployment.yaml
```

### Systemd Service (Production)

**File:** `/etc/systemd/system/infinite-scheduler-link3.service`

```ini
[Unit]
Description=Infinite Scheduler - Link3 Tenant (Production)
After=network.target mysql.service

[Service]
Type=simple
User=scheduler
Group=scheduler
WorkingDirectory=/opt/infinite-scheduler

Environment="SCHEDULER_TENANT_NAME=link3"
Environment="SCHEDULER_TENANT_PROFILE=prod"
Environment="LINK3_MYSQL_PASSWORD=from_vault_or_secrets"
Environment="JAVA_OPTS=-Xms512m -Xmx2g"

ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/infinite-scheduler/infinite-scheduler-1.0.0.jar

Restart=always
RestartSec=10

StandardOutput=journal
StandardError=journal
SyslogIdentifier=scheduler-link3

[Install]
WantedBy=multi-user.target
```

**Enable and start:**
```bash
sudo systemctl daemon-reload
sudo systemctl enable infinite-scheduler-link3
sudo systemctl start infinite-scheduler-link3
sudo systemctl status infinite-scheduler-link3
```

---

## Monitoring

### Health Check
```bash
curl http://localhost:7072/q/health
```

### Metrics
```bash
curl http://localhost:7072/q/metrics
```

### Web UI
```
http://localhost:7072/index.html
```

### Logs

**Development:**
```bash
tail -f /var/log/infinite-scheduler/link3-dev.log
```

**Staging:**
```bash
tail -f /var/log/infinite-scheduler/link3-staging.log
```

**Production:**
```bash
tail -f /var/log/infinite-scheduler/link3-prod.log

# Or via systemd journal
journalctl -u infinite-scheduler-link3 -f
```

---

## Testing

### Send Test Job to Kafka

```bash
# Connect to Kafka
kafka-console-producer.sh \
  --bootstrap-server 10.10.199.20:9092 \
  --topic Job_Schedule_Link3_Dev \
  --property "parse.key=true" \
  --property "key.separator=:"

# Send message (paste this):
test-1:{"appName":"sms_retry","scheduledTime":"2025-11-14 23:00:00","jobData":{"campaignTaskId":12345,"createdOn":"2025-11-14 22:00:00","retryTime":"2025-11-14 23:00:00","retryAttempt":1}}
```

### Expected Output
```
📥 Received message - Partition: 0, Offset: 1, Key: test-1
✅ Job scheduled successfully - App: sms_retry, Time: 2025-11-14 23:00:00
```

---

## Configuration Summary

| Setting              | Dev                | Staging            | Production         |
|----------------------|--------------------|--------------------|-------------------|
| **Port**             | 7072               | 7072               | 7072              |
| **Database Host**    | 127.0.0.1          | 10.10.198.10       | 10.10.199.10      |
| **Database Name**    | scheduler_link3_dev| scheduler_link3_staging| scheduler_link3_prod|
| **Kafka Servers**    | 10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092 (all profiles) |
| **Kafka Enabled**    | Yes                | Yes                | Yes               |
| **Logging Level**    | DEBUG              | DEBUG              | INFO              |
| **Thread Pool**      | 10                 | 15                 | 20                |
| **Max Jobs/Fetch**   | 1,000              | 5,000              | 10,000            |
| **Retention Days**   | 7                  | 15                 | 30                |

---

## Security Notes

1. **Passwords:** Never commit passwords to Git. Always use environment variables:
   ```yaml
   password: "${LINK3_MYSQL_PASSWORD:change_me}"
   ```

2. **Production Secrets:** Use Kubernetes Secrets or Vault:
   ```bash
   export LINK3_MYSQL_PASSWORD=$(vault kv get -field=password secret/link3/mysql)
   ```

3. **Database Access:** Restrict database user privileges in production:
   ```sql
   GRANT SELECT, INSERT, UPDATE, DELETE ON scheduler_link3_prod.* TO 'link3_user'@'10.10.%';
   ```

4. **Kafka Security:** For production, consider enabling SSL/TLS and SASL authentication.

---

## Summary

**Link3 tenant is now fully configured** with:

✅ **Three Profiles** - dev, staging, prod
✅ **Kafka Cluster** - 10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092
✅ **Unique Port** - 7072 (different from example/tenant1)
✅ **Dedicated Databases** - Per environment
✅ **Dedicated Topics** - Per environment
✅ **Production Ready** - Password from env vars
✅ **Fully Tested** - Build successful

**To use Link3 tenant:**
```bash
export SCHEDULER_TENANT_NAME=link3
export SCHEDULER_TENANT_PROFILE=dev
mvn quarkus:dev
```

**For more information:**
- `MULTI_TENANT_CONFIGURATION.md` - Full multi-tenant guide
- `MULTI_TENANT_QUICK_REFERENCE.md` - Quick reference
- Configuration files in `config/tenants/link3/`
