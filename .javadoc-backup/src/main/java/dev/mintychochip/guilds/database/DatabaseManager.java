package dev.mintychochip.guilds.database;

import dev.mintychochip.guilds.database.migration.SchemaInitializer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared-pool SQL access for guilds persistence.
 *
 * <p>Uses the host plugin's {@link DataSource}; it does not open a second pool.
 */
public class DatabaseManager {

    /** The logger. */
    private final Logger logger;
    /** The data source. */
    private final DataSource dataSource;
    /** The schema initializer. */
    private final SchemaInitializer schemaInitializer;
    /** The owns data source. */
    private final boolean ownsDataSource;

    /**
     * Creates a new database manager instance.
     * @param logger the logger
     * @param dataSource the data source
     * @param schemaInitializer the schema initializer
     */
    public DatabaseManager(Logger logger, DataSource dataSource, SchemaInitializer schemaInitializer) {
        this(logger, dataSource, schemaInitializer, false);
    }

    /**
     * Creates a new database manager instance.
     * @param logger the logger
     * @param dataSource the data source
     * @param schemaInitializer the schema initializer
     * @param ownsDataSource the owns data source
     */
    public DatabaseManager(Logger logger, DataSource dataSource,
                           SchemaInitializer schemaInitializer, boolean ownsDataSource) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.schemaInitializer = Objects.requireNonNull(schemaInitializer, "schemaInitializer");
        this.ownsDataSource = ownsDataSource;

        initializeDatabase();
    }

    /**
     * Initialize the database and create tables if they don't exist
     */
    public void initializeDatabase() {
        try {
            try (Connection connection = getConnection()) {
                if (connection != null && !connection.isClosed()) {
                    logger.info("Database connection established successfully.");

                    schemaInitializer.initialize(connection);

                    logger.info("Database initialization completed.");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize database: " + e.getMessage(), e);
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
            logger.info("Guilds test database pool closed.");
        } else {
            logger.info("Guilds database manager released shared PostgreSQL resources.");
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
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikari) {
            com.zaxxer.hikari.HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
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
                logger.log(Level.WARNING, "Transaction rolled back: " + e.getMessage(), e);
                return false;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to execute transaction: " + e.getMessage(), e);
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
                logger.log(Level.WARNING, "Transaction rolled back: " + e.getMessage(), e);
                return Optional.empty();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to execute transaction: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Interface for database transactions
     */
    @FunctionalInterface
    public interface TransactionCallback {
        /**
         * Performs the execute operation.
         * @param connection the connection
         * @throws SQLException if an error occurs
         */
        void execute(Connection connection) throws SQLException;
    }

    /**
     * Interface for database transactions with result
     */
    @FunctionalInterface
    public interface TransactionWithResultCallback<T> {
        /**
         * Performs the execute operation.
         * @param connection the connection
         * @return the result
         * @throws SQLException if an error occurs
         */
        T execute(Connection connection) throws SQLException;
    }
}
