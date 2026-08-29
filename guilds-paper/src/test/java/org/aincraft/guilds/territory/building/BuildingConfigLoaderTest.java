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
        assertEquals(Set.of(Material.AMETHYST_BLOCK),
                config.anchorMaterials(FacilityType.GUILD_CRYSTAL));
        assertEquals(Set.of(Material.LODESTONE),
                config.anchorMaterials(FacilityType.TELEPORT_TERMINAL));
        assertEquals(Set.of(Material.OAK_PLANKS), config.anchorMaterials(FacilityType.BOAT));
        assertEquals(Set.of(Material.IRON_BLOCK), config.anchorMaterials(FacilityType.AIRSHIP));
        assertEquals(60_000L, config.placementTimeoutMillis());
        assertEquals(100L, config.waystoneWarmupTicks());
        assertEquals(60_000L, config.waystoneCooldownMillis());
        BuildingConfig.TransportGeometry geometry = config.transportGeometry();
        assertEquals(2, geometry.boatEntryRadius());
        assertEquals(3, geometry.boatEntryWidth());
        assertEquals(2, geometry.clearBoatSpaceHeight());
        assertEquals(32, geometry.searchChunkRadius());
        assertEquals(256, geometry.searchChunkBudget());
        assertEquals(2, geometry.airshipPlatformRadius());
        assertEquals(16, geometry.airshipVerticalClearanceHeight());
    }
    @Test
    void loadsExplicitTransportGeometry() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("buildings.transport.boat.entry-radius", 4);
        yaml.set("buildings.transport.boat.entry-width", 5);
        yaml.set("buildings.transport.boat.clear-space-height", 6);
        yaml.set("buildings.transport.boat.search-chunk-radius", 7);
        yaml.set("buildings.transport.boat.search-chunk-budget", 8);
        yaml.set("buildings.transport.airship.platform-radius", 9);
        yaml.set("buildings.transport.airship.clear-sky-height", 10);

        BuildingConfig.TransportGeometry geometry =
                BuildingConfigLoader.from(yaml).transportGeometry();

        assertEquals(4, geometry.boatEntryRadius());
        assertEquals(5, geometry.boatEntryWidth());
        assertEquals(6, geometry.clearBoatSpaceHeight());
        assertEquals(7, geometry.searchChunkRadius());
        assertEquals(8, geometry.searchChunkBudget());
        assertEquals(9, geometry.airshipPlatformRadius());
        assertEquals(10, geometry.airshipVerticalClearanceHeight());
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
