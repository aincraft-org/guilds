package org.aincraft.towny.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.config.model.LevelDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads town level definitions from {@code town_levels} in config.yml.
 *
 * <p>Expected layout:</p>
 * <pre>
 * town_levels:
 *   max_level: 150
 *   levels:
 *     2:
 *       requirements:
 *         DIAMOND: 50
 *       benefits:
 *         tech_points: 1
 *         claim_limit_bonus: 2
 *         assistant_slots_bonus: 0
 *         daily_income_bonus: 0.5
 *         unlocked_plot_types: []
 * </pre>
 */
@Singleton
public class TownLevelConfigLoader {

    private static final String CONFIG_SECTION = "town_levels";
    private static final String LEVELS_SECTION = CONFIG_SECTION + ".levels";

    private final TownyPlugin plugin;
    private final Map<Integer, LevelDefinition> levelDefinitions = new LinkedHashMap<>();
    private int maxLevel = 150;

    @Inject
    public TownLevelConfigLoader(TownyPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load (or reload) level definitions from the plugin's config.yml.
     */
    public void loadConfiguration() {
        levelDefinitions.clear();

        YamlConfiguration config = (YamlConfiguration) plugin.getConfig();
        maxLevel = config.getInt(CONFIG_SECTION + ".max_level", 150);
        if (maxLevel < 1) {
            maxLevel = 150;
        }

        ConfigurationSection levelsSection = config.getConfigurationSection(LEVELS_SECTION);
        if (levelsSection == null) {
            plugin.getLogger().warning("No '" + LEVELS_SECTION + "' section found in config.yml");
            return;
        }

        for (String levelKey : levelsSection.getKeys(false)) {
            try {
                int level = Integer.parseInt(levelKey);
                ConfigurationSection levelSection = levelsSection.getConfigurationSection(levelKey);
                if (levelSection == null) {
                    continue;
                }

                LevelDefinition definition = parseLevel(level, levelSection);
                levelDefinitions.put(level, definition);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Skipping town_levels entry with non-numeric level key: " + levelKey);
            }
        }

        plugin.getLogger().info("Loaded " + levelDefinitions.size() + " town level definitions from config.");
    }

    private LevelDefinition parseLevel(int level, ConfigurationSection levelSection) {
        LevelDefinition definition = new LevelDefinition();
        definition.setLevel(level);

        // requirements
        Map<String, Integer> requirements = new HashMap<>();
        ConfigurationSection requirementsSection = levelSection.getConfigurationSection("requirements");
        if (requirementsSection != null) {
            for (String material : requirementsSection.getKeys(false)) {
                requirements.put(material, requirementsSection.getInt(material, 0));
            }
        }
        definition.setRequirements(requirements);

        // benefits
        ConfigurationSection benefits = levelSection.getConfigurationSection("benefits");
        if (benefits != null) {
            definition.setTechPoints(benefits.getInt("tech_points", 0));
            definition.setClaimLimitBonus(benefits.getInt("claim_limit_bonus", 0));
            definition.setAssistantSlotsBonus(benefits.getInt("assistant_slots_bonus", 0));
            definition.setDailyIncomeBonus(benefits.getDouble("daily_income_bonus", 0.0));
            definition.setUnlockedPlotTypes(new ArrayList<>(benefits.getStringList("unlocked_plot_types")));
        }

        return definition;
    }

    /**
     * @return map of level number to definition, in config ordering
     */
    public Map<Integer, LevelDefinition> getLevelDefinitions() {
        return new LinkedHashMap<>(levelDefinitions);
    }

    /**
     * @return the configured maximum town level
     */
    public int getMaxLevel() {
        return maxLevel;
    }

    /** Directly sets the max level (used on config reload). */
    void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }
}
