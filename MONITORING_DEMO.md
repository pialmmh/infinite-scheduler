# Infinite Scheduler - Monitoring Demo

## Real-Time Monitoring Features

The Infinite Scheduler includes comprehensive real-time monitoring that continuously displays useful debugging information.

### What the Monitor Shows:

#### System Status
- **Current Time**: Current system timestamp
- **Scheduler State**: RUNNING, STOPPED, STANDBY, or SHUTDOWN
- **Last Fetch**: When the fetcher last ran and how many jobs were found

#### Job Statistics
- **Total Jobs Scheduled**: Total number of jobs scheduled since startup
- **Active Jobs in Queue**: Current number of jobs waiting in Quartz
- **Jobs Executed**: Successfully completed jobs
- **Jobs Failed**: Failed job executions
- **Currently Executing**: Jobs being processed right now

#### Upcoming Schedule
- **Next 5 Jobs**: Shows the next jobs to execute with their IDs and execution times
- **Next Job ID**: ID of the very next job to run
- **Next Run Time**: When the next job will execute

#### System Resources
- **Memory Usage**: Current memory consumption vs available
- **Available CPUs**: Number of CPU cores
- **Thread Pool Size**: Quartz thread pool configuration

## Running the Demo

### Method 1: Monitoring Demo Test
```bash
mvn test -Dtest=MonitoringDemoTest#testMonitoringWithDummyJobs
```

This creates 5 dummy SMS jobs scheduled to execute every 20 seconds for the next 2 minutes, with monitoring output every 5 seconds.

### Method 2: Full Example
```bash
mvn exec:java -Dexec.mainClass="com.telcobright.scheduler.examples.SchedulerExample"
```

This creates 10 dummy SMS jobs scheduled every 30 seconds for the next 5 minutes, with monitoring output and a 10-minute runtime.

## Sample Monitor Output

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                     INFINITE SCHEDULER - REAL-TIME STATUS                       ║
╠══════════════════════════════════════════════════════════════════════════════╣
║ Current Time:         2025-08-06 19:44:03                                        ║
║ Scheduler State:      RUNNING                                                    ║
║ Last Fetch:           2025-08-06 19:43:58 (2 jobs)                               ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                              JOB STATISTICS                                     ║
╠══════════════════════════════════════════════════════════════════════════════╣
║ Total Jobs Scheduled: 15                                                         ║
║ Active Jobs in Queue: 2                                                          ║
║ Jobs Executed:        2                                                          ║
║ Jobs Failed:          0                                                          ║
║ Currently Executing:  0                                                          ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                           UPCOMING JOBS (Next 5)                                ║
╠══════════════════════════════════════════════════════════════════════════════╣
║ 1. job-4-2025-08-06T19:44:28 @ 2025-08-06 19:44:28                           ║
║ 2. job-3-2025-08-06T19:44:08 @ 2025-08-06 19:44:08                           ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                            SYSTEM RESOURCES                                     ║
╠══════════════════════════════════════════════════════════════════════════════╣
║ Memory Usage:         26 MB / 80 MB (Max: 7960 MB)                       ║
║ Available CPUs:       8                                                          ║
║ Thread Pool Size:     10                                                         ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

## Configuration

The monitoring system is automatically enabled when you create an `InfiniteScheduler`. It uses virtual threads for efficient resource usage and updates every 5 seconds by default.

### Demo Configuration
- **Fetch Interval**: 5-10 seconds (faster than production for demo)
- **Lookahead Window**: 30-60 seconds
- **Monitor Interval**: 5 seconds (fixed)
- **Demo Jobs**: 5-10 SMS jobs scheduled over 2-5 minutes

## What You'll See

1. **Job Creation**: Dummy SMS jobs being inserted into the repository
2. **Fetcher Activity**: Every few seconds, the fetcher finds and schedules jobs
3. **Job Execution**: Jobs executing at their scheduled times
4. **Statistics Updates**: Real-time counters updating as jobs are processed
5. **System Health**: Memory usage and resource consumption tracking

The monitoring provides comprehensive visibility into the scheduler's operation, making it easy to debug issues and understand system behavior.