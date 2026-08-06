package org.aincraft.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Renames the legacy {@code nation*} schema objects to {@code alliance*} naming.
 * <p>
 * Version 18 — after RetireBlueprintMigration (v17).
 * <p>
 * Alliances are the alliance entities; the "nation" vocabulary was retired
 * (commands, permissions, services, and schema). The historical chain (v11
 * creates {@code nations}, v15 adds its governance form, v16 renames its
 * town-era columns) is left untouched for replayability; this migration
 * renames the tables, columns, and indexes in place, preserving all rows.
 * Permission rows written with {@code target_type = 'nation'} (the plot-role
 * vocabulary) are rewritten to {@code 'alliance'}.
 * <p>
 * Idempotency and safety mirror AddGuildRenameMigration: each rename is
 * guarded by existence checks, both-old-and-new-present fails loudly, and the
 * whole sequence runs in a transaction.
 */
public class AddAllianceRenameMigration implements DatabaseMigration {

    private static final int VERSION = 18;
    private static final String DESCRIPTION = "Rename nation schema objects to alliance naming";

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        boolean wasAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            renameTable(connection, "nations", "alliances");
            renameTable(connection, "nation_members", "alliance_members");
            renameTable(connection, "nation_ministers", "alliance_ministers");
            renameTable(connection, "nation_relations", "alliance_relations");

            renameColumn(connection, "alliance_members", "nation_id", "alliance_id");
            renameColumn(connection, "alliance_ministers", "nation_id", "alliance_id");
            renameColumn(connection, "alliance_relations", "nation_id", "alliance_id");
            renameColumn(connection, "alliance_relations", "other_nation", "other_alliance");

            // SQLite keeps index names when a table is renamed, so the old
            // names must be dropped explicitly and recreated under new names.
            recreateIndex(connection, "idx_nation_members_nation", "idx_alliance_members_alliance",
                    "alliance_members", "alliance_id");
            recreateIndex(connection, "idx_nation_members_guild", "idx_alliance_members_guild",
                    "alliance_members", "guild_id");
            recreateIndex(connection, "idx_nation_ministers_nation", "idx_alliance_ministers_alliance",
                    "alliance_ministers", "alliance_id");
            recreateIndex(connection, "idx_nation_relations_nation", "idx_alliance_relations_alliance",
                    "alliance_relations", "alliance_id");
            recreateIndex(connection, "idx_nations_capital", "idx_alliances_capital",
                    "alliances", "capital_guild_id");
            recreateIndex(connection, "idx_nations_king", "idx_alliances_king",
                    "alliances", "king_uuid");

            // Plot-role permission rows used the retired "nation" vocabulary.
            if (tableExists(connection, "permissions")) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(
                            "UPDATE permissions SET target_type = 'alliance' WHERE target_type = 'nation'");
                }
            }

            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(wasAutoCommit);
        }
    }

    private static void renameTable(Connection connection, String oldName, String newName) throws SQLException {
        boolean oldExists = tableExists(connection, oldName);
        boolean newExists = tableExists(connection, newName);
        if (oldExists && newExists) {
            throw new SQLException("Ambiguous schema state: both '" + oldName + "' and '" + newName
                    + "' exist — refusing to rename (data may be split).");
        }
        if (oldExists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + oldName + " RENAME TO " + newName);
            }
        }
    }

    private static void renameColumn(Connection connection, String table, String oldColumn, String newColumn)
            throws SQLException {
        if (!tableExists(connection, table)) {
            return; // older servers may lack this subsystem table entirely
        }
        boolean oldExists = columnExists(connection, table, oldColumn);
        boolean newExists = columnExists(connection, table, newColumn);
        if (oldExists && newExists) {
            throw new SQLException("Ambiguous schema state: columns '" + table + "." + oldColumn + "' and '"
                    + table + "." + newColumn + "' both exist — refusing to rename.");
        }
        if (oldExists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " RENAME COLUMN " + oldColumn + " TO " + newColumn);
            }
        }
    }

    private static void recreateIndex(Connection connection, String oldName, String newName, String table, String columns)
            throws SQLException {
        if (!tableExists(connection, table)) {
            return; // older servers may lack this subsystem table entirely
        }
        if (indexExists(connection, oldName)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP INDEX " + oldName);
            }
        }
        if (!indexExists(connection, newName)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE INDEX " + newName + " ON " + table + " (" + columns + ")");
            }
        }
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean indexExists(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ? AND name NOT LIKE 'sqlite_autoindex%'")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (resultSet.next()) {
                if (column.equals(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isApplied(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM schema_migrations WHERE version = " + VERSION)) {
            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }
        } catch (SQLException e) {
            // Table doesn't exist yet, so migration is not applied
            return false;
        }
        return false;
    }

    @Override
    public void markAsApplied(Connection connection) throws SQLException {
        String sql = "INSERT INTO schema_migrations (version, description, applied_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, getVersion());
            statement.setString(2, getDescription());
            statement.setString(3, LocalDateTime.now().toString());
            statement.executeUpdate();
        }
    }
}
