# Database Setup Guide

## Database Name

The database name depends on the **enabled tenant** and **profile** in `application.properties`.

### Current Configuration

**Enabled Tenant:**
```properties
tenants[0].name=link3
tenants[0].enabled=true
tenants[0].profile=dev
```

**Database Name:**
```
scheduler_link3_dev
```

**Location:** MySQL at `127.0.0.1:3306`

---

## Database Naming Convention

### Pattern
```
scheduler_{tenant}_{profile}
```

### Examples

| Tenant  | Profile | Database Name              |
|---------|---------|----------------------------|
| link3   | dev     | `scheduler_link3_dev`      |
| link3   | prod    | `scheduler_link3_prod`     |
| link3   | staging | `scheduler_link3_staging`  |
| example | dev     | `scheduler_example_dev`    |
| example | prod    | `scheduler_example_prod`   |
| tenant1 | dev     | `scheduler_tenant1_dev`    |
| tenant1 | prod    | `scheduler_tenant1_prod`   |

---

## When Is Database Created?

**The database is NOT created automatically.**

You must create the database **manually** before starting the scheduler.

---

## How to Create Database

### Step 1: Connect to MySQL

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p123456
```

### Step 2: Create Database

```sql
CREATE DATABASE scheduler_link3_dev
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### Step 3: Grant Permissions (Optional)

If using a dedicated user:

```sql
-- Create user (if not exists)
CREATE USER IF NOT EXISTS 'scheduler_user'@'%' IDENTIFIED BY 'password';

-- Grant permissions
GRANT ALL PRIVILEGES ON scheduler_link3_dev.* TO 'scheduler_user'@'%';

-- Apply changes
FLUSH PRIVILEGES;
```

### Step 4: Verify Database

```sql
SHOW DATABASES LIKE 'scheduler_%';
USE scheduler_link3_dev;
SHOW TABLES;
```

---

## What Gets Created in the Database?

### Automatic Table Creation

When the scheduler starts, it automatically creates:

#### 1. Quartz Scheduler Tables (11 tables)
- `QRTZ_JOB_DETAILS` - Job definitions
- `QRTZ_TRIGGERS` - Trigger definitions
- `QRTZ_SIMPLE_TRIGGERS` - Simple trigger data
- `QRTZ_CRON_TRIGGERS` - Cron trigger data
- `QRTZ_SIMPROP_TRIGGERS` - Simple property triggers
- `QRTZ_BLOB_TRIGGERS` - Blob triggers
- `QRTZ_CALENDARS` - Calendar data
- `QRTZ_PAUSED_TRIGGER_GRPS` - Paused trigger groups
- `QRTZ_FIRED_TRIGGERS` - Currently firing triggers
- `QRTZ_SCHEDULER_STATE` - Scheduler state
- `QRTZ_LOCKS` - Pessimistic locking

#### 2. Split-Verse Tables (Dynamic)
- `scheduled_jobs_YYYYMMDD` - Time-based partitioned tables
- Created automatically as needed (one table per day/week/month based on config)

**Example:**
```
scheduled_jobs_20251115  ← Created for Nov 15, 2025
scheduled_jobs_20251116  ← Created for Nov 16, 2025
```

---

## Database Setup for All Tenants

### Link3 Tenant

**Development:**
```sql
CREATE DATABASE scheduler_link3_dev
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

**Staging:**
```sql
CREATE DATABASE scheduler_link3_staging
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

**Production:**
```sql
CREATE DATABASE scheduler_link3_prod
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Create production user
CREATE USER 'link3_user'@'%' IDENTIFIED BY 'secure_password';
GRANT ALL PRIVILEGES ON scheduler_link3_prod.* TO 'link3_user'@'%';
FLUSH PRIVILEGES;
```

### Example Tenant

**Development:**
```sql
CREATE DATABASE scheduler_example_dev
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

**Production:**
```sql
CREATE DATABASE scheduler_example_prod
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### Tenant1

**Development:**
```sql
CREATE DATABASE scheduler_tenant1_dev
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

**Production:**
```sql
CREATE DATABASE scheduler_tenant1_prod
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

---

## Quick Setup Script

### Create Link3 Dev Database (Current Configuration)

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p123456 -e "
CREATE DATABASE IF NOT EXISTS scheduler_link3_dev
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
"

echo "Database 'scheduler_link3_dev' created successfully!"
```

### Verify Database

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p123456 -e "
USE scheduler_link3_dev;
SELECT
  'Database exists' AS status,
  DATABASE() AS current_database,
  @@character_set_database AS charset,
  @@collation_database AS collation;
"
```

---

## First Run Behavior

### What Happens on First Start

1. **Quarkus starts** and loads tenant configuration
2. **TenantConfigSource** loads: `config/tenants/link3/dev/profile-dev.yml`
3. **Scheduler connects** to MySQL: `127.0.0.1:3306/scheduler_link3_dev`
4. **Quartz initializes** and creates its 11 tables (if they don't exist)
5. **Split-Verse initializes** and creates time-partitioned tables as needed
6. **Scheduler is ready** to accept jobs

### Expected Output

```
[TenantConfigSource] Loading tenant configuration:
[TenantConfigSource]   Tenant:  link3
[TenantConfigSource]   Profile: dev

📋 Configuration:
  ┌─────────────────────────────────────────────────────────────┐
  │ Tenant Instance                                             │
  ├─────────────────────────────────────────────────────────────┤
  │ Tenant:   link3
  │ Profile:  dev
  └─────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────┐
  │ MySQL Database                                              │
  ├─────────────────────────────────────────────────────────────┤
  │ Host:     127.0.0.1:3306
  │ Database: scheduler_link3_dev
  │ Username: root
  └─────────────────────────────────────────────────────────────┘

✅ SCHEDULER STARTED SUCCESSFULLY
```

---

## Troubleshooting

### Error: "Unknown database 'scheduler_link3_dev'"

**Problem:** Database doesn't exist

**Solution:**
```sql
CREATE DATABASE scheduler_link3_dev
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### Error: "Access denied for user 'root'@'localhost'"

**Problem:** Wrong credentials

**Solution:**
1. Check password in `config/tenants/link3/dev/profile-dev.yml`
2. Verify MySQL user exists and has permissions:
```sql
SELECT user, host FROM mysql.user WHERE user='root';
GRANT ALL PRIVILEGES ON scheduler_link3_dev.* TO 'root'@'%';
FLUSH PRIVILEGES;
```

### Error: "Communications link failure"

**Problem:** MySQL not running or wrong host/port

**Solution:**
1. Check MySQL is running:
```bash
systemctl status mysql
```

2. Verify connection:
```bash
mysql -h 127.0.0.1 -P 3306 -u root -p123456 -e "SELECT 1;"
```

3. Check configuration in tenant YAML

---

## Database Maintenance

### View Current Tables

```sql
USE scheduler_link3_dev;
SHOW TABLES;
```

### View Quartz Jobs

```sql
SELECT
  JOB_NAME,
  JOB_GROUP,
  JOB_CLASS_NAME,
  DESCRIPTION
FROM QRTZ_JOB_DETAILS;
```

### View Scheduled Triggers

```sql
SELECT
  t.TRIGGER_NAME,
  t.TRIGGER_GROUP,
  t.TRIGGER_STATE,
  t.NEXT_FIRE_TIME,
  FROM_UNIXTIME(t.NEXT_FIRE_TIME/1000) AS next_fire_readable
FROM QRTZ_TRIGGERS t;
```

### View Split-Verse Tables

```sql
SELECT
  TABLE_NAME,
  CREATE_TIME,
  TABLE_ROWS
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'scheduler_link3_dev'
  AND TABLE_NAME LIKE 'scheduled_jobs_%'
ORDER BY TABLE_NAME DESC;
```

### Cleanup Old Tables

The scheduler automatically cleans up old partitioned tables based on retention policy (7 days for dev).

**Manual cleanup:**
```sql
-- Drop tables older than 7 days
DROP TABLE IF EXISTS scheduled_jobs_20251101;
DROP TABLE IF EXISTS scheduled_jobs_20251102;
-- etc.
```

---

## Summary

**Database Name:** `scheduler_link3_dev`

**When Created:** **Manually before first start**

**Command:**
```bash
mysql -h 127.0.0.1 -P 3306 -u root -p123456 -e "
CREATE DATABASE scheduler_link3_dev
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
"
```

**Tables Created:** Automatically by Quartz (11 tables) and Split-Verse (dynamic tables)

**To Start Scheduler:**
```bash
# After creating database
mvn quarkus:dev
```

**Database is ready!** 🎉
