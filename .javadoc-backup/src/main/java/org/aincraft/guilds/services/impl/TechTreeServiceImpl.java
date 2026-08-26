package org.aincraft.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.config.TechTreeConfigLoader;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildTechData;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.services.GuildService;

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

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final TechTreeConfigLoader configLoader;
    private final GuildService guildService;

    /** In-memory node definitions loaded from config. */
    private final Map<String, TechTreeNode> nodeDefinitions = new LinkedHashMap<>();
    private boolean definitionsLoaded = false;


    public TechTreeServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager,
                               TechTreeConfigLoader configLoader, GuildService guildService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configLoader = configLoader;
        this.guildService = guildService;
    }

    // ── Definition loading ─────────────────────────────────────────────

    private void ensureDefinitionsLoaded() {
        if (definitionsLoaded) return;
        nodeDefinitions.clear();
        for (TechTreeNode node : configLoader.getNodes()) {
            nodeDefinitions.put(node.getId(), node);
        }
        definitionsLoaded = true;
        plugin.getLogger().info("Loaded " + nodeDefinitions.size() + " tech tree node definitions.");
    }

    @Override
    public void syncConfigToDatabase() {
        ensureDefinitionsLoaded();

        String sql = """
            INSERT INTO tech_tree_nodes
                (id, name, branch, cost, prerequisites, effects, position_x, position_y)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                branch = EXCLUDED.branch,
                cost = EXCLUDED.cost,
                prerequisites = EXCLUDED.prerequisites,
                effects = EXCLUDED.effects,
                position_x = EXCLUDED.position_x,
                position_y = EXCLUDED.position_y
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (TechTreeNode node : nodeDefinitions.values()) {
                ps.setString(1, node.getId());
                ps.setString(2, node.getName());
                ps.setString(3, node.getBranch() != null ? node.getBranch().name() : null);
                ps.setInt(4, node.getCost());
                ps.setString(5, serializeList(node.getPrerequisites()));
                ps.setString(6, serializeEffects(node.getEffects()));
                ps.setInt(7, node.getPositionX());
                ps.setInt(8, node.getPositionY());
                ps.addBatch();
            }

            ps.executeBatch();
            plugin.getLogger().info("Synced " + nodeDefinitions.size() + " tech tree nodes to database.");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sync tech tree config to database: " + e.getMessage(), e);
        }
    }

    @Override
    public void reloadConfig() {
        configLoader.loadConfiguration();
        definitionsLoaded = false;
        syncConfigToDatabase();
    }

    // ── Node queries ───────────────────────────────────────────────────

    @Override
    public List<TechTreeNode> getAllNodes() {
        ensureDefinitionsLoaded();
        return new ArrayList<>(nodeDefinitions.values());
    }

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

    @Override
    public Optional<TechTreeNode> getNode(String nodeId) {
        ensureDefinitionsLoaded();
        return Optional.ofNullable(nodeDefinitions.get(nodeId));
    }

    // ── Unlock logic ───────────────────────────────────────────────────

    @Override
    public boolean isTechNodeUnlocked(Guild guild, String nodeId) {
        return guild.isTechNodeUnlocked(nodeId);
    }

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

    @Override
    public void loadGuildTechData(Guild guild) {
        String sql = "SELECT node_id, unlocked_at FROM guild_unlocked_nodes WHERE guild_id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, guild.getId());

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

    @Override
    public void saveGuildTechData(Guild guild) {
        // Delete existing unlocks for this guild and re-insert
        String deleteSql = "DELETE FROM guild_unlocked_nodes WHERE guild_id = ?";
        String insertSql = """
            INSERT INTO guild_unlocked_nodes (guild_id, node_id, unlocked_at)
            VALUES (?, ?, ?)
            ON CONFLICT (guild_id, node_id) DO UPDATE SET unlocked_at = EXCLUDED.unlocked_at
            """;

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                del.setString(1, guild.getId());
                del.executeUpdate();
            }

            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                GuildTechData techData = guild.getTechData();
                for (String nodeId : techData.getUnlockedNodeIds()) {
                    LocalDateTime ts = techData.getUnlockTimestamp(nodeId);
                    ins.setString(1, guild.getId());
                    ins.setString(2, nodeId);
                    ins.setString(3, ts != null ? ts.toString() : LocalDateTime.now().toString());
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
