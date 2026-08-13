package org.aincraft.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/** Creates persistent guild-bank player enrollment state. */
public class AddGuildBankEnrollmentMigration implements DatabaseMigration {
    private static final int VERSION = 21;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Add guild bank player enrollment tracking";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS guild_bank_enrollments (
                    guild_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    active BOOLEAN NOT NULL DEFAULT TRUE,
                    enrolled_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (guild_id, player_uuid),
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                    FOREIGN KEY (player_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_guild_bank_enrollments_player ON guild_bank_enrollments(player_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_guild_bank_enrollments_active ON guild_bank_enrollments(guild_id, player_uuid, active)");
        }
    }

    @Override
    public boolean isApplied(Connection connection) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = current_schema() AND table_name = 'guild_bank_enrollments'
            """;
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.next() && result.getInt(1) > 0;
        }
    }

    @Override
    public void markAsApplied(Connection connection) throws SQLException {
        String sql = "INSERT INTO schema_migrations (version, description, applied_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, VERSION);
            statement.setString(2, getDescription());
            statement.setString(3, LocalDateTime.now().toString());
            statement.executeUpdate();
        }
    }
}
