package com.telcobright.scheduler.startup;

import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.config.InfiniteSchedulerProperties;
import com.telcobright.scheduler.config.KafkaConfigProperties;
import com.telcobright.scheduler.config.SchedulerConfigProperties;
import com.telcobright.scheduler.examples.SmallRyeSmsRetryJobHandler;
import com.telcobright.scheduler.kafka.KafkaIngestConfig;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quarkus startup bean that automatically initializes and starts
 * the Infinite Scheduler when the application starts.
 *
 * This bean:
 * - Observes Quarkus StartupEvent to start the scheduler
 * - Observes Quarkus ShutdownEvent for graceful shutdown
 * - Reads configuration from application.properties
 * - Registers application handlers via CDI
 * - Starts all schedulers (Quartz, fetchers, Kafka)
 */
@ApplicationScoped
public class InfiniteSchedulerStartup {

    private static final Logger logger = LoggerFactory.getLogger(InfiniteSchedulerStartup.class);

    @Inject
    Config config;

    @Inject
    InfiniteSchedulerProperties schedulerProps;

    @Inject
    KafkaConfigProperties kafkaProps;

    @Inject
    SchedulerConfigProperties schedulerConfig;

    @Inject
    SmallRyeSmsRetryJobHandler smsRetryHandler;

    private MultiAppSchedulerManager schedulerManager;

    /**
     * Startup event handler - starts the scheduler automatically
     */
    void onStart(@Observes StartupEvent event) {
        try {
            logger.info("╔════════════════════════════════════════════════════════════════╗");
            logger.info("║     INFINITE SCHEDULER - QUARKUS STARTUP                       ║");
            logger.info("╚════════════════════════════════════════════════════════════════╝");
            logger.info("");

            // Print configuration
            printConfiguration();

            // Create scheduler manager
            schedulerManager = createSchedulerManager();

            // Register application handlers
            registerApplicationHandlers();

            // Start all schedulers
            schedulerManager.startAll();

            // Print startup summary
            printStartupSummary();

        } catch (Exception e) {
            logger.error("❌ Failed to start Infinite Scheduler", e);
            throw new RuntimeException("Scheduler startup failed", e);
        }
    }

    /**
     * Shutdown event handler - stops the scheduler gracefully
     */
    void onStop(@Observes ShutdownEvent event) {
        if (schedulerManager != null) {
            try {
                logger.info("🛑 Stopping Infinite Scheduler...");
                schedulerManager.stopAll();
                logger.info("✅ Infinite Scheduler stopped successfully");
            } catch (Exception e) {
                logger.error("❌ Error stopping scheduler", e);
            }
        }
    }

    /**
     * Creates the scheduler manager from configuration
     */
    private MultiAppSchedulerManager createSchedulerManager() throws Exception {
        logger.info("📋 Creating scheduler manager...");

        MultiAppSchedulerManager.Builder builder = MultiAppSchedulerManager.builder()
            .mysqlHost(schedulerProps.mysql().host())
            .mysqlPort(schedulerProps.mysql().port())
            .mysqlDatabase(schedulerProps.mysql().database())
            .mysqlUsername(schedulerProps.mysql().username())
            .mysqlPassword(schedulerProps.mysql().password());

        // Add Kafka ingest if enabled
        if (schedulerConfig.kafka().ingest().enabled()) {
            KafkaIngestConfig kafkaIngestConfig = KafkaIngestConfig.builder()
                .bootstrapServers(kafkaProps.bootstrapServers())
                .groupId(kafkaProps.consumer().groupId())
                .topic(schedulerConfig.kafka().ingest().topic())
                .dlqTopic(schedulerConfig.kafka().ingest().dlqTopic())
                .maxPollRecords(kafkaProps.consumer().maxPollRecords())
                .pollTimeoutMs(schedulerConfig.kafka().ingest().pollTimeoutMs())
                .enableAutoCommit(kafkaProps.consumer().enableAutoCommit())
                .maxRetries(schedulerConfig.kafka().ingest().maxRetries())
                .enabled(schedulerConfig.kafka().ingest().enabled())
                .build();

            builder.withKafkaIngest(kafkaIngestConfig);
            logger.info("✅ Kafka ingest configured");
        } else {
            logger.info("⚠️  Kafka ingest disabled");
        }

        return builder.build();
    }

    /**
     * Registers application handlers with the scheduler
     */
    private void registerApplicationHandlers() {
        logger.info("📝 Registering application handlers...");

        // Register SMS retry handler (injected via CDI)
        schedulerManager.registerApp("sms_retry", smsRetryHandler);
        logger.info("✅ Registered: sms_retry → SmallRyeSmsRetryJobHandler");

        // Add more app registrations here as needed
        // schedulerManager.registerApp("payment", paymentHandler);
        // schedulerManager.registerApp("notification", notificationHandler);

        logger.info("📝 Total applications registered: {}",
            schedulerManager.getRegisteredApps().size());
    }

    /**
     * Print current configuration
     */
    private void printConfiguration() {
        // Get tenant information from infinite-scheduler config
        String tenantName = config.getOptionalValue("infinite-scheduler.tenant.name", String.class).orElse("unknown");
        String tenantProfile = config.getOptionalValue("infinite-scheduler.tenant.profile", String.class).orElse("unknown");

        logger.info("📋 Configuration:");
        logger.info("  ┌─────────────────────────────────────────────────────────────┐");
        logger.info("  │ Tenant Instance                                             │");
        logger.info("  ├─────────────────────────────────────────────────────────────┤");
        logger.info("  │ Tenant:   {}", pad(tenantName, 52));
        logger.info("  │ Profile:  {}", pad(tenantProfile, 52));
        logger.info("  └─────────────────────────────────────────────────────────────┘");
        logger.info("");

        logger.info("  ┌─────────────────────────────────────────────────────────────┐");
        logger.info("  │ MySQL Database                                              │");
        logger.info("  ├─────────────────────────────────────────────────────────────┤");
        logger.info("  │ Host:     {}:{}",
            schedulerProps.mysql().host(),
            schedulerProps.mysql().port());
        logger.info("  │ Database: {}", schedulerProps.mysql().database());
        logger.info("  │ Username: {}", schedulerProps.mysql().username());
        logger.info("  └─────────────────────────────────────────────────────────────┘");
        logger.info("");

        if (schedulerConfig.kafka().ingest().enabled()) {
            logger.info("  ┌─────────────────────────────────────────────────────────────┐");
            logger.info("  │ Kafka Configuration                                         │");
            logger.info("  ├─────────────────────────────────────────────────────────────┤");
            logger.info("  │ Brokers:      {}",
                truncate(kafkaProps.bootstrapServers(), 45));
            logger.info("  │ Group ID:     {}", kafkaProps.consumer().groupId());
            logger.info("  │ Input Topic:  {}", schedulerConfig.kafka().ingest().topic());
            logger.info("  │ DLQ Topic:    {}", schedulerConfig.kafka().ingest().dlqTopic());
            logger.info("  │ Consumer:     SmallRye Reactive Messaging");
            logger.info("  └─────────────────────────────────────────────────────────────┘");
        } else {
            logger.info("  ┌─────────────────────────────────────────────────────────────┐");
            logger.info("  │ Kafka: Disabled                                             │");
            logger.info("  └─────────────────────────────────────────────────────────────┘");
        }
        logger.info("");

        logger.info("  ┌─────────────────────────────────────────────────────────────┐");
        logger.info("  │ Scheduler Settings                                          │");
        logger.info("  ├─────────────────────────────────────────────────────────────┤");
        logger.info("  │ Repository DB:    {}", schedulerConfig.repository().database());
        logger.info("  │ Table Prefix:     {}", schedulerConfig.repository().table().prefix());
        logger.info("  │ Retention Days:   {}", schedulerConfig.repository().retention().days());
        logger.info("  │ Fetcher Interval: {}s", schedulerConfig.fetcher().intervalSeconds());
        logger.info("  │ Lookahead Window: {}s", schedulerConfig.fetcher().lookaheadSeconds());
        logger.info("  │ Web UI Port:      {}", schedulerConfig.web().port());
        logger.info("  └─────────────────────────────────────────────────────────────┘");
        logger.info("");
    }

    /**
     * Print startup summary
     */
    private void printStartupSummary() {
        logger.info("");
        logger.info("╔════════════════════════════════════════════════════════════════╗");
        logger.info("║          ✅ SCHEDULER STARTED SUCCESSFULLY                     ║");
        logger.info("╠════════════════════════════════════════════════════════════════╣");
        logger.info("║                                                                ║");
        logger.info("║  🌐 Web UI:        http://{}:{}{}",
            schedulerConfig.web().host(),
            schedulerConfig.web().port(),
            "/index.html".length() < 30 ? "/index.html                       " : "");
        logger.info("║                                                                ║");

        if (schedulerConfig.kafka().ingest().enabled()) {
            logger.info("║  📥 Kafka Ingest:  ENABLED                                     ║");
            logger.info("║     Topic:         {}",
                pad(schedulerConfig.kafka().ingest().topic(), 42));
            logger.info("║     Type:          SmallRye Reactive Messaging                 ║");
        } else {
            logger.info("║  📥 Kafka Ingest:  DISABLED                                    ║");
        }

        logger.info("║                                                                ║");
        logger.info("║  🎯 Registered Apps:                                           ║");
        for (String appName : schedulerManager.getRegisteredApps()) {
            logger.info("║     • {}",
                pad(appName, 56));
        }
        logger.info("║                                                                ║");
        logger.info("║  ⚡ Features:                                                   ║");
        logger.info("║     • Quartz Scheduler (MySQL persistence)                    ║");
        logger.info("║     • Split-Verse (time-based partitioning)                   ║");
        logger.info("║     • Virtual Threads (Java 21)                               ║");
        logger.info("║     • Automatic table cleanup                                 ║");
        logger.info("║     • Web UI for monitoring                                   ║");
        if (schedulerConfig.kafka().ingest().enabled()) {
            logger.info("║     • SmallRye Reactive Messaging                             ║");
            logger.info("║     • Automatic DLQ on failure                                ║");
        }
        logger.info("║                                                                ║");
        logger.info("╚════════════════════════════════════════════════════════════════╝");
        logger.info("");
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    private String pad(String str, int length) {
        if (str == null) str = "";
        if (str.length() >= length) return str;
        return str + " ".repeat(length - str.length());
    }

    /**
     * Get the scheduler manager (for testing or external access)
     */
    public MultiAppSchedulerManager getSchedulerManager() {
        return schedulerManager;
    }
}
