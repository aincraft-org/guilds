package org.aincraft.towny.database.migration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.TownyPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages database schema initialization and migrations
 */
@Singleton
public class SchemaInitializer {

    private final TownyPlugin plugin;
    private final List<DatabaseMigration> migrations;

    @Inject
    public SchemaInitializer(TownyPlugin plugin) {
        this.plugin = plugin;
        this.migrations = new ArrayList<>();

        // Register migrations here
        registerMigrations();
    }

    /**
     * Register all available migrations
     */
    private void registerMigrations() {
        // Initial schema setup
        migrations.add(new InitialSchemaMigration());

        // Add spawn location support
        migrations.add(new AddTownSpawnMigration());

        // Future migrations will be added here
        // migrations.add(new MigrationV2_AddPlotTypes());
        // migrations.add(new MigrationV3_AddPermissionFlags());
    }

    /**
     * Initialize the database schema and apply any pending migrations
     */
    public void initialize(Connection connection) {
        try {
            // Create migration tracking table if it doesn't exist
            createMigrationTable(connection);

            // Get current database version
            int currentVersion = getCurrentDatabaseVersion(connection);

            plugin.getLogger().info("Current database version: " + currentVersion);

            // Apply any pending migrations
            for (DatabaseMigration migration : migrations) {
                if (migration.getVersion() > currentVersion && !migration.isApplied(connection)) {
                    plugin.getLogger().info("Applying migration " + migration.getVersion() + ": " + migration.getDescription());

                    try {
                        migration.migrate(connection);
                        migration.markAsApplied(connection);
                        plugin.getLogger().info("Migration " + migration.getVersion() + " applied successfully.");
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to apply migration " + migration.getVersion() + ": " + e.getMessage(), e);
                        throw new RuntimeException("Migration failed", e);
                    }
                }
            }

            int newVersion = getCurrentDatabaseVersion(connection);
            plugin.getLogger().info("Database schema initialization completed. Current version: " + newVersion);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database schema: " + e.getMessage(), e);
            throw new RuntimeException("Schema initialization failed", e);
        }
    }

    /**
     * Create the migration tracking table
     */
    private void createMigrationTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INTEGER PRIMARY KEY,
                    description TEXT NOT NULL,
                    applied_at TEXT NOT NULL,
                    checksum TEXT
                )
            """);
        }
    }

    /**
     * Get the current database version (highest applied migration)
     */
    private int getCurrentDatabaseVersion(Connection connection) throws SQLException {
        String sql = "SELECT MAX(version) FROM schema_migrations";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
            return 0;
        }
    }

    /**
     * Check if a specific migration has been applied
     */
    public boolean isMigrationApplied(Connection connection, int version) throws SQLException {
        String sql = "SELECT COUNT(*) FROM schema_migrations WHERE version = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, version);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Get all applied migrations
     */
    public List<MigrationInfo> getAppliedMigrations(Connection connection) throws SQLException {
        List<MigrationInfo> appliedMigrations = new ArrayList<>();
        String sql = "SELECT version, description, applied_at, checksum FROM schema_migrations ORDER BY version";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                MigrationInfo info = new MigrationInfo(
                    resultSet.getInt("version"),
                    resultSet.getString("description"),
                    resultSet.getString("applied_at"),
                    resultSet.getString("checksum")
                );
                appliedMigrations.add(info);
            }
        }

        return appliedMigrations;
    }

    /**
     * Information about an applied migration
     */
    public static class MigrationInfo {
        private final int version;
        private final String description;
        private final String appliedAt;
        private final String checksum;

        public MigrationInfo(int version, String description, String appliedAt, String checksum) {
            this.version = version;
            this.description = description;
            this.appliedAt = appliedAt;
            this.checksum = checksum;
        }

        public int getVersion() { return version; }
        public String getDescription() { return description; }
        public String getAppliedAt() { return appliedAt; }
        public String getChecksum() { return checksum; }
    }

    /**
     * Initial schema migration - creates the basic database structure
     */
    private static class InitialSchemaMigration implements DatabaseMigration {

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public String getDescription() {
            return "Initial database schema creation";
        }

        @Override
        public void migrate(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                // Create residents table
                statement.execute("""
                    CREATE TABLE residents (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        town_name TEXT,
                        last_online INTEGER NOT NULL,
                        is_online BOOLEAN DEFAULT FALSE,
                        joined_at TEXT NOT NULL,
                        permissions_flags INTEGER DEFAULT 0
                    )
                """);

                // Create towns table
                statement.execute("""
                    CREATE TABLE towns (
                        id TEXT PRIMARY KEY,
                        name TEXT UNIQUE NOT NULL,
                        mayor_uuid TEXT NOT NULL,
                        balance REAL DEFAULT 0.0,
                        home_block_x INTEGER,
                        home_block_z INTEGER,
                        home_block_world TEXT,
                        is_open BOOLEAN DEFAULT TRUE,
                        created_at TEXT NOT NULL,
                        permissions_flags INTEGER DEFAULT 0,
                        tax_rates TEXT,
                        FOREIGN KEY (mayor_uuid) REFERENCES residents(uuid) ON DELETE SET NULL
                    )
                """);

                // Create town residents mapping table
                statement.execute("""
                    CREATE TABLE town_residents (
                        town_id TEXT,
                        resident_uuid TEXT,
                        role TEXT DEFAULT 'resident',
                        joined_at TEXT NOT NULL,
                        PRIMARY KEY (town_id, resident_uuid),
                        FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE,
                        FOREIGN KEY (resident_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
                    )
                """);

                // Create town blocks table
                statement.execute("""
                    CREATE TABLE town_blocks (
                        id TEXT PRIMARY KEY,
                        x INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        world TEXT NOT NULL,
                        town_id TEXT,
                        owner_uuid TEXT,
                        plot_type TEXT DEFAULT 'default',
                        price REAL DEFAULT 0.0,
                        permissions_flags INTEGER DEFAULT 0,
                        claimed_at TEXT NOT NULL,
                        custom_name TEXT,
                        FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE SET NULL,
                        FOREIGN KEY (owner_uuid) REFERENCES residents(uuid) ON DELETE SET NULL,
                        UNIQUE(x, z, world)
                    )
                """);

                // Create permissions table with bitwise flags
                statement.execute("""
                    CREATE TABLE permissions (
                        id TEXT PRIMARY KEY,
                        context TEXT NOT NULL,
                        context_id TEXT NOT NULL,
                        target_type TEXT NOT NULL,
                        target_id TEXT,
                        permissions_flags INTEGER NOT NULL,
                        granted_at TEXT NOT NULL,
                        granted_by_uuid TEXT,
                        FOREIGN KEY (granted_by_uuid) REFERENCES residents(uuid) ON DELETE SET NULL
                    )
                """);

                // Create indexes
                statement.execute("CREATE INDEX idx_residents_town ON residents(town_name)");
                statement.execute("CREATE INDEX idx_towns_name ON towns(name)");
                statement.execute("CREATE INDEX idx_town_blocks_location ON town_blocks(x, z, world)");
                statement.execute("CREATE INDEX idx_town_blocks_town ON town_blocks(town_id)");
                statement.execute("CREATE INDEX idx_town_blocks_owner ON town_blocks(owner_uuid)");
                statement.execute("CREATE INDEX idx_permissions_context ON permissions(context, context_id)");
                statement.execute("CREATE INDEX idx_permissions_target ON permissions(target_type, target_id)");
            }
        }

        @Override
        public boolean isApplied(Connection connection) throws SQLException {
            return false; // This is the initial migration, never considered applied
        }

        @Override
        public void markAsApplied(Connection connection) throws SQLException {
            String sql = "INSERT INTO schema_migrations (version, description, applied_at) VALUES (?, ?, ?)";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, getVersion());
                statement.setString(2, getDescription());
                statement.setString(3, java.time.LocalDateTime.now().toString());
                statement.executeUpdate();
            }
        }
    }

    /**
     * Migration to add spawn location columns to towns table
     */
    private static class AddTownSpawnMigration implements DatabaseMigration {

        @Override
        public int getVersion() {
            return 2;
        }

        @Override
        public String getDescription() {
            return "Add spawn location columns to towns table";
        }

        @Override
        public void migrate(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                // Add spawn location columns to towns table
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN spawn_x REAL DEFAULT NULL
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN spawn_y REAL DEFAULT NULL
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN spawn_z REAL DEFAULT NULL
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN spawn_yaw REAL DEFAULT NULL
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN spawn_pitch REAL DEFAULT NULL
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN spawn_world TEXT DEFAULT NULL
                """);
            }
        }

        @Override
        public boolean isApplied(Connection connection) throws SQLException {
            // Check if spawn_x column exists
            try (Statement statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(towns)")) {
                    while (resultSet.next()) {
                        String columnName = resultSet.getString("name");
                        if ("spawn_x".equals(columnName)) {
                            return true;
                        }
                    }
                }
            } catch (SQLException e) {
                // Table doesn't exist or other error, consider not applied
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
                statement.setString(3, java.time.LocalDateTime.now().toString());
                statement.executeUpdate();
            }
        }
    }
}