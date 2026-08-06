package com.azoth.territory.persist;

import com.azoth.territory.model.Territory;
import com.azoth.territory.registry.TerritoryRegistry;
import com.google.gson.JsonParser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Remote PostgreSQL store for the territory registry.
 * <p>
 * The database is assumed remote: connections come from a pooled, validated
 * HikariCP data source and every save is a single transaction, so a failed
 * write leaves the previous state fully intact.
 */
public final class PostgresTerritoryRepository implements TerritoryRepository {
    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS territories (
                id  TEXT PRIMARY KEY,
                doc JSONB NOT NULL
            )
            """;

    private final HikariDataSource dataSource;
    private final TerritoryJson json = new TerritoryJson();

    /**
     * @throws IOException if the database is unreachable or schema init fails
     */
    public PostgresTerritoryRepository(DatabaseSettings settings) throws IOException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("azoth-territory-pg");
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(settings.poolSize());
        config.setMinimumIdle(2);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(600_000);
        config.setConnectionTimeout(30_000);
        config.setConnectionTestQuery("SELECT 1");

        HikariDataSource ds = null;
        try {
            ds = new HikariDataSource(config);
            try (Connection c = ds.getConnection();
                 Statement s = c.createStatement()) {
                s.execute(SCHEMA);
            }
        } catch (SQLException | RuntimeException e) {
            if (ds != null) {
                ds.close();
            }
            throw new IOException("PostgreSQL unavailable at " + settings.jdbcUrl()
                    + " — " + e.getMessage(), e);
        }
        this.dataSource = ds;
    }

    @Override
    public void loadInto(TerritoryRegistry registry) throws IOException {
        List<Territory> territories = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT doc FROM territories")) {
            while (rs.next()) {
                String doc = rs.getString("doc");
                territories.add(json.fromJson(JsonParser.parseString(doc).getAsJsonObject()));
            }
        } catch (SQLException e) {
            throw new IOException("Failed to load territories from PostgreSQL", e);
        }
        registry.replaceAll(territories);
    }

    @Override
    public void save(TerritoryRegistry registry) throws IOException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (Statement clear = c.createStatement()) {
                    clear.execute("DELETE FROM territories");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO territories (id, doc) VALUES (?, ?::jsonb)")) {
                    for (Territory t : registry.list()) {
                        ps.setString(1, t.id());
                        ps.setString(2, json.toJson(t).toString());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IOException("Failed to save territories to PostgreSQL", e);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
