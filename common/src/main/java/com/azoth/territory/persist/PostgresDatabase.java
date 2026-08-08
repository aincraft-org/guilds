package com.azoth.territory.persist;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Owns the single PostgreSQL connection pool used by every durable store.
 */
public final class PostgresDatabase implements AutoCloseable {
    private static final String[] COMMON_SCHEMA = {
            "CREATE TABLE IF NOT EXISTS territories (id TEXT PRIMARY KEY, doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS influence_state (id INTEGER PRIMARY KEY CHECK (id = 1), doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS standing_state (id INTEGER PRIMARY KEY CHECK (id = 1), doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS reconciliation_entries (idempotency_key TEXT PRIMARY KEY, doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS facilities (id TEXT PRIMARY KEY, doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS expenses (idempotency_key TEXT PRIMARY KEY, doc JSONB NOT NULL)"
    };

    static {
        // The JDBC driver is shaded into the plugin jar. Paper loads plugins
        // through their own classloaders, so DriverManager (system classloader)
        // never sees the driver from the service file. Register it explicitly —
        // this is the standard fix for shaded JDBC drivers on Bukkit/Paper.
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("PostgreSQL JDBC driver missing: " + e.getMessage());
        }
    }

    private final HikariDataSource dataSource;

    public PostgresDatabase(DatabaseSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        if (!settings.jdbcUrl().startsWith("jdbc:postgresql:")) {
            throw new IOException("PostgreSQL JDBC URL required: " + settings.jdbcUrl());
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("azoth-postgres");
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(settings.poolSize());
        config.setMinimumIdle(Math.min(2, Math.max(1, settings.poolSize())));
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(600_000);
        config.setConnectionTimeout(30_000);
        config.setConnectionTestQuery("SELECT 1");
        try {
            this.dataSource = new HikariDataSource(config);
            try (Connection ignored = dataSource.getConnection()) {
                // Force a connection now so startup fails before services are wired.
            }
        } catch (Exception e) {
            throw new IOException("PostgreSQL unavailable at " + settings.jdbcUrl()
                    + " — " + e.getMessage(), e);
        }
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    public void initializeSchema() throws IOException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            for (String sql : COMMON_SCHEMA) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IOException("Failed to initialize shared PostgreSQL schema", e);
        }
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
