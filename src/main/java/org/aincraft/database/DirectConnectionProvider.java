package org.aincraft.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Direct connection provider without pooling.
 * Used for SQLite where connection pooling provides no benefit.
 */
public class DirectConnectionProvider implements ConnectionProvider {
    private final DatabaseConfig config;
    private final String jdbcUrl;

    public DirectConnectionProvider(DatabaseConfig config) {
        this.config = config;
        this.jdbcUrl = config.buildJdbcUrl();
    }

    @Override
    public void initialize() throws SQLException {
        try {
            Class.forName(config.getType().getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + config.getType().getDriverClass(), e);
        }

        // Test connection
        try (Connection conn = getConnection()) {
            if (!conn.isValid(5)) {
                throw new SQLException("Database connection is not valid");
            }
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    @Override
    public void shutdown() {
        // No pooled connections to close
    }

    @Override
    public DatabaseType getDatabaseType() {
        return config.getType();
    }
}
