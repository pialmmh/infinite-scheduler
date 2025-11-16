package com.telcobright.scheduler.config;

import java.util.regex.Pattern;

/**
 * Enforced topic naming convention for multi-tenant scheduler.
 *
 * Convention:
 * - Inbound (scheduling):  {tenant}_{apptype}_scheduler_in
 * - Outbound (execution):  {tenant}_{apptype}_scheduler_out
 * - DLQ (dead letter):     {tenant}_{apptype}_scheduler_dlq
 *
 * Examples:
 * - link3_sms_scheduler_in      - Link3 SMS campaign publishes schedule requests
 * - link3_sms_scheduler_out     - Link3 SMS gateway consumes execution results
 * - link3_sms_scheduler_dlq     - Link3 SMS failed messages
 * - link3_payment_scheduler_in  - Link3 payment scheduling
 * - example_sms_scheduler_in    - Example tenant SMS scheduling
 *
 * Validation Rules:
 * - Tenant name: lowercase alphanumeric, hyphens allowed
 * - App type: lowercase alphanumeric only (e.g., sms, payment, notification)
 * - Suffix: Must be exactly _scheduler_in, _scheduler_out, or _scheduler_dlq
 */
public class TopicNamingConvention {

    private static final String TENANT_PATTERN = "[a-z0-9-]+";
    private static final String APPTYPE_PATTERN = "[a-z0-9]+";
    private static final String SUFFIX_IN = "_scheduler_in";
    private static final String SUFFIX_OUT = "_scheduler_out";
    private static final String SUFFIX_DLQ = "_scheduler_dlq";

    // Full topic pattern: {tenant}_{apptype}_scheduler_{in|out|dlq}
    private static final Pattern TOPIC_PATTERN = Pattern.compile(
        "^" + TENANT_PATTERN + "_" + APPTYPE_PATTERN + "_scheduler_(in|out|dlq)$"
    );

    /**
     * Generate inbound topic name for scheduling.
     *
     * @param tenant Tenant name (e.g., "link3", "example")
     * @param appType Application type (e.g., "sms", "payment")
     * @return Topic name (e.g., "link3_sms_scheduler_in")
     */
    public static String inbound(String tenant, String appType) {
        validateTenantName(tenant);
        validateAppType(appType);
        return normalize(tenant) + "_" + normalize(appType) + SUFFIX_IN;
    }

    /**
     * Generate outbound topic name for execution results.
     *
     * @param tenant Tenant name (e.g., "link3", "example")
     * @param appType Application type (e.g., "sms", "payment")
     * @return Topic name (e.g., "link3_sms_scheduler_out")
     */
    public static String outbound(String tenant, String appType) {
        validateTenantName(tenant);
        validateAppType(appType);
        return normalize(tenant) + "_" + normalize(appType) + SUFFIX_OUT;
    }

    /**
     * Generate DLQ topic name for failed messages.
     *
     * @param tenant Tenant name (e.g., "link3", "example")
     * @param appType Application type (e.g., "sms", "payment")
     * @return Topic name (e.g., "link3_sms_scheduler_dlq")
     */
    public static String dlq(String tenant, String appType) {
        validateTenantName(tenant);
        validateAppType(appType);
        return normalize(tenant) + "_" + normalize(appType) + SUFFIX_DLQ;
    }

    /**
     * Validate a topic name against the convention.
     *
     * @param topicName Topic name to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String topicName) {
        if (topicName == null || topicName.trim().isEmpty()) {
            return false;
        }
        return TOPIC_PATTERN.matcher(topicName.trim()).matches();
    }

    /**
     * Validate a topic name and throw exception if invalid.
     *
     * @param topicName Topic name to validate
     * @param description Description for error message
     * @throws IllegalArgumentException if invalid
     */
    public static void validate(String topicName, String description) {
        if (!isValid(topicName)) {
            throw new IllegalArgumentException(
                String.format("Invalid topic name for %s: '%s'. " +
                    "Must follow pattern: {tenant}_{apptype}_scheduler_{in|out|dlq}. " +
                    "Examples: link3_sms_scheduler_in, link3_sms_scheduler_out",
                    description, topicName)
            );
        }
    }

    /**
     * Validate tenant name.
     */
    private static void validateTenantName(String tenant) {
        if (tenant == null || tenant.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant name cannot be null or empty");
        }
        if (!tenant.matches(TENANT_PATTERN)) {
            throw new IllegalArgumentException(
                String.format("Invalid tenant name: '%s'. Must be lowercase alphanumeric with optional hyphens.", tenant)
            );
        }
    }

    /**
     * Validate app type name.
     */
    private static void validateAppType(String appType) {
        if (appType == null || appType.trim().isEmpty()) {
            throw new IllegalArgumentException("App type cannot be null or empty");
        }
        if (!appType.matches(APPTYPE_PATTERN)) {
            throw new IllegalArgumentException(
                String.format("Invalid app type: '%s'. Must be lowercase alphanumeric only.", appType)
            );
        }
    }

    /**
     * Normalize name to lowercase and trim.
     */
    private static String normalize(String name) {
        return name.trim().toLowerCase();
    }

    /**
     * Extract tenant name from topic.
     *
     * @param topicName Topic name
     * @return Tenant name or null if invalid
     */
    public static String extractTenant(String topicName) {
        if (!isValid(topicName)) return null;
        int firstUnderscore = topicName.indexOf('_');
        return firstUnderscore > 0 ? topicName.substring(0, firstUnderscore) : null;
    }

    /**
     * Extract app type from topic.
     *
     * @param topicName Topic name
     * @return App type or null if invalid
     */
    public static String extractAppType(String topicName) {
        if (!isValid(topicName)) return null;
        int firstUnderscore = topicName.indexOf('_');
        int secondUnderscore = topicName.indexOf('_', firstUnderscore + 1);
        if (firstUnderscore > 0 && secondUnderscore > firstUnderscore) {
            return topicName.substring(firstUnderscore + 1, secondUnderscore);
        }
        return null;
    }

    /**
     * Get topic type (in, out, dlq).
     *
     * @param topicName Topic name
     * @return Topic type or null if invalid
     */
    public static String getTopicType(String topicName) {
        if (!isValid(topicName)) return null;
        if (topicName.endsWith(SUFFIX_IN)) return "in";
        if (topicName.endsWith(SUFFIX_OUT)) return "out";
        if (topicName.endsWith(SUFFIX_DLQ)) return "dlq";
        return null;
    }
}
