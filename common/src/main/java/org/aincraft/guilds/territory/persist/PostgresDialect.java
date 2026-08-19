package org.aincraft.guilds.territory.persist;

public final class PostgresDialect implements DatabaseDialect {
    private static final String[] SCHEMA = {
            "CREATE TABLE IF NOT EXISTS territories (id TEXT PRIMARY KEY, doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS influence_state (id INTEGER PRIMARY KEY CHECK (id = 1), doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS standing_state (id INTEGER PRIMARY KEY CHECK (id = 1), doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS reconciliation_entries (idempotency_key TEXT PRIMARY KEY, doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS facilities (id TEXT PRIMARY KEY, doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS expenses (idempotency_key TEXT PRIMARY KEY, doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS upkeep_state (id INTEGER PRIMARY KEY CHECK (id = 1), doc JSONB NOT NULL)",
            "CREATE TABLE IF NOT EXISTS invasion_state (id INTEGER PRIMARY KEY CHECK (id = 1), doc JSONB NOT NULL)"
    };
    @Override public DatabaseType type() { return DatabaseType.POSTGRESQL; }
    @Override public String driverClassName() { return "org.postgresql.Driver"; }
    @Override public boolean acceptsJdbcUrl(String url) { return url != null && url.startsWith("jdbc:postgresql:"); }
    @Override public String[] schemaStatements() { return SCHEMA.clone(); }
    @Override public String jsonValueExpression() { return "?::jsonb"; }
    @Override public String documentUpsertSql(String table, String keyColumn) {
        return "INSERT INTO " + table + " (" + keyColumn + ", doc) VALUES (?, ?::jsonb) "
                + "ON CONFLICT (" + keyColumn + ") DO UPDATE SET doc = EXCLUDED.doc";
    }
    @Override public String singletonUpsertSql(String table, String idColumn) {
        return "INSERT INTO " + table + " (" + idColumn + ", doc) VALUES (1, ?::jsonb) "
                + "ON CONFLICT (" + idColumn + ") DO UPDATE SET doc = EXCLUDED.doc";
    }
}
