package org.aincraft.guilds.territory.persist;

public final class MySqlDialect implements DatabaseDialect {
    private static final String[] SCHEMA = {
            "CREATE TABLE IF NOT EXISTS territories (id VARCHAR(255) PRIMARY KEY, doc JSON NOT NULL)",
            "CREATE TABLE IF NOT EXISTS influence_state (id INT PRIMARY KEY, doc JSON NOT NULL)",
            "CREATE TABLE IF NOT EXISTS standing_state (id INT PRIMARY KEY, doc JSON NOT NULL)",
            "CREATE TABLE IF NOT EXISTS reconciliation_entries (idempotency_key VARCHAR(255) PRIMARY KEY, doc JSON NOT NULL)",
            "CREATE TABLE IF NOT EXISTS facilities (id VARCHAR(255) PRIMARY KEY, doc JSON NOT NULL)",
            "CREATE TABLE IF NOT EXISTS expenses (idempotency_key VARCHAR(255) PRIMARY KEY, doc JSON NOT NULL)",
            "CREATE TABLE IF NOT EXISTS upkeep_state (id INT PRIMARY KEY, doc JSON NOT NULL)",
            "CREATE TABLE IF NOT EXISTS invasion_state (id INT PRIMARY KEY, doc JSON NOT NULL)"
    };
    @Override public DatabaseType type() { return DatabaseType.MYSQL; }
    @Override public String driverClassName() { return "com.mysql.cj.jdbc.Driver"; }
    @Override public boolean acceptsJdbcUrl(String url) { return url != null && url.startsWith("jdbc:mysql:"); }
    @Override public String[] schemaStatements() { return SCHEMA.clone(); }
    @Override public String jsonValueExpression() { return "?"; }
    @Override public String documentUpsertSql(String table, String keyColumn) {
        return "INSERT INTO " + table + " (" + keyColumn + ", doc) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE doc = VALUES(doc)";
    }
    @Override public String singletonUpsertSql(String table, String idColumn) {
        return "INSERT INTO " + table + " (" + idColumn + ", doc) VALUES (1, ?) "
                + "ON DUPLICATE KEY UPDATE doc = VALUES(doc)";
    }
}
