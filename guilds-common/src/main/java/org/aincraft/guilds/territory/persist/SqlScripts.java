package org.aincraft.guilds.territory.persist;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads and applies versioned SQL resources, including include/index/column directives. */
public final class SqlScripts {
    private static final Pattern INCLUDE = Pattern.compile("^--\\s*\\+include\\s+(\\S+)\\s*$");
    private static final Pattern INDEX = Pattern.compile("^--\\s*\\+index\\s+(\\S+)\\s+(\\S+)\\s+\\((.+)\\)\\s*$");
    private static final Pattern ADD_COLUMN = Pattern.compile("^--\\s*\\+add-column\\s+(\\S+)\\s+(\\S+)\\s+(.+?)\\s*$");
    private static final Pattern ADD_STRING_COLUMN = Pattern.compile(
            "^--\\s*\\+add-string-column(-if-table)?\\s+(\\S+)\\.(\\S+)(?:\\s+(.+))?\\s*$");
    private static final Pattern WIDEN_PAYLOAD_COLUMN = Pattern.compile(
            "^--\\s*\\+widen-payload-column\\s+(\\S+)\\.(\\S+)\\s*$");
    private static final Pattern SET_DEFAULT = Pattern.compile("^--\\s*\\+set-default\\s+(\\S+)\\s+(\\S+)\\s+(.+?)\\s*$");
    private static final Pattern WIDEN_BIGINT = Pattern.compile("^--\\s*\\+widen-bigint\\s+(\\S+)\\s+(\\S+)\\s*$");

    private SqlScripts() {
    }

    public static String checksum(String resource) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(resolve(resource).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String resolve(String resource) {
        return resolve(resource, new LinkedHashSet<>());
    }

    public static void apply(Connection connection, String resource) throws SQLException {
        applyResolved(connection, resolve(resource));
    }

    static void applyResolved(Connection connection, String script) throws SQLException {
        boolean mysql = SqlSupport.mysql(connection);
        StringBuilder statement = new StringBuilder();
        for (String raw : script.lines().toList()) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher index = INDEX.matcher(line);
            if (index.matches()) {
                flush(connection, mysql, statement);
                SqlSupport.createIndexIfAbsent(connection, index.group(1), index.group(2), index.group(3).strip());
                continue;
            }
            Matcher addColumn = ADD_COLUMN.matcher(line);
            if (addColumn.matches()) {
                flush(connection, mysql, statement);
                SqlSupport.addColumnIfAbsent(connection, addColumn.group(1), addColumn.group(2),
                        SqlSupport.withIdType(mysql, addColumn.group(3).strip()));
                continue;
            }
            Matcher addString = ADD_STRING_COLUMN.matcher(line);
            if (addString.matches()) {
                flush(connection, mysql, statement);
                String table = addString.group(2);
                String column = addString.group(3);
                if (addString.group(1) != null && !SqlSupport.tableExists(connection, table)) {
                    continue;
                }
                String suffix = addString.group(4) == null ? "" : " " + addString.group(4).strip();
                SqlSupport.addColumnIfAbsent(
                        connection, table, column, SqlSupport.stringColumnType(mysql, column) + suffix);
                continue;
            }
            Matcher widenPayload = WIDEN_PAYLOAD_COLUMN.matcher(line);
            if (widenPayload.matches()) {
                flush(connection, mysql, statement);
                SqlSupport.widenPayloadColumnIfPresent(
                        connection, widenPayload.group(1), widenPayload.group(2));
                continue;
            }
            Matcher setDefault = SET_DEFAULT.matcher(line);
            if (setDefault.matches()) {
                flush(connection, mysql, statement);
                SqlSupport.setColumnDefault(connection, setDefault.group(1), setDefault.group(2),
                        setDefault.group(3).strip());
                continue;
            }
            Matcher widen = WIDEN_BIGINT.matcher(line);
            if (widen.matches()) {
                flush(connection, mysql, statement);
                SqlSupport.widenIntegerToBigint(connection, widen.group(1), widen.group(2));
                continue;
            }
            if (line.startsWith("--")) {
                continue;
            }
            statement.append(raw).append('\n');
            if (line.endsWith(";")) {
                flush(connection, mysql, statement);
            }
        }
        flush(connection, mysql, statement);
    }

    private static void flush(Connection connection, boolean mysql, StringBuilder statement) throws SQLException {
        String sql = statement.toString().strip();
        statement.setLength(0);
        if (sql.isEmpty()) {
            return;
        }
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).strip();
        }
        if (sql.isEmpty()) {
            return;
        }
        try (Statement execute = connection.createStatement()) {
            execute.execute(SqlSupport.withIdType(mysql, sql));
        }
    }

    private static String resolve(String resource, Set<String> seen) {
        if (!seen.add(resource)) {
            throw new IllegalStateException("Circular SQL include: " + resource);
        }
        String body = SqlStatements.load(resource);
        StringBuilder out = new StringBuilder();
        for (String raw : body.split("\\R", -1)) {
            Matcher include = INCLUDE.matcher(raw.strip());
            if (include.matches()) {
                String included = resolve(include.group(1), seen).strip();
                out.append(included);
                if (!included.endsWith(";")) {
                    out.append(';');
                }
                out.append('\n');
            } else {
                out.append(raw).append('\n');
            }
        }
        return out.toString();
    }

    static String dialectFolder(DatabaseType type) {
        return type == DatabaseType.MYSQL ? "mysql" : "postgres";
    }

    static String trackResource(String track, DatabaseType type, int version, String slug) {
        String file = "V" + version + "__" + slug + ".sql";
        String dialectPath = "migrations/" + track + "/" + dialectFolder(type) + "/" + file;
        try {
            SqlStatements.load(dialectPath);
            return dialectPath;
        } catch (IllegalStateException ignored) {
            return "migrations/" + track + "/" + file;
        }
    }
}
