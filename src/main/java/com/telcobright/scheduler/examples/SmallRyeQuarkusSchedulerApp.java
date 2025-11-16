package com.telcobright.scheduler.examples;

import com.telcobright.scheduler.MultiAppSchedulerManager;
import com.telcobright.scheduler.config.KafkaConfigProperties;
import com.telcobright.scheduler.config.SchedulerConfigProperties;
import com.telcobright.scheduler.kafka.SmallRyeKafkaJobIngestConsumer;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Infinite Scheduler with SmallRye Reactive Messaging (Quarkus Native).
 *
 * Features:
 * - SmallRye Reactive Messaging for Kafka (no manual consumer/producer)
 * - Automatic message consumption from Job_Schedule topic
 * - Automatic message production to SMS_Send topic
 * - Dead Letter Queue for failed messages
 * - Idempotency support
 * - CDI dependency injection
 * - Virtual thread-based processing
 * - Type-safe configuration
 *
 * Configuration:
 * - Kafka brokers: application.properties (mp.messaging.*)
 * - Incoming channel: job-schedule -> Job_Schedule topic
 * - Outgoing channel: sms-send -> SMS_Send topic
 * - DLQ: Job_Schedule_DLQ
 *
 * To run:
 *   mvn quarkus:dev                                    # Dev mode
 *   mvn package && java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=prod
 */
@QuarkusMain
public class SmallRyeQuarkusSchedulerApp implements QuarkusApplication {

    private static final Logger logger = LoggerFactory.getLogger(SmallRyeQuarkusSchedulerApp.class);

    @Inject
    KafkaConfigProperties kafkaConfig;

    @Inject
    SchedulerConfigProperties schedulerConfig;

    @Inject
    MultiAppSchedulerManager schedulerManager;

    @Inject
    SmallRyeSmsRetryJobHandler smsRetryHandler;

    @Inject
    SmallRyeKafkaJobIngestConsumer kafkaConsumer;

    @Override
    public int run(String... args) throws Exception {
        printBanner();
        printConfiguration();

        // Register SMS retry handler
        schedulerManager.registerApp("sms_retry", smsRetryHandler);
        logger.info("✅ Registered SMS Retry Handler (SmallRye-based)");

        // Start scheduler
        schedulerManager.startAll();

        printStartupSummary();

        // Print metrics periodically
        startMetricsReporter();

        // Keep application running
        Quarkus.waitForExit();
        return 0;
    }

    private void printBanner() {
        logger.info("╔════════════════════════════════════════════════════════════════════╗");
        logger.info("║                                                                    ║");
        logger.info("║         INFINITE SCHEDULER - SMALLRYE REACTIVE MESSAGING          ║");
        logger.info("║                      Quarkus Native Edition                       ║");
        logger.info("║                                                                    ║");
        logger.info("╚════════════════════════════════════════════════════════════════════╝");
        logger.info("");
    }

    private void printConfiguration() {
        logger.info("📋 Configuration:");
        logger.info("  ┌─────────────────────────────────────────────────────────────────┐");
        logger.info("  │ Kafka Configuration                                             │");
        logger.info("  ├─────────────────────────────────────────────────────────────────┤");
        logger.info("  │ Bootstrap Servers: {}",
            truncate(kafkaConfig.bootstrapServers(), 40));
        logger.info("  │ Consumer Group:    {}", kafkaConfig.consumer().groupId());
        logger.info("  │ Ingest Topic:      {}", schedulerConfig.kafka().ingest().topic());
        logger.info("  │ DLQ Topic:         {}", schedulerConfig.kafka().ingest().dlqTopic());
        logger.info("  │ SMS Send Topic:    SMS_Send");
        logger.info("  │ Consumer Type:     SmallRye Reactive Messaging");
        logger.info("  │ Producer Type:     SmallRye Reactive Messaging");
        logger.info("  └─────────────────────────────────────────────────────────────────┘");
        logger.info("");
        logger.info("  ┌─────────────────────────────────────────────────────────────────┐");
        logger.info("  │ Scheduler Configuration                                         │");
        logger.info("  ├─────────────────────────────────────────────────────────────────┤");
        logger.info("  │ Repository Database: {}", schedulerConfig.repository().database());
        logger.info("  │ Table Prefix:        {}", schedulerConfig.repository().table().prefix());
        logger.info("  │ Retention Days:      {}", schedulerConfig.repository().retention().days());
        logger.info("  │ Fetcher Interval:    {}s", schedulerConfig.fetcher().intervalSeconds());
        logger.info("  │ Lookahead Window:    {}s", schedulerConfig.fetcher().lookaheadSeconds());
        logger.info("  │ Web UI Port:         {}", schedulerConfig.web().port());
        logger.info("  └─────────────────────────────────────────────────────────────────┘");
        logger.info("");
    }

    private void printStartupSummary() {
        logger.info("");
        logger.info("╔════════════════════════════════════════════════════════════════════╗");
        logger.info("║                ✅ SCHEDULER STARTED SUCCESSFULLY                   ║");
        logger.info("╠════════════════════════════════════════════════════════════════════╣");
        logger.info("║                                                                    ║");
        logger.info("║  🌐 Web UI:      http://{}:{}{}",
            truncate(schedulerConfig.web().host(), 12),
            schedulerConfig.web().port(),
            "/index.html".length() < 20 ? "/index.html" : "");
        logger.info("║                                                                    ║");
        logger.info("║  📥 Consuming:   {} (SmallRye)",
            truncate(schedulerConfig.kafka().ingest().topic(), 30));
        logger.info("║  📤 Publishing:  SMS_Send (SmallRye)                              ║");
        logger.info("║  🔀 DLQ:         {}",
            truncate(schedulerConfig.kafka().ingest().dlqTopic(), 30));
        logger.info("║                                                                    ║");
        logger.info("║  🎯 Registered Apps:                                              ║");
        logger.info("║     • sms_retry (SmallRyeSmsRetryJobHandler)                     ║");
        logger.info("║                                                                    ║");
        logger.info("║  ⚡ Features:                                                      ║");
        logger.info("║     • Reactive Messaging (Virtual Threads)                       ║");
        logger.info("║     • Automatic DLQ on failure                                   ║");
        logger.info("║     • Idempotency (24h TTL cache)                                ║");
        logger.info("║     • Manual acknowledgment                                      ║");
        logger.info("║     • At-least-once delivery                                     ║");
        logger.info("║                                                                    ║");
        logger.info("║  Press Ctrl+C to stop                                             ║");
        logger.info("║                                                                    ║");
        logger.info("╚════════════════════════════════════════════════════════════════════╝");
        logger.info("");
    }

    private void startMetricsReporter() {
        // Print metrics every 60 seconds
        Thread.ofVirtual().start(() -> {
            while (true) {
                try {
                    Thread.sleep(60_000);
                    kafkaConsumer.printMetrics();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    public static void main(String[] args) {
        Quarkus.run(SmallRyeQuarkusSchedulerApp.class, args);
    }
}
