package com.telcobright.scheduler;

import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SchedulerMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(SchedulerMonitor.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId BANGLADESH_TIMEZONE = ZoneId.of("Asia/Dhaka");
    
    private final Scheduler quartzScheduler;
    private final AtomicInteger totalJobsScheduled = new AtomicInteger(0);
    private final AtomicInteger totalJobsExecuted = new AtomicInteger(0);
    private final AtomicInteger totalJobsFailed = new AtomicInteger(0);
    private final AtomicLong lastFetchTime = new AtomicLong(0);
    private final AtomicInteger lastFetchJobCount = new AtomicInteger(0);
    private volatile LocalDateTime lastRunTime;
    private volatile String nextJobId;
    private volatile LocalDateTime nextRunTime;
    private volatile boolean running = false;
    private Thread monitorThread;
    
    public SchedulerMonitor(Scheduler quartzScheduler) {
        this.quartzScheduler = quartzScheduler;
    }
    
    public void start() {
        if (!running) {
            running = true;
            monitorThread = Thread.ofVirtual()
                .name("scheduler-monitor")
                .start(this::runMonitor);
            logger.info("Scheduler monitor started");
        }
    }
    
    public void stop() {
        running = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        logger.info("Scheduler monitor stopped");
    }
    
    private void runMonitor() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                printSchedulerStatus();
                Thread.sleep(5000); // Print status every 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in monitor thread", e);
            }
        }
    }
    
    private void printSchedulerStatus() {
        try {
            StringBuilder status = new StringBuilder("\n");
            status.append("╔══════════════════════════════════════════════════════════════════════════════╗\n");
            status.append("║                     INFINITE SCHEDULER - REAL-TIME STATUS                       ║\n");
            status.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
            
            // Current time in Bangladesh timezone
            LocalDateTime nowBD = ZonedDateTime.now(BANGLADESH_TIMEZONE).toLocalDateTime();
            status.append(String.format("║ Current Time (BD):    %-58s ║\n", nowBD.format(formatter) + " GMT+6"));
            
            // Scheduler state
            boolean isStarted = quartzScheduler.isStarted();
            boolean isShutdown = quartzScheduler.isShutdown();
            boolean isInStandbyMode = quartzScheduler.isInStandbyMode();
            String state = isShutdown ? "SHUTDOWN" : (isInStandbyMode ? "STANDBY" : (isStarted ? "RUNNING" : "STOPPED"));
            status.append(String.format("║ Scheduler State:      %-58s ║\n", state));
            
            // Last fetch information in Bangladesh time
            if (lastFetchTime.get() > 0) {
                LocalDateTime lastFetch = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(lastFetchTime.get()), 
                    BANGLADESH_TIMEZONE
                );
                status.append(String.format("║ Last Fetch (BD):      %-58s ║\n", 
                    lastFetch.format(formatter) + " (" + lastFetchJobCount.get() + " jobs)"));
            } else {
                status.append(String.format("║ Last Fetch (BD):      %-58s ║\n", "Not yet fetched"));
            }
            
            // Job statistics
            status.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
            status.append("║                              JOB STATISTICS                                     ║\n");
            status.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
            
            // Get current job counts from Quartz
            Set<JobKey> jobKeys = quartzScheduler.getJobKeys(GroupMatcher.anyJobGroup());
            int activeJobs = jobKeys.size();
            
            status.append(String.format("║ Total Jobs Scheduled: %-58d ║\n", totalJobsScheduled.get()));
            status.append(String.format("║ Active Jobs in Queue: %-58d ║\n", activeJobs));
            status.append(String.format("║ Jobs Executed:        %-58d ║\n", totalJobsExecuted.get()));
            status.append(String.format("║ Jobs Failed:          %-58d ║\n", totalJobsFailed.get()));
            
            // Currently executing jobs
            List<JobExecutionContext> currentlyExecuting = quartzScheduler.getCurrentlyExecutingJobs();
            status.append(String.format("║ Currently Executing:  %-58d ║\n", currentlyExecuting.size()));
            
            if (!currentlyExecuting.isEmpty() && currentlyExecuting.size() <= 3) {
                for (JobExecutionContext ctx : currentlyExecuting) {
                    String jobName = ctx.getJobDetail().getKey().getName();
                    if (jobName.length() > 56) {
                        jobName = jobName.substring(0, 53) + "...";
                    }
                    status.append(String.format("║   → %-71s ║\n", jobName));
                }
            }
            
            // Next scheduled jobs
            status.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
            status.append("║                           UPCOMING JOBS (Next 5)                                ║\n");
            status.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
            
            int upcomingCount = 0;
            
            for (JobKey jobKey : jobKeys) {
                if (upcomingCount >= 5) break;
                
                List<? extends Trigger> jobTriggers = quartzScheduler.getTriggersOfJob(jobKey);
                for (Trigger trigger : jobTriggers) {
                    if (upcomingCount >= 5) break;
                    
                    Date nextFireTime = trigger.getNextFireTime();
                    if (nextFireTime != null) {
                        LocalDateTime nextTimeBD = LocalDateTime.ofInstant(
                            nextFireTime.toInstant(), 
                            BANGLADESH_TIMEZONE
                        );
                        
                        String jobId = jobKey.getName();
                        if (jobId.length() > 30) {
                            jobId = jobId.substring(0, 27) + "...";
                        }
                        
                        String timeStr = nextTimeBD.format(formatter) + " BD";
                        String info = String.format("%s @ %s", jobId, timeStr);
                        status.append(String.format("║ %d. %-73s ║\n", upcomingCount + 1, info));
                        upcomingCount++;
                        
                        // Track the very next job
                        if (upcomingCount == 1) {
                            nextJobId = jobKey.getName();
                            nextRunTime = nextTimeBD;
                        }
                    }
                }
            }
            
            if (upcomingCount == 0) {
                status.append(String.format("║ %-76s ║\n", "No upcoming jobs scheduled"));
            }
            
            // System resources
            status.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
            status.append("║                            SYSTEM RESOURCES                                     ║\n");
            status.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
            
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory() / (1024 * 1024);
            long freeMemory = runtime.freeMemory() / (1024 * 1024);
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory() / (1024 * 1024);
            
            status.append(String.format("║ Memory Usage:         %d MB / %d MB (Max: %d MB)%s║\n", 
                usedMemory, totalMemory, maxMemory,
                " ".repeat(Math.max(0, 31 - String.valueOf(usedMemory).length() - 
                    String.valueOf(totalMemory).length() - String.valueOf(maxMemory).length()))));
            
            int availableProcessors = runtime.availableProcessors();
            status.append(String.format("║ Available CPUs:       %-58d ║\n", availableProcessors));
            
            // Thread pool info
            SchedulerMetaData metaData = quartzScheduler.getMetaData();
            status.append(String.format("║ Thread Pool Size:     %-58d ║\n", metaData.getThreadPoolSize()));
            
            status.append("╚══════════════════════════════════════════════════════════════════════════════╝");
            
            logger.info(status.toString());
            
        } catch (SchedulerException e) {
            logger.error("Error getting scheduler status", e);
        }
    }
    
    // Methods to update statistics
    public void recordJobScheduled() {
        totalJobsScheduled.incrementAndGet();
    }
    
    public void recordJobExecuted() {
        totalJobsExecuted.incrementAndGet();
        lastRunTime = LocalDateTime.now();
    }
    
    public void recordJobFailed() {
        totalJobsFailed.incrementAndGet();
    }
    
    public void recordFetch(int jobCount) {
        lastFetchTime.set(System.currentTimeMillis());
        lastFetchJobCount.set(jobCount);
    }
    
    public int getTotalJobsScheduled() {
        return totalJobsScheduled.get();
    }
    
    public int getTotalJobsExecuted() {
        return totalJobsExecuted.get();
    }
    
    public int getTotalJobsFailed() {
        return totalJobsFailed.get();
    }
}