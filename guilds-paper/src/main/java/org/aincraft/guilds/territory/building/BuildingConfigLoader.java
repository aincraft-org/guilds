package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.FacilityType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BuildingConfigLoader {
    private BuildingConfigLoader() {
    }

    public static BuildingConfig from(ConfigurationSection config) {
        long timeoutSeconds = config.getLong("buildings.placement-timeout-seconds", 60L);
        long warmupSeconds = config.getLong("buildings.waystone.warmup-seconds", 5L);
        long cooldownSeconds = config.getLong("buildings.waystone.cooldown-seconds", 60L);
        if (timeoutSeconds <= 0L || warmupSeconds < 0L || cooldownSeconds < 0L) {
            throw new IllegalArgumentException("invalid building timing configuration");
        }

        EnumMap<FacilityType, Set<Material>> materials = new EnumMap<>(FacilityType.class);
        materials.put(FacilityType.WAYSTONE, materials(config,
                "buildings.waystone.anchor-materials", List.of("LODESTONE")));
        materials.put(FacilityType.TRADING_POST, materials(config,
                "buildings.trading-post.anchor-materials", List.of("BELL", "LECTERN")));
        materials.put(FacilityType.STORAGE, materials(config,
                "buildings.storage.anchor-materials", List.of("BARREL", "CHEST")));
        materials.put(FacilityType.BANK, materials(config,
                "buildings.bank.anchor-materials", List.of("GOLD_BLOCK")));
        materials.put(FacilityType.GUILD_CRYSTAL, materials(config,
                "buildings.guild-crystal.anchor-materials", List.of("AMETHYST_BLOCK")));
        materials.put(FacilityType.TELEPORT_TERMINAL, materials(config,
                "buildings.teleport-terminal.anchor-materials", List.of("LODESTONE")));
        materials.put(FacilityType.BOAT, materials(config,
                "buildings.boat.anchor-materials", List.of("OAK_PLANKS")));
        materials.put(FacilityType.AIRSHIP, materials(config,
                "buildings.airship.anchor-materials", List.of("IRON_BLOCK")));

        BuildingConfig.TransportGeometry geometry = new BuildingConfig.TransportGeometry(
                config.getInt("buildings.transport.boat.entry-radius", 2),
                config.getInt("buildings.transport.boat.entry-width", 3),
                config.getInt("buildings.transport.boat.clear-space-height", 2),
                config.getInt("buildings.transport.boat.search-chunk-radius", 32),
                config.getInt("buildings.transport.boat.search-chunk-budget", 256),
                config.getInt("buildings.transport.airship.platform-radius", 2),
                config.getInt("buildings.transport.airship.clear-sky-height", 16));

        return new BuildingConfig(
                Math.multiplyExact(timeoutSeconds, 1_000L),
                Map.copyOf(materials),
                Math.multiplyExact(warmupSeconds, 20L),
                Math.multiplyExact(cooldownSeconds, 1_000L),
                geometry);
    }

    private static Set<Material> materials(ConfigurationSection config, String path,
                                           List<String> defaults) {
        List<String> names = config.isSet(path) ? config.getStringList(path) : defaults;
        if (names.isEmpty()) {
            throw new IllegalArgumentException(path + " must contain at least one material");
        }
        Set<Material> result = new LinkedHashSet<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name == null ? "" : name.trim());
            if (material == null || material == Material.AIR
                    || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                throw new IllegalArgumentException("invalid anchor material at " + path + ": " + name);
            }
            result.add(material);
        }
        return Set.copyOf(result);
    }
}
