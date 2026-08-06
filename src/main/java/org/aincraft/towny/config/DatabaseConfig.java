package org.aincraft.towny.config;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.aincraft.towny.TownyPlugin;

import javax.sql.DataSource;
import java.io.File;

/**
 * Database configuration manager for Towny plugin
 */
public class DatabaseConfig {

    private final TownyPlugin plugin;
    private final File databaseFile;
    private final String databaseUrl;
    private DataSource dataSource;

    @Inject
    public DatabaseConfig(TownyPlugin plugin,
                         @Named("databaseFile") File databaseFile,
                         @Named("databaseUrl") String databaseUrl) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
        this.databaseUrl = databaseUrl;
        setupDataSource();
    }

    /**
     * Set up the HikariCP data source
     */
    private void setupDataSource() {
        HikariConfig config = new HikariConfig();

        // SQLite configuration
        config.setJdbcUrl(databaseUrl);
        config.setDriverClassName("org.sqlite.JDBC");

        // Connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000); // 5 minutes
        config.setMaxLifetime(600000); // 10 minutes
        config.setConnectionTimeout(30000); // 30 seconds

        // SQLite specific settings
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        // Enable foreign key support in SQLite
        config.addDataSourceProperty("foreign_keys", "true");

        // Set connection test query for SQLite
        config.setConnectionTestQuery("SELECT 1");

        try {
            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("Database connection pool initialized successfully.");
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to initialize database connection pool", e);
        }
    }

    /**
     * Get the configured data source
     * @return HikariCP data source
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Get the database file
     * @return Database file
     */
    public File getDatabaseFile() {
        return databaseFile;
    }

    /**
     * Get the database URL
     * @return Database URL
     */
    public String getDatabaseUrl() {
        return databaseUrl;
    }

    /**
     * Check if the database file exists
     * @return True if database file exists
     */
    public boolean databaseExists() {
        return databaseFile.exists();
    }

    /**
     * Create the database file and directory if they don't exist
     */
    public void ensureDatabaseExists() {
        if (!databaseFile.exists()) {
            try {
                File parentDir = databaseFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                databaseFile.createNewFile();
                plugin.getLogger().info("Created new database file: " + databaseFile.getAbsolutePath());
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create database file", e);
            }
        }
    }

    /**
     * Close the data source and cleanup resources
     */
    public void shutdown() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }
}