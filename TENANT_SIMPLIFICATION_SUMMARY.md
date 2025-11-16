# Tenant Configuration Simplification

## Overview

The tenant configuration system has been simplified to use **only tenant name** instead of tenant name + profile. This makes configuration cleaner and easier to manage.

---

## What Changed

### Before: Tenant Name + Profile
```properties
scheduler.tenant.name=${SCHEDULER_TENANT_NAME:example}
scheduler.tenant.profile=${SCHEDULER_TENANT_PROFILE:dev}
```

**Config Path:** `config/tenants/{tenant}/{profile}/profile-{profile}.yml`

**Example:**
- `config/tenants/example/dev/profile-dev.yml`
- `config/tenants/example/prod/profile-prod.yml`

### After: Tenant Name Only
```properties
scheduler.tenant.name=${SCHEDULER_TENANT_NAME:link3}
```

**Config Path:** `config/tenants/{tenant}/config.yml`

**Example:**
- `config/tenants/link3/config.yml`

---

## File Changes

### 1. application.properties

**Before:**
```properties
scheduler.tenant.name=${SCHEDULER_TENANT_NAME:link3}
scheduler.tenant.profile=${SCHEDULER_TENANT_PROFILE:dev}
```

**After:**
```properties
scheduler.tenant.name=${SCHEDULER_TENANT_NAME:link3}
```

**Lines:** 46 lines (even cleaner)

### 2. TenantConfigSource.java

**Updated to:**
- Look for `config/tenants/{tenant}/config.yml` instead of `config/tenants/{tenant}/{profile}/profile-{profile}.yml`
- Removed `getTenantProfile()` method
- Simplified tenant loading logic
- Updated error messages

**Key Changes:**
```java
// Before
String yamlPath = "config/tenants/" + tenantName + "/" + tenantProfile + "/profile-" + tenantProfile + ".yml";

// After
String yamlPath = "config/tenants/" + tenantName + "/config.yml";
```

### 3. InfiniteSchedulerStartup.java

**Simplified startup banner:**
```
Before:
  ┌─────────────────────────────────────────────────────────────┐
  │ Tenant Instance                                             │
  ├─────────────────────────────────────────────────────────────┤
  │ Tenant:   example
  │ Profile:  dev
  └─────────────────────────────────────────────────────────────┘

After:
  ┌─────────────────────────────────────────────────────────────┐
  │ Tenant: link3
  └─────────────────────────────────────────────────────────────┘
```

### 4. Tenant Configuration Files

**Link3 tenant updated:**
- Created: `config/tenants/link3/config.yml`
- Removed profile metadata

**Before (in config.yml):**
```yaml
infinite-scheduler:
  tenant:
    name: "link3"
    profile: "dev"  # ← Removed
    description: "Link3 tenant for development"
```

**After:**
```yaml
infinite-scheduler:
  tenant:
    name: "link3"
    description: "Link3 tenant instance"
```

---

## Usage

### Running with Link3 Tenant

**Default (uses link3):**
```bash
mvn quarkus:dev
```

**Explicit:**
```bash
export SCHEDULER_TENANT_NAME=link3
mvn quarkus:dev
```

**Override:**
```bash
export SCHEDULER_TENANT_NAME=example
mvn quarkus:dev
```

**System Property:**
```bash
mvn quarkus:dev -Dscheduler.tenant.name=tenant1
```

---

## Multiple Environments Pattern

Since we no longer have profiles, you can handle multiple environments in two ways:

### Option 1: Environment-Specific Tenant Names

Create separate tenant configurations for each environment:

```
config/tenants/
├── link3-dev/config.yml      # Development
├── link3-staging/config.yml  # Staging
└── link3-prod/config.yml     # Production
```

**Select environment:**
```bash
# Development
export SCHEDULER_TENANT_NAME=link3-dev

# Staging
export SCHEDULER_TENANT_NAME=link3-staging

# Production
export SCHEDULER_TENANT_NAME=link3-prod
```

### Option 2: Environment Variables in Config

Use a single tenant config with environment variable placeholders:

```yaml
# config/tenants/link3/config.yml
infinite-scheduler:
  mysql:
    host: "${MYSQL_HOST:127.0.0.1}"
    database: "${MYSQL_DATABASE:scheduler_link3_dev}"
    username: "${MYSQL_USERNAME:root}"
    password: "${MYSQL_PASSWORD:123456}"
```

**Development:**
```bash
export SCHEDULER_TENANT_NAME=link3
export MYSQL_HOST=127.0.0.1
export MYSQL_DATABASE=scheduler_link3_dev
mvn quarkus:dev
```

**Production:**
```bash
export SCHEDULER_TENANT_NAME=link3
export MYSQL_HOST=10.10.199.10
export MYSQL_DATABASE=scheduler_link3_prod
export MYSQL_PASSWORD=secure_password
java -jar target/quarkus-app/quarkus-run.jar
```

---

## Migration Guide

### For Existing Multi-Profile Tenants

If you have existing tenants with dev/prod/staging profiles:

**Example tenant:**
```
config/tenants/example/
├── dev/profile-dev.yml
├── prod/profile-prod.yml
└── staging/profile-staging.yml
```

**Option 1: Keep most common profile**
```bash
# Use dev profile as base
cp config/tenants/example/dev/profile-dev.yml config/tenants/example/config.yml
```

**Option 2: Create environment-specific tenants**
```bash
# Create separate tenants
mkdir -p config/tenants/{example-dev,example-staging,example-prod}
cp config/tenants/example/dev/profile-dev.yml config/tenants/example-dev/config.yml
cp config/tenants/example/staging/profile-staging.yml config/tenants/example-staging/config.yml
cp config/tenants/example/prod/profile-prod.yml config/tenants/example-prod/config.yml
```

---

## Benefits

### 1. ✅ Simpler Configuration
- Only one parameter to set: tenant name
- No need to remember profile names
- Clearer intent

### 2. ✅ Cleaner application.properties
- From 47 lines to 46 lines
- One less configuration property
- Easier to understand

### 3. ✅ Flexible Environment Handling
- Can use tenant naming: `link3-dev`, `link3-prod`
- Can use environment variables in config
- Mix and match as needed

### 4. ✅ Easier Deployment
- Single environment variable: `SCHEDULER_TENANT_NAME`
- No confusion about which profile to use
- Clear tenant selection

### 5. ✅ Better for Multi-Tenant SaaS
- Each tenant = one configuration
- No profile overhead
- Tenant name is the identity

---

## Current Tenant Structure

### Link3 (Simplified)
```
config/tenants/link3/
├── config.yml           ← Active (simplified)
├── dev/
│   └── profile-dev.yml  ← Legacy (can be removed)
├── prod/
│   └── profile-prod.yml ← Legacy (can be removed)
└── staging/
    └── profile-staging.yml ← Legacy (can be removed)
```

### Example & Tenant1 (Legacy Structure)
```
config/tenants/example/
├── dev/profile-dev.yml
├── prod/profile-prod.yml
└── staging/profile-staging.yml

config/tenants/tenant1/
├── dev/profile-dev.yml
└── prod/profile-prod.yml
```

**Note:** Example and Tenant1 still use the old structure and need to be migrated if you want to use them.

---

## Configuration Priority

1. **System Property** (highest)
   ```bash
   -Dscheduler.tenant.name=link3
   ```

2. **Environment Variable**
   ```bash
   export SCHEDULER_TENANT_NAME=link3
   ```

3. **Tenant Config YAML**
   ```yaml
   # config/tenants/link3/config.yml
   infinite-scheduler:
     mysql:
       host: "127.0.0.1"
   ```

4. **application.properties** (lowest)
   ```properties
   scheduler.tenant.name=${SCHEDULER_TENANT_NAME:link3}
   ```

---

## Startup Output

**New Simplified Output:**
```
[TenantConfigSource] ════════════════════════════════════════════
[TenantConfigSource] Loading tenant configuration:
[TenantConfigSource]   Tenant:  link3
[TenantConfigSource]   Loaded 127 configuration properties
[TenantConfigSource] ════════════════════════════════════════════

╔════════════════════════════════════════════════════════════════╗
║     INFINITE SCHEDULER - QUARKUS STARTUP                       ║
╚════════════════════════════════════════════════════════════════╝

📋 Configuration:
  ┌─────────────────────────────────────────────────────────────┐
  │ Tenant: link3
  └─────────────────────────────────────────────────────────────┘
```

---

## Build Status

**✅ BUILD SUCCESS**

```
[INFO] Building Infinite Scheduler 1.0.0
[INFO] Compiling 54 source files
[INFO] BUILD SUCCESS
[INFO] Total time: 7.473 s
```

---

## Summary

**The tenant configuration is now simplified:**

✅ **One parameter** - Only `scheduler.tenant.name`
✅ **Simpler path** - `config/tenants/{tenant}/config.yml`
✅ **Cleaner code** - Removed profile handling
✅ **Flexible** - Can still handle multiple environments
✅ **Easier to use** - Single environment variable
✅ **Better DX** - Less configuration overhead

**To use:**
```bash
export SCHEDULER_TENANT_NAME=link3
mvn quarkus:dev
```

**That's it!** 🎉
