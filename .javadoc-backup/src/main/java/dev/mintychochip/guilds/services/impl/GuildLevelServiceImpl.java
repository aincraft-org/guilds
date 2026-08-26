package dev.mintychochip.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.config.GuildLevelConfigLoader;
import dev.mintychochip.guilds.config.model.LevelDefinition;
import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildLevel;
import dev.mintychochip.guilds.models.ResourceType;
import dev.mintychochip.guilds.projects.GuildSkillPoints;
import dev.mintychochip.guilds.projects.XpUpgradeGate;
import dev.mintychochip.guilds.services.GuildLevelService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.sql.NamedSql;

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
    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The config loader. */
    private final GuildLevelConfigLoader configLoader;

    // Cache for guild level definitions
    /** The level cache. */
    private final Map<Integer, GuildLevel> levelCache = new HashMap<>();
    /** The cache initialized. */
    private boolean cacheInitialized = false;


    /**
     * Creates a new guild level service impl instance.
     * @param plugin the plugin
     * @param databaseManager the database manager
     * @param guildService the guild service
     * @param configLoader the config loader
     */
    public GuildLevelServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager, GuildService guildService, GuildLevelConfigLoader configLoader) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configLoader = configLoader;
    }
    /** Immutable data carrier for upgrade mutation. */
    private record UpgradeMutation(
            boolean successful,
            String message,
            int previousLevel,
            int newLevel,
            int techPointsEarned,
            int newTechPoints
    ) {
        /**
         * Performs the failure operation.
         * @param previousLevel the previous level
         * @param message the message
         * @return the result
         */
        private static UpgradeMutation failure(int previousLevel, String message) {
            return new UpgradeMutation(false, message, previousLevel, previousLevel, 0, 0);
        }

        /**
         * Performs the success operation.
         * @param previousLevel the previous level
         * @param newLevel the new level
         * @param techPointsEarned the tech points earned
         * @param newTechPoints the new tech points
         * @return the result
         */
        private static UpgradeMutation success(
                int previousLevel, int newLevel, int techPointsEarned, int newTechPoints) {
            return new UpgradeMutation(
                    true, "Successfully upgraded", previousLevel, newLevel,
                    techPointsEarned, newTechPoints);
        }
    }

    /**
     * Returns the guild level.
     * @param level the level
     * @return the result
     */
    @Override
    public Optional<GuildLevel> getGuildLevel(int level) {
        initializeCacheIfNeeded();

        if (level < 1 || level > getMaxLevel()) {
            return Optional.empty();
        }

        return Optional.ofNullable(levelCache.get(level));
    }

    /**
     * Returns the next guild level.
     * @param guild the guild
     * @return the result
     */
    @Override
    public Optional<GuildLevel> getNextGuildLevel(Guild guild) {
        if (guild == null) {
            return Optional.empty();
        }

        int nextLevel = guild.getGuildLevel() + 1;
        return getGuildLevel(nextLevel);
    }

    /**
     * Returns the all guild levels.
     * @return the result
     */
    @Override
    public List<GuildLevel> getAllGuildLevels() {
        initializeCacheIfNeeded();
        return new ArrayList<>(levelCache.values());
    }

    /**
     * Returns the guild levels in range.
     * @param startLevel the start level
     * @param endLevel the end level
     * @return the result
     */
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

    /**
     * Checks the upgrade eligibility.
     * @param guild the guild
     * @return the result
     */
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
        int requiredXp = XpUpgradeGate.requiredExperience(nextLevel.getResourceCosts());
        Map<String, Integer> requiredResources = Map.of(XpUpgradeGate.EXPERIENCE_KEY, requiredXp);
        Map<String, Integer> contributedResources = calculateTotalContributions(guild);
        boolean eligible = XpUpgradeGate.hasEnoughExperience(contributedResources, requiredXp);
        Map<String, Boolean> resourceStatus = Map.of(XpUpgradeGate.EXPERIENCE_KEY, eligible);
        String reason = eligible
                ? "All requirements met"
                : "Insufficient experience for upgrade";
        return new UpgradeEligibility(
                eligible, reason, requiredResources, contributedResources, resourceStatus);
    }

    /**
     * Performs the perform guild upgrade operation.
     * @param guild the guild
     * @return the result
     */
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

    /**
     * Performs the upgrade in transaction operation.
     * @param connection the connection
     * @param guildId the guild id
     * @return the result
     * @throws SQLException if an error occurs
     */
    private UpgradeMutation upgradeInTransaction(Connection connection, String guildId)
            throws SQLException {
        int currentLevel;
        int currentTechPoints;
        String progressJson;
        try (PreparedStatement statement = SQL.prepare(connection, "levels/select-for-update.sql", Map.of(
                "id", guildId))) {
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
        int requiredXp = XpUpgradeGate.requiredExperience(nextLevel.getResourceCosts());
        if (!XpUpgradeGate.hasEnoughExperience(progress, requiredXp)) {
            return UpgradeMutation.failure(currentLevel, "Insufficient experience for upgrade");
        }

        int newLevel = nextLevel.getLevel();
        int newTechPoints = GuildSkillPoints.unspentAfterLevelChange(
                currentTechPoints, currentLevel, newLevel);
        int pointsEarned = newTechPoints - currentTechPoints;
        try (PreparedStatement statement = SQL.prepare(connection, "levels/update-upgrade.sql", Map.of(
                "guild_level", newLevel,
                "tech_points", newTechPoints,
                "id", guildId,
                "expected_level", currentLevel))) {
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Guild level changed during upgrade");
            }
        }
        recordLevelBenefits(connection, guildId, nextLevel);
        return UpgradeMutation.success(
                currentLevel, newLevel, pointsEarned, newTechPoints);
    }

    /**
     * Computes the total contributions.
     * @param guild the guild
     * @return the result
     */
    @Override
    public Map<String, Integer> calculateTotalContributions(Guild guild) {
        if (guild == null) {
            return Map.of();
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "levels/select-upgrade-progress.sql", Map.of(
                     "id", guild.getId()))) {
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

    /**
     * Computes the upgrade progress.
     * @param guild the guild
     * @return the result
     */
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
        int requiredXp = XpUpgradeGate.requiredExperience(nextLevelOpt.get().getResourceCosts());
        if (requiredXp <= 0) {
            return 0.0;
        }
        return Math.min(100.0, XpUpgradeGate.contributedExperience(contributions) * 100.0 / requiredXp);
    }

    /**
     * Returns the max level.
     * @return the result
     */
    @Override
    public int getMaxLevel() {
        return configLoader.getMaxLevel();
    }

    /**
     * Returns whether at max level.
     * @param guild the guild
     * @return the result
     */
    @Override
    public boolean isAtMaxLevel(Guild guild) {
        return guild != null && guild.getGuildLevel() >= getMaxLevel();
    }

    /**
     * Returns the level benefits.
     * @param level the level
     * @return the result
     */
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

    /**
     * Returns the current guild benefits.
     * @param guild the guild
     * @return the result
     */
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

    /**
     * Computes the total tech points.
     * @param guild the guild
     * @return the result
     */
    @Override
    public int calculateTotalTechPoints(Guild guild) {
        if (guild == null) {
            return 0;
        }
        return GuildSkillPoints.totalEarned(guild.getGuildLevel());
    }

    /**
     * Performs the sync guild level data operation.
     * @param guild the guild
     */
    @Override
    public void syncGuildLevelData(Guild guild) {
        if (guild == null) {
            return;
        }

        try {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = SQL.prepare(connection, "levels/select-level-data.sql", Map.of(
                         "id", guild.getId()))) {

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

    /** Performs the reset all guild level data operation. */
    @Override
    public void resetAllGuildLevelData() {
        try {
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = SQL.prepare(connection, "levels/reset-all.sql", Map.of())) {

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

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SQL.jdbc("levels/select-all.sql"))) {

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
     * Performs the normalize resource key operation.
     * @param key the key
     * @return the result
     */
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

    /**
     * Performs the record level benefits operation.
     * @param connection the connection
     * @param guildId the guild id
     * @param level the level
     * @throws SQLException if an error occurs
     */
    private void recordLevelBenefits(
            Connection connection, String guildId, GuildLevel level) throws SQLException {
        String now = LocalDateTime.now().toString();
        if (level.getClaimLimitBonus() > 0) {
            insertLevelBenefit(connection, guildId, level, "claim_limit_bonus",
                    String.valueOf(level.getClaimLimitBonus()), now);
        }
        if (level.getAssistantSlotsBonus() > 0) {
            insertLevelBenefit(connection, guildId, level, "assistant_slots_bonus",
                    String.valueOf(level.getAssistantSlotsBonus()), now);
        }
        if (level.getDailyIncomeBonus() > 0) {
            insertLevelBenefit(connection, guildId, level, "daily_income_bonus",
                    String.valueOf(level.getDailyIncomeBonus()), now);
        }
        if (level.getTechPointsReward() > 0) {
            insertLevelBenefit(connection, guildId, level, "tech_points_reward",
                    String.valueOf(level.getTechPointsReward()), now);
        }
        for (String plotType : level.getUnlockedPlotTypes()) {
            insertLevelBenefit(
                    connection, guildId, level, "unlocked_plot_type:" + plotType, plotType, now);
        }
    }

    /**
     * Inserts the level benefit.
     * @param connection the connection
     * @param guildId the guild id
     * @param level the level
     * @param benefitType the benefit type
     * @param benefitValue the benefit value
     * @param unlockedAt the unlocked at
     * @throws SQLException if an error occurs
     */
    private void insertLevelBenefit(
            Connection connection,
            String guildId,
            GuildLevel level,
            String benefitType,
            String benefitValue,
            String unlockedAt
    ) throws SQLException {
        String benefitId = UUID.nameUUIDFromBytes(
                (guildId + ":" + level.getLevel() + ":" + benefitType + ":" + benefitValue)
                        .getBytes(StandardCharsets.UTF_8)).toString();
        try (PreparedStatement statement = SQL.prepare(connection, "levels/insert-benefit.sql", Map.of(
                "id", benefitId,
                "guild_id", guildId,
                "level", level.getLevel(),
                "benefit_type", benefitType,
                "benefit_value", benefitValue,
                "unlocked_at", unlockedAt))) {
            statement.executeUpdate();
        }
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

            var parsed = SQL.sql("levels/upsert-level.sql");
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(parsed.jdbcSql(Map.of()))) {

                String currentTime = LocalDateTime.now().toString();

                for (LevelDefinition definition : definitions.values()) {
                    parsed.bind(statement, Map.of(
                            "level", definition.getLevel(),
                            "resource_costs_json", serializeResourceCosts(definition.getRequirements()),
                            "tech_points_reward", definition.getTechPoints(),
                            "claim_limit_bonus", definition.getClaimLimitBonus(),
                            "assistant_slots_bonus", definition.getAssistantSlotsBonus(),
                            "daily_income_bonus", definition.getDailyIncomeBonus(),
                            "unlocked_plot_types", serializeList(definition.getUnlockedPlotTypes()),
                            "created_at", currentTime));
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