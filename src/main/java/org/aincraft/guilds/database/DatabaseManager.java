package org.aincraft.guilds.database;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.config.DatabaseConfig;
import org.aincraft.guilds.database.migration.SchemaInitializer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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


    public DatabaseManager(JavaPlugin plugin, DatabaseConfig databaseConfig, SchemaInitializer schemaInitializer) {
        this.plugin = plugin;
        this.databaseConfig = databaseConfig;
        this.dataSource = databaseConfig.getDataSource();
        this.schemaInitializer = schemaInitializer;

        // Initialize database
        initializeDatabase();
    }

    /**
     * Initialize the database and create tables if they don't exist
     */
    public void initializeDatabase() {
        try {
            // Ensure database file exists
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
     * Create all necessary database tables
     */
    private void createTables() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            // Create residents table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS residents (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    town_name TEXT,
                    last_online INTEGER NOT NULL,
                    is_online BOOLEAN DEFAULT FALSE,
                    joined_at TEXT NOT NULL,
                    permissions_flags INTEGER DEFAULT 0
                )
            """);

            // Create towns table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS towns (
                    id TEXT PRIMARY KEY,
                    name TEXT UNIQUE NOT NULL,
                    mayor_uuid TEXT NOT NULL,
                    balance REAL DEFAULT 0.0,
                    home_block_x INTEGER,
                    home_block_z INTEGER,
                    home_block_world TEXT,
                    is_open BOOLEAN DEFAULT TRUE,
                    created_at TEXT NOT NULL,
                    permissions_flags INTEGER DEFAULT 0,
                    tax_rates TEXT, -- JSON string for tax rates
                    FOREIGN KEY (mayor_uuid) REFERENCES residents(uuid) ON DELETE SET NULL
                )
            """);

            // Create town residents mapping table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS town_residents (
                    town_id TEXT,
                    resident_uuid TEXT,
                    role TEXT DEFAULT 'resident', -- resident, assistant, mayor
                    joined_at TEXT NOT NULL,
                    PRIMARY KEY (town_id, resident_uuid),
                    FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE,
                    FOREIGN KEY (resident_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
                )
            """);

            // Create town blocks table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS town_blocks (
                    id TEXT PRIMARY KEY,
                    x INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    world TEXT NOT NULL,
                    town_id TEXT,
                    owner_uuid TEXT,
                    plot_type TEXT DEFAULT 'default',
                    price REAL DEFAULT 0.0,
                    permissions_flags INTEGER DEFAULT 0,
                    claimed_at TEXT NOT NULL,
                    custom_name TEXT,
                    FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE SET NULL,
                    FOREIGN KEY (owner_uuid) REFERENCES residents(uuid) ON DELETE SET NULL,
                    UNIQUE(x, z, world)
                )
            """);

            // Create permissions table (using bitwise flags)
            statement.execute("""
                CREATE TABLE IF NOT EXISTS permissions (
                    id TEXT PRIMARY KEY,
                    context TEXT NOT NULL, -- town, plot, resident, global
                    context_id TEXT NOT NULL,
                    target_type TEXT NOT NULL, -- resident, town, all, assistant, mayor
                    target_id TEXT, -- can be null for 'all'
                    permissions_flags INTEGER NOT NULL,
                    granted_at TEXT NOT NULL,
                    granted_by_uuid TEXT,
                    FOREIGN KEY (granted_by_uuid) REFERENCES residents(uuid) ON DELETE SET NULL
                )
            """);

            // Create indexes for better performance
            statement.execute("CREATE INDEX IF NOT EXISTS idx_residents_town ON residents(town_name)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_towns_name ON towns(name)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_town_blocks_location ON town_blocks(x, z, world)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_town_blocks_town ON town_blocks(town_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_town_blocks_owner ON town_blocks(owner_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_permissions_context ON permissions(context, context_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_permissions_target ON permissions(target_type, target_id)");

            plugin.getLogger().info("Database tables and indexes created/verified successfully.");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables: " + e.getMessage(), e);
        }
    }

    /**
     * Close the database connection pool
     */
    public void shutdown() {
        if (dataSource instanceof javax.sql.DataSource) {
            try {
                if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
                    ((com.zaxxer.hikari.HikariDataSource) dataSource).close();
                }
                plugin.getLogger().info("Database connection pool closed.");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error closing database connection pool: " + e.getMessage(), e);
            }
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