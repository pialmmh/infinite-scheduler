package com.telcobright.scheduler.config;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/**
 * Custom ConfigSource that loads tenant-specific configuration from YAML profile files.
 * This runs early in Quarkus startup to make tenant-specific config available.
 *
 * Configuration Hierarchy:
 * 1. System properties (highest priority)
 * 2. Environment variables
 * 3. Tenant-specific YAML (this ConfigSource)
 * 4. application.properties (lowest priority)
 *
 * Tenant Configuration Path:
 * src/main/resources/config/tenants/{tenant-name}/{profile}/profile-{profile}.yml
 *
 * Example:
 * - config/tenants/link3/dev/profile-dev.yml
 * - config/tenants/link3/prod/profile-prod.yml
 * - config/tenants/example/dev/profile-dev.yml
 */
public class TenantConfigSource implements ConfigSource {

    private static final int PRIORITY = 270; // Higher than default (100) but lower than env vars (300)
    private final Map<String, String> properties = new HashMap<>();

    public TenantConfigSource() {
        loadTenantConfiguration();
    }

    /**
     * Load tenant-specific configuration from YAML profile files
     */
    private void loadTenantConfiguration() {
        try {
            // Step 1: Find enabled tenant from application.properties
            Properties appProps = new Properties();
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
                if (is != null) {
                    appProps.load(is);
                }
            }

            String tenantName = null;
            String tenantProfile = null;

            // Search for enabled tenant in tenants array
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

            if (tenantName == null || tenantProfile == null) {
                System.err.println("[TenantConfigSource] WARN: No enabled tenant found in application.properties");
                System.err.println("[TenantConfigSource]   Set tenants[x].enabled=true in application.properties");
                return;
            }

            System.out.println("[TenantConfigSource] ════════════════════════════════════════════");
            System.out.println("[TenantConfigSource] Loading tenant configuration:");
            System.out.println("[TenantConfigSource]   Tenant:  " + tenantName);
            System.out.println("[TenantConfigSource]   Profile: " + tenantProfile);

            // Step 2: Load tenant's profile YAML
            String yamlPath = "config/tenants/" + tenantName + "/" + tenantProfile + "/profile-" + tenantProfile + ".yml";

            try (InputStream yamlStream = getClass().getClassLoader().getResourceAsStream(yamlPath)) {
                if (yamlStream == null) {
                    System.err.println("[TenantConfigSource] ERROR: Tenant profile file not found: " + yamlPath);
                    System.err.println("[TenantConfigSource] Available tenants should be in: src/main/resources/config/tenants/");
                    return;
                }

                Yaml yaml = new Yaml();
                Map<String, Object> yamlData = yaml.load(yamlStream);

                // Step 3: Flatten and load all configuration
                if (yamlData != null) {
                    // Load infinite-scheduler.* config
                    if (yamlData.containsKey("infinite-scheduler")) {
                        Map<String, Object> schedulerConfig = (Map<String, Object>) yamlData.get("infinite-scheduler");
                        flattenMap("infinite-scheduler", schedulerConfig, properties);
                    }

                    // Load quarkus.* config (datasource, logging, etc.)
                    if (yamlData.containsKey("quarkus")) {
                        Map<String, Object> quarkusConfig = (Map<String, Object>) yamlData.get("quarkus");
                        flattenMap("quarkus", quarkusConfig, properties);
                    }

                    // Load mp.messaging.* config (SmallRye Reactive Messaging)
                    if (yamlData.containsKey("mp")) {
                        Map<String, Object> mpConfig = (Map<String, Object>) yamlData.get("mp");
                        flattenMap("mp", mpConfig, properties);
                    }

                    // Also expose scheduler.* for compatibility with existing code
                    if (yamlData.containsKey("infinite-scheduler")) {
                        Map<String, Object> schedulerConfig = (Map<String, Object>) yamlData.get("infinite-scheduler");

                        // Map infinite-scheduler.mysql.* to scheduler.mysql.*
                        if (schedulerConfig.containsKey("mysql")) {
                            Map<String, Object> mysqlConfig = (Map<String, Object>) schedulerConfig.get("mysql");
                            flattenMap("scheduler.mysql", mysqlConfig, properties);
                        }

                        // Map infinite-scheduler.kafka.* to scheduler.kafka.* and kafka.*
                        if (schedulerConfig.containsKey("kafka")) {
                            Map<String, Object> kafkaConfig = (Map<String, Object>) schedulerConfig.get("kafka");
                            flattenMap("scheduler.kafka", kafkaConfig, properties);

                            // Also expose kafka.bootstrap.servers for backward compatibility
                            if (kafkaConfig.containsKey("bootstrap-servers")) {
                                properties.put("kafka.bootstrap.servers", kafkaConfig.get("bootstrap-servers").toString());
                            }
                        }

                        // Map other scheduler configs
                        if (schedulerConfig.containsKey("repository")) {
                            Map<String, Object> repoConfig = (Map<String, Object>) schedulerConfig.get("repository");
                            flattenMap("scheduler.repository", repoConfig, properties);
                        }

                        if (schedulerConfig.containsKey("fetcher")) {
                            Map<String, Object> fetcherConfig = (Map<String, Object>) schedulerConfig.get("fetcher");
                            flattenMap("scheduler.fetcher", fetcherConfig, properties);
                        }

                        if (schedulerConfig.containsKey("cleanup")) {
                            Map<String, Object> cleanupConfig = (Map<String, Object>) schedulerConfig.get("cleanup");
                            flattenMap("scheduler.cleanup", cleanupConfig, properties);
                        }

                        if (schedulerConfig.containsKey("quartz")) {
                            Map<String, Object> quartzConfig = (Map<String, Object>) schedulerConfig.get("quartz");
                            flattenMap("scheduler.quartz", quartzConfig, properties);
                        }

                        if (schedulerConfig.containsKey("web")) {
                            Map<String, Object> webConfig = (Map<String, Object>) schedulerConfig.get("web");
                            flattenMap("scheduler.web", webConfig, properties);
                        }

                        if (schedulerConfig.containsKey("monitoring")) {
                            Map<String, Object> monitoringConfig = (Map<String, Object>) schedulerConfig.get("monitoring");
                            flattenMap("scheduler.monitoring", monitoringConfig, properties);
                        }
                    }
                }

                System.out.println("[TenantConfigSource]   Loaded " + properties.size() + " configuration properties");

                // Validate all Kafka topic names - FAIL FAST if invalid
                try {
                    System.out.println("[TenantConfigSource]   Validating topic naming convention...");
                    TopicNamingValidator.validateAllTopics(tenantName, tenantProfile, properties);
                    System.out.println("[TenantConfigSource]   ✅ All topic names are valid");
                } catch (TopicNamingValidator.TopicValidationException e) {
                    System.err.println("[TenantConfigSource] ════════════════════════════════════════════");
                    System.err.println(e.getMessage());
                    System.err.println("[TenantConfigSource] ════════════════════════════════════════════");
                    throw new RuntimeException("Topic naming validation failed - cannot start service", e);
                }

                System.out.println("[TenantConfigSource] ════════════════════════════════════════════");

            } catch (Exception e) {
                System.err.println("[TenantConfigSource] ERROR loading tenant config: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.err.println("[TenantConfigSource] ERROR in initialization: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Resolve property placeholders like ${ENV_VAR:default}
     * Returns the default value if present, null otherwise
     */
    private String resolvePropertyPlaceholder(String value) {
        if (value == null) {
            return null;
        }

        // Check if it's a placeholder: ${VAR:default} or ${VAR}
        if (value.startsWith("${") && value.endsWith("}")) {
            String placeholder = value.substring(2, value.length() - 1);

            // Check if it has a default value
            if (placeholder.contains(":")) {
                String[] parts = placeholder.split(":", 2);
                String envVar = parts[0];
                String defaultValue = parts[1];

                // Try to get from environment first
                String envValue = System.getenv(envVar);
                if (envValue != null) {
                    return envValue;
                }

                return defaultValue;
            }

            // No default value, try to get from environment
            String envValue = System.getenv(placeholder);
            if (envValue != null) {
                return envValue;
            }

            return null;
        }

        return value; // Not a placeholder, return as-is
    }

    /**
     * Recursively flatten a nested map into dot-notation properties
     * Converts kebab-case keys to dot notation (e.g., bootstrap-servers -> bootstrap.servers)
     */
    private void flattenMap(String prefix, Map<String, Object> map, Map<String, String> result) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();

            // Convert kebab-case to dot notation for some keys
            String convertedKey = key.replace("-", ".");

            String fullKey = prefix.isEmpty() ? convertedKey : prefix + "." + convertedKey;
            Object value = entry.getValue();

            if (value instanceof Map) {
                flattenMap(fullKey, (Map<String, Object>) value, result);
            } else if (value instanceof List) {
                // For lists, convert to comma-separated string
                List<?> list = (List<?>) value;
                result.put(fullKey, String.join(",", list.stream()
                    .map(Object::toString)
                    .toArray(String[]::new)));
            } else if (value != null) {
                result.put(fullKey, value.toString());
            }
        }
    }

    @Override
    public Map<String, String> getProperties() {
        return new HashMap<>(properties);
    }

    @Override
    public String getValue(String propertyName) {
        return properties.get(propertyName);
    }

    @Override
    public String getName() {
        return "TenantConfigSource";
    }

    @Override
    public int getOrdinal() {
        return PRIORITY;
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.keySet();
    }
}
