package org.aincraft.guilds.territory.persist;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Compatibility wrapper that opens the shared Hikari PostgreSQL pool. */
public final class PostgresDatabase implements Database {
    private final Database delegate;

    public PostgresDatabase(DatabaseSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        if (!settings.jdbcUrl().startsWith("jdbc:postgresql:")) {
            throw new IOException("PostgreSQL JDBC URL required: " + settings.jdbcUrl());
        }
        this.delegate = DatabaseFactory.open(settings);
    }

    @Override public DataSource dataSource() { return delegate.dataSource(); }
    @Override public Connection connection() throws SQLException { return delegate.connection(); }
    @Override public DatabaseType type() { return DatabaseType.POSTGRESQL; }
    @Override public DatabaseDialect dialect() { return delegate.dialect(); }
    @Override public void initializeSchema() throws IOException { delegate.initializeSchema(); }
    @Override public String poolStatistics() { return delegate.poolStatistics(); }
    @Override public void close() { delegate.close(); }
}
