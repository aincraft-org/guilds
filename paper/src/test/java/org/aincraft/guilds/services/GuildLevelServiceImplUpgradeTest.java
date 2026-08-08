package org.aincraft.guilds.services;

import org.aincraft.guilds.GuildsServiceTestFixture;
import org.aincraft.guilds.config.GuildLevelConfigLoader;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.impl.GuildLevelServiceImpl;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuildLevelServiceImplUpgradeTest {

    @Test
    void eligibility_normalizesConfiguredResourceKeysAgainstPersistedProgress() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("guild-level-test"));
        DatabaseManager database = mock(DatabaseManager.class);
        Connection connection = mock(Connection.class);
        Statement levelsStatement = mock(Statement.class);
        ResultSet levels = mock(ResultSet.class);
        PreparedStatement progressStatement = mock(PreparedStatement.class);
        ResultSet progress = mock(ResultSet.class);
        when(database.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(levelsStatement);
        when(levelsStatement.executeQuery("SELECT * FROM guild_levels ORDER BY level")).thenReturn(levels);
        when(levels.next()).thenReturn(true, false);
        when(levels.getInt("level")).thenReturn(2);
        when(levels.getString("resource_costs_json")).thenReturn("{\"GOLD_INGOT\":5}");
        when(levels.getInt("tech_points_reward")).thenReturn(0);
        when(levels.getInt("claim_limit_bonus")).thenReturn(0);
        when(levels.getInt("assistant_slots_bonus")).thenReturn(0);
        when(levels.getDouble("daily_income_bonus")).thenReturn(0.0);
        when(levels.getString("unlocked_plot_types")).thenReturn("[]");
        when(connection.prepareStatement("SELECT upgrade_progress FROM guilds WHERE id = ?"))
                .thenReturn(progressStatement);
        when(progressStatement.executeQuery()).thenReturn(progress);
        when(progress.next()).thenReturn(true);
        when(progress.getString(1)).thenReturn("{\"gold\":5}");

        GuildLevelConfigLoader loader = mock(GuildLevelConfigLoader.class);
        when(loader.getMaxLevel()).thenReturn(2);
        Guild guild = new Guild("Normalization Guild", UUID.randomUUID());
        GuildLevelServiceImpl service = new GuildLevelServiceImpl(
                plugin, database, mock(GuildService.class), loader);

        GuildLevelService.UpgradeEligibility eligibility =
                service.checkUpgradeEligibility(guild);

        assertTrue(eligibility.isEligible());
        assertTrue(eligibility.getResourceStatus().get("gold"));
    }

    @Test
    void upgrade_persistsLevelConsumesProgressAndRecordsBenefits(@TempDir Path tempDir) throws Exception {
        GuildsServiceTestFixture.Services services = GuildsServiceTestFixture.create(tempDir);
        try {
            clearTestRows(services.databaseManager());
            UUID mayor = UUID.randomUUID();
            services.residentService().createResident(mayor, "upgrade-mayor");
            Guild guild = services.guildService().createGuild("Upgrade Guild", mayor);
            seedLevelAndProgress(services.databaseManager(), guild.getId(), 5);
            guild.setUpgradeProgress(Map.of("diamond", 5));

            GuildLevelServiceImpl service = newService(services.databaseManager(), services.guildService());
            GuildLevelService.UpgradeResult result = service.performGuildUpgrade(guild);

            assertTrue(result.isSuccessful(), result.getMessage());
            assertEquals(2, databaseInt(services.databaseManager(),
                    "SELECT guild_level FROM guilds WHERE id = ?", guild.getId()));
            assertEquals(1, databaseInt(services.databaseManager(),
                    "SELECT tech_points FROM guilds WHERE id = ?", guild.getId()));
            assertEquals("{}", databaseString(services.databaseManager(),
                    "SELECT upgrade_progress FROM guilds WHERE id = ?", guild.getId()));
            assertEquals(1, benefitCount(services.databaseManager(), guild.getId(), 2));
        } finally {
            services.databaseManager().shutdown();
        }
    }

    @Test
    void repeatedUpgradeAttempt_rechecksCommittedLevelAndCannotConsumeRequirementsTwice(
            @TempDir Path tempDir) throws Exception {
        GuildsServiceTestFixture.Services services = GuildsServiceTestFixture.create(tempDir);
        try {
            clearTestRows(services.databaseManager());
            UUID mayor = UUID.randomUUID();
            services.residentService().createResident(mayor, "upgrade-mayor-2");
            Guild guild = services.guildService().createGuild("Upgrade Guild 2", mayor);
            seedLevelAndProgress(services.databaseManager(), guild.getId(), 5);
            guild.setUpgradeProgress(Map.of("diamond", 5));
            GuildLevelServiceImpl service = newService(services.databaseManager(), services.guildService());

            assertTrue(service.performGuildUpgrade(guild).isSuccessful());
            Guild staleGuild = services.guildService().getGuildById(guild.getId()).orElseThrow();
            GuildLevelService.UpgradeResult second = service.performGuildUpgrade(staleGuild);

            assertFalse(second.isSuccessful());
            assertEquals(1, benefitCount(services.databaseManager(), guild.getId(), 2));
        } finally {
            services.databaseManager().shutdown();
        }
    }

    private static GuildLevelServiceImpl newService(DatabaseManager database, GuildService guilds) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("guild-level-test"));
        GuildLevelConfigLoader loader = mock(GuildLevelConfigLoader.class);
        when(loader.getMaxLevel()).thenReturn(2);
        return new GuildLevelServiceImpl(plugin, database, guilds, loader);
    }

    private static void seedLevelAndProgress(DatabaseManager database, String guildId, int amount)
            throws Exception {
        try (Connection connection = database.getDataSource().getConnection();
             PreparedStatement level = connection.prepareStatement("""
                     INSERT INTO guild_levels (
                         level, resource_costs_json, tech_points_reward, claim_limit_bonus,
                         assistant_slots_bonus, daily_income_bonus, unlocked_plot_types, created_at)
                     VALUES (2, '{"DIAMOND":5}', 1, 0, 0, 0.0, '[]', ?)
                     ON CONFLICT (level) DO UPDATE SET resource_costs_json = EXCLUDED.resource_costs_json,
                         tech_points_reward = EXCLUDED.tech_points_reward
                     """);
             PreparedStatement progress = connection.prepareStatement(
                     "UPDATE guilds SET guild_level = 1, tech_points = 0, upgrade_progress = ? WHERE id = ?")) {
            level.setString(1, java.time.LocalDateTime.now().toString());
            level.executeUpdate();
            progress.setString(1, "{\"diamond\":" + amount + "}");
            progress.setString(2, guildId);
            progress.executeUpdate();
        }
    }

    private static int databaseInt(DatabaseManager database, String sql, String guildId) throws Exception {
        try (Connection connection = database.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static String databaseString(DatabaseManager database, String sql, String guildId) throws Exception {
        try (Connection connection = database.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private static int benefitCount(DatabaseManager database, String guildId, int level) throws Exception {
        try (Connection connection = database.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM guild_level_benefits WHERE guild_id = ? AND level = ?")) {
            statement.setString(1, guildId);
            statement.setInt(2, level);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static void clearTestRows(DatabaseManager database) throws Exception {
        try (Connection connection = database.getDataSource().getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE guild_level_benefits, guild_levels, guilds, residents CASCADE");
        }
    }
}
