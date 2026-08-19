package org.aincraft.guilds.territory.persist;

import java.util.Map;

public final class PostgresDialect implements DatabaseDialect {
    private static final String[] SCHEMA = {
            SqlStatements.load("territory/create-postgres.sql"),
            SqlStatements.load("influence/create-postgres.sql"),
            SqlStatements.load("standing/create-postgres.sql"),
            SqlStatements.load("reconciliation/create-postgres.sql"),
            SqlStatements.load("facility/create-postgres.sql"),
            SqlStatements.load("expense/create-postgres.sql"),
            SqlStatements.load("upkeep/create-postgres.sql"),
            SqlStatements.load("invasion/create-postgres.sql")
    };
    @Override public DatabaseType type() { return DatabaseType.POSTGRESQL; }
    @Override public String driverClassName() { return "org.postgresql.Driver"; }
    @Override public boolean acceptsJdbcUrl(String url) { return url != null && url.startsWith("jdbc:postgresql:"); }
    @Override public String[] schemaStatements() { return SCHEMA.clone(); }
    @Override public String jsonValueExpression() { return "?::jsonb"; }
    @Override public String documentUpsertSql(String table, String keyColumn) {
        return SqlStatements.load("dialect/postgres/document-upsert.sql",
                Map.of("table", table, "keyColumn", keyColumn));
    }
    @Override public String singletonUpsertSql(String table, String idColumn) {
        return SqlStatements.load("dialect/postgres/singleton-upsert.sql",
                Map.of("table", table, "idColumn", idColumn));
    }
}
