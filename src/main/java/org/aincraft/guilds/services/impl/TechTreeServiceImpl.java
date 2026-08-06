package org.aincraft.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.config.TechTreeConfigLoader;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.models.TownTechData;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.services.TownService;

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
    private final TownService townService;

    /** In-memory node definitions loaded from config. */
    private final Map<String, TechTreeNode> nodeDefinitions = new LinkedHashMap<>();
    private boolean definitionsLoaded = false;


    public TechTreeServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager,
                               TechTreeConfigLoader configLoader, TownService townService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configLoader = configLoader;
        this.townService = townService;
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
            INSERT OR REPLACE INTO tech_tree_nodes
                (id, name, branch, cost, prerequisites, effects, position_x, position_y)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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
    public boolean isTechNodeUnlocked(Town town, String nodeId) {
        return town.isTechNodeUnlocked(nodeId);
    }

    @Override
    public boolean canUnlockNode(Town town, String nodeId) {
        if (town.isTechNodeUnlocked(nodeId)) return false;

        ensureDefinitionsLoaded();
        TechTreeNode node = nodeDefinitions.get(nodeId);
        if (node == null) return false;

        // Check tech points
        if (town.getTechPoints() < node.getCost()) return false;

        // Check prerequisites
        if (node.getPrerequisites() != null) {
            for (String prereq : node.getPrerequisites()) {
                if (!town.isTechNodeUnlocked(prereq)) return false;
            }
        }

        return true;
    }

    @Override
    public List<TechTreeNode> getAvailableNodes(Town town) {
        ensureDefinitionsLoaded();
        List<TechTreeNode> available = new ArrayList<>();
        for (TechTreeNode node : nodeDefinitions.values()) {
            if (canUnlockNode(town, node.getId())) {
                available.add(node);
            }
        }
        return available;
    }

    @Override
    public boolean unlockTechNode(Town town, String nodeId) {
        if (!canUnlockNode(town, nodeId)) return false;

        ensureDefinitionsLoaded();
        TechTreeNode node = nodeDefinitions.get(nodeId);
        if (node == null) return false;

        // Deduct tech points
        town.setTechPoints(town.getTechPoints() - node.getCost());

        // Mark unlocked
        town.unlockTechNode(nodeId);

        // Apply effects
        applyEffects(town, node);

        // Persist
        saveTownTechData(town);
        townService.updateTown(town);

        plugin.getLogger().info("Town " + town.getName() + " unlocked tech node: " + node.getName());
        return true;
    }

    // ── Persistence ────────────────────────────────────────────────────

    @Override
    public void loadTownTechData(Town town) {
        String sql = "SELECT node_id, unlocked_at FROM town_unlocked_nodes WHERE town_id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, town.getId());

            try (ResultSet rs = ps.executeQuery()) {
                TownTechData techData = new TownTechData();
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
                town.setTechData(techData);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load tech data for town " + town.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void saveTownTechData(Town town) {
        // Delete existing unlocks for this town and re-insert
        String deleteSql = "DELETE FROM town_unlocked_nodes WHERE town_id = ?";
        String insertSql = """
            INSERT OR REPLACE INTO town_unlocked_nodes (town_id, node_id, unlocked_at)
            VALUES (?, ?, ?)
            """;

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                del.setString(1, town.getId());
                del.executeUpdate();
            }

            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                TownTechData techData = town.getTechData();
                for (String nodeId : techData.getUnlockedNodeIds()) {
                    LocalDateTime ts = techData.getUnlockTimestamp(nodeId);
                    ins.setString(1, town.getId());
                    ins.setString(2, nodeId);
                    ins.setString(3, ts != null ? ts.toString() : LocalDateTime.now().toString());
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            conn.commit();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save tech data for town " + town.getName() + ": " + e.getMessage(), e);
        }
    }

    // ── Effect application ─────────────────────────────────────────────

    private void applyEffects(Town town, TechTreeNode node) {
        if (node.getEffects() == null) return;

        for (Map.Entry<String, Object> entry : node.getEffects().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            switch (key) {
                case "extra_claims" -> {
                    // Claim bonus is calculated dynamically from unlocked nodes in TownLevelData
                    // Store as metadata on the town for future queries
                    plugin.getLogger().info("  Effect: +" + value + " extra claims for " + town.getName());
                }
                case "extra_assistants" -> {
                    plugin.getLogger().info("  Effect: +" + value + " extra assistants for " + town.getName());
                }
                case "income_bonus" -> {
                    plugin.getLogger().info("  Effect: " + value + " income bonus for " + town.getName());
                }
                default -> {
                    // Store unrecognized effects for future expansion
                    plugin.getLogger().info("  Effect: " + key + "=" + value + " for " + town.getName());
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
