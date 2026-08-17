package org.aincraft.guilds.services;

import org.aincraft.guilds.config.TechTreeConfigLoader;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.projects.GuildProjectRules;
import org.aincraft.guilds.services.impl.GuildProjectServiceImpl;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuildProjectServiceImplTest {

    @Test
    void startProject_startsAffordableRootNodeAndDeductsPoints() throws Exception {
        ProjectHarness harness = harness(2, null, List.of());
        when(harness.update.executeUpdate()).thenReturn(1);
        Guild guild = guildWithPoints(2);

        GuildProjectService.ProjectStartResult result = harness.service.startProject(guild, "better_storage");

        assertTrue(result.isSuccessful(), String.valueOf(result.getStatus()));
        assertEquals("better_storage", result.getActiveProjectId());
        assertEquals(1, result.getUnspentPoints());
        assertEquals("better_storage", guild.getActiveProjectId());
        assertEquals(1, guild.getTechPoints());
        assertEquals(2, new org.aincraft.guilds.services.impl.GuildLevelServiceImpl(
                mock(JavaPlugin.class), mock(DatabaseManager.class),
                mock(GuildService.class), mock(org.aincraft.guilds.config.GuildLevelConfigLoader.class))
                .calculateTotalTechPoints(guild));
        verify(harness.update).executeUpdate();
    }

    @Test
    void startProject_refusesUnmetRequirementsAndLeavesStateUnchanged() throws Exception {
        ProjectHarness harness = harness(2, null, List.of());
        Guild guild = guildWithPoints(2);

        GuildProjectService.ProjectStartResult result = harness.service.startProject(guild, "fast_travel");

        assertFalse(result.isSuccessful());
        assertEquals(GuildProjectRules.StartStatus.UNMET_REQUIREMENTS, result.getStatus());
        assertNull(guild.getActiveProjectId());
        assertEquals(2, guild.getTechPoints());
        verify(harness.update, never()).executeUpdate();
    }

    @Test
    void startProject_refusesWhenCostExceedsUnspentPoints() throws Exception {
        ProjectHarness harness = harness(0, null, List.of());
        Guild guild = guildWithPoints(0);

        GuildProjectService.ProjectStartResult result = harness.service.startProject(guild, "better_storage");

        assertFalse(result.isSuccessful());
        assertEquals(GuildProjectRules.StartStatus.INSUFFICIENT_POINTS, result.getStatus());
        assertNull(guild.getActiveProjectId());
        assertEquals(0, guild.getTechPoints());
        verify(harness.update, never()).executeUpdate();
    }

    @Test
    void startProject_refusesSecondStartWhileOneIsActive() throws Exception {
        ProjectHarness harness = harness(2, "better_storage", List.of());
        Guild guild = guildWithPoints(2);
        guild.setActiveProjectId("better_storage");

        GuildProjectService.ProjectStartResult result = harness.service.startProject(guild, "guard_posts");

        assertFalse(result.isSuccessful());
        assertEquals(GuildProjectRules.StartStatus.ALREADY_ACTIVE, result.getStatus());
        assertEquals("better_storage", result.getActiveProjectId());
        assertEquals("better_storage", guild.getActiveProjectId());
        assertEquals(2, guild.getTechPoints());
        verify(harness.update, never()).executeUpdate();
    }

    @Test
    void clearActiveProject_allowsAnotherStart() throws Exception {
        ProjectHarness harness = harness(1, "better_storage", List.of("better_storage"));
        when(harness.update.executeUpdate()).thenReturn(1);
        Guild guild = guildWithPoints(1);
        guild.setActiveProjectId("better_storage");

        assertTrue(harness.service.clearActiveProject(guild));
        assertNull(guild.getActiveProjectId());

        harness = harness(1, null, List.of("better_storage"));
        when(harness.update.executeUpdate()).thenReturn(1);
        GuildProjectService.ProjectStartResult result = harness.service.startProject(guild, "fast_travel");

        assertTrue(result.isSuccessful(), String.valueOf(result.getStatus()));
        assertEquals("fast_travel", result.getActiveProjectId());
    }

    private static Guild guildWithPoints(int unspent) {
        Guild guild = new Guild("Project Guild", UUID.randomUUID());
        guild.setGuildLevel(2);
        guild.setTechPoints(unspent);
        return guild;
    }

    private record ProjectHarness(GuildProjectService service, PreparedStatement update) {
    }

    private static ProjectHarness harness(int unspent, String activeProject, List<String> unlocked)
            throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("guild-project-test"));
        DatabaseManager database = mock(DatabaseManager.class);
        Connection tx = mock(Connection.class);
        PreparedStatement selectGuild = mock(PreparedStatement.class);
        PreparedStatement selectUnlocks = mock(PreparedStatement.class);
        PreparedStatement update = mock(PreparedStatement.class);
        ResultSet guildRow = mock(ResultSet.class);
        ResultSet unlockRows = mock(ResultSet.class);
        when(tx.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM guilds")) {
                return selectGuild;
            }
            if (sql.contains("guild_unlocked_nodes")) {
                return selectUnlocks;
            }
            return update;
        });
        when(selectGuild.executeQuery()).thenReturn(guildRow);
        when(guildRow.next()).thenReturn(true);
        when(guildRow.getInt("tech_points")).thenReturn(unspent);
        when(guildRow.getString("active_project_id")).thenReturn(activeProject);
        when(selectUnlocks.executeQuery()).thenReturn(unlockRows);
        if (unlocked.isEmpty()) {
            when(unlockRows.next()).thenReturn(false);
        } else {
            Boolean[] rest = new Boolean[unlocked.size()];
            java.util.Arrays.fill(rest, false);
            rest[rest.length - 1] = false;
            when(unlockRows.next()).thenReturn(true, rest);
            if (unlocked.size() == 1) {
                when(unlockRows.getString("node_id")).thenReturn(unlocked.get(0));
            } else {
                when(unlockRows.getString("node_id")).thenReturn(
                        unlocked.get(0), unlocked.subList(1, unlocked.size()).toArray(String[]::new));
            }
        }
        when(database.executeTransactionWithResult(any())).thenAnswer(invocation -> {
            DatabaseManager.TransactionWithResultCallback<?> callback = invocation.getArgument(0);
            return Optional.ofNullable(callback.execute(tx));
        });

        TechTreeConfigLoader loader = mock(TechTreeConfigLoader.class);
        when(loader.getNodes()).thenReturn(List.of(rootNode(), childNode(), otherRoot()));
        GuildProjectService service = new GuildProjectServiceImpl(plugin, database, loader);
        return new ProjectHarness(service, update);
    }

    private static TechTreeNode rootNode() {
        TechTreeNode node = new TechTreeNode("better_storage");
        node.setName("Better Storage");
        node.setCost(1);
        node.setPrerequisites(List.of());
        return node;
    }

    private static TechTreeNode childNode() {
        TechTreeNode node = new TechTreeNode("fast_travel");
        node.setName("Fast Travel");
        node.setCost(1);
        node.setPrerequisites(List.of("better_storage"));
        return node;
    }

    private static TechTreeNode otherRoot() {
        TechTreeNode node = new TechTreeNode("guard_posts");
        node.setName("Guard Posts");
        node.setCost(1);
        node.setPrerequisites(List.of());
        return node;
    }
}
