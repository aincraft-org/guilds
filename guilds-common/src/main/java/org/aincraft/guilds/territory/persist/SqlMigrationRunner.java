package org.aincraft.guilds.territory.persist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Applies versioned SQL resources from {@code /sql/migrations/{track}} through Hikari connections.
 */
public final class SqlMigrationRunner {
    private static final Logger LOGGER = Logger.getLogger(SqlMigrationRunner.class.getName());

    public void apply(Connection connection, String track, DatabaseType type, Map<Integer, SqlMigrationHook> hooks)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(type, "type");
        Map<Integer, SqlMigrationHook> after = hooks == null ? Map.of() : hooks;
        ensureTables(connection);
        if ("guilds".equals(track)) {
            bootstrapGuildsFromLegacy(connection);
        }
        int current = currentVersion(connection, track);
        List<SqlMigration> migrations = SqlMigrationCatalog.load(track, type);
        validateApplied(connection, track, migrations);
        LOGGER.info(() -> track + " schema current version: " + current);
        for (SqlMigration migration : migrations) {
            if (migration.version() <= current) {
                continue;
            }
            LOGGER.info(() -> "Applying " + track + " migration " + migration.version() + ": " + migration.description());
            SqlScripts.apply(connection, migration.resource());
            SqlMigrationHook hook = after.get(migration.version());
            if (hook != null) {
                hook.after(connection);
            }
            markApplied(connection, migration);
            LOGGER.info(() -> track + " migration " + migration.version() + " applied");
        }
    }

    static void ensureTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(SqlSupport.withIdType(connection, SqlStatements.load("migrations/create-sql_schema_migrations.sql")));
            statement.execute(SqlStatements.load("migrations/create-schema_migrations.sql"));
        }
    }

    static void bootstrapGuildsFromLegacy(Connection connection) throws SQLException {
        if (currentVersion(connection, "guilds") > 0) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(SqlStatements.load("migrations/bootstrap-guilds-from-legacy.sql"));
        }
    }

    public static int currentVersion(Connection connection, String track) throws SQLException {
        String sql = SqlStatements.load("migrations/select-track-version.sql");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, track);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Verifies checksums for migration rows already applied on the selected track.
     *
     * @throws SQLException when an applied migration no longer matches its resource
     */
    public static void validateApplied(
            Connection connection, String track, List<SqlMigration> migrations) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(track, "track");
        Objects.requireNonNull(migrations, "migrations");

        Map<Integer, String> appliedChecksums = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("migrations/select-applied.sql"))) {
            statement.setString(1, track);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    appliedChecksums.put(resultSet.getInt("version"), resultSet.getString("checksum"));
                }
            }
        }

        Map<Integer, SqlMigration> catalogByVersion = new HashMap<>();
        for (SqlMigration migration : migrations) {
            catalogByVersion.put(migration.version(), migration);
        }
        int highestApplied = 0;
        for (Integer appliedVersion : appliedChecksums.keySet()) {
            if (!catalogByVersion.containsKey(appliedVersion)) {
                throw new SQLException("Applied migration " + track + " V" + appliedVersion
                        + " is not present in the current catalog");
            }
            highestApplied = Math.max(highestApplied, appliedVersion);
        }

        for (SqlMigration migration : migrations) {
            if (migration.version() <= highestApplied && !appliedChecksums.containsKey(migration.version())) {
                throw new SQLException("Migration " + track + " V" + migration.version()
                        + " is missing from the applied history");
            }
            String applied = appliedChecksums.get(migration.version());
            if (applied != null && !applied.equals(migration.checksum())) {
                throw new SQLException("Migration " + track + " V" + migration.version()
                        + " has a different checksum than its resource");
            }
        }
    }
    static void markApplied(Connection connection, SqlMigration migration) throws SQLException {
        String now = LocalDateTime.now(ZoneOffset.UTC).toString();
        String insertTrack = SqlSupport.upsertSql(connection, SqlStatements.load("migrations/insert-sql_schema_migrations.sql"),
                "track, version", """
                description = EXCLUDED.description,
                checksum = EXCLUDED.checksum,
                applied_at = EXCLUDED.applied_at
                """);
        try (PreparedStatement statement = connection.prepareStatement(insertTrack)) {
            statement.setString(1, migration.track());
            statement.setInt(2, migration.version());
            statement.setString(3, migration.description());
            statement.setString(4, migration.checksum());
            statement.setString(5, now);
            statement.executeUpdate();
        }
        if ("guilds".equals(migration.track())) {
            String insertLegacy = SqlSupport.upsertSql(connection, SqlStatements.load("migrations/insert-schema_migrations.sql"),
                    "version", """
                    description = EXCLUDED.description,
                    applied_at = EXCLUDED.applied_at,
                    checksum = EXCLUDED.checksum
                    """);
            try (PreparedStatement statement = connection.prepareStatement(insertLegacy)) {
                statement.setInt(1, migration.version());
                statement.setString(2, migration.description());
                statement.setString(3, now);
                statement.setString(4, migration.checksum());
                statement.executeUpdate();
            }
        }
    }
}
