package org.aincraft.progression;

import com.google.inject.Inject;
import org.bukkit.Material;

import java.util.*;
import java.util.UUID;

/**
 * Generates procedural material costs for guild leveling.
 * Creates unique, deterministic costs for each guild based on guild ID seed.
 * Uses weighted probability selection for material variety.
 */
public class ProceduralCostGenerator {
    private final MaterialRegistry materialRegistry;

    @Inject
    public ProceduralCostGenerator(MaterialRegistry materialRegistry) {
        this.materialRegistry = Objects.requireNonNull(materialRegistry, "Material registry cannot be null");
    }

    /**
     * Generates material costs for a guild leveling up.
     * Uses guild ID + level as seed for deterministic randomness.
     * Selects materials using weighted probability.
     *
     * @param guildId the guild ID
     * @param currentLevel the current level (leveling FROM this level)
     * @return the level-up cost
     */
    public LevelUpCost generateCost(UUID guildId, int currentLevel) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        // Create deterministic random based on guild ID + level
        long seed = Objects.hash(guildId, currentLevel);
        Random random = new Random(seed);

        // Determine how many materials (2-6 based on level)
        int materialCount = Math.min(2 + (currentLevel / 15), 6);

        // Get eligible materials for this level
        List<MaterialDefinition> eligibleMaterials = materialRegistry.getEligibleMaterials(currentLevel);

        if (eligibleMaterials.isEmpty()) {
            // Fallback: no materials eligible
            return new LevelUpCost(Collections.emptyMap());
        }

        // Select materials using weighted probability
        Set<Material> selectedMaterials = new HashSet<>();
        Map<Material, Integer> requirements = new LinkedHashMap<>();

        int attempts = 0;
        int maxAttempts = eligibleMaterials.size() * 3; // Prevent infinite loop

        while (selectedMaterials.size() < materialCount && attempts < maxAttempts) {
            MaterialDefinition selected = selectWeightedRandom(eligibleMaterials, random);

            if (!selectedMaterials.contains(selected.getMaterial())) {
                selectedMaterials.add(selected.getMaterial());

                // Exponential scaling: base * (1.1 ^ (level - 1))
                int amount = (int) Math.ceil(selected.getBaseAmount() * Math.pow(1.1, currentLevel - 1));
                requirements.put(selected.getMaterial(), amount);
            }

            attempts++;
        }

        return new LevelUpCost(requirements);
    }

    /**
     * Selects a material definition using weighted random selection.
     * Higher probability = more likely to be selected.
     *
     * @param materials list of material definitions
     * @param random random instance
     * @return selected material definition
     */
    private MaterialDefinition selectWeightedRandom(List<MaterialDefinition> materials, Random random) {
        // Calculate total weight
        double totalWeight = materials.stream()
                .mapToDouble(MaterialDefinition::getProbability)
                .sum();

        // Generate random value in range [0, totalWeight)
        double randomValue = random.nextDouble() * totalWeight;

        // Select material based on cumulative weights
        double cumulative = 0.0;
        for (MaterialDefinition def : materials) {
            cumulative += def.getProbability();
            if (randomValue <= cumulative) {
                return def;
            }
        }

        // Fallback (should never happen)
        return materials.get(materials.size() - 1);
    }
}
