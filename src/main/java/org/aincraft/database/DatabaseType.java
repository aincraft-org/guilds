package org.aincraft.database;

/**
 * Supported database types.
 */
public enum DatabaseType {
    SQLITE("org.sqlite.JDBC", "jdbc:sqlite:"),
    MYSQL("com.mysql.cj.jdbc.Driver", "jdbc:mysql://"),
    MARIADB("org.mariadb.jdbc.Driver", "jdbc:mariadb://"),
    POSTGRESQL("org.postgresql.Driver", "jdbc:postgresql://"),
    H2("org.h2.Driver", "jdbc:h2:");

    private final String driverClass;
    private final String jdbcPrefix;

    DatabaseType(String driverClass, String jdbcPrefix) {
        this.driverClass = driverClass;
        this.jdbcPrefix = jdbcPrefix;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public String getJdbcPrefix() {
        return jdbcPrefix;
    }

    public boolean isFileBased() {
        return this == SQLITE || this == H2;
    }

    public boolean supportsPooling() {
        return this != SQLITE;
    }

    public static DatabaseType fromString(String type) {
        return switch (type.toLowerCase()) {
            case "sqlite" -> SQLITE;
            case "mysql" -> MYSQL;
            case "mariadb" -> MARIADB;
            case "postgresql", "postgres" -> POSTGRESQL;
            case "h2" -> H2;
            default -> throw new IllegalArgumentException("Unknown database type: " + type);
        };
    }
}
