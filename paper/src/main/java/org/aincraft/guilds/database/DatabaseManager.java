package org.aincraft.guilds.database;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.config.DatabaseConfig;
import org.aincraft.guilds.database.migration.SchemaInitializer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Database manager for Guilds plugin
 * Handles database connections, initialization, and basic operations
 */

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final DatabaseConfig databaseConfig;
    private final DataSource dataSource;
    private final SchemaInitializer schemaInitializer;
    private final boolean ownsDataSource;

    public DatabaseManager(JavaPlugin plugin, DatabaseConfig databaseConfig, SchemaInitializer schemaInitializer) {
        this(plugin, databaseConfig, schemaInitializer, false);
    }

    public DatabaseManager(JavaPlugin plugin, DatabaseConfig databaseConfig,
                           SchemaInitializer schemaInitializer, boolean ownsDataSource) {
        this.plugin = plugin;
        this.databaseConfig = databaseConfig;
        this.dataSource = databaseConfig.getDataSource();
        this.schemaInitializer = schemaInitializer;
        this.ownsDataSource = ownsDataSource;

        // Initialize database
        initializeDatabase();
    }

    /**
     * Initialize the database and create tables if they don't exist
     */
    public void initializeDatabase() {
        try {
            databaseConfig.ensureDatabaseExists();

            // Test connection
            try (Connection connection = getConnection()) {
                if (connection != null && !connection.isClosed()) {
                    plugin.getLogger().info("Database connection established successfully.");

                    // Initialize schema with migrations
                    schemaInitializer.initialize(connection);

                    plugin.getLogger().info("Database initialization completed.");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database: " + e.getMessage(), e);
        }
    }

    /**
     * Get a database connection from the connection pool
     * @return Database connection
     * @throws SQLException If connection fails
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Get the data source
     * @return Data source
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Close the database connection pool
     */
    public void shutdown() {
        if (ownsDataSource && dataSource instanceof com.zaxxer.hikari.HikariDataSource hikari) {
            hikari.close();
            plugin.getLogger().info("Guilds test database pool closed.");
        } else {
            plugin.getLogger().info("Guilds database manager released shared "
                    + databaseConfig.type() + " resources.");
        }
    }

    /**
     * Check if database connection is healthy
     * @return True if connection is healthy
     */
    public boolean isHealthy() {
        try (Connection connection = getConnection()) {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Get database connection pool statistics
     * @return Connection pool info as string
     */
    public String getPoolStatistics() {
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
            com.zaxxer.hikari.HikariPoolMXBean pool = ((com.zaxxer.hikari.HikariDataSource) dataSource).getHikariPoolMXBean();
            return String.format("Active: %d, Idle: %d, Total: %d, Waiting: %d",
                    pool.getActiveConnections(),
                    pool.getIdleConnections(),
                    pool.getTotalConnections(),
                    pool.getThreadsAwaitingConnection());
        }
        return "Pool statistics not available";
    }

    /**
     * Execute a database transaction
     * @param transaction Transaction to execute
     * @return True if transaction succeeded
     */
    public boolean executeTransaction(TransactionCallback transaction) {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                transaction.execute(connection);
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                plugin.getLogger().log(Level.WARNING, "Transaction rolled back: " + e.getMessage(), e);
                return false;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to execute transaction: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Execute a database transaction with result
     * @param transaction Transaction to execute
     * @param <T> Result type
     * @return Optional result from transaction
     */
    public <T> Optional<T> executeTransactionWithResult(TransactionWithResultCallback<T> transaction) {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = transaction.execute(connection);
                connection.commit();
                return Optional.ofNullable(result);
            } catch (SQLException e) {
                connection.rollback();
                plugin.getLogger().log(Level.WARNING, "Transaction rolled back: " + e.getMessage(), e);
                return Optional.empty();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to execute transaction: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Interface for database transactions
     */
    @FunctionalInterface
    public interface TransactionCallback {
        void execute(Connection connection) throws SQLException;
    }

    /**
     * Interface for database transactions with result
     */
    @FunctionalInterface
    public interface TransactionWithResultCallback<T> {
        T execute(Connection connection) throws SQLException;
    }
}