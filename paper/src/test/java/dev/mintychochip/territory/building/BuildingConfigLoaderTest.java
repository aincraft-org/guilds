package dev.mintychochip.territory.building;

import dev.mintychochip.territory.model.FacilityType;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildingConfigLoaderTest {
    @Test
    void loadsApprovedDefaults() {
        BuildingConfig config = BuildingConfigLoader.from(new YamlConfiguration());

        assertEquals(Set.of(Material.LODESTONE), config.anchorMaterials(FacilityType.WAYSTONE));
        assertEquals(Set.of(Material.BELL, Material.LECTERN),
                config.anchorMaterials(FacilityType.TRADING_POST));
        assertEquals(Set.of(Material.CHEST, Material.BARREL, Material.TRAPPED_CHEST),
                config.anchorMaterials(FacilityType.STORAGE));
        assertEquals(60_000L, config.placementTimeoutMillis());
        assertEquals(100L, config.waystoneWarmupTicks());
        assertEquals(60_000L, config.waystoneCooldownMillis());
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
