package com.telcobright.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class QuartzTableManager {
    
    private static final Logger logger = LoggerFactory.getLogger(QuartzTableManager.class);
    
    private static final String[] QUARTZ_TABLES = {
        "QRTZ_JOB_DETAILS",
        "QRTZ_TRIGGERS",
        "QRTZ_SIMPLE_TRIGGERS",
        "QRTZ_CRON_TRIGGERS",
        "QRTZ_SIMPROP_TRIGGERS",
        "QRTZ_BLOB_TRIGGERS",
        "QRTZ_CALENDARS",
        "QRTZ_PAUSED_TRIGGER_GRPS",
        "QRTZ_FIRED_TRIGGERS",
        "QRTZ_SCHEDULER_STATE",
        "QRTZ_LOCKS"
    };
    
    private final DataSource dataSource;
    private final String tablePrefix;
    
    public QuartzTableManager(DataSource dataSource) {
        this(dataSource, "QRTZ_");
    }
    
    public QuartzTableManager(DataSource dataSource, String tablePrefix) {
        this.dataSource = dataSource;
        this.tablePrefix = tablePrefix;
    }
    
    public void initializeQuartzTables() {
        logger.info("Checking Quartz tables...");
        
        try (Connection conn = dataSource.getConnection()) {
            List<String> missingTables = getMissingTables(conn);
            
            if (missingTables.isEmpty()) {
                logger.info("All Quartz tables exist");
                return;
            }
            
            logger.info("Missing Quartz tables: {}. Creating them now...", missingTables);
            createQuartzTables(conn);
            logger.info("Successfully created all Quartz tables");
            
        } catch (SQLException e) {
            logger.error("Failed to initialize Quartz tables", e);
            throw new RuntimeException("Failed to initialize Quartz tables", e);
        }
    }
    
    private List<String> getMissingTables(Connection conn) throws SQLException {
        List<String> missingTables = new ArrayList<>();
        DatabaseMetaData metaData = conn.getMetaData();
        
        for (String tableName : QUARTZ_TABLES) {
            String actualTableName = tableName.replace("QRTZ_", tablePrefix);
            
            try (ResultSet rs = metaData.getTables(
                    conn.getCatalog(), 
                    null, 
                    actualTableName, 
                    new String[]{"TABLE"})) {
                
                if (!rs.next()) {
                    missingTables.add(actualTableName);
                }
            }
        }
        
        return missingTables;
    }
    
    private void createQuartzTables(Connection conn) throws SQLException {
        String schemaScript = loadSchemaScript();
        
        // Replace default prefix if necessary
        if (!tablePrefix.equals("QRTZ_")) {
            schemaScript = schemaScript.replace("QRTZ_", tablePrefix);
        }
        
        // Split the script into individual statements
        String[] statements = schemaScript.split(";");
        
        // Debug: log first few statements to see their format
        logger.info("Total statements found: {}", statements.length);
        for (int i = 0; i < Math.min(statements.length, 10); i++) {
            String statement = statements[i].trim();
            if (!statement.isEmpty()) {
                logger.info("Statement {}: '{}'", i, statement.length() > 100 ? statement.substring(0, 100) + "..." : statement);
            }
        }
        
        try (Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false);
            
            // First pass: Create all tables
            logger.info("Creating tables...");
            int tableCount = 0;
            for (String sql : statements) {
                sql = sql.trim();
                
                // Clean SQL by removing comments and extra whitespace
                String cleanedSql = cleanSqlStatement(sql);
                if (cleanedSql.isEmpty()) {
                    continue;
                }
                
                String sqlUpper = cleanedSql.toUpperCase();
                if (sqlUpper.startsWith("CREATE TABLE")) {
                    logger.info("Creating table: {}", cleanedSql.substring(0, Math.min(cleanedSql.length(), 100)) + "...");
                    try {
                        stmt.execute(cleanedSql);
                        tableCount++;
                        logger.info("Table created successfully");
                    } catch (SQLException e) {
                        logger.error("Failed to create table with SQL: {}", cleanedSql, e);
                        throw e;
                    }
                }
            }
            logger.info("Created {} tables total", tableCount);
            
            // Second pass: Insert initial data
            logger.info("Inserting initial data...");
            for (String sql : statements) {
                String cleanedSql = cleanSqlStatement(sql.trim());
                if (!cleanedSql.isEmpty() && cleanedSql.toUpperCase().startsWith("INSERT")) {
                    logger.info("Inserting data: {}", cleanedSql);
                    try {
                        stmt.execute(cleanedSql);
                    } catch (SQLException e) {
                        logger.error("Failed to execute INSERT statement: {}", cleanedSql, e);
                        throw e;
                    }
                }
            }
            
            // Third pass: Create indexes
            logger.info("Creating indexes...");
            for (String sql : statements) {
                String cleanedSql = cleanSqlStatement(sql.trim());
                if (!cleanedSql.isEmpty() && cleanedSql.toUpperCase().startsWith("CREATE INDEX")) {
                    logger.info("Creating index: {}", cleanedSql.substring(0, Math.min(cleanedSql.length(), 50)) + "...");
                    stmt.execute(cleanedSql);
                }
            }
            
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw new SQLException("Failed to create Quartz tables", e);
        } finally {
            conn.setAutoCommit(true);
        }
    }
    
    private String loadSchemaScript() {
        String resourcePath = "/sql/quartz-mysql-schema.sql";
        
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Could not find Quartz schema file: " + resourcePath);
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Quartz schema script", e);
        }
    }
    
    public boolean verifyQuartzTables() {
        try (Connection conn = dataSource.getConnection()) {
            List<String> missingTables = getMissingTables(conn);
            
            if (!missingTables.isEmpty()) {
                logger.warn("Missing Quartz tables: {}", missingTables);
                return false;
            }
            
            logger.info("All Quartz tables verified successfully");
            return true;
            
        } catch (SQLException e) {
            logger.error("Failed to verify Quartz tables", e);
            return false;
        }
    }
    
    private String cleanSqlStatement(String sql) {
        if (sql == null || sql.isEmpty()) {
            return "";
        }
        
        // Remove single-line comments (--) but keep the SQL after them
        StringBuilder cleaned = new StringBuilder();
        String[] lines = sql.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            
            // If line starts with --, it's a comment, skip it
            if (line.startsWith("--")) {
                continue;
            }
            
            // If line contains -- somewhere, remove the comment part
            int commentIndex = line.indexOf("--");
            if (commentIndex >= 0) {
                line = line.substring(0, commentIndex).trim();
            }
            
            if (!line.isEmpty()) {
                cleaned.append(line).append("\n");
            }
        }
        
        return cleaned.toString().trim();
    }
}