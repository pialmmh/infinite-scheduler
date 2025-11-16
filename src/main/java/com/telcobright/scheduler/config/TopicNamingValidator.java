package com.telcobright.scheduler.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fail-fast validator for topic naming convention.
 *
 * This validator ensures all Kafka topics follow the strict naming convention
 * before the scheduler starts. If any topic name is invalid, the application
 * will fail to start with a clear error message.
 *
 * Validation occurs during TenantConfigSource initialization (early startup).
 */
public class TopicNamingValidator {

    /**
     * Validate all Kafka topic configurations.
     *
     * @param tenant Tenant name
     * @param profile Profile name (dev/prod/staging)
     * @param properties All configuration properties from tenant YAML
     * @throws TopicValidationException if any topic name is invalid
     */
    public static void validateAllTopics(String tenant, String profile, Map<String, String> properties) {
        List<String> errors = new ArrayList<>();

        // Validate incoming topics
        validateIncomingTopics(tenant, properties, errors);

        // Validate outgoing topics
        validateOutgoingTopics(tenant, properties, errors);

        // If any errors, fail fast
        if (!errors.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder();
            errorMessage.append("\n╔════════════════════════════════════════════════════════════════╗\n");
            errorMessage.append("║  ❌ TOPIC NAMING VALIDATION FAILED - SERVICE CANNOT START     ║\n");
            errorMessage.append("╚════════════════════════════════════════════════════════════════╝\n");
            errorMessage.append("\nTenant: ").append(tenant).append("\n");
            errorMessage.append("Profile: ").append(profile).append("\n\n");
            errorMessage.append("Errors:\n");
            for (int i = 0; i < errors.size(); i++) {
                errorMessage.append(String.format("  %d. %s\n", i + 1, errors.get(i)));
            }
            errorMessage.append("\nRequired Pattern:\n");
            errorMessage.append("  Inbound:  {tenant}_{apptype}_scheduler_in\n");
            errorMessage.append("  Outbound: {tenant}_{apptype}_scheduler_out\n");
            errorMessage.append("  DLQ:      {tenant}_{apptype}_scheduler_dlq\n");
            errorMessage.append("\nExamples:\n");
            errorMessage.append("  - link3_sms_scheduler_in\n");
            errorMessage.append("  - link3_sms_scheduler_out\n");
            errorMessage.append("  - link3_sms_scheduler_dlq\n");
            errorMessage.append("  - link3_payment_scheduler_in\n");
            errorMessage.append("\nFix the topic names in: config/tenants/").append(tenant).append("/").append(profile).append("/profile-").append(profile).append(".yml\n");

            throw new TopicValidationException(errorMessage.toString());
        }
    }

    /**
     * Validate incoming (consumer) topics.
     */
    private static void validateIncomingTopics(String tenant, Map<String, String> properties, List<String> errors) {
        // Check mp.messaging.incoming.* topics
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("mp.messaging.incoming.") && key.endsWith(".topic")) {
                String channelName = extractChannelName(key, "incoming");
                String topicName = entry.getValue();

                if (!TopicNamingConvention.isValid(topicName)) {
                    errors.add(String.format(
                        "Invalid incoming topic for channel '%s': '%s'",
                        channelName, topicName
                    ));
                } else {
                    // Validate tenant matches
                    String extractedTenant = TopicNamingConvention.extractTenant(topicName);
                    if (!tenant.equalsIgnoreCase(extractedTenant)) {
                        errors.add(String.format(
                            "Topic tenant mismatch for channel '%s': topic '%s' has tenant '%s' but expected '%s'",
                            channelName, topicName, extractedTenant, tenant
                        ));
                    }

                    // Validate it's an inbound topic (ends with _scheduler_in)
                    String topicType = TopicNamingConvention.getTopicType(topicName);
                    if (!"in".equals(topicType)) {
                        errors.add(String.format(
                            "Incoming channel '%s' must use _scheduler_in topic, but got: '%s' (type: %s)",
                            channelName, topicName, topicType
                        ));
                    }
                }
            }
        }

        // Also check for DLQ topics in failure-strategy config
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("mp.messaging.incoming.") && key.endsWith(".dead-letter-queue.topic")) {
                String channelName = extractChannelName(key, "incoming");
                String topicName = entry.getValue();

                if (!TopicNamingConvention.isValid(topicName)) {
                    errors.add(String.format(
                        "Invalid DLQ topic for channel '%s': '%s'",
                        channelName, topicName
                    ));
                } else {
                    // Validate it's a DLQ topic (ends with _scheduler_dlq)
                    String topicType = TopicNamingConvention.getTopicType(topicName);
                    if (!"dlq".equals(topicType)) {
                        errors.add(String.format(
                            "DLQ topic for channel '%s' must use _scheduler_dlq suffix, but got: '%s' (type: %s)",
                            channelName, topicName, topicType
                        ));
                    }
                }
            }
        }
    }

    /**
     * Validate outgoing (producer) topics.
     */
    private static void validateOutgoingTopics(String tenant, Map<String, String> properties, List<String> errors) {
        // Check mp.messaging.outgoing.* topics
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("mp.messaging.outgoing.") && key.endsWith(".topic")) {
                String channelName = extractChannelName(key, "outgoing");
                String topicName = entry.getValue();

                if (!TopicNamingConvention.isValid(topicName)) {
                    errors.add(String.format(
                        "Invalid outgoing topic for channel '%s': '%s'",
                        channelName, topicName
                    ));
                } else {
                    // Validate tenant matches
                    String extractedTenant = TopicNamingConvention.extractTenant(topicName);
                    if (!tenant.equalsIgnoreCase(extractedTenant)) {
                        errors.add(String.format(
                            "Topic tenant mismatch for channel '%s': topic '%s' has tenant '%s' but expected '%s'",
                            channelName, topicName, extractedTenant, tenant
                        ));
                    }

                    // Validate it's an outbound topic (ends with _scheduler_out)
                    String topicType = TopicNamingConvention.getTopicType(topicName);
                    if (!"out".equals(topicType)) {
                        errors.add(String.format(
                            "Outgoing channel '%s' must use _scheduler_out topic, but got: '%s' (type: %s)",
                            channelName, topicName, topicType
                        ));
                    }
                }
            }
        }
    }

    /**
     * Extract channel name from property key.
     * Example: mp.messaging.incoming.job-schedule.topic → job-schedule
     */
    private static String extractChannelName(String propertyKey, String direction) {
        String prefix = "mp.messaging." + direction + ".";
        String suffix = ".topic";
        if (propertyKey.startsWith(prefix) && propertyKey.endsWith(suffix)) {
            return propertyKey.substring(prefix.length(), propertyKey.length() - suffix.length());
        }
        return propertyKey;
    }

    /**
     * Custom exception for topic validation failures.
     */
    public static class TopicValidationException extends RuntimeException {
        public TopicValidationException(String message) {
            super(message);
        }
    }
}
