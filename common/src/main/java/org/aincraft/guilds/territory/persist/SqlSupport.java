package org.aincraft.guilds.territory.persist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared PostgreSQL / MySQL SQL helpers for guilds schema and service statements.
 */
public final class SqlSupport {
    private static final Set<String> MYSQL_KEEP_TEXT = Set.of(
            "content", "description", "tax_rates", "prerequisites", "effects",
            "resource_costs_json", "upgrade_progress", "permissions_enum",
            "explicit_denials", "unlocked_plot_types", "metadata", "permissions",
            "benefit_value", "schematic_data");
    private static final Pattern MYSQL_TEXT_COLUMN = Pattern.compile("(?i)\\b([a-z0-9_]+)\\s+TEXT\\b");

    private SqlSupport() {
    }

    public static boolean mysql(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        if (product == null) {
            return false;
        }
        String lower = product.toLowerCase(Locale.ROOT);
        return lower.contains("mysql") || lower.contains("mariadb");
    }

    public static String stringType(boolean mysql) {
        return mysql ? "VARCHAR(255)" : "TEXT";
    }

    public static String withIdType(Connection connection, String sql) throws SQLException {
        return withIdType(mysql(connection), sql);
    }

    public static String withIdType(boolean mysql, String sql) {
        if (!mysql) {
            return sql;
        }
        String rewritten = sql
                .replace("BYTEA", "LONGBLOB")
                .replace("mayor_uuid TEXT NOT NULL", "mayor_uuid TEXT")
                .replaceAll("TEXT NOT NULL DEFAULT '[^']*'", "TEXT NOT NULL")
                .replaceAll("TEXT DEFAULT '[^']*'", "TEXT")
                .replaceAll("(?i)TEXT PRIMARY KEY", "VARCHAR(255) PRIMARY KEY")
                .replaceAll("(?i)TEXT UNIQUE", "VARCHAR(255) UNIQUE");
        Matcher matcher = MYSQL_TEXT_COLUMN.matcher(rewritten);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String column = matcher.group(1).toLowerCase(Locale.ROOT);
            if (MYSQL_KEEP_TEXT.contains(column)) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + " VARCHAR(255)"));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String stringAggDistinct(boolean mysql, String expression, String separator) {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(separator, "separator");
        if (mysql) {
            return "GROUP_CONCAT(DISTINCT " + expression + " SEPARATOR '" + separator + "')";
        }
        return "STRING_AGG(DISTINCT " + expression + ", '" + separator + "')";
    }

    public static String upsertSql(boolean mysql, String insert, String conflictColumns, String updates) {
        Objects.requireNonNull(insert, "insert");
        if (mysql) {
            if (updates == null || updates.isBlank()) {
                return insert.replaceFirst("(?i)INSERT\\s+INTO",
                        SqlStatements.load("support/insert-ignore.sql"));
            }
            String mysqlUpdates = updates.replaceAll("(?i)EXCLUDED\\.([A-Za-z0-9_]+)", "VALUES($1)");
            return insert + " ON DUPLICATE KEY UPDATE " + mysqlUpdates;
        }
        if (updates == null || updates.isBlank()) {
            return insert + " ON CONFLICT (" + conflictColumns + ") DO NOTHING";
        }
        return insert + " ON CONFLICT (" + conflictColumns + ") DO UPDATE SET " + updates;
    }

    public static String upsertSql(Connection connection, String insert, String conflictColumns, String updates)
            throws SQLException {
        return upsertSql(mysql(connection), insert, conflictColumns, updates);
    }

    public static boolean tableExists(Connection connection, String table) throws SQLException {
        String sql = mysql(connection)
                ? SqlStatements.load("support/table-exists-mysql.sql")
                : SqlStatements.load("support/table-exists-postgres.sql");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        String sql = mysql(connection)
                ? SqlStatements.load("support/column-exists-mysql.sql")
                : SqlStatements.load("support/column-exists-postgres.sql");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public static boolean indexExists(Connection connection, String index) throws SQLException {
        String sql = mysql(connection)
                ? SqlStatements.load("support/index-exists-mysql.sql")
                : SqlStatements.load("support/index-exists-postgres.sql");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, index);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public static void addColumnIfAbsent(Connection connection, String table, String column, String definition)
            throws SQLException {
        if (columnExists(connection, table, column)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(SqlStatements.load("support/add-column.sql",
                    Map.of("table", table, "column", column, "definition", definition)));
        }
    }

    public static void createIndexIfAbsent(Connection connection, String index, String table, String columns)
            throws SQLException {
        if (indexExists(connection, index)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(SqlStatements.load("support/create-index.sql",
                    Map.of("index", index, "table", table, "columns", columns)));
        }
    }

    public static void dropIndexIfPresent(Connection connection, String index, String table) throws SQLException {
        if (!indexExists(connection, index)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (mysql(connection)) {
                statement.execute(SqlStatements.load("support/drop-index-mysql.sql",
                        Map.of("index", index, "table", table)));
            } else {
                statement.execute(SqlStatements.load("support/drop-index-postgres.sql",
                        Map.of("index", index)));
            }
        }
    }

    public static void setColumnDefault(Connection connection, String table, String column, String defaultValue)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(SqlStatements.load("support/set-column-default.sql",
                    Map.of("table", table, "column", column, "defaultValue", defaultValue)));
        }
    }

    public static void widenIntegerToBigint(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (mysql(connection)) {
                statement.execute(SqlStatements.load("support/widen-bigint-mysql.sql",
                        Map.of("table", table, "column", column)));
            } else {
                statement.execute(SqlStatements.load("support/widen-bigint-postgres.sql",
                        Map.of("table", table, "column", column)));
            }
        }
    }

    public static boolean isBigint(Connection connection, String table, String column) throws SQLException {
        String sql = mysql(connection)
                ? SqlStatements.load("support/is-bigint-mysql.sql")
                : SqlStatements.load("support/is-bigint-postgres.sql");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
