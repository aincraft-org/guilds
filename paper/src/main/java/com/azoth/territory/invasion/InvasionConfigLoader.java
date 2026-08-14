package com.azoth.territory.invasion;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class InvasionConfigLoader {
    private InvasionConfigLoader() {}

    public record LoadedConfig(boolean enabled, InvasionConfig config, Set<Material> materials,
                               int spawnRadius, int spawnAttempts, int nearbyRadius, long waveDelayTicks) {}

    public static LoadedConfig fromBukkit(FileConfiguration cfg) {
        ConfigurationSection root = cfg == null ? null : cfg.getConfigurationSection("invasions");
        boolean enabled = root == null || root.getBoolean("enabled", true);
        long budget = root == null ? 500 : root.getLong("damage.block-budget", 500);
        int spawnRadius = root == null ? 24 : root.getInt("spawn-radius", 24);
        int spawnAttempts = root == null ? 24 : root.getInt("spawn-attempts", 24);
        int nearbyRadius = root == null ? 96 : root.getInt("bossbar.nearby-radius", 96);
        long delay = root == null ? 100 : root.getLong("wave-delay-ticks", 100);
        if (budget <= 0) throw new IllegalArgumentException("invasions.damage.block-budget must be positive");
        if (spawnRadius < 0) throw new IllegalArgumentException("invasions.spawn-radius must be non-negative");
        if (spawnAttempts <= 0) throw new IllegalArgumentException("invasions.spawn-attempts must be positive");
        if (nearbyRadius < 0) throw new IllegalArgumentException("invasions.bossbar.nearby-radius must be non-negative");
        if (delay < 0) throw new IllegalArgumentException("invasions.wave-delay-ticks must be non-negative");

        List<String> materialNames = root == null ? List.of("STONE", "COBBLESTONE", "OAK_PLANKS", "SPRUCE_PLANKS")
                : root.getStringList("damage.allowlist");
        if (materialNames.isEmpty()) throw new IllegalArgumentException("invasions.damage.allowlist must not be empty");
        Set<Material> materials = materialNames.stream().map(name -> {
            Material material = Material.matchMaterial(name);
            if (material == null) throw new IllegalArgumentException("invalid material: " + name);
            return material;
        }).collect(java.util.stream.Collectors.toUnmodifiableSet());

        List<?> waveValues = root == null ? null : root.getList("waves");
        List<Wave> waves = new ArrayList<>();
        if (waveValues == null) {
            waves.add(new Wave(List.of(new MobEntry("ZOMBIE", 5))));
            waves.add(new Wave(List.of(new MobEntry("ZOMBIE", 8), new MobEntry("SKELETON", 2))));
            waves.add(new Wave(List.of(new MobEntry("ZOMBIE", 10), new MobEntry("SKELETON", 5), new MobEntry("RAVAGER", 1))));
        } else {
            if (waveValues.size() != 3) throw new IllegalArgumentException("invasions.waves must contain exactly three waves");
            for (int i = 0; i < 3; i++) {
                ConfigurationSection wave = root.getConfigurationSection("waves." + i);
                if (wave == null) throw new IllegalArgumentException("invalid wave " + i);
                List<String> entities = wave.getStringList("entities");
                List<Integer> counts = wave.getIntegerList("counts");
                if (entities.isEmpty() || entities.size() != counts.size()) throw new IllegalArgumentException("invalid wave " + i);
                List<MobEntry> mobs = new ArrayList<>();
                for (int j = 0; j < entities.size(); j++) {
                    String entity = entities.get(j).toUpperCase(Locale.ROOT);
                    if (EntityType.fromName(entity) == null) throw new IllegalArgumentException("invalid entity type: " + entity);
                    mobs.add(new MobEntry(entity, counts.get(j)));
                }
                waves.add(new Wave(mobs));
            }
        }
        return new LoadedConfig(enabled, new InvasionConfig(budget, waves), materials, spawnRadius, spawnAttempts, nearbyRadius, delay);
    }
}
