package dev.mintychochip.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.config.TechTreeConfigLoader;
import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.TechTreeBranch;
import dev.mintychochip.guilds.models.TechTreeNode;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildTechData;
import dev.mintychochip.guilds.services.TechTreeService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.sql.NamedSql;
import dev.mintychochip.sql.SqlParams;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Implementation of TechTreeService.
 * Reads node definitions from TechTreeConfigLoader and persists unlock state in the database.
 */

public class TechTreeServiceImpl implements TechTreeService {
    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The config loader. */
    private final TechTreeConfigLoader configLoader;
    /** The guild service. */
    private final GuildService guildService;

    /** In-memory node definitions loaded from config. */
    private final Map<String, TechTreeNode> nodeDefinitions = new LinkedHashMap<>();
    /** The definitions loaded. */
    private boolean definitionsLoaded = false;


    /**
     * Creates a new tech tree service impl instance.
     * @param plugin the plugin
     * @param databaseManager the database manager
     * @param configLoader the config loader
     * @param guildService the guild service
     */
    public TechTreeServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager,
                               TechTreeConfigLoader configLoader, GuildService guildService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configLoader = configLoader;
        this.guildService = guildService;
    }

    // ── Definition loading ─────────────────────────────────────────────

    /** Performs the ensure definitions loaded operation. */
    private void ensureDefinitionsLoaded() {
        if (definitionsLoaded) return;
        nodeDefinitions.clear();
        for (TechTreeNode node : configLoader.getNodes()) {
            nodeDefinitions.put(node.getId(), node);
        }
        definitionsLoaded = true;
        plugin.getLogger().info("Loaded " + nodeDefinitions.size() + " tech tree node definitions.");
    }

    /** Performs the sync config to database operation. */
    @Override
    public void syncConfigToDatabase() {
        ensureDefinitionsLoaded();

        var parsed = SQL.sql("techtree/upsert-node.sql");
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(parsed.jdbcSql(Map.of()))) {

            for (TechTreeNode node : nodeDefinitions.values()) {
                parsed.bind(ps, SqlParams.of(
                        "id", node.getId(),
                        "name", node.getName(),
                        "branch", node.getBranch() != null ? node.getBranch().name() : null,
                        "cost", node.getCost(),
                        "prerequisites", serializeList(node.getPrerequisites()),
                        "effects", serializeEffects(node.getEffects()),
                        "position_x", node.getPositionX(),
                        "position_y", node.getPositionY()));
                ps.addBatch();
            }

            ps.executeBatch();
            plugin.getLogger().info("Synced " + nodeDefinitions.size() + " tech tree nodes to database.");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sync tech tree config to database: " + e.getMessage(), e);
        }
    }

    /** Performs the reload config operation. */
    @Override
    public void reloadConfig() {
        configLoader.loadConfiguration();
        definitionsLoaded = false;
        syncConfigToDatabase();
    }

    // ── Node queries ───────────────────────────────────────────────────

    /**
     * Returns the all nodes.
     * @return the result
     */
    @Override
    public List<TechTreeNode> getAllNodes() {
        ensureDefinitionsLoaded();
        return new ArrayList<>(nodeDefinitions.values());
    }

    /**
     * Returns the nodes by branch.
     * @param branch the branch
     * @return the result
     */
    @Override
    public List<TechTreeNode> getNodesByBranch(TechTreeBranch branch) {
        ensureDefinitionsLoaded();
        List<TechTreeNode> result = new ArrayList<>();
        for (TechTreeNode node : nodeDefinitions.values()) {
            if (node.getBranch() == branch) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Returns the node.
     * @param nodeId the node id
     * @return the result
     */
    @Override
    public Optional<TechTreeNode> getNode(String nodeId) {
        ensureDefinitionsLoaded();
        return Optional.ofNullable(nodeDefinitions.get(nodeId));
    }

    // ── Unlock logic ───────────────────────────────────────────────────

    /**
     * Returns whether tech node unlocked.
     * @param guild the guild
     * @param nodeId the node id
     * @return the result
     */
    @Override
    public boolean isTechNodeUnlocked(Guild guild, String nodeId) {
        return guild.isTechNodeUnlocked(nodeId);
    }

    /**
     * Returns whether unlock node.
     * @param guild the guild
     * @param nodeId the node id
     * @return the result
     */
    @Override
    public boolean canUnlockNode(Guild guild, String nodeId) {
        if (guild.isTechNodeUnlocked(nodeId)) return false;

        ensureDefinitionsLoaded();
        TechTreeNode node = nodeDefinitions.get(nodeId);
        if (node == null) return false;

        // Check tech points
        if (guild.getTechPoints() < node.getCost()) return false;

        // Check prerequisites
        if (node.getPrerequisites() != null) {
            for (String prereq : node.getPrerequisites()) {
                if (!guild.isTechNodeUnlocked(prereq)) return false;
            }
        }

        return true;
    }

    /**
     * Returns the available nodes.
     * @param guild the guild
     * @return the result
     */
    @Override
    public List<TechTreeNode> getAvailableNodes(Guild guild) {
        ensureDefinitionsLoaded();
        List<TechTreeNode> available = new ArrayList<>();
        for (TechTreeNode node : nodeDefinitions.values()) {
            if (canUnlockNode(guild, node.getId())) {
                available.add(node);
            }
        }
        return available;
    }

    /**
     * Performs the unlock tech node operation.
     * @param guild the guild
     * @param nodeId the node id
     * @return the result
     */
    @Override
    public boolean unlockTechNode(Guild guild, String nodeId) {
        if (!canUnlockNode(guild, nodeId)) return false;

        ensureDefinitionsLoaded();
        TechTreeNode node = nodeDefinitions.get(nodeId);
        if (node == null) return false;

        // Deduct tech points
        guild.setTechPoints(guild.getTechPoints() - node.getCost());

        // Mark unlocked
        guild.unlockTechNode(nodeId);

        // Apply effects
        applyEffects(guild, node);

        // Persist
        saveGuildTechData(guild);
        guildService.updateGuild(guild);

        plugin.getLogger().info("Guild " + guild.getName() + " unlocked tech node: " + node.getName());
        return true;
    }

    // ── Persistence ────────────────────────────────────────────────────

    /**
     * Loads the guild tech data.
     * @param guild the guild
     */
    @Override
    public void loadGuildTechData(Guild guild) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = SQL.prepare(conn, "techtree/select-unlocked.sql", Map.of(
                     "guild_id", guild.getId()))) {

            try (ResultSet rs = ps.executeQuery()) {
                GuildTechData techData = new GuildTechData();
                while (rs.next()) {
                    String nodeId = rs.getString("node_id");
                    String timestampStr = rs.getString("unlocked_at");
                    LocalDateTime timestamp = null;
                    if (timestampStr != null) {
                        try { timestamp = LocalDateTime.parse(timestampStr); } catch (Exception ignored) {}
                    }
                    if (timestamp != null) {
                        techData.unlockNode(nodeId, timestamp);
                    } else {
                        techData.unlockNode(nodeId);
                    }
                }
                guild.setTechData(techData);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load tech data for guild " + guild.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Saves the guild tech data.
     * @param guild the guild
     */
    @Override
    public void saveGuildTechData(Guild guild) {
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement del = SQL.prepare(conn, "techtree/delete-unlocked.sql", Map.of(
                    "guild_id", guild.getId()))) {
                del.executeUpdate();
            }

            var parsed = SQL.sql("techtree/upsert-unlocked.sql");
            try (PreparedStatement ins = conn.prepareStatement(parsed.jdbcSql(Map.of()))) {
                GuildTechData techData = guild.getTechData();
                for (String nodeId : techData.getUnlockedNodeIds()) {
                    LocalDateTime ts = techData.getUnlockTimestamp(nodeId);
                    parsed.bind(ins, Map.of(
                            "guild_id", guild.getId(),
                            "node_id", nodeId,
                            "unlocked_at", ts != null ? ts.toString() : LocalDateTime.now().toString()));
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            conn.commit();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save tech data for guild " + guild.getName() + ": " + e.getMessage(), e);
        }
    }

    // ── Effect application ─────────────────────────────────────────────

    /**
     * Applies the effects.
     * @param guild the guild
     * @param node the node
     */
    private void applyEffects(Guild guild, TechTreeNode node) {
        if (node.getEffects() == null) return;

        for (Map.Entry<String, Object> entry : node.getEffects().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            switch (key) {
                case "extra_claims" -> {
                    // Claim bonus is calculated dynamically from unlocked nodes in GuildLevelData
                    // Store as metadata on the guild for future queries
                    plugin.getLogger().info("  Effect: +" + value + " extra claims for " + guild.getName());
                }
                case "extra_assistants" -> {
                    plugin.getLogger().info("  Effect: +" + value + " extra assistants for " + guild.getName());
                }
                case "income_bonus" -> {
                    plugin.getLogger().info("  Effect: " + value + " income bonus for " + guild.getName());
                }
                default -> {
                    // Store unrecognized effects for future expansion
                    plugin.getLogger().info("  Effect: " + key + "=" + value + " for " + guild.getName());
                }
            }
        }
    }

    // ── Serialization helpers ──────────────────────────────────────────

    /**
     * Performs the serialize list operation.
     * @param list the list
     * @return the result
     */
    private String serializeList(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Performs the serialize effects operation.
     * @param effects the effects
     * @return the result
     */
    private String serializeEffects(Map<String, Object> effects) {
        if (effects == null || effects.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : effects.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object v = entry.getValue();
            if (v instanceof Number) {
                sb.append(v);
            } else {
                sb.append("\"").append(v).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
