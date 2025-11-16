# RouteSphere-Core Pattern Implementation

## Overview

The Infinite Scheduler now follows the **exact same pattern** as RouteSphere Core for tenant configuration. This ensures consistency across all TelcoBright projects.

---

## Configuration Pattern

### application.properties

Following the RouteSphere Core pattern exactly:

```properties
# Infinite Scheduler - Multi-Tenant Configuration
# This file ONLY defines which tenants are enabled and their profiles
# All other configuration (MySQL, Kafka, etc.) is in tenant-specific profile files

# Application Info
quarkus.application.name=infinite-scheduler
quarkus.application.version=1.0.0

# Quarkus HTTP Server Configuration (default server)
quarkus.http.host=0.0.0.0
quarkus.http.port=7070

# Package as uber-jar (single JAR with all dependencies included)
quarkus.package.type=uber-jar

# ==========================================================
# Tenant Configuration
# ==========================================================
# Only ONE tenant should be enabled at a time
# Tenant config path: config/tenants/{name}/{profile}/profile-{profile}.yml

tenants[0].name=link3
tenants[0].enabled=true
tenants[0].profile=dev

tenants[1].name=example
tenants[1].enabled=false
tenants[1].profile=dev

tenants[2].name=tenant1
tenants[2].enabled=false
tenants[2].profile=dev

# Logging Configuration
quarkus.log.category."com.telcobright.scheduler".level=INFO
quarkus.log.category."org.quartz".level=WARN
quarkus.log.category."org.apache.kafka".level=WARN
quarkus.kafka.devservices.enabled=false
```

---

## Tenant Configuration Structure

### Folder Structure

```
src/main/resources/config/tenants/
├── link3/
│   ├── dev/
│   │   └── profile-dev.yml
│   ├── prod/
│   │   └── profile-prod.yml
│   └── staging/
│       └── profile-staging.yml
├── example/
│   ├── dev/
│   │   └── profile-dev.yml
│   ├── prod/
│   │   └── profile-prod.yml
│   └── staging/
│       └── profile-staging.yml
└── tenant1/
    ├── dev/
    │   └── profile-dev.yml
    └── prod/
        └── profile-prod.yml
```

### Profile YAML Structure

**Example: `config/tenants/link3/dev/profile-dev.yml`**

```yaml
# Link3 Tenant - Development Profile
# Development environment settings for Link3 tenant

infinite-scheduler:
  tenant:
    name: "link3"
    profile: "dev"
    description: "Link3 tenant for development and testing"
    enabled: true

  # MySQL Database Configuration
  mysql:
    host: "127.0.0.1"
    port: 3306
    database: "scheduler_link3_dev"
    username: "root"
    password: "123456"

  # Kafka Configuration
  kafka:
    bootstrap-servers: "10.10.199.20:9092,10.10.198.20:9092,10.10.197.20:9092"
    ingest:
      enabled: true
      topic: "Job_Schedule_Link3_Dev"
      dlq-topic: "Job_Schedule_Link3_DLQ_Dev"

  # ... other configuration sections

# Quarkus Configuration
quarkus:
  http:
    port: 7072
  datasource:
    db-kind: mysql
    username: "root"
    password: "123456"
    jdbc:
      url: "jdbc:mysql://127.0.0.1:3306/scheduler_link3_dev?..."

# SmallRye Reactive Messaging
mp:
  messaging:
    incoming:
      job-schedule:
        connector: smallrye-kafka
        topic: "Job_Schedule_Link3_Dev"
        bootstrap.servers: "10.10.199.20:9092,..."
```

---

## How It Works

### 1. TenantConfigSource

Custom Quarkus ConfigSource that:
- Reads `application.properties` at startup
- Finds the enabled tenant (where `tenants[x].enabled=true`)
- Loads the corresponding profile YAML file
- Exposes all configuration properties to Quarkus

**Implementation:**
```java
// Search for enabled tenant
for (int i = 0; i < 10; i++) {
    String name = appProps.getProperty("tenants[" + i + "].name");
    String enabled = appProps.getProperty("tenants[" + i + "].enabled");
    String profile = appProps.getProperty("tenants[" + i + "].profile");

    if (name != null && "true".equalsIgnoreCase(enabled)) {
        tenantName = name;
        tenantProfile = profile;
        break;
    }
}

// Load profile YAML
String yamlPath = "config/tenants/" + tenantName + "/" + tenantProfile + "/profile-" + tenantProfile + ".yml";
```

### 2. Registered ConfigSource

**File:** `META-INF/services/org.eclipse.microprofile.config.spi.ConfigSource`

```
com.telcobright.scheduler.config.TenantConfigSource
```

### 3. Startup Banner

Displays tenant and profile information:

```
[TenantConfigSource] ════════════════════════════════════════════
[TenantConfigSource] Loading tenant configuration:
[TenantConfigSource]   Tenant:  link3
[TenantConfigSource]   Profile: dev
[TenantConfigSource]   Loaded 127 configuration properties
[TenantConfigSource] ════════════════════════════════════════════

╔════════════════════════════════════════════════════════════════╗
║     INFINITE SCHEDULER - QUARKUS STARTUP                       ║
╚════════════════════════════════════════════════════════════════╝

📋 Configuration:
  ┌─────────────────────────────────────────────────────────────┐
  │ Tenant Instance                                             │
  ├─────────────────────────────────────────────────────────────┤
  │ Tenant:   link3
  │ Profile:  dev
  └─────────────────────────────────────────────────────────────┘
```

---

## Usage

### Switch Between Tenants

**Option 1: Edit application.properties**

```properties
# Enable link3
tenants[0].name=link3
tenants[0].enabled=true
tenants[0].profile=dev

# Disable others
tenants[1].enabled=false
tenants[2].enabled=false
```

**Option 2: Use Different Profiles**

```properties
# Switch to production
tenants[0].name=link3
tenants[0].enabled=true
tenants[0].profile=prod  # ← Changed to prod
```

**Option 3: Switch Tenant**

```properties
# Enable example tenant
tenants[0].enabled=false

tenants[1].name=example
tenants[1].enabled=true   # ← Enabled
tenants[1].profile=dev
```

### Running the Scheduler

```bash
# Development mode
mvn quarkus:dev

# Production mode
mvn clean package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

---

## Adding New Tenants

### Step 1: Create Tenant Folders

```bash
mkdir -p src/main/resources/config/tenants/newtenant/{dev,prod,staging}
```

### Step 2: Create Profile YAMLs

Copy from an existing tenant and modify:

```bash
cp config/tenants/link3/dev/profile-dev.yml \
   config/tenants/newtenant/dev/profile-dev.yml
```

Edit `newtenant/dev/profile-dev.yml`:
```yaml
infinite-scheduler:
  tenant:
    name: "newtenant"
    profile: "dev"
    description: "New tenant for development"

  mysql:
    database: "scheduler_newtenant_dev"

  kafka:
    ingest:
      topic: "Job_Schedule_NewTenant_Dev"
```

### Step 3: Add to application.properties

```properties
tenants[3].name=newtenant
tenants[3].enabled=false
tenants[3].profile=dev
```

### Step 4: Enable and Run

```properties
tenants[3].enabled=true
```

```bash
mvn quarkus:dev
```

---

## Comparison with RouteSphere Core

### RouteSphere Core

```properties
# application.properties
routesphere.tenant.name=${ROUTESPHERE_TENANT_NAME:bdcom}
routesphere.tenant.active-profile=${ROUTESPHERE_TENANT_ACTIVE_PROFILE:dev}

tenants[0].name=link3
tenants[0].enabled=true
tenants[0].profile=dev
```

**Config Path:**
```
config/tenants/{tenant}/{profile}/profile-{profile}.yml
```

### Infinite Scheduler (Now Identical)

```properties
# application.properties
tenants[0].name=link3
tenants[0].enabled=true
tenants[0].profile=dev
```

**Config Path:**
```
config/tenants/{tenant}/{profile}/profile-{profile}.yml
```

**Same pattern!** ✅

---

## Benefits

### 1. ✅ Consistent with RouteSphere Core
- Same configuration pattern
- Same folder structure
- Same tenant selection mechanism
- Easy to understand for team members

### 2. ✅ Multiple Tenant Support
- Can configure multiple tenants
- Easy to switch between tenants
- Clear enable/disable flags

### 3. ✅ Profile Support
- dev/prod/staging profiles
- Different settings per environment
- Easy environment switching

### 4. ✅ Clean Separation
- application.properties: Tenant selection only
- Profile YAMLs: All configuration
- No configuration duplication

### 5. ✅ Production Ready
- Well-tested pattern (used in RouteSphere Core)
- Clear tenant management
- Profile-based deployment

---

## Configuration Priority

1. **System Properties** (highest priority)
2. **Environment Variables**
3. **Tenant Profile YAML** (loaded by TenantConfigSource)
4. **application.properties** (lowest priority)

---

## Docker/Kubernetes Deployment

### Using Single Tenant per Container

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:21-jre
COPY target/quarkus-app/ /app/
COPY application-link3-prod.properties /app/application.properties
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
```

**application-link3-prod.properties:**
```properties
tenants[0].name=link3
tenants[0].enabled=true
tenants[0].profile=prod
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: scheduler-link3-prod
spec:
  replicas: 1
  template:
    spec:
      containers:
      - name: scheduler
        image: infinite-scheduler:1.0.0
        volumeMounts:
        - name: config
          mountPath: /app/application.properties
          subPath: application.properties
      volumes:
      - name: config
        configMap:
          name: scheduler-link3-config
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: scheduler-link3-config
data:
  application.properties: |
    tenants[0].name=link3
    tenants[0].enabled=true
    tenants[0].profile=prod
```

---

## Migration from Simplified Pattern

If you were using the simplified pattern (single tenant name only), migration is straightforward:

### Before (Simplified)
```properties
scheduler.tenant.name=${SCHEDULER_TENANT_NAME:link3}
```

### After (RouteSphere Pattern)
```properties
tenants[0].name=link3
tenants[0].enabled=true
tenants[0].profile=dev
```

**No code changes needed!** The TenantConfigSource handles everything.

---

## Summary

**The Infinite Scheduler now uses the RouteSphere Core pattern:**

✅ **tenants[x] array** - Same as RouteSphere Core
✅ **enabled flag** - Only one tenant active at a time
✅ **profile support** - dev/prod/staging
✅ **Same folder structure** - config/tenants/{name}/{profile}/profile-{profile}.yml
✅ **Same loading mechanism** - Custom ConfigSource
✅ **Consistent across projects** - Easier for team to maintain

**Configuration is simple:**
```properties
tenants[0].name=link3
tenants[0].enabled=true
tenants[0].profile=dev
```

**Just run:**
```bash
mvn quarkus:dev
```

**The scheduler starts automatically with the enabled tenant!** 🚀
