package com.azoth.territory.invasion;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
public final class InvasionConfigLoader {
    private InvasionConfigLoader() {}

    public record LoadedConfig(boolean enabled, InvasionConfig config, Set<Material> materials,
                               int spawnRadius, int spawnAttempts, int nearbyRadius, long waveDelayTicks) {}

    public static LoadedConfig fromBukkit(FileConfiguration cfg) {
        ConfigurationSection root = cfg == null ? null : cfg.getConfigurationSection("invasions");
        boolean enabled = root == null || root.getBoolean("enabled", true);
        long budget = getLong(root, "damage.block-budget", 500);
        int spawnRadius = getInt(root, "spawn-radius", 24);
        int spawnAttempts = getInt(root, "spawn-attempts", 24);
        int nearbyRadius = getInt(root, "bossbar.nearby-radius", 96);
        long delay = getLong(root, "wave-delay-ticks", 100);
        if (budget <= 0) throw new IllegalArgumentException("invasions.damage.block-budget must be positive");
        if (spawnRadius < 0) throw new IllegalArgumentException("invasions.spawn-radius must be non-negative");
        if (spawnAttempts <= 0) throw new IllegalArgumentException("invasions.spawn-attempts must be positive");
        if (nearbyRadius < 0) throw new IllegalArgumentException("invasions.bossbar.nearby-radius must be non-negative");
        if (delay < 0) throw new IllegalArgumentException("invasions.wave-delay-ticks must be non-negative");

        List<String> materialNames = root == null || !root.contains("damage.allowlist")
                ? List.of("STONE", "COBBLESTONE", "OAK_PLANKS", "SPRUCE_PLANKS")
                : root.getStringList("damage.allowlist");
        if (materialNames.isEmpty()) throw new IllegalArgumentException("invasions.damage.allowlist must not be empty");
        Set<Material> materials = materialNames.stream().map(name -> {
            Material material = Material.matchMaterial(name);
            if (material == null) throw new IllegalArgumentException("invalid material: " + name);
            return material;
        }).collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Map<?, ?>> waveValues = root == null || !root.contains("waves") ? null : root.getMapList("waves");
        List<Wave> waves = new ArrayList<>();
        if (waveValues == null) {
            waves.add(new Wave(List.of(new MobEntry("ZOMBIE", 5))));
            waves.add(new Wave(List.of(new MobEntry("ZOMBIE", 8), new MobEntry("SKELETON", 2))));
            waves.add(new Wave(List.of(new MobEntry("ZOMBIE", 10), new MobEntry("SKELETON", 5), new MobEntry("RAVAGER", 1))));
        } else if (waveValues.isEmpty()) {
            throw new IllegalArgumentException("invasions.waves must contain exactly three waves");
        } else {
            if (waveValues.size() != 3) throw new IllegalArgumentException("invasions.waves must contain exactly three waves");
            for (Map<?, ?> wave : waveValues) {
                List<?> entities = asList(wave.get("entities"));
                List<?> counts = asList(wave.get("counts"));
                if (entities.isEmpty() || entities.size() != counts.size()) throw new IllegalArgumentException("invalid wave");
                List<MobEntry> mobs = new ArrayList<>();
                for (int j = 0; j < entities.size(); j++) {
                    String entity = String.valueOf(entities.get(j)).toUpperCase(Locale.ROOT);
                    if (EntityType.fromName(entity) == null) throw new IllegalArgumentException("invalid entity type: " + entity);
                    int count = waveCount(counts.get(j));
                    if (count <= 0) throw new IllegalArgumentException("wave counts must be positive");
                    mobs.add(new MobEntry(entity, count));
                }
                waves.add(new Wave(mobs));
            }
        }
        return new LoadedConfig(enabled, new InvasionConfig(budget, waves), materials, spawnRadius, spawnAttempts, nearbyRadius, delay);
    }

    private static int waveCount(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("wave counts must be finite exact positive integers");
        }
        double numeric = number.doubleValue();
        if (!Double.isFinite(numeric) || numeric != Math.rint(numeric)
                || numeric < Integer.MIN_VALUE || numeric > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("wave counts must be finite exact positive integers");
        }
        return number.intValue();
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static long getLong(ConfigurationSection root, String path, long fallback) {
        return root == null ? fallback : root.getLong(path, fallback);
    }

    private static int getInt(ConfigurationSection root, String path, int fallback) {
        return root == null ? fallback : root.getInt(path, fallback);
    }
}
