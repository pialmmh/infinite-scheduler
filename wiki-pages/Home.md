# Infinite Scheduler Wiki

Welcome to the **Infinite Scheduler** documentation! This is a high-performance, scalable job scheduling system built on Quartz Scheduler with MySQL persistence.

## 🚀 Quick Navigation

### Getting Started
- **[Quick Start](Quick-Start)** - Get up and running in 5 minutes
- **[Configuration](Configuration)** - Complete configuration guide
- **[Examples](Examples)** - Working code examples

### Technical Details  
- **[Architecture](Architecture)** - How it works under the hood
- **[API Reference](API-Reference)** - Complete API documentation
- **[Performance & Monitoring](Performance-Monitoring)** - Metrics and optimization

### Support
- **[Troubleshooting](Troubleshooting)** - Common issues and solutions
- **[Best Practices](Best-Practices)** - Production recommendations
- **[FAQ](FAQ)** - Frequently asked questions

## ⭐ Key Features

- **High Performance**: Handle millions to billions of jobs with virtual threads
- **MySQL Persistence**: All job data stored in MySQL with automatic recovery
- **Smart Duplicate Detection**: Only new jobs are scheduled, existing jobs skipped
- **Automatic Cleanup**: Completed jobs automatically removed
- **Clustering Support**: Multi-node scheduler clustering for high availability
- **Type Safety**: Generic design supports any entity type
- **Simple API**: Easy-to-use configuration and setup

## 📋 Quick Example

```java
// 1. Create your entity
@Table(name = "sms_schedules")
public class SmsEntity implements SchedulableEntity<Long> {
    @Id private Long id;
    @ShardingKey @Column(name = "scheduled_time") private LocalDateTime scheduledTime;
    @Column(name = "scheduled") private Boolean scheduled;
    // ... other fields
}

// 2. Configure scheduler
SchedulerConfig config = SchedulerConfig.builder()
    .fetchInterval(25)
    .lookaheadWindow(30) 
    .mysqlHost("127.0.0.1")
    .mysqlDatabase("scheduler")
    .repositoryTablePrefix("sms")
    .build();

// 3. Create and start scheduler
InfiniteScheduler<SmsEntity, Long> scheduler = 
    new InfiniteScheduler<>(SmsEntity.class, Long.class, config, SmsJob.class);
scheduler.start();
```

## 🔗 External Links

- **[GitHub Repository](https://github.com/your-repo/infinite-scheduler)**
- **[Partitioned-Repo Library](https://github.com/your-repo/partitioned-repo)**
- **[Quartz Scheduler Documentation](http://www.quartz-scheduler.org/)**

---

**Next:** Start with [Quick Start](Quick-Start) to get your first scheduler running!