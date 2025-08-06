package com.telcobright.scheduler;

import org.quartz.CronTrigger;

public class SchedulerConfig {
    
    public static final int DEFAULT_MISFIRE_INSTRUCTION = CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW;
    
    private final int fetchIntervalSeconds;
    private final int lookaheadWindowSeconds;
    private final String quartzDataSource;
    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUsername;
    private final String mysqlPassword;
    private final int maxJobsPerFetch;
    private final int misfireInstruction;
    private final boolean clusteringEnabled;
    private final int threadPoolSize;
    private final int batchSize;
    private final boolean autoCreateTables;
    private final boolean autoCleanupCompletedJobs;
    private final int cleanupIntervalMinutes;
    private final String repositoryDatabase;
    private final String repositoryTablePrefix;
    
    private SchedulerConfig(Builder builder) {
        this.fetchIntervalSeconds = builder.fetchIntervalSeconds;
        this.lookaheadWindowSeconds = builder.lookaheadWindowSeconds;
        this.mysqlHost = builder.mysqlHost;
        this.mysqlPort = builder.mysqlPort;
        this.mysqlDatabase = builder.mysqlDatabase;
        this.mysqlUsername = builder.mysqlUsername;
        this.mysqlPassword = builder.mysqlPassword;
        this.quartzDataSource = buildJdbcUrl();
        this.maxJobsPerFetch = builder.maxJobsPerFetch;
        this.misfireInstruction = builder.misfireInstruction;
        this.clusteringEnabled = builder.clusteringEnabled;
        this.threadPoolSize = builder.threadPoolSize;
        this.batchSize = builder.batchSize;
        this.autoCreateTables = builder.autoCreateTables;
        this.autoCleanupCompletedJobs = builder.autoCleanupCompletedJobs;
        this.cleanupIntervalMinutes = builder.cleanupIntervalMinutes;
        this.repositoryDatabase = builder.repositoryDatabase;
        this.repositoryTablePrefix = builder.repositoryTablePrefix;
        
        validate();
    }
    
    private String buildJdbcUrl() {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                mysqlHost, mysqlPort, mysqlDatabase);
    }
    
    private void validate() {
        if (lookaheadWindowSeconds <= fetchIntervalSeconds) {
            throw new IllegalArgumentException(
                "Lookahead window (" + lookaheadWindowSeconds + "s) must be greater than fetch interval (" + fetchIntervalSeconds + "s)"
            );
        }
        if (fetchIntervalSeconds <= 0) {
            throw new IllegalArgumentException("Fetch interval must be positive");
        }
        if (maxJobsPerFetch <= 0) {
            throw new IllegalArgumentException("Max jobs per fetch must be positive");
        }
        if (threadPoolSize <= 0) {
            throw new IllegalArgumentException("Thread pool size must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        if (mysqlHost == null || mysqlHost.trim().isEmpty()) {
            throw new IllegalArgumentException("MySQL host is required");
        }
        if (mysqlDatabase == null || mysqlDatabase.trim().isEmpty()) {
            throw new IllegalArgumentException("MySQL database is required");
        }
        if (mysqlUsername == null || mysqlUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("MySQL username is required");
        }
        if (mysqlPassword == null) {
            throw new IllegalArgumentException("MySQL password is required (can be empty string)");
        }
        if (mysqlPort <= 0 || mysqlPort > 65535) {
            throw new IllegalArgumentException("MySQL port must be between 1 and 65535");
        }
        if (repositoryDatabase == null || repositoryDatabase.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository database is required");
        }
        if (repositoryTablePrefix == null || repositoryTablePrefix.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository table prefix is required");
        }
    }
    
    public int getFetchIntervalSeconds() {
        return fetchIntervalSeconds;
    }
    
    public int getLookaheadWindowSeconds() {
        return lookaheadWindowSeconds;
    }
    
    public String getQuartzDataSource() {
        return quartzDataSource;
    }
    
    public int getMaxJobsPerFetch() {
        return maxJobsPerFetch;
    }
    
    public int getMisfireInstruction() {
        return misfireInstruction;
    }
    
    public boolean isClusteringEnabled() {
        return clusteringEnabled;
    }
    
    public int getThreadPoolSize() {
        return threadPoolSize;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public boolean isAutoCreateTables() {
        return autoCreateTables;
    }
    
    public boolean isAutoCleanupCompletedJobs() {
        return autoCleanupCompletedJobs;
    }
    
    public int getCleanupIntervalMinutes() {
        return cleanupIntervalMinutes;
    }
    
    public String getMysqlHost() {
        return mysqlHost;
    }
    
    public int getMysqlPort() {
        return mysqlPort;
    }
    
    public String getMysqlDatabase() {
        return mysqlDatabase;
    }
    
    public String getMysqlUsername() {
        return mysqlUsername;
    }
    
    public String getMysqlPassword() {
        return mysqlPassword;
    }
    
    public String getRepositoryDatabase() {
        return repositoryDatabase;
    }
    
    public String getRepositoryTablePrefix() {
        return repositoryTablePrefix;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int fetchIntervalSeconds = 25;
        private int lookaheadWindowSeconds = 30;
        private String mysqlHost;
        private int mysqlPort = 3306;
        private String mysqlDatabase;
        private String mysqlUsername;
        private String mysqlPassword;
        private int maxJobsPerFetch = 10000;
        private int misfireInstruction = DEFAULT_MISFIRE_INSTRUCTION;
        private boolean clusteringEnabled = false;
        private int threadPoolSize = 10;
        private int batchSize = 1000;
        private boolean autoCreateTables = true;
        private boolean autoCleanupCompletedJobs = true;
        private int cleanupIntervalMinutes = 60;
        private String repositoryDatabase;
        private String repositoryTablePrefix;
        
        public Builder fetchInterval(int fetchIntervalSeconds) {
            this.fetchIntervalSeconds = fetchIntervalSeconds;
            return this;
        }
        
        public Builder lookaheadWindow(int lookaheadWindowSeconds) {
            this.lookaheadWindowSeconds = lookaheadWindowSeconds;
            return this;
        }
        
        public Builder mysqlHost(String mysqlHost) {
            this.mysqlHost = mysqlHost;
            return this;
        }
        
        public Builder mysqlPort(int mysqlPort) {
            this.mysqlPort = mysqlPort;
            return this;
        }
        
        public Builder mysqlDatabase(String mysqlDatabase) {
            this.mysqlDatabase = mysqlDatabase;
            return this;
        }
        
        public Builder mysqlUsername(String mysqlUsername) {
            this.mysqlUsername = mysqlUsername;
            return this;
        }
        
        public Builder mysqlPassword(String mysqlPassword) {
            this.mysqlPassword = mysqlPassword;
            return this;
        }
        
        /**
         * @deprecated Use mysqlHost(), mysqlPort(), mysqlDatabase(), mysqlUsername(), mysqlPassword() instead
         */
        @Deprecated
        public Builder quartzDataSource(String quartzDataSource) {
            // Parse the JDBC URL to extract components - kept for backward compatibility
            if (quartzDataSource != null && quartzDataSource.startsWith("jdbc:mysql://")) {
                try {
                    java.net.URI uri = java.net.URI.create(quartzDataSource.substring(5)); // Remove "jdbc:"
                    this.mysqlHost = uri.getHost();
                    this.mysqlPort = uri.getPort() > 0 ? uri.getPort() : 3306;
                    this.mysqlDatabase = uri.getPath().substring(1); // Remove leading "/"
                    
                    // Parse query parameters for username and password
                    String query = uri.getQuery();
                    if (query != null) {
                        for (String param : query.split("&")) {
                            String[] kv = param.split("=", 2);
                            if (kv.length == 2) {
                                if ("user".equals(kv[0])) {
                                    this.mysqlUsername = java.net.URLDecoder.decode(kv[1], "UTF-8");
                                } else if ("password".equals(kv[0])) {
                                    this.mysqlPassword = java.net.URLDecoder.decode(kv[1], "UTF-8");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid JDBC URL format: " + quartzDataSource, e);
                }
            }
            return this;
        }
        
        public Builder maxJobsPerFetch(int maxJobsPerFetch) {
            this.maxJobsPerFetch = maxJobsPerFetch;
            return this;
        }
        
        public Builder misfireInstruction(int misfireInstruction) {
            this.misfireInstruction = misfireInstruction;
            return this;
        }
        
        public Builder clusteringEnabled(boolean clusteringEnabled) {
            this.clusteringEnabled = clusteringEnabled;
            return this;
        }
        
        public Builder threadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
            return this;
        }
        
        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }
        
        public Builder autoCreateTables(boolean autoCreateTables) {
            this.autoCreateTables = autoCreateTables;
            return this;
        }
        
        public Builder autoCleanupCompletedJobs(boolean autoCleanupCompletedJobs) {
            this.autoCleanupCompletedJobs = autoCleanupCompletedJobs;
            return this;
        }
        
        public Builder cleanupIntervalMinutes(int cleanupIntervalMinutes) {
            this.cleanupIntervalMinutes = cleanupIntervalMinutes;
            return this;
        }
        
        public Builder repositoryDatabase(String repositoryDatabase) {
            this.repositoryDatabase = repositoryDatabase;
            return this;
        }
        
        public Builder repositoryTablePrefix(String repositoryTablePrefix) {
            this.repositoryTablePrefix = repositoryTablePrefix;
            return this;
        }
        
        public SchedulerConfig build() {
            return new SchedulerConfig(this);
        }
    }
}