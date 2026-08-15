package com.azoth.territory.persist;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/** Compatibility wrapper for the PostgreSQL backend. */
public final class PostgresDatabase implements Database {
    private final HikariDataSource dataSource;
    private final PostgresDialect dialect = new PostgresDialect();

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("PostgreSQL JDBC driver missing: " + e.getMessage());
        }
    }

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
        settings.dataSourceProperties().forEach((k, v) -> config.addDataSourceProperty((String) k, v));
        try {
            this.dataSource = new HikariDataSource(config);
            try (Connection ignored = dataSource.getConnection()) { }
        } catch (Exception e) {
            throw new IOException("PostgreSQL unavailable at " + settings.jdbcUrl() + " — " + e.getMessage(), e);
        }
    }

    @Override public DataSource dataSource() { return dataSource; }
    @Override public Connection connection() throws SQLException { return dataSource.getConnection(); }
    @Override public DatabaseType type() { return DatabaseType.POSTGRESQL; }
    @Override public DatabaseDialect dialect() { return dialect; }
    @Override public void initializeSchema() throws IOException {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            for (String sql : dialect.schemaStatements()) s.execute(sql);
        } catch (SQLException e) {
            throw new IOException("Failed to initialize shared PostgreSQL schema", e);
        }
    }
    @Override public void close() { if (!dataSource.isClosed()) dataSource.close(); }
}
