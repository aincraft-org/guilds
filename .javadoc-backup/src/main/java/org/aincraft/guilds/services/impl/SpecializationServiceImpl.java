package org.aincraft.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildSpecialization;
import org.aincraft.guilds.services.SpecializationService;
import org.aincraft.guilds.services.GuildService;

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


public class SpecializationServiceImpl implements SpecializationService {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final GuildService guildService;

    /** In-memory cache of guild specializations */
    private final Map<String, GuildSpecialization> cache = new HashMap<>();
    private boolean initialized = false;


    public SpecializationServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager,
                                     GuildService guildService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.guildService = guildService;
    }

    @Override
    public Optional<GuildSpecialization> getSpecialization(String guildId) {
        ensureInitialized();
        return Optional.ofNullable(cache.get(guildId));
    }

    @Override
    public boolean canSpecialize(String guildId) {
        Optional<Guild> guild = guildService.getGuild(guildId);
        if (guild.isEmpty()) {
            return false;
        }
        return guild.get().getGuildLevel() >= 10;
    }

    @Override
    public void setSpecialization(String guildId, GuildSpecialization specialization) {
        databaseManager.executeTransaction(conn -> {
            String sql = """
                INSERT INTO guild_specializations
                (guild_id, specialization, set_at)
                VALUES (?, ?, ?)
                ON CONFLICT (guild_id) DO UPDATE SET
                    specialization = EXCLUDED.specialization,
                    set_at = EXCLUDED.set_at
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guildId);
                ps.setString(2, specialization.name());
                ps.setString(3, LocalDateTime.now().toString());
                ps.executeUpdate();
                cache.put(guildId, specialization);
            }
        });
    }

    @Override
    public void removeSpecialization(String guildId) {
        databaseManager.executeTransaction(conn -> {
            String sql = "DELETE FROM guild_specializations WHERE guild_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guildId);
                ps.executeUpdate();
                cache.remove(guildId);
            }
        });
    }

    @Override
    public List<GuildSpecialization> getAvailableSpecializations(String guildId) {
        return Arrays.stream(GuildSpecialization.values())
                .filter(spec -> getGuildLevel(guildId) >= spec.getRequiredLevel())
                .toList();
    }

    @Override
    public GuildSpecialization fromString(String name) {
        try {
            return GuildSpecialization.valueOf(name.toUpperCase());
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
        String sql = "SELECT guild_id, specialization FROM guild_specializations";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String guildId = rs.getString("guild_id");
                String specName = rs.getString("specialization");
                try {
                    GuildSpecialization specialization = GuildSpecialization.valueOf(specName);
                    cache.put(guildId, specialization);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().log(Level.WARNING, "Invalid specialization stored for guild " + guildId + ": " + specName);
                }
            }
            plugin.getLogger().info("Loaded " + cache.size() + " guild specializations from database");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load guild specializations", e);
        }
    }

    private int getGuildLevel(String guildId) {
        return guildService.getGuild(guildId)
                .map(Guild::getGuildLevel)
                .orElse(0);
    }
}