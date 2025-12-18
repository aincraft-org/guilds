package org.aincraft.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Connection provider using HikariCP connection pooling.
 * Used for MySQL, MariaDB, PostgreSQL, and H2.
 */
public class HikariConnectionProvider implements ConnectionProvider {
    private final DatabaseConfig config;
    private HikariDataSource dataSource;

    public HikariConnectionProvider(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public void initialize() throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl(config.buildJdbcUrl());
        hikariConfig.setDriverClassName(config.getType().getDriverClass());

        if (!config.getType().isFileBased()) {
            hikariConfig.setUsername(config.getUsername());
            hikariConfig.setPassword(config.getPassword());
        }

        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setIdleTimeout(config.getIdleTimeout());
        hikariConfig.setMaxLifetime(config.getMaxLifetime());

        hikariConfig.setPoolName("GuildsPool");

        // Database-specific optimizations
        switch (config.getType()) {
            case MYSQL, MARIADB -> {
                hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
                hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
                hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            }
            case POSTGRESQL -> {
                hikariConfig.addDataSourceProperty("prepareThreshold", "5");
            }
            case H2 -> {
                hikariConfig.addDataSourceProperty("MODE", "MySQL");
            }
            default -> {}
        }

        try {
            this.dataSource = new HikariDataSource(hikariConfig);
        } catch (Exception e) {
            throw new SQLException("Failed to initialize HikariCP connection pool", e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Connection provider not initialized");
        }
        return dataSource.getConnection();
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public DatabaseType getDatabaseType() {
        return config.getType();
    }
}
