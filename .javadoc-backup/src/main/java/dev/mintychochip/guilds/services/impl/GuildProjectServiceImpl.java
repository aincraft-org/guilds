package dev.mintychochip.guilds.services.impl;

import dev.mintychochip.guilds.config.TechTreeConfigLoader;
import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.TechTreeNode;
import dev.mintychochip.guilds.projects.GuildProjectRules;
import dev.mintychochip.guilds.services.GuildProjectService;
import dev.mintychochip.sql.NamedSql;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/** Implementation of guild project service. */
public class GuildProjectServiceImpl implements GuildProjectService {
    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The config loader. */
    private final TechTreeConfigLoader configLoader;

    /**
     * Creates a new guild project service impl instance.
     * @param plugin the plugin
     * @param databaseManager the database manager
     * @param configLoader the config loader
     */
    public GuildProjectServiceImpl(
            JavaPlugin plugin,
            DatabaseManager databaseManager,
            TechTreeConfigLoader configLoader
    ) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configLoader = configLoader;
    }

    /**
     * Performs the start project operation.
     * @param guild the guild
     * @param nodeId the node id
     * @return the result
     */
    @Override
    public ProjectStartResult startProject(Guild guild, String nodeId) {
        if (guild == null) {
            return new ProjectStartResult(false, GuildProjectService.StartStatus.UNKNOWN_NODE, null, 0);
        }
        Optional<TechTreeNode> node = getProject(nodeId);
        if (node.isEmpty()) {
            return new ProjectStartResult(
                    false, GuildProjectService.StartStatus.UNKNOWN_NODE,
                    guild.getActiveProjectId(), guild.getTechPoints());
        }
        try {
            Optional<ProjectStartResult> mutation = databaseManager.executeTransactionWithResult(
                    connection -> startInTransaction(connection, guild.getId(), node.get()));
            if (mutation == null || mutation.isEmpty()) {
                return new ProjectStartResult(
                        false, GuildProjectService.StartStatus.UNKNOWN_NODE,
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
                    false, GuildProjectService.StartStatus.UNKNOWN_NODE,
                    guild.getActiveProjectId(), guild.getTechPoints());
        }
    }

    /**
     * Performs the start in transaction operation.
     * @param connection the connection
     * @param guildId the guild id
     * @param node the node
     * @return the result
     * @throws SQLException if an error occurs
     */
    private ProjectStartResult startInTransaction(Connection connection, String guildId, TechTreeNode node)
            throws SQLException {
        int unspent;
        String activeProjectId;
        try (PreparedStatement statement = SQL.prepare(connection, "projects/select-points-for-update.sql", Map.of(
                "id", guildId))) {
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return new ProjectStartResult(false, GuildProjectService.StartStatus.UNKNOWN_NODE, null, 0);
                }
                unspent = result.getInt("tech_points");
                activeProjectId = result.getString("active_project_id");
            }
        }
        Set<String> unlocked = loadUnlockedNodes(connection, guildId);
        GuildProjectService.StartStatus status = GuildProjectRules.evaluateStart(
                node, activeProjectId, unlocked, unspent);
        if (status != GuildProjectService.StartStatus.STARTED) {
            return new ProjectStartResult(false, status, activeProjectId, unspent);
        }
        int remaining = GuildProjectRules.unspentAfterStart(unspent, node.getCost());
        try (PreparedStatement statement = SQL.prepare(connection, "projects/update-start.sql", Map.of(
                "tech_points", remaining,
                "active_project_id", node.getId(),
                "id", guildId))) {
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Guild project start updated no row");
            }
        }
        return new ProjectStartResult(true, status, node.getId(), remaining);
    }

    /**
     * Performs the complete active project operation.
     * @param guild the guild
     * @return the result
     */
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

    /**
     * Performs the complete in transaction operation.
     * @param connection the connection
     * @param guildId the guild id
     * @return the result
     * @throws SQLException if an error occurs
     */
    private String completeInTransaction(Connection connection, String guildId) throws SQLException {
        String activeProjectId;
        try (PreparedStatement statement = SQL.prepare(connection, "projects/select-active-for-update.sql", Map.of(
                "id", guildId))) {
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
        try (PreparedStatement statement = SQL.prepare(connection, "projects/insert-unlocked-node.sql", Map.of(
                "guild_id", guildId,
                "node_id", activeProjectId,
                "unlocked_at", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).toString()))) {
            statement.executeUpdate();
        }
        try (PreparedStatement statement = SQL.prepare(connection, "projects/clear-active.sql", Map.of(
                "id", guildId))) {
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Guild project complete updated no row");
            }
        }
        return activeProjectId;
    }

    /**
     * Performs the clear active project operation.
     * @param guild the guild
     * @return the result
     */
    @Override
    public boolean clearActiveProject(Guild guild) {
        if (guild == null) {
            return false;
        }
        try {
            Optional<Boolean> cleared = databaseManager.executeTransactionWithResult(
                    connection -> clearInTransaction(connection, guild.getId()));
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

    /**
     * Performs the clear in transaction operation.
     * @param connection the connection
     * @param guildId the guild id
     * @return the result
     * @throws SQLException if an error occurs
     */
    private Boolean clearInTransaction(Connection connection, String guildId) throws SQLException {
        String activeProjectId;
        try (PreparedStatement statement = SQL.prepare(connection, "projects/select-active-for-update.sql", Map.of(
                "id", guildId))) {
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return false;
                }
                activeProjectId = result.getString("active_project_id");
            }
        }
        if (!GuildProjectRules.canClear(activeProjectId)) {
            return false;
        }
        try (PreparedStatement statement = SQL.prepare(connection, "projects/clear-active.sql", Map.of(
                "id", guildId))) {
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * Returns the active project id.
     * @param guild the guild
     * @return the result
     */
    @Override
    public Optional<String> getActiveProjectId(Guild guild) {
        if (guild == null) {
            return Optional.empty();
        }
        if (GuildProjectRules.hasActiveProject(guild.getActiveProjectId())) {
            return Optional.of(guild.getActiveProjectId());
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "projects/select-active.sql", Map.of(
                     "id", guild.getId()))) {
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

    /**
     * Returns the project.
     * @param nodeId the node id
     * @return the result
     */
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

    /**
     * Loads the unlocked nodes.
     * @param connection the connection
     * @param guildId the guild id
     * @return the result
     * @throws SQLException if an error occurs
     */
    private static Set<String> loadUnlockedNodes(Connection connection, String guildId) throws SQLException {
        Set<String> unlocked = new HashSet<>();
        try (PreparedStatement statement = SQL.prepare(connection, "projects/select-unlocked-nodes.sql", Map.of(
                "guild_id", guildId))) {
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
