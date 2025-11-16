package com.telcobright.scheduler.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration properties for Infinite Scheduler.
 * Maps to properties prefixed with "scheduler." in application.properties
 */
@ConfigMapping(prefix = "scheduler")
public interface InfiniteSchedulerProperties {

    /**
     * MySQL database configuration
     */
    MysqlConfig mysql();

    interface MysqlConfig {
        /**
         * MySQL host
         */
        @WithDefault("127.0.0.1")
        String host();

        /**
         * MySQL port
         */
        @WithDefault("3306")
        int port();

        /**
         * Database name
         */
        @WithDefault("scheduler")
        String database();

        /**
         * Database username
         */
        @WithDefault("root")
        String username();

        /**
         * Database password
         */
        @WithDefault("")
        String password();
    }
}
