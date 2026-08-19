package org.aincraft.guilds.territory.persist;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Single HikariCP-backed database used by territory stores and Guilds services. */
public final class HikariDatabase implements Database {
    private final DatabaseSettings settings;
    private final DatabaseDialect dialect;
    private final HikariDataSource dataSource;

    static {
        loadDriver("org.postgresql.Driver");
        loadDriver("com.mysql.cj.jdbc.Driver");
    }

    public HikariDatabase(DatabaseSettings settings) throws IOException {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.dialect = settings.type() == DatabaseType.MYSQL ? new MySqlDialect() : new PostgresDialect();
        String url = settings.jdbcUrl();
        if (!dialect.acceptsJdbcUrl(url)) {
            throw new IOException(settings.type() + " JDBC URL required: " + url);
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("guilds-" + settings.type().name().toLowerCase(Locale.ROOT));
        config.setJdbcUrl(url);
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(Math.max(1, settings.poolSize()));
        config.setMinimumIdle(Math.min(2, Math.max(1, settings.poolSize())));
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(600_000);
        config.setConnectionTimeout(30_000);
        config.setInitializationFailTimeout(1);
        config.setLeakDetectionThreshold(60_000);
        config.setConnectionTestQuery(SqlStatements.load("support/connection-test.sql"));
        settings.dataSourceProperties().forEach((key, value) -> config.addDataSourceProperty((String) key, value));
        try {
            this.dataSource = new HikariDataSource(config);
            try (Connection ignored = dataSource.getConnection()) {
            }
        } catch (Exception e) {
            throw new IOException(settings.type() + " unavailable at " + url + " — " + e.getMessage(), e);
        }
    }

    private static void loadDriver(String name) {
        try {
            Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(name + " missing: " + e.getMessage());
        }
    }

    public HikariDataSource hikari() {
        return dataSource;
    }

    @Override public DataSource dataSource() { return dataSource; }
    @Override public Connection connection() throws SQLException { return dataSource.getConnection(); }
    @Override public DatabaseType type() { return settings.type(); }
    @Override public DatabaseDialect dialect() { return dialect; }

    @Override
    public void initializeSchema() throws IOException {
        try (Connection connection = connection()) {
            new SqlMigrationRunner().apply(connection, "persist", type(), Map.of());
        } catch (SQLException e) {
            throw new IOException("Failed to initialize shared " + settings.type() + " schema", e);
        }
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
