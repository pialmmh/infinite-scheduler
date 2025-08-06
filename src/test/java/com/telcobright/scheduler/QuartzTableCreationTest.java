package com.telcobright.scheduler;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

public class QuartzTableCreationTest {
    
    @Test
    public void testQuartzTableCreation() {
        // Create a DataSource
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/scheduler?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        config.setUsername("root");
        config.setPassword("123456");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        
        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            QuartzTableManager tableManager = new QuartzTableManager(dataSource);
            tableManager.initializeQuartzTables();
            System.out.println("SUCCESS: Quartz tables created successfully");
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}