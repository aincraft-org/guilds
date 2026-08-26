package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.config.TechTreeConfigLoader;
import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.TechTreeNode;
import dev.mintychochip.guilds.services.impl.GuildProjectServiceImpl;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

/** Unit tests for guild project service impl. */
class GuildProjectServiceImplTest {

    /**
     * Performs the start project starts affordable root node and deducts points operation.
     * @throws Exception if an error occurs
     */
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
        assertEquals(2, new dev.mintychochip.guilds.services.impl.GuildLevelServiceImpl(
                mock(JavaPlugin.class), mock(DatabaseManager.class),
                mock(GuildService.class), mock(dev.mintychochip.guilds.config.GuildLevelConfigLoader.class))
                .calculateTotalTechPoints(guild));
        verify(harness.update).executeUpdate();
    }

    /**
     * Performs the start project refuses unmet requirements and leaves state unchanged operation.
     * @throws Exception if an error occurs
     */
    @Test
    void startProject_refusesUnmetRequirementsAndLeavesStateUnchanged() throws Exception {
        ProjectHarness harness = harness(2, null, List.of());
        Guild guild = guildWithPoints(2);

        GuildProjectService.ProjectStartResult result = harness.service.startProject(guild, "fast_travel");

        assertFalse(result.isSuccessful());
        assertEquals(GuildProjectService.StartStatus.UNMET_REQUIREMENTS, result.getStatus());
        assertNull(guild.getActiveProjectId());
        assertEquals(2, guild.getTechPoints());
        verify(harness.update, never()).executeUpdate();
    }

    /**
     * Performs the start project refuses when cost exceeds unspent points operation.
     * @throws Exception if an error occurs
     */
    @Test
    void startProject_refusesWhenCostExceedsUnspentPoints() throws Exception {
        ProjectHarness harness = harness(0, null, List.of());
        Guild guild = guildWithPoints(0);

        GuildProjectService.ProjectStartResult result = harness.service.startProject(guild, "better_storage");

        assertFalse(result.isSuccessful());
        assertEquals(GuildProjectService.StartStatus.INSUFFICIENT_POINTS, result.getStatus());
        assertNull(guild.getActiveProjectId());
        assertEquals(0, guild.getTechPoints());
        verify(harness.update, never()).executeUpdate();
    }

    /**
     * Performs the start project refuses second start while one is active operation.
     * @throws Exception if an error occurs
     */
    @Test
    void startProject_refusesSecondStartWhileOneIsActive() throws Exception {
        ProjectHarness harness = harness(2, "better_storage", List.of());
        Guild guild = guildWithPoints(2);
        guild.setActiveProjectId("better_storage");

        GuildProjectService.ProjectStartResult result = harness.service.startProject(guild, "guard_posts");

        assertFalse(result.isSuccessful());
        assertEquals(GuildProjectService.StartStatus.ALREADY_ACTIVE, result.getStatus());
        assertEquals("better_storage", result.getActiveProjectId());
        assertEquals("better_storage", guild.getActiveProjectId());
        assertEquals(2, guild.getTechPoints());
        verify(harness.update, never()).executeUpdate();
    }

    /** Performs the complete active project unlocks root so child can start without preseeded unlocks operation. */
    @Test
    void completeActiveProject_unlocksRootSoChildCanStartWithoutPreseededUnlocks() {
        StatefulProjectStore store = new StatefulProjectStore(2, null);
        GuildProjectService service = store.service();
        Guild guild = guildWithPoints(2);

        GuildProjectService.ProjectStartResult startRoot = service.startProject(guild, "better_storage");
        assertTrue(startRoot.isSuccessful(), String.valueOf(startRoot.getStatus()));
        assertEquals("better_storage", guild.getActiveProjectId());
        assertTrue(store.unlocked.isEmpty(), "start must not unlock the node");

        assertTrue(service.completeActiveProject(guild));
        assertNull(guild.getActiveProjectId());
        assertTrue(store.unlocked.contains("better_storage"));
        assertTrue(guild.isTechNodeUnlocked("better_storage"));

        GuildProjectService.ProjectStartResult startChild = service.startProject(guild, "fast_travel");
        assertTrue(startChild.isSuccessful(), String.valueOf(startChild.getStatus()));
        assertEquals("fast_travel", startChild.getActiveProjectId());
        assertEquals("fast_travel", guild.getActiveProjectId());
    }

    /**
     * Performs the clear active project allows another start operation.
     * @throws Exception if an error occurs
     */
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

    /**
     * Performs the guild with points operation.
     * @param unspent the unspent
     * @return the result
     */
    private static Guild guildWithPoints(int unspent) {
        Guild guild = new Guild("Project Guild", UUID.randomUUID());
        guild.setGuildLevel(2);
        guild.setTechPoints(unspent);
        return guild;
    }

    /** Immutable data carrier for project harness. */
    private record ProjectHarness(GuildProjectService service, PreparedStatement update) {
    }

    /**
     * Performs the harness operation.
     * @param unspent the unspent
     * @param activeProject the active project
     * @param unlocked the unlocked
     * @return the result
     * @throws Exception if an error occurs
     */
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

    /**
     * Performs the root node operation.
     * @return the result
     */
    private static TechTreeNode rootNode() {
        TechTreeNode node = new TechTreeNode("better_storage");
        node.setName("Better Storage");
        node.setCost(1);
        node.setPrerequisites(List.of());
        return node;
    }

    /**
     * Performs the child node operation.
     * @return the result
     */
    private static TechTreeNode childNode() {
        TechTreeNode node = new TechTreeNode("fast_travel");
        node.setName("Fast Travel");
        node.setCost(1);
        node.setPrerequisites(List.of("better_storage"));
        return node;
    }

    /**
     * In-memory guilds + unlock table that the shipped service SQL actually mutates.
     */
    private static final class StatefulProjectStore {
        /** The unspent. */
        private int unspent;
        /** The active project. */
        private String activeProject;
        /** The unlocked. */
        private final Set<String> unlocked = new java.util.LinkedHashSet<>();
        /** The database. */
        private final DatabaseManager database = mock(DatabaseManager.class);
        /** The connection. */
        private final Connection connection = mock(Connection.class);

        /**
         * Creates a new stateful project store instance.
         * @param unspent the unspent
         * @param activeProject the active project
         */
        private StatefulProjectStore(int unspent, String activeProject) {
            this.unspent = unspent;
            this.activeProject = activeProject;
            try {
                when(connection.prepareStatement(anyString())).thenAnswer(invocation ->
                        statementFor(invocation.getArgument(0)));
                when(database.executeTransactionWithResult(any())).thenAnswer(invocation -> {
                    DatabaseManager.TransactionWithResultCallback<?> callback = invocation.getArgument(0);
                    return Optional.ofNullable(callback.execute(connection));
                });
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        /**
         * Performs the service operation.
         * @return the result
         */
        private GuildProjectService service() {
            JavaPlugin plugin = mock(JavaPlugin.class);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("guild-project-test"));
            TechTreeConfigLoader loader = mock(TechTreeConfigLoader.class);
            when(loader.getNodes()).thenReturn(List.of(rootNode(), childNode(), otherRoot()));
            return new GuildProjectServiceImpl(plugin, database, loader);
        }

        /**
         * Performs the statement for operation.
         * @param sql the sql
         * @return the result
         */
        private PreparedStatement statementFor(String sql) {
            Object[] bound = new Object[8];
            List<String> snapshot = List.copyOf(unlocked);
            final int[] cursor = {-1};
            return mock(PreparedStatement.class, invocation -> {
                String method = invocation.getMethod().getName();
                if ("setString".equals(method) || "setInt".equals(method) || "setObject".equals(method)) {
                    bound[invocation.getArgument(0)] = invocation.getArgument(1);
                    return null;
                }
                if ("executeQuery".equals(method)) {
                    if (sql.contains("FROM guilds")) {
                        return guildRow();
                    }
                    if (sql.contains("guild_unlocked_nodes")) {
                        return unlockRows(snapshot, cursor);
                    }
                    return mock(ResultSet.class, inv -> "next".equals(inv.getMethod().getName()) ? false : null);
                }
                if ("executeUpdate".equals(method)) {
                    if (sql.contains("INSERT") && sql.contains("guild_unlocked_nodes")) {
                        Object nodeId = bound[2];
                        if (nodeId != null) {
                            unlocked.add(nodeId.toString());
                        }
                        return 1;
                    }
                    if (sql.contains("tech_points") && sql.contains("active_project_id")) {
                        if (bound[1] instanceof Integer points) {
                            unspent = points;
                        }
                        activeProject = bound[2] == null ? null : bound[2].toString();
                        return 1;
                    }
                    if (sql.contains("active_project_id = NULL") || sql.contains("active_project_id = null")) {
                        activeProject = null;
                        return 1;
                    }
                    return 1;
                }
                if ("close".equals(method) || "setQueryTimeout".equals(method)) {
                    return null;
                }
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            });
        }

        /**
         * Performs the guild row operation.
         * @return the result
         */
        private ResultSet guildRow() {
            return mock(ResultSet.class, invocation -> {
                String method = invocation.getMethod().getName();
                if ("next".equals(method)) {
                    return true;
                }
                if ("getInt".equals(method) && "tech_points".equals(invocation.getArgument(0))) {
                    return unspent;
                }
                if ("getString".equals(method) && "active_project_id".equals(invocation.getArgument(0))) {
                    return activeProject;
                }
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            });
        }

        /**
         * Performs the unlock rows operation.
         * @param snapshot the snapshot
         * @param cursor the cursor
         * @return the result
         */
        private static ResultSet unlockRows(List<String> snapshot, int[] cursor) {
            return mock(ResultSet.class, invocation -> {
                String method = invocation.getMethod().getName();
                if ("next".equals(method)) {
                    cursor[0]++;
                    return cursor[0] < snapshot.size();
                }
                if ("getString".equals(method) && "node_id".equals(invocation.getArgument(0))) {
                    return snapshot.get(cursor[0]);
                }
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            });
        }
    }

    /**
     * Performs the other root operation.
     * @return the result
     */
    private static TechTreeNode otherRoot() {
        TechTreeNode node = new TechTreeNode("guard_posts");
        node.setName("Guard Posts");
        node.setCost(1);
        node.setPrerequisites(List.of());
        return node;
    }
}
