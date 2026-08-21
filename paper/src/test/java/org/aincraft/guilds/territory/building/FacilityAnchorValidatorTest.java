package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FacilityAnchorValidatorTest {
    private Server server;
    private World world;
    private Block block;
    private TerritoryRegistry territories;
    private FacilityRegistry facilities;
    private SettlementFacility waystone;
    private FacilityAnchorValidator validator;

    @BeforeEach
    void setUp() {
        server = mock(Server.class);
        world = mock(World.class);
        block = mock(Block.class);
        territories = new TerritoryRegistry(List.of(territory()));
        facilities = new FacilityRegistry(territories);
        waystone = new SettlementFacility(
                "north", "North", "t1", FacilityType.WAYSTONE, "world", 5, 64, 5);
        facilities.register(waystone);
        BuildingConfig config = new BuildingConfig(60_000L,
                Map.of(FacilityType.WAYSTONE, Set.of(Material.LODESTONE)), 100L, 60_000L);
        validator = new FacilityAnchorValidator(server, territories, facilities, config);
        when(server.getWorld("world")).thenReturn(world);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getBlockAt(5, 64, 5)).thenReturn(block);
    }

    @Test
    void validatesOnlyExactConfiguredAnchor() {
        when(block.getType()).thenReturn(Material.LODESTONE);

        assertEquals(AnchorStatus.ACTIVE, validator.validate(waystone).status());
        assertEquals(java.util.Optional.of(waystone), validator.activeAt("world", 5, 64, 5));
        verify(world, org.mockito.Mockito.times(2)).getBlockAt(5, 64, 5);
        verify(world, never()).getBlockAt(6, 64, 5);
    }

    @Test
    void wrongMaterialIsInactiveAndRestorationReactivates() {
        when(block.getType()).thenReturn(Material.STONE, Material.LODESTONE);

        assertEquals(AnchorStatus.WRONG_MATERIAL, validator.validate(waystone).status());
        assertTrue(validator.validate(waystone).active());
    }

    @Test
    void unavailableWorldOrChunkIsInactiveWithoutBlockLookup() {
        when(server.getWorld("world")).thenReturn(null);
        assertEquals(AnchorStatus.WORLD_UNAVAILABLE, validator.validate(waystone).status());
        verify(world, never()).getBlockAt(5, 64, 5);
    }

    @Test
    void activeStorageNearPrefersStorageOverCloserNonStorageFacility() {
        SettlementFacility storage = new SettlementFacility(
                "storage", "Storage", "t1", FacilityType.STORAGE, "world", 7, 64, 5);
        facilities.register(storage);
        BuildingConfig config = new BuildingConfig(60_000L,
                Map.of(
                        FacilityType.WAYSTONE, Set.of(Material.LODESTONE),
                        FacilityType.STORAGE, Set.of(Material.BARREL)),
                100L, 60_000L);
        validator = new FacilityAnchorValidator(server, territories, facilities, config);
        Block storageBlock = mock(Block.class);
        when(world.getBlockAt(7, 64, 5)).thenReturn(storageBlock);
        when(storageBlock.getType()).thenReturn(Material.BARREL);

        assertEquals(java.util.Optional.of(storage), validator.activeStorageNear("world", 6, 64, 5));
    }

    @Test
    void activeStorageNearBreaksDistanceTiesByFacilityId() {
        SettlementFacility first = new SettlementFacility(
                "a-storage", "A", "t1", FacilityType.STORAGE, "world", 6, 64, 5);
        SettlementFacility second = new SettlementFacility(
                "b-storage", "B", "t1", FacilityType.STORAGE, "world", 6, 64, 6);
        facilities.register(first);
        facilities.register(second);
        BuildingConfig config = new BuildingConfig(60_000L,
                Map.of(FacilityType.STORAGE, Set.of(Material.BARREL)), 100L, 60_000L);
        validator = new FacilityAnchorValidator(server, territories, facilities, config);
        Block firstBlock = mock(Block.class);
        Block secondBlock = mock(Block.class);
        when(world.getBlockAt(6, 64, 5)).thenReturn(firstBlock);
        when(world.getBlockAt(6, 64, 6)).thenReturn(secondBlock);
        when(firstBlock.getType()).thenReturn(Material.BARREL);
        when(secondBlock.getType()).thenReturn(Material.BARREL);

        assertEquals(java.util.Optional.of(first), validator.activeStorageNear("world", 5, 64, 5));
    }


    private static Territory territory() {
        return new Territory("t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))));
    }
}
