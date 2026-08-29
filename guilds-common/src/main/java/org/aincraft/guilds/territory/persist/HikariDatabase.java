package org.aincraft.guilds.territory.persist;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariPoolMXBean;

import org.aincraft.db.sql.SqlDatabase;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Single utility-managed Hikari/Jdbi database used by territory stores and Guilds services. */
public final class HikariDatabase implements Database {
    private final DatabaseSettings settings;
    private final DatabaseDialect dialect;
    private final SqlDatabase database;
    private final DataSource dataSource;
    private final String poolName;

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
        this.poolName = "guilds-" + settings.type().name().toLowerCase(Locale.ROOT)
                + "-" + UUID.randomUUID();
        config.setPoolName(poolName);
        config.setRegisterMbeans(true);
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
        settings.dataSourceProperties().forEach((key, value) ->
                config.addDataSourceProperty((String) key, value));

        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(HikariDatabase.class.getClassLoader());
        SqlDatabase openedDatabase = null;
        try {
            // SqlDatabase owns the sole Hikari pool. The JDBC adapter below is only a compatibility
            // view for existing consumers; it never creates or closes a second pool.
            openedDatabase = SqlDatabase.create(config, "classpath:guilds-no-runtime-migrations");
            this.database = openedDatabase;
            this.dataSource = new JdbiDataSource(openedDatabase.jdbi());
        } catch (RuntimeException exception) {
            if (openedDatabase != null) {
                openedDatabase.close();
            }
            throw new IOException(settings.type() + " unavailable at " + url + " — "
                    + exception.getMessage(), exception);
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    private static void loadDriver(String name) {
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
    @Override
    public String poolStatistics() {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("com.zaxxer.hikari:type=Pool (" + poolName + ")");
            HikariPoolMXBean pool = JMX.newMXBeanProxy(server, name, HikariPoolMXBean.class);
            return String.format("Active: %d, Idle: %d, Total: %d, Waiting: %d",
                    pool.getActiveConnections(),
                    pool.getIdleConnections(),
                    pool.getTotalConnections(),
                    pool.getThreadsAwaitingConnection());
        } catch (Exception ignored) {
            return "Pool statistics not available";
        }
    }
    @Override
    public void initializeSchema() throws IOException {
        // Guilds and persistence tracks use the consumer-owned runner so existing
        // sql_schema_migrations history and legacy rename hooks remain authoritative.
        try (Connection connection = connection()) {
            new SqlMigrationRunner().apply(connection, "persist", type(), Map.of());
        } catch (SQLException e) {
            throw new IOException("Failed to initialize shared " + settings.type() + " schema", e);
        }
    }

    @Override
    public void close() {
        if (!database.closed()) {
            database.close();
        }
    }
}
