package com.telcobright.scheduler.config;

/**
 * Configuration for a single application in the multi-app scheduler.
 * Each app (sms, sipcall, payment_gateway, etc.) has its own configuration.
 */
public class AppConfig {
    private final String appName;
    private final String tablePrefix;
    private final String historyTableName;
    private final int fetchIntervalSeconds;
    private final int lookaheadWindowSeconds;
    private final int maxJobsPerFetch;

    public AppConfig(String appName) {
        this(appName,
             appName + "_scheduled_jobs",  // Default table prefix
             appName + "_job_execution_history",  // Default history table
             5,  // Default fetch interval
             30, // Default lookahead window
             1000); // Default max jobs per fetch
    }

    public AppConfig(String appName, String tablePrefix, String historyTableName,
                    int fetchIntervalSeconds, int lookaheadWindowSeconds, int maxJobsPerFetch) {
        this.appName = appName;
        this.tablePrefix = tablePrefix;
        this.historyTableName = historyTableName;
        this.fetchIntervalSeconds = fetchIntervalSeconds;
        this.lookaheadWindowSeconds = lookaheadWindowSeconds;
        this.maxJobsPerFetch = maxJobsPerFetch;
    }

    public String getAppName() {
        return appName;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public String getHistoryTableName() {
        return historyTableName;
    }

    public int getFetchIntervalSeconds() {
        return fetchIntervalSeconds;
    }

    public int getLookaheadWindowSeconds() {
        return lookaheadWindowSeconds;
    }

    public int getMaxJobsPerFetch() {
        return maxJobsPerFetch;
    }

    public static Builder builder(String appName) {
        return new Builder(appName);
    }

    public static class Builder {
        private String appName;
        private String tablePrefix;
        private String historyTableName;
        private int fetchIntervalSeconds = 5;
        private int lookaheadWindowSeconds = 30;
        private int maxJobsPerFetch = 1000;

        public Builder(String appName) {
            this.appName = appName;
            this.tablePrefix = appName + "_scheduled_jobs";
            this.historyTableName = appName + "_job_execution_history";
        }

        public Builder tablePrefix(String tablePrefix) {
            this.tablePrefix = tablePrefix;
            return this;
        }

        public Builder historyTableName(String historyTableName) {
            this.historyTableName = historyTableName;
            return this;
        }

        public Builder fetchIntervalSeconds(int fetchIntervalSeconds) {
            this.fetchIntervalSeconds = fetchIntervalSeconds;
            return this;
        }

        public Builder lookaheadWindowSeconds(int lookaheadWindowSeconds) {
            this.lookaheadWindowSeconds = lookaheadWindowSeconds;
            return this;
        }

        public Builder maxJobsPerFetch(int maxJobsPerFetch) {
            this.maxJobsPerFetch = maxJobsPerFetch;
            return this;
        }

        public AppConfig build() {
            return new AppConfig(appName, tablePrefix, historyTableName,
                fetchIntervalSeconds, lookaheadWindowSeconds, maxJobsPerFetch);
        }
    }
}