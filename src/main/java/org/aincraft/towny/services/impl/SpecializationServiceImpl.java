package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.models.TownSpecialization;
import org.aincraft.towny.services.SpecializationService;
import org.aincraft.towny.services.TownService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

@Singleton
public class SpecializationServiceImpl implements SpecializationService {

    private final TownyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final TownService townService;
    
    /** In-memory cache of town specializations */
    private final Map<String, TownSpecialization> cache = new HashMap<>();
    private boolean initialized = false;

    @Inject
    public SpecializationServiceImpl(TownyPlugin plugin, DatabaseManager databaseManager,
                                     TownService townService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.townService = townService;
    }

    @Override
    public Optional<TownSpecialization> getSpecialization(String townId) {
        ensureInitialized();
        return Optional.ofNullable(cache.get(townId));
    }

    @Override
    public boolean canSpecialize(String townId) {
        Optional<Town> town = townService.getTown(townId);
        if (town.isEmpty()) {
            return false;
        }
        return town.get().getTownLevel() >= 10;
    }

    @Override
    public void setSpecialization(String townId, TownSpecialization specialization) {
        databaseManager.executeTransaction(conn -> {
            String sql = """
                INSERT OR REPLACE INTO town_specializations
                (town_id, specialization, set_at)
                VALUES (?, ?, ?)
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, townId);
                ps.setString(2, specialization.name());
                ps.setString(3, LocalDateTime.now().toString());
                ps.executeUpdate();
                cache.put(townId, specialization);
            }
        });
    }

    @Override
    public void removeSpecialization(String townId) {
        databaseManager.executeTransaction(conn -> {
            String sql = "DELETE FROM town_specializations WHERE town_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, townId);
                ps.executeUpdate();
                cache.remove(townId);
            }
        });
    }

    @Override
    public List<TownSpecialization> getAvailableSpecializations(String townId) {
        return Arrays.stream(TownSpecialization.values())
                .filter(spec -> getTownLevel(townId) >= spec.getRequiredLevel())
                .toList();
    }

    @Override
    public TownSpecialization fromString(String name) {
        try {
            return TownSpecialization.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void ensureInitialized() {
        if (!initialized) {
            loadFromDatabase();
            initialized = true;
        }
    }

    private void loadFromDatabase() {
        String sql = "SELECT town_id, specialization FROM town_specializations";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String townId = rs.getString("town_id");
                String specName = rs.getString("specialization");
                try {
                    TownSpecialization specialization = TownSpecialization.valueOf(specName);
                    cache.put(townId, specialization);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().log(Level.WARNING, "Invalid specialization stored for town " + townId + ": " + specName);
                }
            }
            plugin.getLogger().info("Loaded " + cache.size() + " town specializations from database");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load town specializations", e);
        }
    }

    private int getTownLevel(String townId) {
        return townService.getTown(townId)
                .map(Town::getTownLevel)
                .orElse(0);
    }
}