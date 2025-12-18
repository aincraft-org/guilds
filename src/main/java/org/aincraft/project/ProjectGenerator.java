package org.aincraft.project;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.project.llm.LLMProjectTextService;
import org.aincraft.project.llm.ProjectText;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Procedurally generates random project definitions for guilds.
 * Generation is seeded by guildId + current day, ensuring each guild
 * gets the same projects for 24 hours before they refresh.
 *
 * Projects contain materials with level-based filtering and rarity weights.
 * Buff values scale based on total material cost.
 */
@Singleton
public class ProjectGenerator {

    private static final String[] ADJECTIVES = {
        "Ancient", "Mystic", "Savage", "Divine", "Shadow",
        "Iron", "Golden", "Eternal", "Primal", "Arcane",
        "Radiant", "Spectral", "Infernal", "Celestial", "Twilight"
    };

    private static final String[] NOUNS = {
        "Hunt", "Harvest", "Conquest", "Expedition", "Crusade",
        "Endeavor", "Quest", "Trial", "Challenge", "Venture",
        "Ascension", "Dominion", "Awakening", "Siege", "Odyssey"
    };

    private static final BuffTemplate[] BUFF_TEMPLATES = {
        new BuffTemplate("XP_MULTIPLIER", 1.1, 1.5, "XP Boost", BuffType.GLOBAL),
        new BuffTemplate("DAMAGE_BOOST", 1.05, 1.25, "Damage Boost", BuffType.TERRITORY),
        new BuffTemplate("PROTECTION_BOOST", 0.85, 0.95, "Damage Reduction", BuffType.TERRITORY),
        new BuffTemplate("CROP_GROWTH_SPEED", 1.25, 1.75, "Crop Growth", BuffType.TERRITORY),
        new BuffTemplate("MINING_SPEED", 1.1, 1.3, "Mining Speed", BuffType.TERRITORY),
    };

    private static final MaterialTemplate[] MATERIAL_TEMPLATES = {
        // Common (level 1+)
        new MaterialTemplate(Material.COAL, 128, 512, 1, 1),
        new MaterialTemplate(Material.IRON_INGOT, 64, 256, 1, 1),
        new MaterialTemplate(Material.COPPER_INGOT, 64, 256, 1, 1),
        new MaterialTemplate(Material.COBBLESTONE, 256, 1024, 1, 1),

        // Uncommon (level 10+)
        new MaterialTemplate(Material.GOLD_INGOT, 32, 128, 10, 2),
        new MaterialTemplate(Material.REDSTONE, 64, 256, 10, 2),
        new MaterialTemplate(Material.LAPIS_LAZULI, 32, 128, 10, 2),

        // Rare (level 25+)
        new MaterialTemplate(Material.DIAMOND, 8, 32, 25, 5),
        new MaterialTemplate(Material.EMERALD, 16, 64, 25, 5),

        // Epic (level 50+)
        new MaterialTemplate(Material.NETHERITE_INGOT, 1, 4, 50, 10),
    };

    private final Logger logger;
    private final LLMProjectTextService llmTextService;

    @Inject
    public ProjectGenerator(
            @com.google.inject.name.Named("guilds") Logger logger,
            LLMProjectTextService llmTextService) {
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
        this.llmTextService = Objects.requireNonNull(llmTextService, "LLM text service cannot be null");
    }

    /**
     * Generates a list of random projects for a guild.
     * The same guild will receive the same projects for the pool period.
     *
     * @param guildId the guild ID
     * @param guildLevel the guild's current level (used for filtering by required level)
     * @param count the number of projects to generate (typically 3-5)
     * @param poolTimestamp timestamp of when this pool should be generated (for seeding)
     * @return a list of generated project definitions
     */
    public List<ProjectDefinition> generateProjects(String guildId, int guildLevel, int count, long poolTimestamp) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }

        // Seed generation: guildId + pool day ensures same projects for pool period
        long poolDaySeed = poolTimestamp / 86400000L;
        long seed = guildId.hashCode() + poolDaySeed;
        Random random = new Random(seed);

        List<ProjectDefinition> projects = new ArrayList<>();
        Set<String> usedBuffs = new HashSet<>();

        for (int i = 0; i < count; i++) {
            ProjectDefinition project = generateSingleProject(random, guildLevel, i, poolDaySeed, usedBuffs);
            projects.add(project);
        }

        logger.fine("Generated " + projects.size() + " projects for guild " + guildId);
        return projects;
    }

    /**
     * Generates a single random project definition.
     */
    private ProjectDefinition generateSingleProject(
            Random random, int guildLevel, int projectIndex,
            long daySeed, Set<String> usedBuffs) {

        String id = generateProjectId(projectIndex, daySeed);
        BuffTemplate buffTemplate = selectBuffTemplate(random, usedBuffs);

        // Try to get LLM-generated name/description
        ProjectText llmText = llmTextService.getProjectText(buffTemplate.type);
        String name;
        String description;

        if (llmText != null) {
            name = llmText.name();
            description = llmText.description();
        } else {
            // Fallback to heuristic generation
            name = generateProjectName(random);
            description = "Gather materials for this project";
        }

        Map<Material, Integer> materials = generateMaterials(random, guildLevel);
        int totalCost = calculateTotalCost(materials);
        double buffValue = generateBuffValue(random, buffTemplate, totalCost);
        BuffDefinition buff = createBuffDefinition(buffTemplate, buffValue);
        long durationMillis = generateDuration(random);

        return new ProjectDefinition(
            id, name, description, 1,
            buffTemplate.type, buff, List.of(), materials, durationMillis
        );
    }

    /**
     * Generates a unique project ID based on project index and day seed.
     */
    private String generateProjectId(int projectIndex, long daySeed) {
        return "generated_" + projectIndex + "_" + daySeed;
    }

    /**
     * Generates a random project name by combining adjective + noun.
     */
    private String generateProjectName(Random random) {
        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[random.nextInt(NOUNS.length)];
        return adjective + " " + noun;
    }

    /**
     * Selects a buff template, avoiding duplicates in the current pool.
     */
    private BuffTemplate selectBuffTemplate(Random random, Set<String> usedBuffs) {
        BuffTemplate selected;
        int attempts = 0;
        do {
            selected = BUFF_TEMPLATES[random.nextInt(BUFF_TEMPLATES.length)];
            attempts++;
        } while (usedBuffs.contains(selected.categoryId) &&
                 usedBuffs.size() < BUFF_TEMPLATES.length &&
                 attempts < 10);

        usedBuffs.add(selected.categoryId);
        return selected;
    }

    /**
     * Generates a buff value scaled by total material cost.
     * Higher cost projects produce stronger buffs within the template's range.
     *
     * @param random RNG source
     * @param template buff template
     * @param totalCost sum of (amount * rarityWeight) for all materials
     * @return scaled buff value between template min and max
     */
    private double generateBuffValue(Random random, BuffTemplate template, int totalCost) {
        // Scale cost to 0-1 range (assume 1000 is "max" cost for scaling)
        double costFactor = Math.min(1.0, totalCost / 1000.0);

        // Blend: 60% based on cost, 40% random variation
        double value = template.minValue + (template.maxValue - template.minValue) * (0.6 * costFactor + 0.4 * random.nextDouble());

        // Round to 2 decimals
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Creates a buff definition with formatted display name.
     */
    private BuffDefinition createBuffDefinition(BuffTemplate template, double value) {
        String displayName;
        if (template.categoryId.equals("PROTECTION_BOOST")) {
            // For damage reduction, show as percentage reduction (not multiplier)
            displayName = String.format("%.0f%% %s", (1 - value) * 100, template.displayName);
        } else {
            // For other buffs, show as percentage increase
            displayName = String.format("%.0f%% %s", (value - 1) * 100, template.displayName);
        }
        return new BuffDefinition(template.categoryId, value, displayName);
    }

    /**
     * Generates 1-3 random material requirements, filtered by guild level.
     * Materials with minLevel > guildLevel are excluded.
     * Amounts are scaled by guild level (+5% per level).
     *
     * @param random RNG source
     * @param guildLevel current guild level
     * @return map of materials and their amounts
     */
    private Map<Material, Integer> generateMaterials(Random random, int guildLevel) {
        // Filter materials accessible at this guild level
        List<MaterialTemplate> availableMaterials = new ArrayList<>();
        for (MaterialTemplate mat : MATERIAL_TEMPLATES) {
            if (mat.minLevel <= guildLevel) {
                availableMaterials.add(mat);
            }
        }

        // If no materials available (shouldn't happen at level 1+), use common ones
        if (availableMaterials.isEmpty()) {
            availableMaterials.add(MATERIAL_TEMPLATES[0]);
        }

        int materialCount = 1 + random.nextInt(3); // 1-3 materials
        Map<Material, Integer> materials = new LinkedHashMap<>();

        for (int i = 0; i < materialCount; i++) {
            MaterialTemplate matTemplate = availableMaterials.get(random.nextInt(availableMaterials.size()));

            // Only add if not already present (avoid duplicates)
            if (!materials.containsKey(matTemplate.material)) {
                int baseAmount = matTemplate.minAmount + random.nextInt(matTemplate.maxAmount - matTemplate.minAmount);
                // Scale by guild level: +5% per level
                int scaledAmount = (int) (baseAmount * (1.0 + guildLevel * 0.05));
                // Round to stack size (32)
                scaledAmount = (scaledAmount / 32) * 32;
                materials.put(matTemplate.material, Math.max(32, scaledAmount));
            }
        }

        return materials;
    }

    /**
     * Calculates total cost of materials based on amounts and rarity weights.
     * Cost = sum of (amount * rarityWeight) for each material.
     *
     * @param materials map of materials and amounts
     * @return total cost value
     */
    private int calculateTotalCost(Map<Material, Integer> materials) {
        int totalCost = 0;

        for (Map.Entry<Material, Integer> entry : materials.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();

            // Find the rarity weight for this material
            for (MaterialTemplate template : MATERIAL_TEMPLATES) {
                if (template.material == material) {
                    totalCost += amount * template.rarityWeight;
                    break;
                }
            }
        }

        return totalCost;
    }

    /**
     * Generates a random buff duration (1-7 days).
     */
    private long generateDuration(Random random) {
        long durationDays = 1 + random.nextInt(7); // 1-7 days
        return TimeUnit.DAYS.toMillis(durationDays);
    }

    /**
     * Template record for buff configurations.
     */
    private record BuffTemplate(
            String categoryId,
            double minValue,
            double maxValue,
            String displayName,
            BuffType type
    ) {}

    /**
     * Template record for material configurations.
     * Includes level tiering and rarity-based cost calculation.
     */
    private record MaterialTemplate(
            Material material,
            int minAmount,
            int maxAmount,
            int minLevel,
            int rarityWeight
    ) {}
}
