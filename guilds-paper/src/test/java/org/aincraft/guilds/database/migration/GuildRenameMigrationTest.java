package org.aincraft.guilds.database.migration;

import org.aincraft.guilds.territory.PostgresTestDatabase;
import org.aincraft.guilds.territory.persist.PostgresDatabase;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage of the v16 town→guild schema rename, through the real
 * {@link SchemaInitializer} flow:
 * <ul>
 *   <li>fresh install: migrations v1–15 now create guild* names directly and
 *       v16 no-ops;</li>
 *   <li>legacy install: a pre-v16 schema (town* names, rows, schema_migrations
 *       1–15) is renamed in place with all rows preserved;</li>
 *   <li>legacy installs missing optional subsystem tables still migrate;</li>
 *   <li>ambiguous state (both names present) fails loudly;</li>
 *   <li>both successful paths are idempotent (a second run changes nothing).</li>
 * </ul>
 */
class GuildRenameMigrationTest {

    private PostgresDatabase database;
    private Connection connection;
    private String schemaName;

    @BeforeEach
    void setUp() throws Exception {
        database = PostgresTestDatabase.open();
        schemaName = "guild_rename_" + UUID.randomUUID().toString().replace("-", "");
        connection = database.connection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schemaName);
            statement.execute("SET search_path TO " + schemaName);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
            }
            connection.close();
        }
        if (database != null) {
            database.close();
        }
    }


    private static JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("guilds-rename-test"));
        return plugin;
    }

    @Test
    void freshInstall_createsGuildNames_v16NoOps() throws Exception {
        new SchemaInitializer(plugin()).initialize(connection);

        for (String table : new String[]{
                "guilds", "guild_residents", "guild_blocks", "guild_levels",
                "guild_resources", "guild_level_benefits", "guild_specializations",
                "guild_quests", "guild_unlocked_nodes"}) {
            assertTrue(tableExists(table), "expected table " + table);
        }
        for (String legacy : new String[]{
                "towns", "town_residents", "town_blocks", "town_levels",
                "town_resources", "town_level_benefits", "town_specializations",
                "town_quests", "town_unlocked_nodes"}) {
            assertFalse(tableExists(legacy), "legacy table must not exist: " + legacy);
        }
        assertTrue(indexExists("idx_guilds_name"), "idx_guilds_name missing");
        assertTrue(indexExists("idx_guild_blocks_guild"), "idx_guild_blocks_guild missing");
        assertTrue(indexExists("idx_residents_guild"), "idx_residents_guild missing");

        // New schema is usable with the new column names
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO guilds (id, name, mayor_uuid, created_at) "
                    + "VALUES ('t1', 'Everfall', 'm1', '2026-01-01')");
        }
        assertEquals(1, scalar("SELECT COUNT(*) FROM guilds"));

        // Second run is a no-op and stays consistent
        new SchemaInitializer(plugin()).initialize(connection);
        assertEquals(1, scalar("SELECT COUNT(*) FROM guilds"));
    }

    @Test
    void legacySchema_renamesTablesColumnsAndIndexes_preservingRows() throws Exception {
        createLegacySchema();

        new SchemaInitializer(plugin()).initialize(connection);

        // Tables renamed
        assertTrue(tableExists("guilds"));
        assertFalse(tableExists("towns"));
        assertTrue(tableExists("guild_residents"));
        assertTrue(tableExists("guild_blocks"));
        assertTrue(tableExists("guild_levels"));
        assertTrue(tableExists("guild_resources"));
        assertTrue(tableExists("guild_level_benefits"));
        assertTrue(tableExists("guild_specializations"));
        assertTrue(tableExists("guild_quests"));
        assertTrue(tableExists("guild_unlocked_nodes"));

        // Columns renamed (v16 town->guild, v18 nation->alliance)
        assertTrue(columnExists("residents", "guild_name"));
        assertFalse(columnExists("residents", "town_name"));
        assertTrue(columnExists("guilds", "guild_level"));
        assertTrue(columnExists("guild_blocks", "guild_id"));
        assertTrue(columnExists("alliances", "capital_guild_id"));
        assertTrue(columnExists("alliance_members", "alliance_id"));
        assertFalse(columnExists("alliance_members", "nation_id"));
        assertTrue(columnExists("economy_transactions", "guild_id"));

        // Indexes renamed
        assertTrue(indexExists("idx_guilds_name"));
        assertFalse(indexExists("idx_towns_name"));
        assertTrue(indexExists("idx_guild_blocks_guild"));
        assertFalse(indexExists("idx_town_blocks_town"));
        assertTrue(indexExists("idx_residents_guild"));
        assertTrue(indexExists("idx_guild_quests_guild_id"));
        assertTrue(indexExists("idx_guild_unlocked_guild"));
        assertTrue(indexExists("idx_broadcast_messages_guild"));
        assertTrue(indexExists("idx_alliances_capital"));
        assertFalse(indexExists("idx_nations_capital"));
        assertTrue(indexExists("idx_alliance_members_guild"));

        // Rows preserved
        assertEquals("Everfall", scalar("SELECT name FROM guilds WHERE id = 't1'"));
        assertEquals("guild-level-4", scalar("SELECT guild_level FROM guilds WHERE id = 't1'"));
        assertEquals("guild-one", scalar("SELECT guild_name FROM residents WHERE uuid = 'r1'"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM guild_blocks"));
        assertEquals("t1", scalar("SELECT capital_guild_id FROM alliances WHERE id = 'n1'"));
        assertEquals("t1", scalar("SELECT guild_id FROM alliance_members WHERE alliance_id = 'n1'"));
        assertEquals("t1", scalar("SELECT guild_id FROM economy_transactions WHERE id = 'e1'"));

        // New column names are writable by the current code paths
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO guild_residents (guild_id, resident_uuid, role, joined_at) "
                    + "VALUES ('t1', 'r2', 'resident', '2026-01-02')");
        }
        assertEquals(2, scalar("SELECT COUNT(*) FROM guild_residents"));

        // Idempotent: a second initialize() leaves everything intact
        new SchemaInitializer(plugin()).initialize(connection);
        assertEquals(1, scalar("SELECT COUNT(*) FROM guilds"));
        assertFalse(tableExists("towns"));
    }

    @Test
    void legacySchema_withMissingSubsystemTables_stillRenames() throws Exception {
        // A minimal legacy server may lack optional subsystems (blueprints,
        // broadcasts, economy, …) — the rename must skip those objects, not fail.
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE schema_migrations (
                    version INTEGER PRIMARY KEY, description TEXT NOT NULL,
                    applied_at TEXT NOT NULL, checksum TEXT
                )
                """);
            for (int v = 1; v <= 15; v++) {
                statement.execute("INSERT INTO schema_migrations (version, description, applied_at) "
                        + "VALUES (" + v + ", 'legacy v" + v + "', '2026-01-01')");
            }
            statement.execute("""
                CREATE TABLE towns (
                    id TEXT PRIMARY KEY, name TEXT UNIQUE NOT NULL, mayor_uuid TEXT NOT NULL,
                    created_at TEXT NOT NULL, governance_form TEXT NOT NULL DEFAULT 'MONARCHY'
                )
                """);
            statement.execute("""
                CREATE TABLE residents (
                    uuid TEXT PRIMARY KEY, name TEXT NOT NULL, town_name TEXT,
                    last_online INTEGER NOT NULL, joined_at TEXT NOT NULL
                )
                """);
            statement.execute("CREATE INDEX idx_towns_name ON towns(name)");
            statement.execute("CREATE INDEX idx_residents_town ON residents(town_name)");
        }

        new SchemaInitializer(plugin()).initialize(connection);

        assertTrue(tableExists("guilds"));
        assertFalse(tableExists("towns"));
        assertTrue(indexExists("idx_guilds_name"));
        assertTrue(indexExists("idx_residents_guild"));
    }

    @Test
    void bothNamesPresent_failsLoudlyWithoutPartialRename() throws Exception {
        createLegacySchema();
        // Simulate a half-applied state: the new name already exists next to the legacy one
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE guilds (id TEXT PRIMARY KEY, name TEXT)");
        }

        AddGuildRenameMigration migration = new AddGuildRenameMigration();
        assertThrows(SQLException.class, () -> migration.migrate(connection));

        // Nothing was partially renamed: legacy tables and rows are untouched
        assertTrue(tableExists("towns"));
        assertEquals("Everfall", scalar("SELECT name FROM towns WHERE id = 't1'"));
        assertFalse(tableExists("guild_residents"));
        assertTrue(indexExists("idx_towns_name"));
    }

    // ---- helpers ----

    private void createLegacySchema() throws Exception {
        try (Statement statement = connection.createStatement()) {
            // v1..v15 marked applied so only v16 runs (as on real legacy servers)
            statement.execute("""
                CREATE TABLE schema_migrations (
                    version INTEGER PRIMARY KEY,
                    description TEXT NOT NULL,
                    applied_at TEXT NOT NULL,
                    checksum TEXT
                )
                """);
            for (int v = 1; v <= 15; v++) {
                statement.execute("INSERT INTO schema_migrations (version, description, applied_at) "
                        + "VALUES (" + v + ", 'legacy v" + v + "', '2026-01-01')");
            }

            statement.execute("""
                CREATE TABLE towns (
                    id TEXT PRIMARY KEY, name TEXT UNIQUE NOT NULL, mayor_uuid TEXT NOT NULL,
                    balance REAL DEFAULT 0.0, is_open BOOLEAN DEFAULT TRUE, created_at TEXT NOT NULL,
                    town_level INTEGER DEFAULT 1, governance_form TEXT NOT NULL DEFAULT 'MONARCHY',
                    pvp_enabled BOOLEAN DEFAULT FALSE, public_enabled BOOLEAN DEFAULT FALSE,
                    spawn_x REAL, home_block_y INTEGER
                )
                """);
            statement.execute("""
                CREATE TABLE residents (
                    uuid TEXT PRIMARY KEY, name TEXT NOT NULL, town_name TEXT,
                    last_online INTEGER NOT NULL, is_online BOOLEAN DEFAULT FALSE, joined_at TEXT NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE town_residents (
                    town_id TEXT, resident_uuid TEXT, role TEXT DEFAULT 'resident', joined_at TEXT NOT NULL,
                    PRIMARY KEY (town_id, resident_uuid)
                )
                """);
            statement.execute("""
                CREATE TABLE town_blocks (
                    id TEXT PRIMARY KEY, x INTEGER NOT NULL, z INTEGER NOT NULL, world TEXT NOT NULL,
                    town_id TEXT, owner_uuid TEXT, plot_type TEXT DEFAULT 'default',
                    claimed_at TEXT NOT NULL, permissions_flags INTEGER DEFAULT 0,
                    plot_type_definition TEXT
                )
                """);
            statement.execute("""
                CREATE TABLE town_levels (
                    level INTEGER PRIMARY KEY, resource_costs_json TEXT NOT NULL DEFAULT '{}',
                    tech_points_reward INTEGER NOT NULL DEFAULT 0
                )
                """);
            statement.execute("""
                CREATE TABLE town_resources (
                    id TEXT PRIMARY KEY, town_id TEXT NOT NULL, resource_type TEXT NOT NULL,
                    amount INTEGER NOT NULL DEFAULT 0, last_updated TEXT NOT NULL,
                    UNIQUE (town_id, resource_type)
                )
                """);
            statement.execute("""
                CREATE TABLE resource_contributions (
                    id TEXT PRIMARY KEY, town_id TEXT NOT NULL, contributor_uuid TEXT NOT NULL,
                    amount INTEGER NOT NULL DEFAULT 0, contribution_time TEXT NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE town_level_benefits (
                    id TEXT PRIMARY KEY, town_id TEXT NOT NULL, level INTEGER NOT NULL,
                    benefit_type TEXT NOT NULL, unlocked_at TEXT NOT NULL,
                    UNIQUE (town_id, level, benefit_type)
                )
                """);
            statement.execute("""
                CREATE TABLE town_specializations (
                    town_id TEXT PRIMARY KEY, specialization TEXT NOT NULL, set_at TEXT NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE town_quests (
                    id TEXT PRIMARY KEY, town_id TEXT NOT NULL, quest_type TEXT NOT NULL,
                    target_amount INTEGER NOT NULL DEFAULT 1, description TEXT NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE town_unlocked_nodes (
                    town_id TEXT NOT NULL, node_id TEXT NOT NULL, unlocked_at TEXT NOT NULL,
                    PRIMARY KEY (town_id, node_id)
                )
                """);
            statement.execute("""
                CREATE TABLE nations (
                    id TEXT PRIMARY KEY, name TEXT NOT NULL, king_uuid TEXT NOT NULL,
                    capital_town_id TEXT NOT NULL, tax_rate REAL DEFAULT 0.0, created_at TEXT NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE nation_members (
                    nation_id TEXT NOT NULL, town_id TEXT NOT NULL, PRIMARY KEY (nation_id, town_id)
                )
                """);
            statement.execute("""
                CREATE TABLE blueprints (
                    id TEXT PRIMARY KEY, name TEXT NOT NULL, town_id TEXT NOT NULL, created_at TEXT NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE broadcast_messages (
                    id TEXT PRIMARY KEY, town_id TEXT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE economy_transactions (
                    id TEXT PRIMARY KEY, town_id TEXT, player_uuid TEXT, type TEXT NOT NULL, amount REAL NOT NULL
                )
                """);

            // Legacy index names survive table renames and must be recreated.
            statement.execute("CREATE INDEX idx_residents_town ON residents(town_name)");
            statement.execute("CREATE INDEX idx_towns_name ON towns(name)");
            statement.execute("CREATE INDEX idx_town_blocks_location ON town_blocks(x, z, world)");
            statement.execute("CREATE INDEX idx_town_blocks_town ON town_blocks(town_id)");
            statement.execute("CREATE INDEX idx_town_blocks_owner ON town_blocks(owner_uuid)");
            statement.execute("CREATE INDEX idx_town_blocks_plot_type_def ON town_blocks(plot_type_definition)");
            statement.execute("CREATE INDEX idx_town_quests_town_id ON town_quests(town_id)");
            statement.execute("CREATE INDEX idx_town_unlocked_town ON town_unlocked_nodes(town_id)");
            statement.execute("CREATE INDEX idx_town_unlocked_node ON town_unlocked_nodes(node_id)");
            statement.execute("CREATE INDEX idx_broadcast_messages_town ON broadcast_messages(town_id)");
            statement.execute("CREATE INDEX idx_economy_tx_town ON economy_transactions(town_id)");
            statement.execute("CREATE INDEX idx_nation_members_town ON nation_members(town_id)");
            statement.execute("CREATE INDEX idx_blueprints_town ON blueprints(town_id)");
            statement.execute("CREATE INDEX idx_nations_capital ON nations(capital_town_id)");

            // Seed rows
            statement.execute("INSERT INTO towns (id, name, mayor_uuid, created_at, town_level) "
                    + "VALUES ('t1', 'Everfall', 'm1', '2026-01-01', 'guild-level-4')");
            statement.execute("INSERT INTO residents (uuid, name, town_name, last_online, joined_at) "
                    + "VALUES ('r1', 'Robin', 'guild-one', 0, '2026-01-01')");
            statement.execute("INSERT INTO town_residents (town_id, resident_uuid, role, joined_at) "
                    + "VALUES ('t1', 'r1', 'mayor', '2026-01-01')");
            statement.execute("INSERT INTO town_blocks (id, x, z, world, town_id, claimed_at) "
                    + "VALUES ('b1', 1, 1, 'world', 't1', '2026-01-01')");
            statement.execute("INSERT INTO town_blocks (id, x, z, world, town_id, claimed_at) "
                    + "VALUES ('b2', 2, 2, 'world', 't1', '2026-01-01')");
            statement.execute("INSERT INTO nations (id, name, king_uuid, capital_town_id, created_at) "
                    + "VALUES ('n1', 'Pact', 'k1', 't1', '2026-01-01')");
            statement.execute("INSERT INTO nation_members (nation_id, town_id) VALUES ('n1', 't1')");
            statement.execute("INSERT INTO economy_transactions (id, town_id, type, amount) "
                    + "VALUES ('e1', 't1', 'tax', 10.0)");
        }
    }

    private boolean tableExists(String name) throws SQLException {
        return query("SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = current_schema() AND table_name = ?", name);
    }

    private boolean indexExists(String name) throws SQLException {
        return query("SELECT 1 FROM pg_indexes "
                + "WHERE schemaname = current_schema() AND indexname = ?", name);
    }

    private boolean columnExists(String table, String column) throws SQLException {
        return query("SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                table, column);
    }

    private boolean query(String sql, String... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }


    private Object scalar(String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next(), "no row for " + sql);
            return resultSet.getObject(1);
        }
    }
}
