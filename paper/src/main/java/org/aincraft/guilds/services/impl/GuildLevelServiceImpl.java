package org.aincraft.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.config.GuildLevelConfigLoader;
import org.aincraft.guilds.config.model.LevelDefinition;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildLevel;
import org.aincraft.guilds.services.GuildLevelService;
import org.aincraft.guilds.services.GuildService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Implementation of GuildLevelService for guild level system operations
 */

public class GuildLevelServiceImpl implements GuildLevelService {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final GuildService guildService;
    private final GuildLevelConfigLoader configLoader;

    // Cache for guild level definitions
    private final Map<Integer, GuildLevel> levelCache = new HashMap<>();
    private boolean cacheInitialized = false;


    public GuildLevelServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager, GuildService guildService, GuildLevelConfigLoader configLoader) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.guildService = guildService;
        this.configLoader = configLoader;
    }

    @Override
    public Optional<GuildLevel> getGuildLevel(int level) {
        initializeCacheIfNeeded();

        if (level < 1 || level > getMaxLevel()) {
            return Optional.empty();
        }

        return Optional.ofNullable(levelCache.get(level));
    }

    @Override
    public Optional<GuildLevel> getNextGuildLevel(Guild guild) {
        if (guild == null) {
            return Optional.empty();
        }

        int nextLevel = guild.getGuildLevel() + 1;
        return getGuildLevel(nextLevel);
    }

    @Override
    public List<GuildLevel> getAllGuildLevels() {
        initializeCacheIfNeeded();
        return new ArrayList<>(levelCache.values());
    }

    @Override
    public List<GuildLevel> getGuildLevelsInRange(int startLevel, int endLevel) {
        initializeCacheIfNeeded();

        List<GuildLevel> levels = new ArrayList<>();
        for (int level = Math.max(1, startLevel); level <= Math.min(endLevel, getMaxLevel()); level++) {
            GuildLevel guildLevel = levelCache.get(level);
            if (guildLevel != null) {
                levels.add(guildLevel);
            }
        }

        return levels;
    }

    @Override
    public UpgradeEligibility checkUpgradeEligibility(Guild guild) {
        if (guild == null) {
            return new UpgradeEligibility(false, "Town not found", Map.of(), Map.of(), Map.of());
        }

        if (isAtMaxLevel(guild)) {
            return new UpgradeEligibility(false, "Town is already at maximum level", Map.of(), Map.of(), Map.of());
        }

        Optional<GuildLevel> nextLevelOpt = getNextGuildLevel(guild);
        if (nextLevelOpt.isEmpty()) {
            return new UpgradeEligibility(false, "Next level not available", Map.of(), Map.of(), Map.of());
        }

        GuildLevel nextLevel = nextLevelOpt.get();
        Map<String, Integer> requiredResources = nextLevel.getResourceCosts();
        Map<String, Integer> contributedResources = calculateTotalContributions(guild);
        Map<String, Boolean> resourceStatus = new HashMap<>();

        boolean allRequirementsMet = true;
        for (Map.Entry<String, Integer> entry : requiredResources.entrySet()) {
            String resourceType = entry.getKey();
            int required = entry.getValue();
            int contributed = contributedResources.getOrDefault(resourceType, 0);
            boolean hasEnough = contributed >= required;
            resourceStatus.put(resourceType, hasEnough);

            if (!hasEnough) {
                allRequirementsMet = false;
            }
        }

        String reason = allRequirementsMet ? "All requirements met" : "Insufficient resources for upgrade";

        return new UpgradeEligibility(allRequirementsMet, reason, requiredResources, contributedResources, resourceStatus);
    }

    @Override
    public UpgradeResult performGuildUpgrade(Guild guild) {
        if (guild == null) {
            return new UpgradeResult(false, "Town not found", 0, 0, 0);
        }

        int previousLevel = guild.getGuildLevel();

        if (isAtMaxLevel(guild)) {
            return new UpgradeResult(false, "Town is already at maximum level", previousLevel, previousLevel, 0);
        }

        UpgradeEligibility eligibility = checkUpgradeEligibility(guild);
        if (!eligibility.isEligible()) {
            return new UpgradeResult(false, eligibility.getReason(), previousLevel, previousLevel, 0);
        }

        Optional<GuildLevel> nextLevelOpt = getNextGuildLevel(guild);
        if (nextLevelOpt.isEmpty()) {
            return new UpgradeResult(false, "Next level not available", previousLevel, previousLevel, 0);
        }

        GuildLevel nextLevel = nextLevelOpt.get();
        int newLevel = nextLevel.getLevel();
        int techPointsEarned = nextLevel.getTechPointsReward();

        try {
            // Update guild level and tech points
            guild.levelUp(newLevel, techPointsEarned);

            // Save changes to database
            guildService.updateGuild(guild);

            // Record level benefits
            recordLevelBenefits(guild, nextLevel);

            plugin.getLogger().info("Town " + guild.getName() + " upgraded from level " + previousLevel + " to level " + newLevel);

            return new UpgradeResult(true, "Successfully upgraded to level " + newLevel, previousLevel, newLevel, techPointsEarned);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to upgrade town " + guild.getName() + ": " + e.getMessage(), e);
            return new UpgradeResult(false, "Database error during upgrade", previousLevel, previousLevel, 0);
        }
    }

    @Override
    public Map<String, Integer> calculateTotalContributions(Guild guild) {
        if (guild == null) {
            return Map.of();
        }

        // For now, use the guild's upgrade progress
        // In a full implementation, this would sum from the resource_contributions table
        return new HashMap<>(guild.getUpgradeProgress());
    }

    @Override
    public double calculateUpgradeProgress(Guild guild) {
        if (guild == null) {
            return 0.0;
        }

        Optional<GuildLevel> nextLevelOpt = getNextGuildLevel(guild);
        if (nextLevelOpt.isEmpty()) {
            return 100.0; // At max level
        }

        GuildLevel nextLevel = nextLevelOpt.get();
        return guild.getOverallUpgradeProgress(nextLevel.getResourceCosts());
    }

    @Override
    public int getMaxLevel() {
        return configLoader.getMaxLevel();
    }

    @Override
    public boolean isAtMaxLevel(Guild guild) {
        return guild != null && guild.getGuildLevel() >= getMaxLevel();
    }

    @Override
    public LevelBenefits getLevelBenefits(int level) {
        Optional<GuildLevel> guildLevelOpt = getGuildLevel(level);
        if (guildLevelOpt.isEmpty()) {
            return new LevelBenefits(0, 0, 0.0, 0, List.of());
        }

        GuildLevel guildLevel = guildLevelOpt.get();
        return new LevelBenefits(
                guildLevel.getClaimLimitBonus(),
                guildLevel.getAssistantSlotsBonus(),
                guildLevel.getDailyIncomeBonus(),
                guildLevel.getTechPointsReward(),
                guildLevel.getUnlockedPlotTypes()
        );
    }

    @Override
    public LevelBenefits getCurrentGuildBenefits(Guild guild) {
        if (guild == null) {
            return new LevelBenefits(0, 0, 0.0, 0, List.of());
        }

        // Sum up all benefits from levels 1 to current level
        int totalClaimLimitBonus = 0;
        int totalAssistantSlotsBonus = 0;
        double totalDailyIncomeBonus = 0.0;
        int totalTechPointsReward = 0;
        Set<String> allUnlockedPlotTypes = new HashSet<>();

        for (int level = 1; level <= guild.getGuildLevel(); level++) {
            LevelBenefits benefits = getLevelBenefits(level);
            totalClaimLimitBonus += benefits.getClaimLimitBonus();
            totalAssistantSlotsBonus += benefits.getAssistantSlotsBonus();
            totalDailyIncomeBonus += benefits.getDailyIncomeBonus();
            totalTechPointsReward += benefits.getTechPointsReward();
            allUnlockedPlotTypes.addAll(benefits.getUnlockedPlotTypes());
        }

        return new LevelBenefits(
                totalClaimLimitBonus,
                totalAssistantSlotsBonus,
                totalDailyIncomeBonus,
                totalTechPointsReward,
                new ArrayList<>(allUnlockedPlotTypes)
        );
    }

    @Override
    public int calculateTotalTechPoints(Guild guild) {
        if (guild == null) {
            return 0;
        }

        // Sum tech points from all levels up to current level
        int totalTechPoints = 0;
        for (int level = 1; level <= guild.getGuildLevel(); level++) {
            Optional<GuildLevel> guildLevelOpt = getGuildLevel(level);
            if (guildLevelOpt.isPresent()) {
                totalTechPoints += guildLevelOpt.get().getTechPointsReward();
            }
        }

        return totalTechPoints;
    }

    @Override
    public void syncGuildLevelData(Guild guild) {
        if (guild == null) {
            return;
        }

        try {
            // Load level data from database and sync with guild object
            String sql = "SELECT guild_level, tech_points, upgrade_progress FROM guilds WHERE id = ?";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, guild.getId());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        guild.setGuildLevel(resultSet.getInt("guild_level"));
                        guild.setTechPoints(resultSet.getInt("tech_points"));

                        // Parse upgrade progress JSON
                        String upgradeProgressJson = resultSet.getString("upgrade_progress");
                        if (upgradeProgressJson != null && !upgradeProgressJson.isEmpty() && !upgradeProgressJson.equals("{}")) {
                            try {
                                // Simple JSON parsing for now - in a full implementation, use a proper JSON library
                                Map<String, Integer> progress = parseUpgradeProgressJson(upgradeProgressJson);
                                guild.setUpgradeProgress(progress);
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to parse upgrade progress for town " + guild.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sync town level data for " + guild.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void resetAllGuildLevelData() {
        try {
            String sql = "UPDATE guilds SET guild_level = 1, tech_points = 0, upgrade_progress = '{}'";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                int updatedRows = statement.executeUpdate();
                plugin.getLogger().info("Reset town level data for " + updatedRows + " guilds");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reset town level data: " + e.getMessage(), e);
        }
    }

    /**
     * Initialize the level cache if not already done
     */
    private void initializeCacheIfNeeded() {
        if (cacheInitialized) {
            return;
        }

        try {
            loadGuildLevelsFromDatabase();
            cacheInitialized = true;
            plugin.getLogger().info("Loaded " + levelCache.size() + " town levels into cache");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load town levels: " + e.getMessage(), e);
        }
    }

    /**
     * Load guild level definitions from database
     */
    private void loadGuildLevelsFromDatabase() throws SQLException {
        levelCache.clear();
        String sql = "SELECT * FROM guild_levels ORDER BY level";

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                GuildLevel guildLevel = mapResultSetToGuildLevel(resultSet);
                levelCache.put(guildLevel.getLevel(), guildLevel);
            }
        }
    }

    /**
     * Map a ResultSet to a GuildLevel object
     */
    private GuildLevel mapResultSetToGuildLevel(ResultSet resultSet) throws SQLException {
        List<String> unlockedPlotTypes = parseJsonArray(resultSet.getString("unlocked_plot_types"));
        Map<String, Integer> resourceCosts = parseResourceCostsJson(resultSet.getString("resource_costs_json"));

        return new GuildLevel(
                resultSet.getInt("level"),
                resourceCosts,
                resultSet.getInt("tech_points_reward"),
                resultSet.getInt("claim_limit_bonus"),
                resultSet.getInt("assistant_slots_bonus"),
                resultSet.getDouble("daily_income_bonus"),
                unlockedPlotTypes
        );
    }

    /**
     * Parse resource costs JSON into a Map
     */
    private Map<String, Integer> parseResourceCostsJson(String json) {
        Map<String, Integer> costs = new HashMap<>();

        if (json == null || json.isEmpty() || json.equals("{}")) {
            return costs;
        }

        try {
            // Simple JSON parsing for key-value pairs
            String content = json.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
            }

            if (content.trim().isEmpty()) {
                return costs;
            }

            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    String value = keyValue[1].trim();
                    try {
                        costs.put(key, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid number in resource costs JSON: " + value);
                    }
                }
            }

            return costs;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse resource costs JSON: " + json);
            return costs;
        }
    }

    /**
     * Parse a JSON array string into a List of strings
     */
    private List<String> parseJsonArray(String jsonArray) {
        if (jsonArray == null || jsonArray.isEmpty() || jsonArray.equals("[]")) {
            return List.of();
        }

        try {
            // Simple JSON parsing - remove brackets and split by comma
            String content = jsonArray.trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                content = content.substring(1, content.length() - 1);
            }

            if (content.trim().isEmpty()) {
                return List.of();
            }

            String[] items = content.split(",");
            List<String> result = new ArrayList<>();

            for (String item : items) {
                String cleaned = item.trim().replace("\"", "");
                if (!cleaned.isEmpty()) {
                    result.add(cleaned);
                }
            }

            return result;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse JSON array: " + jsonArray);
            return List.of();
        }
    }

    /**
     * Simple JSON parser for upgrade progress
     */
    private Map<String, Integer> parseUpgradeProgressJson(String json) {
        Map<String, Integer> progress = new HashMap<>();

        if (json == null || json.isEmpty() || json.equals("{}")) {
            return progress;
        }

        try {
            // Very simple JSON parsing for key-value pairs
            String content = json.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
            }

            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    int value = Integer.parseInt(keyValue[1].trim());
                    progress.put(key, value);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse upgrade progress JSON: " + json);
        }

        return progress;
    }

    /**
     * Record level benefits for a guild
     */
    private void recordLevelBenefits(Guild guild, GuildLevel level) {
        try {
            String sql = """
                INSERT OR REPLACE INTO guild_level_benefits (id, guild_id, level, benefit_type, benefit_value, unlocked_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                String benefitId = UUID.randomUUID().toString();
                String currentTime = java.time.LocalDateTime.now().toString();

                // Record claim limit bonus
                if (level.getClaimLimitBonus() > 0) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, guild.getId());
                    statement.setInt(3, level.getLevel());
                    statement.setString(4, "claim_limit_bonus");
                    statement.setString(5, String.valueOf(level.getClaimLimitBonus()));
                    statement.setString(6, currentTime);
                    statement.addBatch();
                }

                // Record assistant slots bonus
                if (level.getAssistantSlotsBonus() > 0) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, guild.getId());
                    statement.setInt(3, level.getLevel());
                    statement.setString(4, "assistant_slots_bonus");
                    statement.setString(5, String.valueOf(level.getAssistantSlotsBonus()));
                    statement.setString(6, currentTime);
                    statement.addBatch();
                }

                // Record daily income bonus
                if (level.getDailyIncomeBonus() > 0) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, guild.getId());
                    statement.setInt(3, level.getLevel());
                    statement.setString(4, "daily_income_bonus");
                    statement.setString(5, String.valueOf(level.getDailyIncomeBonus()));
                    statement.setString(6, currentTime);
                    statement.addBatch();
                }

                // Record tech points
                if (level.getTechPointsReward() > 0) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, guild.getId());
                    statement.setInt(3, level.getLevel());
                    statement.setString(4, "tech_points_reward");
                    statement.setString(5, String.valueOf(level.getTechPointsReward()));
                    statement.setString(6, currentTime);
                    statement.addBatch();
                }

                // Record unlocked plot types
                for (String plotType : level.getUnlockedPlotTypes()) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, guild.getId());
                    statement.setInt(3, level.getLevel());
                    statement.setString(4, "unlocked_plot_type");
                    statement.setString(5, plotType);
                    statement.setString(6, currentTime);
                    statement.addBatch();
                }

                statement.executeBatch();
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to record level benefits for town " + guild.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reload level definitions from config
     */
    public void reloadLevelDefinitions() {
        configLoader.loadConfiguration();
        levelCache.clear();
        cacheInitialized = false;
        plugin.getLogger().info("Reloaded town level definitions from config");
    }

    /**
     * Sync configuration to database
     */
    public void syncConfigToDatabase() {
        try {
            Map<Integer, LevelDefinition> definitions = configLoader.getLevelDefinitions();

            if (definitions.isEmpty()) {
                plugin.getLogger().warning("No level definitions to sync to database");
                return;
            }

            String sql = """
                INSERT OR REPLACE INTO guild_levels (
                    level, resource_costs_json, tech_points_reward, claim_limit_bonus,
                    assistant_slots_bonus, daily_income_bonus, unlocked_plot_types, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                String currentTime = LocalDateTime.now().toString();

                for (LevelDefinition definition : definitions.values()) {
                    // Serialize resource costs to JSON
                    String resourceCostsJson = serializeResourceCosts(definition.getRequirements());

                    // Serialize unlocked plot types to JSON
                    String unlockedPlotTypesJson = serializeList(definition.getUnlockedPlotTypes());

                    statement.setInt(1, definition.getLevel());
                    statement.setString(2, resourceCostsJson);
                    statement.setInt(3, definition.getTechPoints());
                    statement.setInt(4, definition.getClaimLimitBonus());
                    statement.setInt(5, definition.getAssistantSlotsBonus());
                    statement.setDouble(6, definition.getDailyIncomeBonus());
                    statement.setString(7, unlockedPlotTypesJson);
                    statement.setString(8, currentTime);

                    statement.addBatch();
                }

                int[] results = statement.executeBatch();
                plugin.getLogger().info("Synced " + results.length + " town level definitions to database");

                // Clear cache to force reload from database
                levelCache.clear();
                cacheInitialized = false;
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sync config to database: " + e.getMessage(), e);
        }
    }

    /**
     * Serialize resource costs map to JSON string
     */
    private String serializeResourceCosts(Map<String, Integer> costs) {
        if (costs == null || costs.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Integer> entry : costs.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }

        json.append("}");
        return json.toString();
    }

    /**
     * Serialize list to JSON array string
     */
    private String serializeList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("\"").append(list.get(i)).append("\"");
        }
        json.append("]");
        return json.toString();
    }
}