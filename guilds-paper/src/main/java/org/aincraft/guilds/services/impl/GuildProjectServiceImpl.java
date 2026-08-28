package org.aincraft.guilds.services.impl;

import org.aincraft.guilds.config.TechTreeConfigLoader;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.territory.persist.SqlStatements;
import org.aincraft.guilds.territory.persist.SqlSupport;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.projects.GuildProjectRules;
import org.aincraft.guilds.services.GuildProjectService;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

public class GuildProjectServiceImpl implements GuildProjectService {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final TechTreeConfigLoader configLoader;

    public GuildProjectServiceImpl(
            JavaPlugin plugin,
            DatabaseManager databaseManager,
            TechTreeConfigLoader configLoader
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configLoader = configLoader;
    }

    @Override
    public ProjectStartResult startProject(Guild guild, String nodeId) {
        if (guild == null) {
            return new ProjectStartResult(false, GuildProjectRules.StartStatus.UNKNOWN_NODE, null, 0);
        }
        Optional<TechTreeNode> node = getProject(nodeId);
        if (node.isEmpty()) {
            return new ProjectStartResult(
                    false, GuildProjectRules.StartStatus.UNKNOWN_NODE,
                    guild.getActiveProjectId(), guild.getTechPoints());
        }
        try {
            Optional<ProjectStartResult> mutation = databaseManager.executeTransactionWithResult(
                    connection -> startInTransaction(connection, guild.getId(), node.get()));
            if (mutation == null || mutation.isEmpty()) {
                return new ProjectStartResult(
                        false, GuildProjectRules.StartStatus.UNKNOWN_NODE,
                        guild.getActiveProjectId(), guild.getTechPoints());
            }
            ProjectStartResult result = mutation.get();
            if (result.isSuccessful()) {
                guild.setActiveProjectId(result.getActiveProjectId());
                guild.setTechPoints(result.getUnspentPoints());
            }
            return result;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start guild project " + nodeId, e);
            return new ProjectStartResult(
                    false, GuildProjectRules.StartStatus.UNKNOWN_NODE,
                    guild.getActiveProjectId(), guild.getTechPoints());
        }
    }

    private ProjectStartResult startInTransaction(Connection connection, String guildId, TechTreeNode node)
            throws SQLException {
        int unspent;
        String activeProjectId;
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("projects/select-guild-for-start.sql"))) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return new ProjectStartResult(false, GuildProjectRules.StartStatus.UNKNOWN_NODE, null, 0);
                }
                unspent = result.getInt("tech_points");
                activeProjectId = result.getString("active_project_id");
            }
        }
        Set<String> unlocked = loadUnlockedNodes(connection, guildId);
        GuildProjectRules.StartStatus status = GuildProjectRules.evaluateStart(
                node, activeProjectId, unlocked, unspent);
        if (status != GuildProjectRules.StartStatus.STARTED) {
            return new ProjectStartResult(false, status, activeProjectId, unspent);
        }
        int remaining = GuildProjectRules.unspentAfterStart(unspent, node.getCost());
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("projects/update-start.sql"))) {
            statement.setInt(1, remaining);
            statement.setString(2, node.getId());
            statement.setString(3, guildId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Guild project start updated no row");
            }
        }
        return new ProjectStartResult(true, status, node.getId(), remaining);
    }

    @Override
    public boolean completeActiveProject(Guild guild) {
        if (guild == null) {
            return false;
        }
        try {
            Optional<String> completed = databaseManager.executeTransactionWithResult(
                    connection -> completeInTransaction(connection, guild.getId()));
            if (completed != null && completed.isPresent()) {
                guild.unlockTechNode(completed.get());
                guild.setActiveProjectId(null);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to complete active guild project", e);
            return false;
        }
    }

    private String completeInTransaction(Connection connection, String guildId) throws SQLException {
        String activeProjectId;
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("projects/select-active-for-update.sql"))) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                activeProjectId = result.getString("active_project_id");
            }
        }
        if (!GuildProjectRules.canClear(activeProjectId)) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(SqlSupport.upsertSql(connection,
                SqlStatements.load("projects/insert.sql"), "guild_id, node_id", "unlocked_at = EXCLUDED.unlocked_at"))) {
            statement.setString(1, guildId);
            statement.setString(2, activeProjectId);
            statement.setString(3, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).toString());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("projects/clear-active.sql"))) {
            statement.setString(1, guildId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Guild project complete updated no row");
            }
        }
        return activeProjectId;
    }

    @Override
    public boolean clearActiveProject(Guild guild) {
        return clearActiveProject(guild, null, false);
    }

    @Override
    public boolean clearActiveProject(Guild guild, String expectedProjectId) {
        return clearActiveProject(guild, expectedProjectId, true);
    }

    private boolean clearActiveProject(Guild guild, String expectedProjectId, boolean checkExpectedProjectId) {
        if (guild == null) {
            return false;
        }
        try {
            Optional<Boolean> cleared = databaseManager.executeTransactionWithResult(
                    connection -> clearInTransaction(
                            connection, guild.getId(), expectedProjectId, checkExpectedProjectId));
            if (cleared != null && cleared.orElse(false)) {
                guild.setActiveProjectId(null);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to clear active guild project", e);
            return false;
        }
    }

    private Boolean clearInTransaction(
            Connection connection, String guildId, String expectedProjectId, boolean checkExpectedProjectId)
            throws SQLException {
        String activeProjectId;
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("projects/select-active-for-update.sql"))) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return false;
                }
                activeProjectId = result.getString("active_project_id");
            }
        }
        if (checkExpectedProjectId && !Objects.equals(activeProjectId, expectedProjectId)) {
            return false;
        }
        if (!GuildProjectRules.canClear(activeProjectId)) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("projects/clear-active.sql"))) {
            statement.setString(1, guildId);
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public Optional<String> getActiveProjectId(Guild guild) {
        if (guild == null) {
            return Optional.empty();
        }
        if (GuildProjectRules.hasActiveProject(guild.getActiveProjectId())) {
            return Optional.of(guild.getActiveProjectId());
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SqlStatements.load("projects/select-active.sql"))) {
            statement.setString(1, guild.getId());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    String active = result.getString("active_project_id");
                    if (GuildProjectRules.hasActiveProject(active)) {
                        guild.setActiveProjectId(active);
                        return Optional.of(active);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load active guild project", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<TechTreeNode> getProject(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return Optional.empty();
        }
        for (TechTreeNode node : configLoader.getNodes()) {
            if (nodeId.equals(node.getId())) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private static Set<String> loadUnlockedNodes(Connection connection, String guildId) throws SQLException {
        Set<String> unlocked = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("projects/select-unlocked.sql"))) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String nodeId = result.getString("node_id");
                    if (nodeId != null && !nodeId.isBlank()) {
                        unlocked.add(nodeId);
                    }
                }
            }
        }
        return unlocked;
    }
}
