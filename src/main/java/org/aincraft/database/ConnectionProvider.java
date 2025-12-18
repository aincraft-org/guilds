package org.aincraft.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides database connections.
 */
public interface ConnectionProvider extends AutoCloseable {

    /**
     * Gets a database connection.
     */
    Connection getConnection() throws SQLException;

    /**
     * Initializes the connection provider.
     */
    void initialize() throws SQLException;

    /**
     * Shuts down the connection provider.
     */
    void shutdown();

    /**
     * Gets the database type.
     */
    DatabaseType getDatabaseType();

    @Override
    default void close() {
        shutdown();
    }
}
