package org.aincraft.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.config.GuildLevelConfigLoader;
import org.aincraft.guilds.config.model.LevelDefinition;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildLevel;
import org.aincraft.guilds.models.ResourceType;
import org.aincraft.guilds.services.GuildLevelService;
import org.aincraft.guilds.services.GuildService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Implementation of GuildLevelService for guild level system operations
 */

public class GuildLevelServiceImpl implements GuildLevelService {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final GuildLevelConfigLoader configLoader;

    // Cache for guild level definitions
    private final Map<Integer, GuildLevel> levelCache = new HashMap<>();
    private boolean cacheInitialized = false;


    public GuildLevelServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager, GuildService guildService, GuildLevelConfigLoader configLoader) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configLoader = configLoader;
    }
    private record UpgradeMutation(
            boolean successful,
            String message,
            int previousLevel,
            int newLevel,
            int techPointsEarned,
            int newTechPoints
    ) {
        private static UpgradeMutation failure(int previousLevel, String message) {
            return new UpgradeMutation(false, message, previousLevel, previousLevel, 0, 0);
        }

        private static UpgradeMutation success(
                int previousLevel, int newLevel, int techPointsEarned, int newTechPoints) {
            return new UpgradeMutation(
                    true, "Successfully upgraded", previousLevel, newLevel,
                    techPointsEarned, newTechPoints);
        }
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
            return new UpgradeEligibility(false, "Guild not found", Map.of(), Map.of(), Map.of());
        }
        if (isAtMaxLevel(guild)) {
            return new UpgradeEligibility(false, "Guild is already at maximum level", Map.of(), Map.of(), Map.of());
        }

        Optional<GuildLevel> nextLevelOpt = getNextGuildLevel(guild);
        if (nextLevelOpt.isEmpty()) {
            return new UpgradeEligibility(false, "Next level not available", Map.of(), Map.of(), Map.of());
        }

        GuildLevel nextLevel = nextLevelOpt.get();
        Map<String, Integer> requiredResources = new HashMap<>();
        for (Map.Entry<String, Integer> entry : nextLevel.getResourceCosts().entrySet()) {
            requiredResources.merge(
                    normalizeResourceKey(entry.getKey()), entry.getValue(), Integer::sum);
        }
        Map<String, Integer> contributedResources = calculateTotalContributions(guild);
        Map<String, Boolean> resourceStatus = new HashMap<>();
        boolean allRequirementsMet = true;
        for (Map.Entry<String, Integer> entry : requiredResources.entrySet()) {
            int contributed = contributedResources.getOrDefault(entry.getKey(), 0);
            boolean hasEnough = contributed >= entry.getValue();
            resourceStatus.put(entry.getKey(), hasEnough);
            allRequirementsMet &= hasEnough;
        }
        String reason = allRequirementsMet
                ? "All requirements met"
                : "Insufficient resources for upgrade";
        return new UpgradeEligibility(
                allRequirementsMet, reason, requiredResources, contributedResources, resourceStatus);
    }

    @Override
    public UpgradeResult performGuildUpgrade(Guild guild) {
        if (guild == null) {
            return new UpgradeResult(false, "Guild not found", 0, 0, 0);
        }

        int requestedLevel = guild.getGuildLevel();
        initializeCacheIfNeeded();
        Optional<UpgradeMutation> mutation;
        try {
            mutation = databaseManager.executeTransactionWithResult(
                    connection -> upgradeInTransaction(connection, guild.getId()));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to upgrade guild " + guild.getName(), e);
            return new UpgradeResult(false, "Database error during upgrade",
                    requestedLevel, requestedLevel, 0);
        }
        if (mutation == null || mutation.isEmpty()) {
            return new UpgradeResult(false, "Database error during upgrade",
                    requestedLevel, requestedLevel, 0);
        }

        UpgradeMutation result = mutation.get();
        if (!result.successful()) {
            return new UpgradeResult(false, result.message(),
                    result.previousLevel(), result.previousLevel(), 0);
        }

        // Reflect the committed row only after the transaction succeeds.
        guild.setGuildLevel(result.newLevel());
        guild.setTechPoints(result.newTechPoints());
        guild.setUpgradeProgress(Map.of());
        plugin.getLogger().info("Guild " + guild.getName() + " upgraded from level "
                + result.previousLevel() + " to level " + result.newLevel());
        return new UpgradeResult(
                true,
                "Successfully upgraded to level " + result.newLevel(),
                result.previousLevel(),
                result.newLevel(),
                result.techPointsEarned());
    }

    private UpgradeMutation upgradeInTransaction(Connection connection, String guildId)
            throws SQLException {
        int currentLevel;
        int currentTechPoints;
        String progressJson;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT guild_level, tech_points, upgrade_progress FROM guilds WHERE id = ? FOR UPDATE")) {
            statement.setString(1, guildId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return UpgradeMutation.failure(0, "Guild not found");
                }
                currentLevel = result.getInt("guild_level");
                currentTechPoints = result.getInt("tech_points");
                progressJson = result.getString("upgrade_progress");
            }
        }

        if (currentLevel >= getMaxLevel()) {
            return UpgradeMutation.failure(currentLevel, "Guild is already at maximum level");
        }
        GuildLevel nextLevel = levelCache.get(currentLevel + 1);
        if (nextLevel == null) {
            return UpgradeMutation.failure(currentLevel, "Next level not available");
        }

        Map<String, Integer> progress = parseUpgradeProgressJson(progressJson);
        for (Map.Entry<String, Integer> requirement : nextLevel.getResourceCosts().entrySet()) {
            if (progress.getOrDefault(normalizeResourceKey(requirement.getKey()), 0) < requirement.getValue()) {
                return UpgradeMutation.failure(currentLevel, "Insufficient resources for upgrade");
            }
        }

        int newLevel = nextLevel.getLevel();
        int newTechPoints = currentTechPoints + nextLevel.getTechPointsReward();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE guilds
                   SET guild_level = ?, tech_points = ?, upgrade_progress = '{}'
                 WHERE id = ? AND guild_level = ?
                """)) {
            statement.setInt(1, newLevel);
            statement.setInt(2, newTechPoints);
            statement.setString(3, guildId);
            statement.setInt(4, currentLevel);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Guild level changed during upgrade");
            }
        }
        recordLevelBenefits(connection, guildId, nextLevel);
        return UpgradeMutation.success(
                currentLevel, newLevel, nextLevel.getTechPointsReward(), newTechPoints);
    }

    @Override
    public Map<String, Integer> calculateTotalContributions(Guild guild) {
        if (guild == null) {
            return Map.of();
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT upgrade_progress FROM guilds WHERE id = ?")) {
            statement.setString(1, guild.getId());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return parseUpgradeProgressJson(result.getString(1));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load guild upgrade progress for " + guild.getId(), e);
        }
        return new HashMap<>(guild.getUpgradeProgress());
    }

    @Override
    public double calculateUpgradeProgress(Guild guild) {
        if (guild == null) {
            return 0.0;
        }
        Optional<GuildLevel> nextLevelOpt = getNextGuildLevel(guild);
        if (nextLevelOpt.isEmpty()) {
            return 100.0;
        }

        Map<String, Integer> contributions = calculateTotalContributions(guild);
        double totalProgress = 0.0;
        int resourceCount = 0;
        for (Map.Entry<String, Integer> requirement : nextLevelOpt.get().getResourceCosts().entrySet()) {
            if (requirement.getValue() > 0) {
                totalProgress += Math.min(100.0,
                        contributions.getOrDefault(normalizeResourceKey(requirement.getKey()), 0) * 100.0
                                / requirement.getValue());
                resourceCount++;
            }
        }
        return resourceCount == 0 ? 0.0 : totalProgress / resourceCount;
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
                                plugin.getLogger().warning("Failed to parse upgrade progress for guild " + guild.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sync guild level data for " + guild.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void resetAllGuildLevelData() {
        try {
            String sql = "UPDATE guilds SET guild_level = 1, tech_points = 0, upgrade_progress = '{}'";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                int updatedRows = statement.executeUpdate();
                plugin.getLogger().info("Reset guild level data for " + updatedRows + " guilds");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reset guild level data: " + e.getMessage(), e);
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
            plugin.getLogger().info("Loaded " + levelCache.size() + " guild levels into cache");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load guild levels: " + e.getMessage(), e);
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

    private static String normalizeResourceKey(String key) {
        return ResourceType.fromString(key)
                .map(ResourceType::getNormalizedName)
                .orElseGet(() -> key == null ? "" : key.trim().toLowerCase(Locale.ROOT));
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
                    String key = normalizeResourceKey(keyValue[0].trim().replace("\"", ""));
                    int value = Integer.parseInt(keyValue[1].trim());
                    progress.put(key, value);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse upgrade progress JSON: " + json);
        }

        return progress;
    }

    private void recordLevelBenefits(
            Connection connection, String guildId, GuildLevel level) throws SQLException {
        String sql = """
                INSERT INTO guild_level_benefits
                    (id, guild_id, level, benefit_type, benefit_value, unlocked_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (guild_id, level, benefit_type) DO NOTHING
                """;
        String now = LocalDateTime.now().toString();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (level.getClaimLimitBonus() > 0) {
                insertLevelBenefit(statement, guildId, level, "claim_limit_bonus",
                        String.valueOf(level.getClaimLimitBonus()), now);
            }
            if (level.getAssistantSlotsBonus() > 0) {
                insertLevelBenefit(statement, guildId, level, "assistant_slots_bonus",
                        String.valueOf(level.getAssistantSlotsBonus()), now);
            }
            if (level.getDailyIncomeBonus() > 0) {
                insertLevelBenefit(statement, guildId, level, "daily_income_bonus",
                        String.valueOf(level.getDailyIncomeBonus()), now);
            }
            if (level.getTechPointsReward() > 0) {
                insertLevelBenefit(statement, guildId, level, "tech_points_reward",
                        String.valueOf(level.getTechPointsReward()), now);
            }
            for (String plotType : level.getUnlockedPlotTypes()) {
                insertLevelBenefit(
                        statement, guildId, level, "unlocked_plot_type:" + plotType, plotType, now);
            }
        }
    }

    private static void insertLevelBenefit(
            PreparedStatement statement,
            String guildId,
            GuildLevel level,
            String benefitType,
            String benefitValue,
            String unlockedAt
    ) throws SQLException {
        String benefitId = UUID.nameUUIDFromBytes(
                (guildId + ":" + level.getLevel() + ":" + benefitType + ":" + benefitValue)
                        .getBytes(StandardCharsets.UTF_8)).toString();
        statement.setString(1, benefitId);
        statement.setString(2, guildId);
        statement.setInt(3, level.getLevel());
        statement.setString(4, benefitType);
        statement.setString(5, benefitValue);
        statement.setString(6, unlockedAt);
        statement.executeUpdate();
    }

    /**
     * Reload level definitions from config
     */
    public void reloadLevelDefinitions() {
        configLoader.loadConfiguration();
        levelCache.clear();
        cacheInitialized = false;
        plugin.getLogger().info("Reloaded guild level definitions from config");
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
                INSERT INTO guild_levels (
                    level, resource_costs_json, tech_points_reward, claim_limit_bonus,
                    assistant_slots_bonus, daily_income_bonus, unlocked_plot_types, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (level) DO UPDATE SET
                    resource_costs_json = EXCLUDED.resource_costs_json,
                    tech_points_reward = EXCLUDED.tech_points_reward,
                    claim_limit_bonus = EXCLUDED.claim_limit_bonus,
                    assistant_slots_bonus = EXCLUDED.assistant_slots_bonus,
                    daily_income_bonus = EXCLUDED.daily_income_bonus,
                    unlocked_plot_types = EXCLUDED.unlocked_plot_types,
                    created_at = EXCLUDED.created_at
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
                plugin.getLogger().info("Synced " + results.length + " guild level definitions to database");

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