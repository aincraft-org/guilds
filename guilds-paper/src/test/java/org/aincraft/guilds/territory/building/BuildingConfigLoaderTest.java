package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.FacilityType;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingConfigLoaderTest {
    @Test
    void loadsApprovedDefaults() {
        BuildingConfig config = BuildingConfigLoader.from(new YamlConfiguration());

        assertEquals(Set.of(Material.LODESTONE), config.anchorMaterials(FacilityType.WAYSTONE));
        assertEquals(Set.of(Material.BELL, Material.LECTERN),
                config.anchorMaterials(FacilityType.TRADING_POST));
        assertEquals(Set.of(Material.BARREL, Material.CHEST),
                config.anchorMaterials(FacilityType.STORAGE));
        assertEquals(Set.of(Material.GOLD_BLOCK), config.anchorMaterials(FacilityType.BANK));
        assertTrue(config.supports(FacilityType.STORAGE));
        assertTrue(config.supports(FacilityType.BANK));
        assertEquals(60_000L, config.placementTimeoutMillis());
        assertEquals(100L, config.waystoneWarmupTicks());
        assertEquals(60_000L, config.waystoneCooldownMillis());
    }

    @Test
    void loadsExplicitStorageMaterials() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("buildings.storage.anchor-materials", List.of("SHULKER_BOX", "ENDER_CHEST"));

        BuildingConfig config = BuildingConfigLoader.from(yaml);

        assertEquals(Set.of(Material.SHULKER_BOX, Material.ENDER_CHEST),
                config.anchorMaterials(FacilityType.STORAGE));
    }

    @Test
    void rejectsInvalidStorageMaterial() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("buildings.storage.anchor-materials", List.of("NOT_A_BLOCK"));

        assertThrows(IllegalArgumentException.class, () -> BuildingConfigLoader.from(yaml));
    }

    @Test
    void rejectsInvalidTimingAndMaterials() {
        YamlConfiguration zeroTimeout = new YamlConfiguration();
        zeroTimeout.set("buildings.placement-timeout-seconds", 0);
        assertThrows(IllegalArgumentException.class, () -> BuildingConfigLoader.from(zeroTimeout));

        YamlConfiguration negativeWarmup = new YamlConfiguration();
        negativeWarmup.set("buildings.waystone.warmup-seconds", -1);
        assertThrows(IllegalArgumentException.class, () -> BuildingConfigLoader.from(negativeWarmup));

        YamlConfiguration unknown = new YamlConfiguration();
        unknown.set("buildings.waystone.anchor-materials", List.of("NOT_A_BLOCK"));
        assertThrows(IllegalArgumentException.class, () -> BuildingConfigLoader.from(unknown));

        YamlConfiguration air = new YamlConfiguration();
        air.set("buildings.waystone.anchor-materials", List.of("AIR"));
        assertThrows(IllegalArgumentException.class, () -> BuildingConfigLoader.from(air));

        YamlConfiguration empty = new YamlConfiguration();
        empty.set("buildings.waystone.anchor-materials", List.of());
        assertThrows(IllegalArgumentException.class, () -> BuildingConfigLoader.from(empty));
    }
}
