package org.aincraft.guilds.services;

import org.aincraft.guilds.GuildsServiceTestFixture;
import org.aincraft.guilds.config.GuildLevelConfigLoader;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildLevel;
import org.aincraft.guilds.services.impl.GuildLevelServiceImpl;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.Optional;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class GuildLevelServiceImplUpgradeTest {

    @Test
    void eligibility_xpOnly_acceptsExperienceProgress() throws Exception {
        GuildLevelServiceImpl service = mockedLevelService(
                "{\"EXPERIENCE\":10}", "{\"experience\":10}");
        Guild guild = new Guild("Xp Guild", UUID.randomUUID());

        GuildLevelService.UpgradeEligibility eligibility = service.checkUpgradeEligibility(guild);

        assertTrue(eligibility.isEligible(), eligibility.getReason());
        assertTrue(eligibility.getResourceStatus().get("experience"));
    }

    @Test
    void eligibility_xpOnly_refusesMaterialProgressWithoutExperience() throws Exception {
        GuildLevelServiceImpl service = mockedLevelService(
                "{\"EXPERIENCE\":10,\"DIAMOND\":5}", "{\"diamond\":5}");
        Guild guild = new Guild("Material Guild", UUID.randomUUID());

        GuildLevelService.UpgradeEligibility eligibility = service.checkUpgradeEligibility(guild);

        assertFalse(eligibility.isEligible());
        assertFalse(eligibility.getResourceStatus().get("experience"));
    }

    @Test
    void eligibility_xpOnly_ignoresUnmetMaterialCostsWhenExperienceIsMet() throws Exception {
        GuildLevelServiceImpl service = mockedLevelService(
                "{\"EXPERIENCE\":10,\"DIAMOND\":50}", "{\"experience\":10}");
        Guild guild = new Guild("Xp Only Guild", UUID.randomUUID());

        GuildLevelService.UpgradeEligibility eligibility = service.checkUpgradeEligibility(guild);

        assertTrue(eligibility.isEligible(), eligibility.getReason());
    }

    @Test
    void performUpgrade_succeedsWithOnlyXpAndGrantsLevelEqualSkillPoints() throws Exception {
        Guild guild = new Guild("Xp Upgrade", UUID.randomUUID());
        guild.setGuildLevel(1);
        guild.setTechPoints(1);
        UpgradeHarness harness = upgradeHarness(
                "{\"EXPERIENCE\":5,\"DIAMOND\":50}", 1, 1, "{\"experience\":5}");
        when(harness.update.executeUpdate()).thenReturn(1);

        GuildLevelService.UpgradeResult result = harness.service.performGuildUpgrade(guild);

        assertTrue(result.isSuccessful(), result.getMessage());
        assertEquals(2, result.getNewLevel());
        assertEquals(2, guild.getGuildLevel());
        assertEquals(2, guild.getTechPoints());
        assertEquals(2, harness.service.calculateTotalTechPoints(guild));
        verify(harness.update).executeUpdate();
    }

    @Test
    void performUpgrade_refusesMaterialProgressAndLeavesPointsUnchanged() throws Exception {
        Guild guild = new Guild("Material Upgrade", UUID.randomUUID());
        guild.setGuildLevel(1);
        guild.setTechPoints(1);
        UpgradeHarness harness = upgradeHarness(
                "{\"EXPERIENCE\":5,\"DIAMOND\":50}", 1, 1, "{\"diamond\":50}");

        GuildLevelService.UpgradeResult result = harness.service.performGuildUpgrade(guild);

        assertFalse(result.isSuccessful());
        assertEquals(1, guild.getGuildLevel());
        assertEquals(1, guild.getTechPoints());
        verify(harness.update, never()).executeUpdate();
    }

    @Test
    void skillPoints_levelTwoHasTwoTotalAndSpendingLeavesOneUnspent() {
        Guild guild = new Guild("Points Guild", UUID.randomUUID());
        guild.setGuildLevel(2);
        guild.setTechPoints(2);
        GuildLevelServiceImpl service = newService(mock(DatabaseManager.class), mock(GuildService.class));

        assertEquals(2, service.calculateTotalTechPoints(guild));
        guild.setTechPoints(guild.getTechPoints() - 1);
        assertEquals(1, guild.getTechPoints());
        assertEquals(2, service.calculateTotalTechPoints(guild));
    }

    @Test
    void levelBenefits_useDistinctKeysForEachUnlockedPlotType() throws Exception {
        GuildLevelServiceImpl service = newService(
                mock(DatabaseManager.class), mock(GuildService.class));
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        GuildLevel level = new GuildLevel(
                2, Map.of(), 0, 0, 0, 0.0, List.of("BANK", "MARKET"));

        Method recordBenefits = GuildLevelServiceImpl.class.getDeclaredMethod(
                "recordLevelBenefits", Connection.class, String.class, GuildLevel.class);
        recordBenefits.setAccessible(true);
        recordBenefits.invoke(service, connection, "guild-id", level);

        ArgumentCaptor<String> benefitTypes = ArgumentCaptor.forClass(String.class);
        verify(statement, times(2)).setString(eq(4), benefitTypes.capture());
        verify(statement, times(2)).executeUpdate();
        assertEquals(List.of("unlocked_plot_type:BANK", "unlocked_plot_type:MARKET"),
                benefitTypes.getAllValues());
    }

    @Test
    void upgrade_persistsLevelConsumesProgressAndRecordsBenefits(@TempDir Path tempDir) throws Exception {
        GuildsServiceTestFixture.Services services = GuildsServiceTestFixture.create(tempDir);
        try {
            clearTestRows(services.databaseManager());
            UUID mayor = UUID.randomUUID();
            services.residentService().createResident(mayor, "upgrade-mayor");
            Guild guild = services.guildService().createGuild("Upgrade Guild", mayor);
            seedLevelAndXpProgress(services.databaseManager(), guild.getId(), 5);
            guild.setUpgradeProgress(Map.of("experience", 5));

            GuildLevelServiceImpl service = newService(services.databaseManager(), services.guildService());
            GuildLevelService.UpgradeResult result = service.performGuildUpgrade(guild);

            assertTrue(result.isSuccessful(), result.getMessage());
            assertEquals(2, databaseInt(services.databaseManager(),
                    "SELECT guild_level FROM guilds WHERE id = ?", guild.getId()));
            assertEquals("{}", databaseString(services.databaseManager(),
                    "SELECT upgrade_progress FROM guilds WHERE id = ?", guild.getId()));
            assertEquals(3, benefitCount(services.databaseManager(), guild.getId(), 2));
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
            seedLevelAndXpProgress(services.databaseManager(), guild.getId(), 5);
            guild.setUpgradeProgress(Map.of("experience", 5));
            GuildLevelServiceImpl service = newService(services.databaseManager(), services.guildService());

            assertTrue(service.performGuildUpgrade(guild).isSuccessful());
            Guild staleGuild = services.guildService().getGuildById(guild.getId()).orElseThrow();
            GuildLevelService.UpgradeResult second = service.performGuildUpgrade(staleGuild);

            assertFalse(second.isSuccessful());
            assertEquals(3, benefitCount(services.databaseManager(), guild.getId(), 2));
        } finally {
            services.databaseManager().shutdown();
        }
    }

    @Test
    void upgrade_refusesWhenOnlyMaterialProgressAndNoXp(@TempDir Path tempDir) throws Exception {
        GuildsServiceTestFixture.Services services = GuildsServiceTestFixture.create(tempDir);
        try {
            clearTestRows(services.databaseManager());
            UUID mayor = UUID.randomUUID();
            services.residentService().createResident(mayor, "material-mayor");
            Guild guild = services.guildService().createGuild("Material Only Guild", mayor);
            seedLevelAndMaterialProgress(services.databaseManager(), guild.getId(), 5);

            GuildLevelServiceImpl service = newService(services.databaseManager(), services.guildService());
            GuildLevelService.UpgradeResult result = service.performGuildUpgrade(guild);

            assertFalse(result.isSuccessful());
            assertEquals(1, databaseInt(services.databaseManager(),
                    "SELECT guild_level FROM guilds WHERE id = ?", guild.getId()));
        } finally {
            services.databaseManager().shutdown();
        }
    }

    private record UpgradeHarness(
            GuildLevelServiceImpl service,
            PreparedStatement update
    ) {
    }

    private static UpgradeHarness upgradeHarness(
            String costsJson, int currentLevel, int currentUnspent, String progressJson)
            throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("guild-level-test"));
        DatabaseManager database = mock(DatabaseManager.class);
        Connection cacheConnection = mock(Connection.class);
        Statement levelsStatement = mock(Statement.class);
        ResultSet levels = mock(ResultSet.class);
        when(database.getConnection()).thenReturn(cacheConnection);
        when(cacheConnection.createStatement()).thenReturn(levelsStatement);
        when(levelsStatement.executeQuery("SELECT * FROM guild_levels ORDER BY level")).thenReturn(levels);
        when(levels.next()).thenReturn(true, false);
        when(levels.getInt("level")).thenReturn(2);
        when(levels.getString("resource_costs_json")).thenReturn(costsJson);
        when(levels.getInt("tech_points_reward")).thenReturn(0);
        when(levels.getInt("claim_limit_bonus")).thenReturn(0);
        when(levels.getInt("assistant_slots_bonus")).thenReturn(0);
        when(levels.getDouble("daily_income_bonus")).thenReturn(0.0);
        when(levels.getString("unlocked_plot_types")).thenReturn("[]");

        Connection tx = mock(Connection.class);
        PreparedStatement select = mock(PreparedStatement.class);
        PreparedStatement update = mock(PreparedStatement.class);
        PreparedStatement benefits = mock(PreparedStatement.class);
        ResultSet row = mock(ResultSet.class);
        when(tx.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FOR UPDATE")) {
                return select;
            }
            if (sql.startsWith("UPDATE guilds")) {
                return update;
            }
            return benefits;
        });
        when(select.executeQuery()).thenReturn(row);
        when(row.next()).thenReturn(true);
        when(row.getInt("guild_level")).thenReturn(currentLevel);
        when(row.getInt("tech_points")).thenReturn(currentUnspent);
        when(row.getString("upgrade_progress")).thenReturn(progressJson);
        when(database.executeTransactionWithResult(any())).thenAnswer(invocation -> {
            DatabaseManager.TransactionWithResultCallback<?> callback = invocation.getArgument(0);
            return Optional.ofNullable(callback.execute(tx));
        });

        GuildLevelConfigLoader loader = mock(GuildLevelConfigLoader.class);
        when(loader.getMaxLevel()).thenReturn(2);
        GuildLevelServiceImpl service = new GuildLevelServiceImpl(
                plugin, database, mock(GuildService.class), loader);
        return new UpgradeHarness(service, update);
    }

    private static GuildLevelServiceImpl mockedLevelService(String costsJson, String progressJson)
            throws Exception {
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
        when(levels.getString("resource_costs_json")).thenReturn(costsJson);
        when(levels.getInt("tech_points_reward")).thenReturn(0);
        when(levels.getInt("claim_limit_bonus")).thenReturn(0);
        when(levels.getInt("assistant_slots_bonus")).thenReturn(0);
        when(levels.getDouble("daily_income_bonus")).thenReturn(0.0);
        when(levels.getString("unlocked_plot_types")).thenReturn("[]");
        when(connection.prepareStatement("SELECT upgrade_progress FROM guilds WHERE id = ?"))
                .thenReturn(progressStatement);
        when(progressStatement.executeQuery()).thenReturn(progress);
        when(progress.next()).thenReturn(true);
        when(progress.getString(1)).thenReturn(progressJson);

        GuildLevelConfigLoader loader = mock(GuildLevelConfigLoader.class);
        when(loader.getMaxLevel()).thenReturn(2);
        return new GuildLevelServiceImpl(plugin, database, mock(GuildService.class), loader);
    }

    private static GuildLevelServiceImpl newService(DatabaseManager database, GuildService guilds) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("guild-level-test"));
        GuildLevelConfigLoader loader = mock(GuildLevelConfigLoader.class);
        when(loader.getMaxLevel()).thenReturn(2);
        return new GuildLevelServiceImpl(plugin, database, guilds, loader);
    }

    private static void seedLevelAndXpProgress(DatabaseManager database, String guildId, int amount)
            throws Exception {
        seedLevelDefinition(database, "{\"EXPERIENCE\":5,\"DIAMOND\":50}");
        try (Connection connection = database.getDataSource().getConnection();
             PreparedStatement progress = connection.prepareStatement(
                     "UPDATE guilds SET guild_level = 1, upgrade_progress = ? WHERE id = ?")) {
            progress.setString(1, "{\"experience\":" + amount + "}");
            progress.setString(2, guildId);
            progress.executeUpdate();
        }
    }

    private static void seedLevelAndMaterialProgress(DatabaseManager database, String guildId, int amount)
            throws Exception {
        seedLevelDefinition(database, "{\"EXPERIENCE\":5,\"DIAMOND\":50}");
        try (Connection connection = database.getDataSource().getConnection();
             PreparedStatement progress = connection.prepareStatement(
                     "UPDATE guilds SET guild_level = 1, upgrade_progress = ? WHERE id = ?")) {
            progress.setString(1, "{\"diamond\":" + amount + "}");
            progress.setString(2, guildId);
            progress.executeUpdate();
        }
    }

    private static void seedLevelDefinition(DatabaseManager database, String costsJson) throws Exception {
        try (Connection connection = database.getDataSource().getConnection();
             PreparedStatement level = connection.prepareStatement("""
                     INSERT INTO guild_levels (
                         level, resource_costs_json, tech_points_reward, claim_limit_bonus,
                         assistant_slots_bonus, daily_income_bonus, unlocked_plot_types, created_at)
                     VALUES (2, ?, 1, 0, 0, 0.0, '["BANK","MARKET"]', ?)
                     ON CONFLICT (level) DO UPDATE SET resource_costs_json = EXCLUDED.resource_costs_json,
                         tech_points_reward = EXCLUDED.tech_points_reward
                     """)) {
            level.setString(1, costsJson);
            level.setString(2, java.time.LocalDateTime.now().toString());
            level.executeUpdate();
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
