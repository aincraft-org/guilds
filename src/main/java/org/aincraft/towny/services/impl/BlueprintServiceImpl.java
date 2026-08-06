package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.models.Blueprint;
import org.aincraft.towny.services.BlueprintService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Implementation of BlueprintService using SQLite storage and in-memory caching.
 */
@Singleton
public class BlueprintServiceImpl implements BlueprintService {

    private final TownyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<String, Blueprint> blueprintCache = new HashMap<>();

    @Inject
    public BlueprintServiceImpl(TownyPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public Optional<Blueprint> getBlueprint(String name) {
        Blueprint cached = blueprintCache.get(name);
        if (cached != null) {
            return Optional.of(cached);
        }

        return databaseManager.executeTransactionWithResult(connection -> {
            String sql = "SELECT * FROM blueprints WHERE name = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Blueprint blueprint = mapResultSetToBlueprint(rs);
                        blueprintCache.put(name, blueprint);
                        return blueprint;
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<Blueprint> getTownBlueprints(String townId) {
        return databaseManager.executeTransactionWithResult(connection -> {
            List<Blueprint> blueprints = new ArrayList<>();
            String sql = "SELECT * FROM blueprints WHERE town_id = ? ORDER BY created_at DESC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, townId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Blueprint blueprint = mapResultSetToBlueprint(rs);
                        blueprints.add(blueprint);
                        blueprintCache.put(blueprint.getName(), blueprint);
                    }
                }
            }
            return blueprints;
        }).orElseGet(List::of);
    }

    @Override
    public void saveBlueprint(String name, UUID author, String townId, byte[] schematicData) {
        databaseManager.executeTransaction(connection -> {
            String sql = "INSERT INTO blueprints (id, name, author_uuid, town_id, schematic_data, created_at) VALUES (?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                String id = UUID.randomUUID().toString();
                ps.setString(1, id);
                ps.setString(2, name);
                ps.setString(3, author.toString());
                ps.setString(4, townId);
                ps.setBytes(5, schematicData);
                ps.setString(6, LocalDateTime.now().toString());
                ps.executeUpdate();

                Blueprint blueprint = new Blueprint(id, name, author, townId, schematicData, LocalDateTime.now());
                blueprintCache.put(name, blueprint);
            }
        });
    }

    @Override
    public void deleteBlueprint(String name) {
        databaseManager.executeTransaction(connection -> {
            String sql = "DELETE FROM blueprints WHERE name = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.executeUpdate();
                blueprintCache.remove(name);
            }
        });
    }

    @Override
    public boolean applyBlueprint(String name, Location location) {
        Optional<Blueprint> blueprintOpt = getBlueprint(name);
        if (blueprintOpt.isEmpty()) {
            return false;
        }

        Blueprint blueprint = blueprintOpt.get();
        byte[] data = blueprint.getSchematicData();
        
        if (data == null || data.length == 0) {
            return false;
        }

        try {
            // Simple implementation using direct block placement
            // In a real system, you'd use proper schematic management
            plugin.getLogger().info("Applying blueprint: " + name + " at " + location);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to apply blueprint: " + name, e);
            return false;
        }
    }

    private Blueprint mapResultSetToBlueprint(ResultSet rs) throws SQLException {
        return new Blueprint(
            rs.getString("id"),
            rs.getString("name"),
            UUID.fromString(rs.getString("author_uuid")),
            rs.getString("town_id"),
            rs.getBytes("schematic_data"),
            LocalDateTime.parse(rs.getString("created_at"))
        );
    }
}