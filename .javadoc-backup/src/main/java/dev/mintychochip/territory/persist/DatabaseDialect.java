package dev.mintychochip.territory.persist;

public interface DatabaseDialect {
    DatabaseType type();
    String driverClassName();
    boolean acceptsJdbcUrl(String jdbcUrl);
    String[] schemaStatements();
    String jsonValueExpression();
    String documentUpsertSql(String table, String keyColumn);
    String singletonUpsertSql(String table, String idColumn);
}
