# LXD Container Deployment

## Overview

This directory contains configuration for building LXC container images from Maven.

**Workflow:**
1. Developer runs Maven task: `mvn clean package lxd:build`
2. Maven builds JAR + LXC container image
3. Image stored in orchestrix repo at configured path
4. Orchestrix AI agent deploys instances from built image

**Separation of concerns:**
- **Developer/Maven:** Builds application + container image
- **Orchestrix agent:** Launches and manages instances

---

## Configuration File

**`lxddeploy.conf`** - Contains all parameters for container image build

Key sections:
- Application identity (name, version)
- Resource requirements (memory, CPU, storage)
- Network configuration (ports)
- Java configuration (version, JVM options)
- Logging (Promtail, Loki integration)
- Optional services (MySQL, Consul)
- Build options

---

## Maven Task Usage

### Build Container Image

```bash
# From routesphere-core directory
mvn clean package lxd:build
```

**What happens:**
1. Maven compiles and packages JAR
2. Reads `deploy/lxd/lxddeploy.conf`
3. Generates orchestrix build.conf from lxddeploy.conf
4. Executes orchestrix build.sh
5. Creates container image

**Output:**
```
Container image: routesphere-core-v1-1730450000.tar.gz
Location: /home/mustafa/telcobright-projects/orchestrix-lxc-work/images/lxc/quarkus-runtime/routesphere-core-v.1/generated/artifact/
```

---

## Deployment (Orchestrix Agent)

After Maven builds the image, tell the orchestrix AI agent:

```
Deploy routesphere-core image built at:
/home/mustafa/telcobright-projects/orchestrix-lxc-work/images/lxc/quarkus-runtime/routesphere-core-v.1/generated/artifact/routesphere-core-v1-1730450000.tar.gz

Launch config:
- Instance: routesphere-prod-1
- IP: 10.10.199.30
- MySQL: 10.10.199.171
- Loki: 10.10.199.200
```

The agent will create launch config and start the instance.

---

## Configuration Reference

### Resource Presets

**Current (routesphere-core):** 4GB RAM, 4 CPU - for 300 TPS SMS processing

**Adjust in lxddeploy.conf:**
```bash
MEMORY_LIMIT="4GB"
CPU_LIMIT="4"
STORAGE_QUOTA_SIZE="20G"
```

### Ports

```bash
APP_PORT="8082"           # Application HTTP port
PROMTAIL_PORT="9080"      # Promtail metrics
```

### JVM Configuration

```bash
JAVA_VERSION="21"
JVM_OPTS="-Xms1g -Xmx3g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Environment Variables

Add application-specific env vars:
```bash
ENV_SMS_BATCH_SIZE="100"
ENV_SMS_PROCESSING_THREADS="10"
ENV_HTTP_IO_THREADS="8"
```

---

## Build-time vs Runtime Configuration

### Build-time (lxddeploy.conf)
- Application identity
- Resource requirements
- Ports
- JVM options
- Default environment variables
- **Baked into container image**

### Runtime (orchestrix launch config)
- Instance name
- Static IP
- Real Loki/MySQL endpoints
- MySQL password (from secrets)
- Environment variable overrides
- **Set when launching instance**

---

## Prerequisites

Before running `mvn lxd:build`:

✅ **Base image exists**
```bash
lxc image list | grep quarkus-base-v1
```

✅ **Orchestrix path accessible**
```bash
ls /home/mustafa/telcobright-projects/orchestrix-lxc-work/images/lxc/quarkus-runtime
```

✅ **Maven plugin configured**
```xml
<plugin>
  <groupId>com.telcobright</groupId>
  <artifactId>lxd-maven-plugin</artifactId>
  <configuration>
    <configFile>deploy/lxd/lxddeploy.conf</configFile>
  </configuration>
</plugin>
```

---

## Complete Example Workflow

### 1. Developer Builds Image

```bash
# In routesphere-core directory
cd /home/mustafa/telcobright-projects/routesphere/routesphere-core

# Edit config if needed
nano deploy/lxd/lxddeploy.conf

# Build JAR and container image
mvn clean package lxd:build

# Note the output location and timestamp
```

### 2. Orchestrix Agent Deploys

**Developer tells agent:**
> "Deploy the routesphere-core image I just built. Use production config with static IP 10.10.199.30, connect to MySQL at 10.10.199.171, ship logs to Loki at 10.10.199.200"

**Agent creates launch config and deploys:**
```bash
cd /home/mustafa/telcobright-projects/orchestrix-lxc-work/images/lxc/quarkus-runtime

# Agent creates this config
cat > configs/routesphere-core-prod.conf << 'EOF'
INSTANCE_NAME="routesphere-core-prod-1"
CONTAINER_IMAGE="routesphere-core-v1-1730450000.tar.gz"
STATIC_IP="10.10.199.30"
LOKI_HOST="10.10.199.200"
MYSQL_HOST="10.10.199.171"
MYSQL_PASSWORD="<from-secrets>"
EOF

# Agent launches
./launch.sh configs/routesphere-core-prod.conf
```

---

## Updating the Configuration

### Change Resources

Edit `lxddeploy.conf`:
```bash
MEMORY_LIMIT="8GB"  # Increase for higher load
CPU_LIMIT="8"
```

Then rebuild:
```bash
mvn clean package lxd:build
```

### Change Ports

Edit `lxddeploy.conf`:
```bash
APP_PORT="8090"  # New port
```

Rebuild image, then tell orchestrix agent to use new port in launch config.

### Add Environment Variables

Edit `lxddeploy.conf`:
```bash
ENV_NEW_FEATURE_ENABLED="true"
ENV_BATCH_SIZE="200"
```

Rebuild, redeploy.

---

## Troubleshooting

### Maven build fails

**Check:**
1. JAR built successfully: `ls -lh target/*.jar`
2. Orchestrix path exists: `ls $ORCHESTRIX_BUILD_PATH`
3. Base image exists: `lxc image list | grep quarkus-base`

### Container image not created

**Check Maven output for:**
- orchestrix build.sh errors
- LXD errors
- Permission issues

**Logs:** Maven should show orchestrix build.sh output

---

## See Also

- **Orchestrix README:** `$ORCHESTRIX_BUILD_PATH/README.md`
- **Launch instructions:** `$ORCHESTRIX_BUILD_PATH/SCAFFOLD_SUMMARY.md`
- **Base image:** `/home/mustafa/telcobright-projects/orchestrix-lxc-work/images/lxc/quarkus-base/`

---

## Notes

- **Don't commit secrets** to lxddeploy.conf (MySQL passwords, API keys, etc.)
- **Use default values** in lxddeploy.conf, override at launch time
- **Version control** this config file (it's part of application repo)
- **Document changes** when updating resource requirements
