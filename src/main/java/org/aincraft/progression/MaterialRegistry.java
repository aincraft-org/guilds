package org.aincraft.progression;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for material definitions used in procedural cost generation.
 * API-accessible for custom material registration with level ranges and probabilities.
 */
@Singleton
public class  MaterialRegistry {
    private final Map<Material, MaterialDefinition> materials;

    @Inject
    public MaterialRegistry() {
        this.materials = new ConcurrentHashMap<>();
        registerDefaults();
    }

    /**
     * Registers default materials with level ranges and probabilities.
     */
    private void registerDefaults() {
        // Common materials (levels 1-50) - high probability
        registerMaterial(new MaterialDefinition(Material.IRON_INGOT, 1, 50, 64, 1.0));
        registerMaterial(new MaterialDefinition(Material.COAL, 1, 40, 64, 1.0));
        registerMaterial(new MaterialDefinition(Material.COPPER_INGOT, 1, 45, 64, 0.8));
        registerMaterial(new MaterialDefinition(Material.COBBLESTONE, 1, 30, 128, 0.9));
        registerMaterial(new MaterialDefinition(Material.OAK_LOG, 1, 35, 64, 0.8));
        registerMaterial(new MaterialDefinition(Material.WHEAT, 1, 25, 64, 0.7));

        // Uncommon materials (levels 20-75) - medium probability
        registerMaterial(new MaterialDefinition(Material.GOLD_INGOT, 20, 75, 32, 0.7));
        registerMaterial(new MaterialDefinition(Material.REDSTONE, 20, 70, 32, 0.8));
        registerMaterial(new MaterialDefinition(Material.LAPIS_LAZULI, 20, 65, 32, 0.7));
        registerMaterial(new MaterialDefinition(Material.QUARTZ, 25, 75, 32, 0.6));
        registerMaterial(new MaterialDefinition(Material.GLOWSTONE_DUST, 25, 70, 32, 0.6));

        // Rare materials (levels 40-100) - lower probability
        registerMaterial(new MaterialDefinition(Material.DIAMOND, 40, 100, 16, 0.6));
        registerMaterial(new MaterialDefinition(Material.EMERALD, 45, 100, 16, 0.5));
        registerMaterial(new MaterialDefinition(Material.ENDER_PEARL, 50, 100, 16, 0.5));
        registerMaterial(new MaterialDefinition(Material.BLAZE_ROD, 50, 95, 16, 0.4));
        registerMaterial(new MaterialDefinition(Material.PRISMARINE_SHARD, 45, 90, 16, 0.4));

        // Very rare materials (levels 70-100) - very low probability
        registerMaterial(new MaterialDefinition(Material.NETHERITE_INGOT, 70, 100, 4, 0.3));
        registerMaterial(new MaterialDefinition(Material.NETHER_STAR, 75, 100, 2, 0.2));
        registerMaterial(new MaterialDefinition(Material.DRAGON_BREATH, 80, 100, 4, 0.2));
        registerMaterial(new MaterialDefinition(Material.ECHO_SHARD, 75, 100, 4, 0.25));
    }

    /**
     * Registers a material definition.
     * API method for external plugins.
     *
     * @param definition the material definition
     * @return true if registered, false if material already exists
     */
    public boolean registerMaterial(MaterialDefinition definition) {
        Objects.requireNonNull(definition, "Material definition cannot be null");
        return materials.putIfAbsent(definition.getMaterial(), definition) == null;
    }

    /**
     * Unregisters a material.
     * API method for external plugins.
     *
     * @param material the material to remove
     * @return true if removed, false if not found
     */
    public boolean unregisterMaterial(Material material) {
        Objects.requireNonNull(material, "Material cannot be null");
        return materials.remove(material) != null;
    }

    /**
     * Updates an existing material definition.
     * API method for external plugins.
     *
     * @param definition the new definition
     * @return true if updated, false if material doesn't exist
     */
    public boolean updateMaterial(MaterialDefinition definition) {
        Objects.requireNonNull(definition, "Material definition cannot be null");
        return materials.replace(definition.getMaterial(), definition) != null;
    }

    /**
     * Gets a material definition.
     *
     * @param material the material
     * @return the definition or null if not registered
     */
    public MaterialDefinition getDefinition(Material material) {
        return materials.get(material);
    }

    /**
     * Gets all materials eligible for a specific level.
     * Filters by level range.
     *
     * @param level the guild level
     * @return list of eligible material definitions
     */
    public List<MaterialDefinition> getEligibleMaterials(int level) {
        return materials.values().stream()
                .filter(def -> def.isEligibleForLevel(level))
                .collect(Collectors.toList());
    }

    /**
     * Gets all registered materials.
     *
     * @return unmodifiable collection of all material definitions
     */
    public Collection<MaterialDefinition> getAllMaterials() {
        return Collections.unmodifiableCollection(materials.values());
    }

    /**
     * Checks if a material is registered.
     *
     * @param material the material
     * @return true if registered
     */
    public boolean isRegistered(Material material) {
        return materials.containsKey(material);
    }

    /**
     * Gets the number of registered materials.
     *
     * @return count of registered materials
     */
    public int getMaterialCount() {
        return materials.size();
    }

    /**
     * Clears all registered materials.
     * WARNING: This will remove defaults too. Use carefully.
     */
    public void clearAll() {
        materials.clear();
    }

    /**
     * Resets to default materials only.
     * Removes all custom registrations.
     */
    public void resetToDefaults() {
        materials.clear();
        registerDefaults();
    }
}
