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
            "CREATE TABLE IF NOT EXISTS reconciliation_entries (idempotency_key TEXT PRIMARY KEY, doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS facilities (id TEXT PRIMARY KEY, doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS expenses (idempotency_key TEXT PRIMARY KEY, doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS guild_storage_banks ("
                    + "guild_id TEXT PRIMARY KEY,"
                    + "schema_version INTEGER NOT NULL,"
                    + "created_at TIMESTAMPTZ NOT NULL,"
                    + "updated_at TIMESTAMPTZ NOT NULL)",
            "CREATE TABLE IF NOT EXISTS guild_storage_tabs ("
                    + "guild_id TEXT NOT NULL,"
                    + "tab_id TEXT NOT NULL,"
                    + "display_name TEXT NOT NULL,"
                    + "ordinal INTEGER NOT NULL,"
                    + "capacity_slots INTEGER NOT NULL CHECK (capacity_slots > 0),"
                    + "unlocked BOOLEAN NOT NULL DEFAULT TRUE,"
                    + "PRIMARY KEY (guild_id, tab_id),"
                    + "UNIQUE (guild_id, ordinal),"
                    + "FOREIGN KEY (guild_id) REFERENCES guild_storage_banks (guild_id) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS guild_storage_slots ("
                    + "guild_id TEXT NOT NULL,"
                    + "tab_id TEXT NOT NULL,"
                    + "slot_index INTEGER NOT NULL CHECK (slot_index >= 0),"
                    + "item_schema TEXT NOT NULL,"
                    + "item_fingerprint TEXT NOT NULL,"
                    + "item_payload JSONB NOT NULL,"
                    + "updated_at TIMESTAMPTZ NOT NULL,"
                    + "PRIMARY KEY (guild_id, tab_id, slot_index),"
                    + "FOREIGN KEY (guild_id, tab_id) REFERENCES guild_storage_tabs (guild_id, tab_id) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS guild_storage_policies ("
                    + "guild_id TEXT PRIMARY KEY,"
                    + "deposit_rank TEXT NOT NULL,"
                    + "withdraw_rank TEXT NOT NULL,"
                    + "manage_rank TEXT NOT NULL,"
                    + "updated_at TIMESTAMPTZ NOT NULL,"
                    + "FOREIGN KEY (guild_id) REFERENCES guild_storage_banks (guild_id) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS guild_storage_audit ("
                    + "id BIGSERIAL PRIMARY KEY,"
                    + "guild_id TEXT NOT NULL,"
                    + "actor_uuid UUID NOT NULL,"
                    + "operation TEXT NOT NULL,"
                    + "tab_id TEXT NOT NULL,"
                    + "slot_index INTEGER,"
                    + "item_schema TEXT,"
                    + "item_fingerprint TEXT,"
                    + "facility_id TEXT NOT NULL,"
                    + "created_at TIMESTAMPTZ NOT NULL)"
    };

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
