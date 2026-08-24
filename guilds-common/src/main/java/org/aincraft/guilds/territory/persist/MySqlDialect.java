package org.aincraft.guilds.territory.persist;

import java.util.Map;

public final class MySqlDialect implements DatabaseDialect {
    private static final String[] SCHEMA = {
            SqlStatements.load("territory/create-mysql.sql"),
            SqlStatements.load("influence/create-mysql.sql"),
            SqlStatements.load("standing/create-mysql.sql"),
            SqlStatements.load("reconciliation/create-mysql.sql"),
            SqlStatements.load("facility/create-mysql.sql"),
            SqlStatements.load("expense/create-mysql.sql"),
            SqlStatements.load("upkeep/create-mysql.sql"),
            SqlStatements.load("invasion/create-mysql.sql")
    };
    @Override public DatabaseType type() { return DatabaseType.MYSQL; }
    @Override public String driverClassName() { return "com.mysql.cj.jdbc.Driver"; }
    @Override public boolean acceptsJdbcUrl(String url) { return url != null && url.startsWith("jdbc:mysql:"); }
    @Override public String[] schemaStatements() { return SCHEMA.clone(); }
    @Override public String jsonValueExpression() { return "?"; }
    @Override public String documentUpsertSql(String table, String keyColumn) {
        return SqlStatements.load("dialect/mysql/document-upsert.sql",
                Map.of("table", table, "keyColumn", keyColumn));
    }
    @Override public String singletonUpsertSql(String table, String idColumn) {
        return SqlStatements.load("dialect/mysql/singleton-upsert.sql",
                Map.of("table", table, "idColumn", idColumn));
    }
}
