package org.aincraft.skilltree;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.GuildsPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Registry for skill definitions and skill tree configuration.
 * Loads skills from config.yml and provides querying capabilities.
 * Single Responsibility: Skill configuration management and loading.
 */
@Singleton
public class SkillTreeRegistry {
    private final GuildsPlugin plugin;
    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();
    private int spPerLevel = 1;
    private RespecConfig respecConfig = new RespecConfig(false, null, 0);

    @Inject
    public SkillTreeRegistry(GuildsPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        loadFromConfig();
    }

    /**
     * Loads skills from the config.yml file.
     * Resets existing skills and reloads from scratch.
     */
    public void loadFromConfig() {
        skills.clear();

        ConfigurationSection skillTreeSection = plugin.getConfig().getConfigurationSection("skill-tree");
        if (skillTreeSection == null) {
            plugin.getLogger().info("No skill-tree section found in config");
            return;
        }

        // Load skill points per level
        spPerLevel = skillTreeSection.getInt("sp-per-level", 1);

        // Load respec configuration
        ConfigurationSection respecSection = skillTreeSection.getConfigurationSection("respec");
        if (respecSection != null) {
            boolean enabled = respecSection.getBoolean("enabled", false);
            if (enabled) {
                String materialName = respecSection.getString("material", "EMERALD");
                int amount = respecSection.getInt("amount", 64);

                try {
                    Material material = Material.valueOf(materialName.toUpperCase());
                    respecConfig = new RespecConfig(true, material, amount);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid respec material: " + materialName);
                }
            }
        }

        // Load skill definitions
        ConfigurationSection skillsSection = skillTreeSection.getConfigurationSection("skills");
        if (skillsSection == null) {
            plugin.getLogger().info("No skills section found in skill-tree config");
            return;
        }

        for (String skillId : skillsSection.getKeys(false)) {
            ConfigurationSection skillSection = skillsSection.getConfigurationSection(skillId);
            if (skillSection == null) continue;

            try {
                SkillDefinition skill = parseSkillDefinition(skillId, skillSection);
                skills.put(skillId, skill);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to parse skill: " + skillId, e);
            }
        }

        // Validate prerequisite chains
        if (!validatePrerequisites()) {
            plugin.getLogger().warning("Skill tree has invalid prerequisite chains");
        }

        plugin.getLogger().info("Loaded " + skills.size() + " skill definitions");
    }

    /**
     * Parses a skill definition from config section.
     *
     * @param skillId the skill ID
     * @param section the configuration section
     * @return the parsed skill definition
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    private SkillDefinition parseSkillDefinition(String skillId, ConfigurationSection section) {
        String name = section.getString("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name is required");
        }

        String description = section.getString("description", "");
        String branchStr = section.getString("branch", "ECONOMY").toUpperCase();
        SkillBranch branch = SkillBranch.valueOf(branchStr);
        int spCost = section.getInt("sp-cost", 1);

        if (spCost < 0) {
            throw new IllegalArgumentException("SP cost cannot be negative");
        }

        // Parse prerequisites
        List<String> prerequisites = new ArrayList<>();
        List<?> preqList = section.getList("prerequisites");
        if (preqList != null) {
            for (Object obj : preqList) {
                if (obj instanceof String) {
                    prerequisites.add((String) obj);
                }
            }
        }

        // Parse effect
        ConfigurationSection effectSection = section.getConfigurationSection("effect");
        if (effectSection == null) {
            throw new IllegalArgumentException("Skill must have an effect section");
        }

        String category = effectSection.getString("category");
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Effect must specify a category");
        }

        double value = effectSection.getDouble("value", 0.0);
        String displayName = effectSection.getString("display-name", category);

        SkillEffect effect = new SkillEffect(category, value, displayName);
        return new SkillDefinition(skillId, name, description, branch, spCost, prerequisites, effect);
    }

    /**
     * Validates that all prerequisites form valid chains (no circular dependencies).
     *
     * @return true if all prerequisite chains are valid
     */
    private boolean validatePrerequisites() {
        for (SkillDefinition skill : skills.values()) {
            if (skill.hasPrerequisites()) {
                if (!validateChain(skill.id(), new HashSet<>())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Validates a prerequisite chain for circular dependencies.
     *
     * @param skillId the current skill ID
     * @param visited set of already visited skill IDs
     * @return true if the chain is valid
     */
    private boolean validateChain(String skillId, Set<String> visited) {
        if (visited.contains(skillId)) {
            return false; // Circular dependency detected
        }

        SkillDefinition skill = skills.get(skillId);
        if (skill == null) {
            return false; // Prerequisite skill not found
        }

        if (!skill.hasPrerequisites()) {
            return true; // No prerequisites, chain is valid
        }

        visited.add(skillId);
        for (String prereqId : skill.prerequisites()) {
            if (!validateChain(prereqId, new HashSet<>(visited))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Retrieves a skill definition by ID.
     *
     * @param id the skill ID
     * @return the skill definition if found
     */
    public Optional<SkillDefinition> getSkill(String id) {
        Objects.requireNonNull(id, "Skill ID cannot be null");
        return Optional.ofNullable(skills.get(id));
    }

    /**
     * Retrieves all skill definitions.
     *
     * @return unmodifiable collection of all skills
     */
    public Collection<SkillDefinition> getAllSkills() {
        return Collections.unmodifiableCollection(skills.values());
    }

    /**
     * Retrieves all skills in a specific branch.
     *
     * @param branch the skill branch
     * @return list of skills in the branch
     */
    public List<SkillDefinition> getSkillsByBranch(SkillBranch branch) {
        Objects.requireNonNull(branch, "Branch cannot be null");
        List<SkillDefinition> result = new ArrayList<>();

        for (SkillDefinition skill : skills.values()) {
            if (skill.branch() == branch) {
                result.add(skill);
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Gets the number of skill points awarded per guild level.
     *
     * @return skill points per level
     */
    public int getSpPerLevel() {
        return spPerLevel;
    }

    /**
     * Gets the respec configuration.
     *
     * @return respec configuration
     */
    public RespecConfig getRespecConfig() {
        return respecConfig;
    }

    /**
     * Checks if a skill exists.
     *
     * @param id the skill ID
     * @return true if the skill is registered
     */
    public boolean hasSkill(String id) {
        return skills.containsKey(id);
    }

    /**
     * Gets the total number of registered skills.
     *
     * @return skill count
     */
    public int getSkillCount() {
        return skills.size();
    }
}
