package com.azoth.territory.persist;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;

public final class DatabaseFactory {
    private DatabaseFactory() { }

    public static Database open(DatabaseSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        return new PooledDatabase(settings);
    }

    private static final class PooledDatabase implements Database {
        private final DatabaseSettings settings;
        private final DatabaseDialect dialect;
        private final HikariDataSource dataSource;

        static {
            load("org.postgresql.Driver");
            load("com.mysql.cj.jdbc.Driver");
        }

        private PooledDatabase(DatabaseSettings settings) throws IOException {
            this.settings = settings;
            this.dialect = settings.type() == DatabaseType.MYSQL
                    ? new MySqlDialect() : new PostgresDialect();
            String url = settings.jdbcUrl();
            if (!dialect.acceptsJdbcUrl(url)) {
                throw new IOException(settings.type() + " JDBC URL required: " + url);
            }
            HikariConfig config = new HikariConfig();
            config.setPoolName("azoth-" + settings.type().name().toLowerCase(Locale.ROOT));
            config.setJdbcUrl(url);
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
                try (Connection ignored = dataSource.getConnection()) { }
            } catch (Exception e) {
                throw new IOException(settings.type() + " unavailable at " + url + " — " + e.getMessage(), e);
            }
        }

        private static void load(String name) {
            try {
                Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new ExceptionInInitializerError(name + " missing: " + e.getMessage());
            }
        }

        @Override public DataSource dataSource() { return dataSource; }
        @Override public Connection connection() throws SQLException { return dataSource.getConnection(); }
        @Override public DatabaseType type() { return settings.type(); }
        @Override public DatabaseDialect dialect() { return dialect; }
        @Override public void initializeSchema() throws IOException {
            try (Connection c = connection(); Statement s = c.createStatement()) {
                for (String sql : dialect.schemaStatements()) s.execute(sql);
            } catch (SQLException e) {
                throw new IOException("Failed to initialize shared " + settings.type() + " schema", e);
            }
        }
        @Override public void close() { if (!dataSource.isClosed()) dataSource.close(); }
    }
}
