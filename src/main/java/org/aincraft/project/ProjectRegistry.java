package org.aincraft.project;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.GuildsPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Singleton
public class ProjectRegistry {

    private final GuildsPlugin plugin;
    private final Map<String, ProjectDefinition> projects = new ConcurrentHashMap<>();
    private int poolSize = 3;
    private int expirationCheckInterval = 60;

    @Inject
    public ProjectRegistry(GuildsPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        loadFromConfig();
    }

    public void loadFromConfig() {
        projects.clear();

        ConfigurationSection projectsSection = plugin.getConfig().getConfigurationSection("projects");
        if (projectsSection == null) {
            plugin.getLogger().info("No projects section found in config, using defaults");
            loadDefaultProjects();
            return;
        }

        poolSize = projectsSection.getInt("pool-size", 3);
        expirationCheckInterval = projectsSection.getInt("expiration-check-interval", 60);

        ConfigurationSection definitionsSection = projectsSection.getConfigurationSection("definitions");
        if (definitionsSection == null) {
            plugin.getLogger().info("No project definitions found, using defaults");
            loadDefaultProjects();
            return;
        }

        for (String projectId : definitionsSection.getKeys(false)) {
            ConfigurationSection projectSection = definitionsSection.getConfigurationSection(projectId);
            if (projectSection == null) continue;

            try {
                ProjectDefinition definition = parseProjectDefinition(projectId, projectSection);
                projects.put(projectId, definition);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to parse project: " + projectId, e);
            }
        }

        plugin.getLogger().info("Loaded " + projects.size() + " project definitions");
    }

    private ProjectDefinition parseProjectDefinition(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        String description = section.getString("description", "");
        int requiredLevel = section.getInt("required-level", 1);

        // Parse buff
        ConfigurationSection buffSection = section.getConfigurationSection("buff");
        BuffDefinition buff = parseBuffDefinition(buffSection);
        BuffType buffType = BuffType.valueOf(section.getString("buff.type", "ECONOMY").toUpperCase());

        // Parse duration
        String durationStr = section.getString("buff.duration", "7d");
        long buffDurationMillis = parseDuration(durationStr);

        // Parse quests
        List<QuestRequirement> quests = new ArrayList<>();
        List<?> questList = section.getList("quests");
        if (questList != null) {
            int questIndex = 0;
            for (Object questObj : questList) {
                if (questObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> questMap = (Map<String, Object>) questObj;
                    QuestRequirement quest = parseQuestRequirement(id + "_quest_" + questIndex, questMap);
                    if (quest != null) {
                        quests.add(quest);
                        questIndex++;
                    }
                }
            }
        }

        // Parse materials
        Map<Material, Integer> materials = new HashMap<>();
        ConfigurationSection materialsSection = section.getConfigurationSection("materials");
        if (materialsSection != null) {
            for (String materialName : materialsSection.getKeys(false)) {
                try {
                    Material material = Material.valueOf(materialName.toUpperCase());
                    int amount = materialsSection.getInt(materialName, 1);
                    materials.put(material, amount);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in project " + id + ": " + materialName);
                }
            }
        }

        return new ProjectDefinition(id, name, description, requiredLevel, buffType, buff, quests, materials, buffDurationMillis);
    }

    private BuffDefinition parseBuffDefinition(ConfigurationSection section) {
        if (section == null) {
            return new BuffDefinition(BuffCategory.XP_MULTIPLIER, 1.0, "Unknown Buff");
        }

        BuffCategory category = BuffCategory.valueOf(section.getString("category", "XP_MULTIPLIER").toUpperCase());
        double value = section.getDouble("value", 1.0);
        String displayName = section.getString("display-name", category.name());

        return new BuffDefinition(category, value, displayName);
    }

    private QuestRequirement parseQuestRequirement(String id, Map<String, Object> map) {
        try {
            QuestType type = QuestType.valueOf(String.valueOf(map.get("type")).toUpperCase());
            String target = String.valueOf(map.get("target"));
            long count = ((Number) map.get("count")).longValue();
            String description = String.valueOf(map.getOrDefault("description", type + " " + target));

            return new QuestRequirement(id, type, target.toUpperCase(), count, description);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse quest: " + map);
            return null;
        }
    }

    private long parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return TimeUnit.DAYS.toMillis(7);
        }

        duration = duration.toLowerCase().trim();
        long value;

        try {
            if (duration.endsWith("d")) {
                value = Long.parseLong(duration.substring(0, duration.length() - 1));
                return TimeUnit.DAYS.toMillis(value);
            } else if (duration.endsWith("h")) {
                value = Long.parseLong(duration.substring(0, duration.length() - 1));
                return TimeUnit.HOURS.toMillis(value);
            } else if (duration.endsWith("m")) {
                value = Long.parseLong(duration.substring(0, duration.length() - 1));
                return TimeUnit.MINUTES.toMillis(value);
            } else {
                return Long.parseLong(duration);
            }
        } catch (NumberFormatException e) {
            return TimeUnit.DAYS.toMillis(7);
        }
    }

    private void loadDefaultProjects() {
        // XP Boost I - starter project
        projects.put("xp_boost_i", new ProjectDefinition(
                "xp_boost_i",
                "Experience Boost I",
                "Increases guild XP gain by 25%",
                1,
                BuffType.ECONOMY,
                new BuffDefinition(BuffCategory.XP_MULTIPLIER, 1.25, "25% XP Boost"),
                List.of(
                        new QuestRequirement("xp1_kill_zombies", QuestType.KILL_MOB, "ZOMBIE", 500, "Kill 500 Zombies"),
                        new QuestRequirement("xp1_mine_coal", QuestType.MINE_BLOCK, "COAL_ORE", 1000, "Mine 1000 Coal Ore")
                ),
                Map.of(Material.IRON_INGOT, 256, Material.COAL, 512),
                TimeUnit.DAYS.toMillis(7)
        ));

        // Fertile Lands - territory buff
        projects.put("fertile_lands", new ProjectDefinition(
                "fertile_lands",
                "Fertile Lands",
                "Crops grow 50% faster in territory",
                15,
                BuffType.TERRITORY,
                new BuffDefinition(BuffCategory.CROP_GROWTH_SPEED, 1.5, "50% Faster Crops"),
                List.of(
                        new QuestRequirement("fertile_collect_wheat", QuestType.COLLECT_ITEM, "WHEAT", 2000, "Collect 2000 Wheat"),
                        new QuestRequirement("fertile_breed_cows", QuestType.BREED_MOB, "COW", 100, "Breed 100 Cows")
                ),
                Map.of(Material.BONE_MEAL, 512, Material.WHEAT_SEEDS, 256),
                TimeUnit.DAYS.toMillis(3)
        ));

        // Guardian Shield - protection buff
        projects.put("guardian_shield", new ProjectDefinition(
                "guardian_shield",
                "Guardian's Shield",
                "Members take 15% less damage in territory",
                40,
                BuffType.TERRITORY,
                new BuffDefinition(BuffCategory.PROTECTION_BOOST, 0.85, "15% Damage Reduction"),
                List.of(
                        new QuestRequirement("shield_kill_skeletons", QuestType.KILL_MOB, "WITHER_SKELETON", 200, "Kill 200 Wither Skeletons"),
                        new QuestRequirement("shield_playtime", QuestType.PLAYTIME_HOURS, "HOURS", 50, "50 hours of guild playtime")
                ),
                Map.of(Material.DIAMOND, 128, Material.OBSIDIAN, 256, Material.BLAZE_ROD, 64),
                TimeUnit.DAYS.toMillis(5)
        ));

        plugin.getLogger().info("Loaded " + projects.size() + " default project definitions");
    }

    public Optional<ProjectDefinition> getProject(String id) {
        return Optional.ofNullable(projects.get(id));
    }

    public Collection<ProjectDefinition> getAllProjects() {
        return Collections.unmodifiableCollection(projects.values());
    }

    public List<ProjectDefinition> getProjectsForLevel(int level) {
        return projects.values().stream()
                .filter(p -> p.requiredLevel() <= level)
                .toList();
    }

    public int getPoolSize() {
        return poolSize;
    }

    public int getExpirationCheckInterval() {
        return expirationCheckInterval;
    }

    public void reload() {
        loadFromConfig();
    }
}
