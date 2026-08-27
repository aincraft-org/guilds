package dev.mintychochip.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildSpecialization;
import dev.mintychochip.guilds.services.SpecializationService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.sql.NamedSql;

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


/** Implementation of specialization service. */
public class SpecializationServiceImpl implements SpecializationService {
    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The guild service. */
    private final GuildService guildService;

    /** In-memory cache of guild specializations */
    private final Map<String, GuildSpecialization> cache = new HashMap<>();
    /** The initialized. */
    private boolean initialized = false;


    /**
     * Creates a new specialization service impl instance.
     * @param plugin the plugin
     * @param databaseManager the database manager
     * @param guildService the guild service
     */
    public SpecializationServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager,
                                     GuildService guildService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.guildService = guildService;
    }

    /**
     * Returns the specialization.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public Optional<GuildSpecialization> getSpecialization(String guildId) {
        ensureInitialized();
        return Optional.ofNullable(cache.get(guildId));
    }

    /**
     * Returns whether specialize.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public boolean canSpecialize(String guildId) {
        Optional<Guild> guild = guildService.getGuild(guildId);
        if (guild.isEmpty()) {
            return false;
        }
        return guild.get().getGuildLevel() >= 10;
    }

    /**
     * Sets the specialization.
     * @param guildId the guild id
     * @param specialization the specialization
     */
    @Override
    public void setSpecialization(String guildId, GuildSpecialization specialization) {
        databaseManager.executeTransaction(conn -> {
            try (PreparedStatement ps = SQL.prepare(conn, "specializations/upsert.sql", Map.of(
                    "guild_id", guildId,
                    "specialization", specialization.name(),
                    "set_at", LocalDateTime.now().toString()))) {
                ps.executeUpdate();
                cache.put(guildId, specialization);
            }
        });
    }

    /**
     * Removes the specialization.
     * @param guildId the guild id
     */
    @Override
    public void removeSpecialization(String guildId) {
        databaseManager.executeTransaction(conn -> {
            try (PreparedStatement ps = SQL.prepare(conn, "specializations/delete.sql", Map.of(
                    "guild_id", guildId))) {
                ps.executeUpdate();
                cache.remove(guildId);
            }
        });
    }

    /**
     * Returns the available specializations.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public List<GuildSpecialization> getAvailableSpecializations(String guildId) {
        return Arrays.stream(GuildSpecialization.values())
                .filter(spec -> getGuildLevel(guildId) >= spec.getRequiredLevel())
                .toList();
    }

    /**
     * Performs the from string operation.
     * @param name the name
     * @return the result
     */
    @Override
    public GuildSpecialization fromString(String name) {
        try {
            return GuildSpecialization.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Performs the ensure initialized operation. */
    private void ensureInitialized() {
        if (!initialized) {
            loadFromDatabase();
            initialized = true;
        }
    }

    /** Loads the from database. */
    private void loadFromDatabase() {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = SQL.prepare(conn, "specializations/select-all.sql", Map.of());
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

    /**
     * Returns the guild level.
     * @param guildId the guild id
     * @return the result
     */
    private int getGuildLevel(String guildId) {
        return guildService.getGuild(guildId)
                .map(Guild::getGuildLevel)
                .orElse(0);
    }
}