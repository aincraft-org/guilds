package org.aincraft.guilds.config;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.models.TechTreeBranch;
import org.aincraft.guilds.models.TechTreeNode;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads tech tree node definitions from techtree.yml.
 */

public class TechTreeConfigLoader {

    private final JavaPlugin plugin;
    private final List<TechTreeNode> nodes = new ArrayList<>();


    public TechTreeConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load (or create) techtree.yml and parse node definitions.
     */
    public void loadConfiguration() {
        nodes.clear();

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, "techtree.yml");

        // Create default config if it doesn't exist
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }

        // Load and parse
        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);

        ConfigurationSection nodesSection = config.getConfigurationSection("nodes");
        if (nodesSection == null) {
            plugin.getLogger().warning("No 'nodes' section found in techtree.yml");
            return;
        }

        for (String nodeId : nodesSection.getKeys(false)) {
            ConfigurationSection nodeSection = nodesSection.getConfigurationSection(nodeId);
            if (nodeSection == null) continue;

            TechTreeNode node = new TechTreeNode(nodeId);
            node.setName(nodeSection.getString("name", nodeId));
            node.setDescription(nodeSection.getString("description", ""));
            node.setBranch(TechTreeBranch.fromString(nodeSection.getString("branch")));
            node.setCost(nodeSection.getInt("cost", 1));
            node.setPrerequisites(nodeSection.getStringList("prerequisites"));
            node.setPositionX(nodeSection.getInt("position-x", 0));
            node.setPositionY(nodeSection.getInt("position-y", 0));

            // Parse effects
            ConfigurationSection effectsSection = nodeSection.getConfigurationSection("effects");
            if (effectsSection != null) {
                Map<String, Object> effects = new HashMap<>();
                for (String effectKey : effectsSection.getKeys(false)) {
                    effects.put(effectKey, effectsSection.get(effectKey));
                }
                node.setEffects(effects);
            }

            nodes.add(node);
        }

        plugin.getLogger().info("Loaded " + nodes.size() + " tech tree nodes from config.");
    }

    public List<TechTreeNode> getNodes() {
        return new ArrayList<>(nodes);
    }

    /**
     * Create a default techtree.yml with 4 branches × 4 nodes each.
     */
    private void createDefaultConfig(File configFile) {
        try (InputStream is = plugin.getResource("techtree.yml")) {
            if (is != null) {
                Files.copy(is, configFile.toPath());
                plugin.getLogger().info("Created default techtree.yml");
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to copy default techtree.yml from jar: " + e.getMessage(), e);
        }

        // Fallback: write directly if resource not found in jar
        try {
            String defaultYaml = buildDefaultYaml();
            Files.writeString(configFile.toPath(), defaultYaml);
            plugin.getLogger().info("Created default techtree.yml (inline)");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create default techtree.yml: " + e.getMessage(), e);
        }
    }

    private String buildDefaultYaml() {
        return """
            # Tech Tree Configuration
            # Each node has: name, description, branch, cost, prerequisites, effects, position-x, position-y
            # Branches: INFRASTRUCTURE, DEFENSE, COMMERCE, CULTURE
            # Position is for the GUI layout (0-indexed, 9 columns per row)

            nodes:
              # ── INFRASTRUCTURE (row 1) ──
              better_storage:
                name: "Better Storage"
                description: "Increases town bank capacity by 50%"
                branch: INFRASTRUCTURE
                cost: 2
                prerequisites: []
                effects:
                  income_bonus: 0.5
                position-x: 1
                position-y: 0

              fast_travel:
                name: "Fast Travel"
                description: "Reduces teleport cooldown to town spawn by 50%"
                branch: INFRASTRUCTURE
                cost: 3
                prerequisites: [better_storage]
                effects:
                  teleport_cooldown_reduction: 0.5
                position-x: 1
                position-y: 1

              advanced_farming:
                name: "Advanced Farming"
                description: "Farm plots within town produce 25% more crops"
                branch: INFRASTRUCTURE
                cost: 3
                prerequisites: [better_storage]
                effects:
                  farm_yield_bonus: 0.25
                position-x: 2
                position-y: 1

              auto_sorter:
                name: "Auto Sorter"
                description: "Unlocks automatic chest sorting in town plots"
                branch: INFRASTRUCTURE
                cost: 5
                prerequisites: [fast_travel, advanced_farming]
                effects:
                  auto_sort: true
                position-x: 1
                position-y: 2

              # ── DEFENSE (row 2) ──
              reinforced_walls:
                name: "Reinforced Walls"
                description: "Enemy TNT damage reduced by 25% in town"
                branch: DEFENSE
                cost: 2
                prerequisites: []
                effects:
                  explosion_damage_reduction: 0.25
                position-x: 4
                position-y: 0

              guard_posts:
                name: "Guard Posts"
                description: "Town border alerts for non-residents"
                branch: DEFENSE
                cost: 3
                prerequisites: [reinforced_walls]
                effects:
                  border_alerts: true
                position-x: 4
                position-y: 1

              siege_shields:
                name: "Siege Shields"
                description: "Town is immune to siege for 1 hour after being attacked"
                branch: DEFENSE
                cost: 4
                prerequisites: [guard_posts]
                effects:
                  siege_immunity_minutes: 60
                position-x: 4
                position-y: 2

              fortification:
                name: "Fortification"
                description: "All defense bonuses doubled while at war"
                branch: DEFENSE
                cost: 6
                prerequisites: [siege_shields]
                effects:
                  war_defense_bonus: 2.0
                position-x: 4
                position-y: 3

              # ── COMMERCE (row 3) ──
              marketplace:
                name: "Marketplace"
                description: "Shop plots have 10% lower transaction fees"
                branch: COMMERCE
                cost: 2
                prerequisites: []
                effects:
                  shop_fee_reduction: 0.1
                position-x: 5
                position-y: 0

              trade_routes:
                name: "Trade Routes"
                description: "Daily town income increased by 15%"
                branch: COMMERCE
                cost: 3
                prerequisites: [marketplace]
                effects:
                  income_bonus: 0.15
                position-x: 6
                position-y: 0

              tax_optimization:
                name: "Tax Optimization"
                description: "Town collects 10% more from resident taxes"
                branch: COMMERCE
                cost: 4
                prerequisites: [trade_routes]
                effects:
                  tax_bonus: 0.1
                position-x: 6
                position-y: 1

              merchant_guild:
                name: "Merchant Guild"
                description: "Residents can set up NPC merchants in shop plots"
                branch: COMMERCE
                cost: 6
                prerequisites: [tax_optimization]
                effects:
                  npc_merchants: true
                  income_bonus: 0.2
                position-x: 6
                position-y: 2

              # ── CULTURE (row 4) ──
              town_banner:
                name: "Town Banner"
                description: "Customizable town banner displayed at spawn"
                branch: CULTURE
                cost: 1
                prerequisites: []
                effects:
                  custom_banner: true
                position-x: 7
                position-y: 0

              broadcast_tower:
                name: "Broadcast Tower"
                description: "Town-wide announcements reach all residents instantly"
                branch: CULTURE
                cost: 3
                prerequisites: [town_banner]
                effects:
                  broadcast_range: "global"
                position-x: 7
                position-y: 1

              town_hall:
                name: "Town Hall"
                description: "Unlocks town hall plot type with meeting room features"
                branch: CULTURE
                cost: 4
                prerequisites: [broadcast_tower]
                effects:
                  town_hall: true
                position-x: 7
                position-y: 2

              monument:
                name: "Monument"
                description: "Grants +2 claim limit and boosts resident morale"
                branch: CULTURE
                cost: 6
                prerequisites: [town_hall]
                effects:
                  extra_claims: 2
                  morale_bonus: true
                position-x: 7
                position-y: 3
            """;
    }
}
