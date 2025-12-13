package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.models.TownLevel;
import org.aincraft.towny.services.TownLevelService;
import org.aincraft.towny.services.TownService;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Implementation of TownLevelService for town level system operations
 */
@Singleton
public class TownLevelServiceImpl implements TownLevelService {

    private final TownyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final TownService townService;

    // Cache for town level definitions
    private final Map<Integer, TownLevel> levelCache = new HashMap<>();
    private boolean cacheInitialized = false;

    @Inject
    public TownLevelServiceImpl(TownyPlugin plugin, DatabaseManager databaseManager, TownService townService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.townService = townService;
    }

    @Override
    public Optional<TownLevel> getTownLevel(int level) {
        initializeCacheIfNeeded();

        if (level < 1 || level > getMaxLevel()) {
            return Optional.empty();
        }

        return Optional.ofNullable(levelCache.get(level));
    }

    @Override
    public Optional<TownLevel> getNextTownLevel(Town town) {
        if (town == null) {
            return Optional.empty();
        }

        int nextLevel = town.getTownLevel() + 1;
        return getTownLevel(nextLevel);
    }

    @Override
    public List<TownLevel> getAllTownLevels() {
        initializeCacheIfNeeded();
        return new ArrayList<>(levelCache.values());
    }

    @Override
    public List<TownLevel> getTownLevelsInRange(int startLevel, int endLevel) {
        initializeCacheIfNeeded();

        List<TownLevel> levels = new ArrayList<>();
        for (int level = Math.max(1, startLevel); level <= Math.min(endLevel, getMaxLevel()); level++) {
            TownLevel townLevel = levelCache.get(level);
            if (townLevel != null) {
                levels.add(townLevel);
            }
        }

        return levels;
    }

    @Override
    public UpgradeEligibility checkUpgradeEligibility(Town town) {
        if (town == null) {
            return new UpgradeEligibility(false, "Town not found", Map.of(), Map.of(), Map.of());
        }

        if (isAtMaxLevel(town)) {
            return new UpgradeEligibility(false, "Town is already at maximum level", Map.of(), Map.of(), Map.of());
        }

        Optional<TownLevel> nextLevelOpt = getNextTownLevel(town);
        if (nextLevelOpt.isEmpty()) {
            return new UpgradeEligibility(false, "Next level not available", Map.of(), Map.of(), Map.of());
        }

        TownLevel nextLevel = nextLevelOpt.get();
        Map<String, Integer> requiredResources = nextLevel.getResourceCosts();
        Map<String, Integer> contributedResources = calculateTotalContributions(town);
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
    public UpgradeResult performTownUpgrade(Town town) {
        if (town == null) {
            return new UpgradeResult(false, "Town not found", 0, 0, 0);
        }

        int previousLevel = town.getTownLevel();

        if (isAtMaxLevel(town)) {
            return new UpgradeResult(false, "Town is already at maximum level", previousLevel, previousLevel, 0);
        }

        UpgradeEligibility eligibility = checkUpgradeEligibility(town);
        if (!eligibility.isEligible()) {
            return new UpgradeResult(false, eligibility.getReason(), previousLevel, previousLevel, 0);
        }

        Optional<TownLevel> nextLevelOpt = getNextTownLevel(town);
        if (nextLevelOpt.isEmpty()) {
            return new UpgradeResult(false, "Next level not available", previousLevel, previousLevel, 0);
        }

        TownLevel nextLevel = nextLevelOpt.get();
        int newLevel = nextLevel.getLevel();
        int techPointsEarned = nextLevel.getTechPointsReward();

        try {
            // Update town level and tech points
            town.levelUp(newLevel, techPointsEarned);

            // Save changes to database
            townService.updateTown(town);

            // Record level benefits
            recordLevelBenefits(town, nextLevel);

            plugin.getLogger().info("Town " + town.getName() + " upgraded from level " + previousLevel + " to level " + newLevel);

            return new UpgradeResult(true, "Successfully upgraded to level " + newLevel, previousLevel, newLevel, techPointsEarned);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to upgrade town " + town.getName() + ": " + e.getMessage(), e);
            return new UpgradeResult(false, "Database error during upgrade", previousLevel, previousLevel, 0);
        }
    }

    @Override
    public Map<String, Integer> calculateTotalContributions(Town town) {
        if (town == null) {
            return Map.of();
        }

        // For now, use the town's upgrade progress
        // In a full implementation, this would sum from the resource_contributions table
        return new HashMap<>(town.getUpgradeProgress());
    }

    @Override
    public double calculateUpgradeProgress(Town town) {
        if (town == null) {
            return 0.0;
        }

        Optional<TownLevel> nextLevelOpt = getNextTownLevel(town);
        if (nextLevelOpt.isEmpty()) {
            return 100.0; // At max level
        }

        TownLevel nextLevel = nextLevelOpt.get();
        return town.getOverallUpgradeProgress(nextLevel.getResourceCosts());
    }

    @Override
    public int getMaxLevel() {
        // Default max level is 150, but this could be configurable
        return 150;
    }

    @Override
    public boolean isAtMaxLevel(Town town) {
        return town != null && town.getTownLevel() >= getMaxLevel();
    }

    @Override
    public LevelBenefits getLevelBenefits(int level) {
        Optional<TownLevel> townLevelOpt = getTownLevel(level);
        if (townLevelOpt.isEmpty()) {
            return new LevelBenefits(0, 0, 0.0, 0, List.of());
        }

        TownLevel townLevel = townLevelOpt.get();
        return new LevelBenefits(
                townLevel.getClaimLimitBonus(),
                townLevel.getAssistantSlotsBonus(),
                townLevel.getDailyIncomeBonus(),
                townLevel.getTechPointsReward(),
                townLevel.getUnlockedPlotTypes()
        );
    }

    @Override
    public LevelBenefits getCurrentTownBenefits(Town town) {
        if (town == null) {
            return new LevelBenefits(0, 0, 0.0, 0, List.of());
        }

        // Sum up all benefits from levels 1 to current level
        int totalClaimLimitBonus = 0;
        int totalAssistantSlotsBonus = 0;
        double totalDailyIncomeBonus = 0.0;
        int totalTechPointsReward = 0;
        Set<String> allUnlockedPlotTypes = new HashSet<>();

        for (int level = 1; level <= town.getTownLevel(); level++) {
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
    public int calculateTotalTechPoints(Town town) {
        if (town == null) {
            return 0;
        }

        // Sum tech points from all levels up to current level
        int totalTechPoints = 0;
        for (int level = 1; level <= town.getTownLevel(); level++) {
            Optional<TownLevel> townLevelOpt = getTownLevel(level);
            if (townLevelOpt.isPresent()) {
                totalTechPoints += townLevelOpt.get().getTechPointsReward();
            }
        }

        return totalTechPoints;
    }

    @Override
    public void syncTownLevelData(Town town) {
        if (town == null) {
            return;
        }

        try {
            // Load level data from database and sync with town object
            String sql = "SELECT town_level, tech_points, upgrade_progress FROM towns WHERE id = ?";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, town.getId());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        town.setTownLevel(resultSet.getInt("town_level"));
                        town.setTechPoints(resultSet.getInt("tech_points"));

                        // Parse upgrade progress JSON
                        String upgradeProgressJson = resultSet.getString("upgrade_progress");
                        if (upgradeProgressJson != null && !upgradeProgressJson.isEmpty() && !upgradeProgressJson.equals("{}")) {
                            try {
                                // Simple JSON parsing for now - in a full implementation, use a proper JSON library
                                Map<String, Integer> progress = parseUpgradeProgressJson(upgradeProgressJson);
                                town.setUpgradeProgress(progress);
                            } catch (Exception e) {
                                plugin.getLogger().warning("Failed to parse upgrade progress for town " + town.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to sync town level data for " + town.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void resetAllTownLevelData() {
        try {
            String sql = "UPDATE towns SET town_level = 1, tech_points = 0, upgrade_progress = '{}'";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                int updatedRows = statement.executeUpdate();
                plugin.getLogger().info("Reset town level data for " + updatedRows + " towns");
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
            loadTownLevelsFromDatabase();
            cacheInitialized = true;
            plugin.getLogger().info("Loaded " + levelCache.size() + " town levels into cache");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load town levels: " + e.getMessage(), e);
        }
    }

    /**
     * Load town level definitions from database
     */
    private void loadTownLevelsFromDatabase() throws SQLException {
        levelCache.clear();
        String sql = "SELECT * FROM town_levels ORDER BY level";

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                TownLevel townLevel = mapResultSetToTownLevel(resultSet);
                levelCache.put(townLevel.getLevel(), townLevel);
            }
        }
    }

    /**
     * Map a ResultSet to a TownLevel object
     */
    private TownLevel mapResultSetToTownLevel(ResultSet resultSet) throws SQLException {
        List<String> unlockedPlotTypes = parseJsonArray(resultSet.getString("unlocked_plot_types"));

        return new TownLevel(
                resultSet.getInt("level"),
                resultSet.getInt("diamond_cost"),
                resultSet.getInt("gold_cost"),
                resultSet.getInt("iron_cost"),
                resultSet.getInt("emerald_cost"),
                resultSet.getInt("experience_cost"),
                resultSet.getInt("tech_points_reward"),
                resultSet.getInt("claim_limit_bonus"),
                resultSet.getInt("assistant_slots_bonus"),
                resultSet.getDouble("daily_income_bonus"),
                unlockedPlotTypes
        );
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
     * Record level benefits for a town
     */
    private void recordLevelBenefits(Town town, TownLevel level) {
        try {
            String sql = """
                INSERT OR REPLACE INTO town_level_benefits (id, town_id, level, benefit_type, benefit_value, unlocked_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                String benefitId = UUID.randomUUID().toString();
                String currentTime = java.time.LocalDateTime.now().toString();

                // Record claim limit bonus
                if (level.getClaimLimitBonus() > 0) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, town.getId());
                    statement.setInt(3, level.getLevel());
                    statement.setString(4, "claim_limit_bonus");
                    statement.setString(5, String.valueOf(level.getClaimLimitBonus()));
                    statement.setString(6, currentTime);
                    statement.addBatch();
                }

                // Record assistant slots bonus
                if (level.getAssistantSlotsBonus() > 0) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, town.getId());
                    statement.setInt(3, level.getLevel());
                    statement.setString(4, "assistant_slots_bonus");
                    statement.setString(5, String.valueOf(level.getAssistantSlotsBonus()));
                    statement.setString(6, currentTime);
                    statement.addBatch();
                }

                // Record daily income bonus
                if (level.getDailyIncomeBonus() > 0) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, town.getId());
                    statement.setInt(3, level.getLevel());
                    statement.setString(4, "daily_income_bonus");
                    statement.setString(5, String.valueOf(level.getDailyIncomeBonus()));
                    statement.setString(6, currentTime);
                    statement.addBatch();
                }

                // Record tech points
                if (level.getTechPointsReward() > 0) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, town.getId());
                    statement.setInt(3, level.getLevel());
                    statement.setString(4, "tech_points_reward");
                    statement.setString(5, String.valueOf(level.getTechPointsReward()));
                    statement.setString(6, currentTime);
                    statement.addBatch();
                }

                // Record unlocked plot types
                for (String plotType : level.getUnlockedPlotTypes()) {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, town.getId());
                    statement.setInt(3, level.getLevel());
                    statement.setString(4, "unlocked_plot_type");
                    statement.setString(5, plotType);
                    statement.setString(6, currentTime);
                    statement.addBatch();
                }

                statement.executeBatch();
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to record level benefits for town " + town.getName() + ": " + e.getMessage(), e);
        }
    }
}