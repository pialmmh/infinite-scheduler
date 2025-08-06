package com.telcobright.scheduler;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerConfigTest {
    
    @Test
    void testValidConfig() {
        SchedulerConfig config = SchedulerConfig.builder()
            .fetchInterval(25)
            .lookaheadWindow(30)
            .mysqlHost("127.0.0.1")
            .mysqlPort(3306)
            .mysqlDatabase("test")
            .mysqlUsername("root")
            .mysqlPassword("123456")
            .repositoryDatabase("test")
            .repositoryTablePrefix("test_table")
            .build();
        
        assertEquals(25, config.getFetchIntervalSeconds());
        assertEquals(30, config.getLookaheadWindowSeconds());
        assertEquals("127.0.0.1", config.getMysqlHost());
        assertEquals(3306, config.getMysqlPort());
        assertEquals("test", config.getMysqlDatabase());
        assertEquals("root", config.getMysqlUsername());
        assertEquals("123456", config.getMysqlPassword());
        assertEquals("test", config.getRepositoryDatabase());
        assertEquals("test_table", config.getRepositoryTablePrefix());
        assertEquals("jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", config.getQuartzDataSource());
        assertEquals(10000, config.getMaxJobsPerFetch());
        assertEquals(SchedulerConfig.DEFAULT_MISFIRE_INSTRUCTION, config.getMisfireInstruction());
        assertFalse(config.isClusteringEnabled());
        assertEquals(10, config.getThreadPoolSize());
        assertEquals(1000, config.getBatchSize());
    }
    
    @Test
    void testInvalidLookaheadWindow() {
        assertThrows(IllegalArgumentException.class, () -> {
            SchedulerConfig.builder()
                .fetchInterval(30)
                .lookaheadWindow(25)
                .mysqlHost("127.0.0.1")
                .mysqlDatabase("test")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .repositoryDatabase("test")
                .repositoryTablePrefix("test_table")
                .build();
        });
    }
    
    @Test
    void testEqualLookaheadWindow() {
        assertThrows(IllegalArgumentException.class, () -> {
            SchedulerConfig.builder()
                .fetchInterval(30)
                .lookaheadWindow(30)
                .mysqlHost("127.0.0.1")
                .mysqlDatabase("test")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .repositoryDatabase("test")
                .repositoryTablePrefix("test_table")
                .build();
        });
    }
    
    @Test
    void testMissingMysqlCredentials() {
        // Test missing host
        assertThrows(IllegalArgumentException.class, () -> {
            SchedulerConfig.builder()
                .fetchInterval(25)
                .lookaheadWindow(30)
                .mysqlDatabase("test")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .build();
        });
        
        // Test missing database
        assertThrows(IllegalArgumentException.class, () -> {
            SchedulerConfig.builder()
                .fetchInterval(25)
                .lookaheadWindow(30)
                .mysqlHost("localhost")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .build();
        });
        
        // Test missing username
        assertThrows(IllegalArgumentException.class, () -> {
            SchedulerConfig.builder()
                .fetchInterval(25)
                .lookaheadWindow(30)
                .mysqlHost("localhost")
                .mysqlDatabase("test")
                .mysqlPassword("testpass")
                .build();
        });
    }
    
    @Test
    void testEmptyMysqlCredentials() {
        // Test empty host
        assertThrows(IllegalArgumentException.class, () -> {
            SchedulerConfig.builder()
                .fetchInterval(25)
                .lookaheadWindow(30)
                .mysqlHost("")
                .mysqlDatabase("test")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .build();
        });
        
        // Test empty database
        assertThrows(IllegalArgumentException.class, () -> {
            SchedulerConfig.builder()
                .fetchInterval(25)
                .lookaheadWindow(30)
                .mysqlHost("localhost")
                .mysqlDatabase("")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .build();
        });
    }
    
    @Test
    void testNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> {
            SchedulerConfig.builder()
                .fetchInterval(-1)
                .lookaheadWindow(30)
                .mysqlHost("localhost")
                .mysqlDatabase("test")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .build();
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            SchedulerConfig.builder()
                .fetchInterval(25)
                .lookaheadWindow(30)
                .mysqlHost("localhost")
                .mysqlDatabase("test")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .maxJobsPerFetch(-1)
                .build();
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            SchedulerConfig.builder()
                .fetchInterval(25)
                .lookaheadWindow(30)
                .mysqlHost("localhost")
                .mysqlDatabase("test")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .threadPoolSize(-1)
                .build();
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            SchedulerConfig.builder()
                .fetchInterval(25)
                .lookaheadWindow(30)
                .mysqlHost("localhost")
                .mysqlDatabase("test")
                .mysqlUsername("root")
                .mysqlPassword("123456")
                .batchSize(-1)
                .build();
        });
    }
    
    @Test
    void testProductionConfig() {
        SchedulerConfig config = SchedulerConfig.builder()
            .fetchInterval(15)
            .lookaheadWindow(45)
            .mysqlHost("127.0.0.1")
            .mysqlPort(3306)
            .mysqlDatabase("scheduler")
            .mysqlUsername("root")
            .mysqlPassword("123456")
            .repositoryDatabase("scheduler")
            .repositoryTablePrefix("prod_table")
            .maxJobsPerFetch(50000)
            .clusteringEnabled(true)
            .threadPoolSize(20)
            .batchSize(1000)
            .build();
        
        assertEquals(15, config.getFetchIntervalSeconds());
        assertEquals(45, config.getLookaheadWindowSeconds());
        assertEquals("127.0.0.1", config.getMysqlHost());
        assertEquals(3306, config.getMysqlPort());
        assertEquals("scheduler", config.getMysqlDatabase());
        assertEquals("root", config.getMysqlUsername());
        assertEquals("123456", config.getMysqlPassword());
        assertEquals("scheduler", config.getRepositoryDatabase());
        assertEquals("prod_table", config.getRepositoryTablePrefix());
        assertEquals(50000, config.getMaxJobsPerFetch());
        assertTrue(config.isClusteringEnabled());
        assertEquals(20, config.getThreadPoolSize());
        assertEquals(1000, config.getBatchSize());
    }
    
    @Test
    @SuppressWarnings("deprecation")
    void testDeprecatedQuartzDataSourceParsing() {
        // Test backward compatibility with deprecated quartzDataSource method
        SchedulerConfig config = SchedulerConfig.builder()
            .fetchInterval(25)
            .lookaheadWindow(30)
            .quartzDataSource("jdbc:mysql://testhost:3307/testdb?user=testuser&password=testpass")
            .repositoryDatabase("testdb")
            .repositoryTablePrefix("test_table")
            .build();
        
        assertEquals("testhost", config.getMysqlHost());
        assertEquals(3307, config.getMysqlPort());
        assertEquals("testdb", config.getMysqlDatabase());
        assertEquals("testuser", config.getMysqlUsername());
        assertEquals("testpass", config.getMysqlPassword());
    }
}