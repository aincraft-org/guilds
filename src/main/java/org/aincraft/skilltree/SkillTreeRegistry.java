package org.aincraft.skilltree;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.GuildsPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Registry for skill tree definitions loaded from config.
 * Manages skill definitions and provides query methods.
 * Follows the singleton pattern with dependency injection.
 */
@Singleton
public class SkillTreeRegistry {

    private final GuildsPlugin plugin;
    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();
    private final Map<SkillBranch, List<SkillDefinition>> branchSkills = new ConcurrentHashMap<>();

    // Config values
    private int spPerLevel = 1;
    private boolean respecEnabled = true;
    private Material respecMaterial = Material.NETHER_STAR;
    private int respecAmount = 1;

    @Inject
    public SkillTreeRegistry(GuildsPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        loadFromConfig();
    }

    /**
     * Loads all skill definitions from config.yml.
     * Falls back to default skills if config section is missing or invalid.
     */
    public void loadFromConfig() {
        skills.clear();
        branchSkills.clear();

        ConfigurationSection skillTreeSection = plugin.getConfig().getConfigurationSection("skill-tree");
        if (skillTreeSection == null) {
            plugin.getLogger().info("No skill-tree section found in config, using defaults");
            loadDefaultSkills();
            return;
        }

        // Load general settings
        spPerLevel = skillTreeSection.getInt("sp-per-level", 1);

        // Load respec settings
        ConfigurationSection respecSection = skillTreeSection.getConfigurationSection("respec");
        if (respecSection != null) {
            respecEnabled = respecSection.getBoolean("enabled", true);
            String materialName = respecSection.getString("material", "NETHER_STAR");
            try {
                respecMaterial = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid respec material: " + materialName + ", using NETHER_STAR");
                respecMaterial = Material.NETHER_STAR;
            }
            respecAmount = respecSection.getInt("amount", 1);
        }

        // Load skill definitions
        ConfigurationSection skillsSection = skillTreeSection.getConfigurationSection("skills");
        if (skillsSection == null) {
            plugin.getLogger().info("No skill definitions found, using defaults");
            loadDefaultSkills();
            return;
        }

        for (String skillId : skillsSection.getKeys(false)) {
            ConfigurationSection skillSection = skillsSection.getConfigurationSection(skillId);
            if (skillSection == null) continue;

            try {
                SkillDefinition definition = parseSkillDefinition(skillId, skillSection);
                skills.put(skillId, definition);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to parse skill: " + skillId, e);
            }
        }

        // Build branch index
        buildBranchIndex();

        plugin.getLogger().info("Loaded " + skills.size() + " skill definitions");
    }

    /**
     * Parses a skill definition from a config section.
     *
     * @param id the skill ID
     * @param section the config section containing skill data
     * @return parsed SkillDefinition
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    private SkillDefinition parseSkillDefinition(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        String description = section.getString("description", "");

        // Parse branch
        String branchStr = section.getString("branch", "ECONOMY").toUpperCase();
        SkillBranch branch;
        try {
            branch = SkillBranch.valueOf(branchStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid branch: " + branchStr);
        }

        int tier = section.getInt("tier", 1);
        int spCost = section.getInt("sp-cost", 1);

        // Parse prerequisites
        List<String> prerequisites = section.getStringList("prerequisites");

        // Parse effect
        ConfigurationSection effectSection = section.getConfigurationSection("effect");
        SkillEffect effect = parseSkillEffect(effectSection);

        return new SkillDefinition(id, name, description, branch, tier, spCost, prerequisites, effect);
    }

    /**
     * Parses a skill effect from a config section.
     * Returns a default XP_MULTIPLIER effect if section is null.
     *
     * @param section the config section containing effect data
     * @return parsed SkillEffect
     */
    private SkillEffect parseSkillEffect(ConfigurationSection section) {
        if (section == null) {
            return new SkillEffect("XP_MULTIPLIER", 0.05, "5% XP Boost");
        }

        String category = section.getString("category", "XP_MULTIPLIER").toUpperCase();
        double value = section.getDouble("value", 0.05);
        String displayName = section.getString("display-name", category);

        return new SkillEffect(category, value, displayName);
    }

    /**
     * Builds the branch index for efficient skill lookup by branch.
     */
    private void buildBranchIndex() {
        for (SkillBranch branch : SkillBranch.values()) {
            List<SkillDefinition> branchList = skills.values().stream()
                    .filter(s -> s.branch() == branch)
                    .sorted(Comparator.comparingInt(SkillDefinition::tier))
                    .collect(Collectors.toList());
            branchSkills.put(branch, branchList);
        }
    }

    /**
     * Loads default skill definitions if none are found in config.
     */
    private void loadDefaultSkills() {
        // Economy branch defaults
        registerSkill(new SkillDefinition(
                "eco_xp_1", "XP Insight I", "Increases guild XP gain by 5%",
                SkillBranch.ECONOMY, 1, 1, List.of(),
                new SkillEffect("XP_MULTIPLIER", 0.05, "5% XP Boost")
        ));
        registerSkill(new SkillDefinition(
                "eco_xp_2", "XP Insight II", "Increases guild XP gain by 10%",
                SkillBranch.ECONOMY, 2, 2, List.of("eco_xp_1"),
                new SkillEffect("XP_MULTIPLIER", 0.10, "10% XP Boost")
        ));
        registerSkill(new SkillDefinition(
                "eco_luck_1", "Fortune's Favor I", "Increases luck bonus by 5%",
                SkillBranch.ECONOMY, 3, 2, List.of("eco_xp_2"),
                new SkillEffect("LUCK_BONUS", 0.05, "5% Luck Bonus")
        ));

        // Territory branch defaults
        registerSkill(new SkillDefinition(
                "terr_crop_1", "Green Thumb I", "Crops grow 10% faster in territory",
                SkillBranch.TERRITORY, 1, 1, List.of(),
                new SkillEffect("CROP_GROWTH_SPEED", 0.10, "10% Crop Growth")
        ));
        registerSkill(new SkillDefinition(
                "terr_crop_2", "Green Thumb II", "Crops grow 20% faster in territory",
                SkillBranch.TERRITORY, 2, 2, List.of("terr_crop_1"),
                new SkillEffect("CROP_GROWTH_SPEED", 0.20, "20% Crop Growth")
        ));
        registerSkill(new SkillDefinition(
                "terr_spawn_1", "Wild Growth I", "Mob spawns increased by 10% in territory",
                SkillBranch.TERRITORY, 3, 2, List.of("terr_crop_2"),
                new SkillEffect("MOB_SPAWN_RATE", 0.10, "10% Mob Spawns")
        ));

        // Combat branch defaults
        registerSkill(new SkillDefinition(
                "combat_prot_1", "Iron Will I", "Take 5% less damage in territory",
                SkillBranch.COMBAT, 1, 1, List.of(),
                new SkillEffect("PROTECTION_BOOST", 0.05, "5% Damage Reduction")
        ));
        registerSkill(new SkillDefinition(
                "combat_prot_2", "Iron Will II", "Take 10% less damage in territory",
                SkillBranch.COMBAT, 2, 2, List.of("combat_prot_1"),
                new SkillEffect("PROTECTION_BOOST", 0.10, "10% Damage Reduction")
        ));
        registerSkill(new SkillDefinition(
                "combat_dmg_1", "Battle Fury I", "Deal 5% more damage in territory",
                SkillBranch.COMBAT, 3, 2, List.of("combat_prot_2"),
                new SkillEffect("DAMAGE_BOOST", 0.05, "5% Damage Boost")
        ));

        buildBranchIndex();
        plugin.getLogger().info("Loaded " + skills.size() + " default skill definitions");
    }

    /**
     * Registers a skill in the registry.
     * Internal helper for loading default skills.
     *
     * @param skill the skill definition to register
     */
    private void registerSkill(SkillDefinition skill) {
        skills.put(skill.id(), skill);
    }

    // Query methods

    /**
     * Gets a skill definition by ID.
     *
     * @param id the skill ID
     * @return optional containing the skill if found
     */
    public Optional<SkillDefinition> getSkill(String id) {
        return Optional.ofNullable(skills.get(id));
    }

    /**
     * Gets all loaded skill definitions.
     *
     * @return unmodifiable collection of all skills
     */
    public Collection<SkillDefinition> getAllSkills() {
        return Collections.unmodifiableCollection(skills.values());
    }

    /**
     * Gets all skills in a specific branch, sorted by tier.
     *
     * @param branch the skill branch
     * @return unmodifiable list of skills in the branch
     */
    public List<SkillDefinition> getSkillsForBranch(SkillBranch branch) {
        return Collections.unmodifiableList(branchSkills.getOrDefault(branch, List.of()));
    }

    /**
     * Gets the maximum tier level for a branch.
     *
     * @param branch the skill branch
     * @return the highest tier in the branch, or 0 if branch is empty
     */
    public int getMaxTier(SkillBranch branch) {
        return branchSkills.getOrDefault(branch, List.of()).stream()
                .mapToInt(SkillDefinition::tier)
                .max()
                .orElse(0);
    }

    /**
     * Gets the skill points gained per guild level.
     *
     * @return skill points per level
     */
    public int getSpPerLevel() {
        return spPerLevel;
    }

    /**
     * Checks if guild members can respec their skill trees.
     *
     * @return true if respec is enabled
     */
    public boolean isRespecEnabled() {
        return respecEnabled;
    }

    /**
     * Gets the material cost item for respec.
     *
     * @return the respec material
     */
    public Material getRespecMaterial() {
        return respecMaterial;
    }

    /**
     * Gets the amount of respec material required per respec.
     *
     * @return the respec cost amount
     */
    public int getRespecAmount() {
        return respecAmount;
    }

    /**
     * Reloads all skill definitions from config.
     * Used for plugin reload commands.
     */
    public void reload() {
        loadFromConfig();
    }
}
