package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.storage.StorageFacilityOpener;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildingListenerStorageTest {
    @Mock
    private Player player;
    @Mock
    private World world;
    @Mock
    private Block anchor;
    @Mock
    private StorageFacilityOpener storageOpener;

    private BuildingListener listener;
    private SettlementFacility storageFacility;

    @BeforeEach
    void setUp() {
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(world.getName()).thenReturn("world");
        when(anchor.getWorld()).thenReturn(world);
        when(anchor.getX()).thenReturn(5);
        when(anchor.getY()).thenReturn(64);
        when(anchor.getZ()).thenReturn(5);

        Territory territory = new Territory("t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))));
        TerritoryRegistry territories = new TerritoryRegistry(List.of(territory));
        FacilityRegistry facilities = new FacilityRegistry(territories);
        storageFacility = new SettlementFacility(
                "vault", "Vault", "t1", FacilityType.STORAGE, "world", 5, 64, 5);
        facilities.register(storageFacility);

        BuildingConfig config = new BuildingConfig(60_000L,
                Map.of(FacilityType.STORAGE, Set.of(Material.BARREL)), 100L, 60_000L);
        FacilityAnchorValidator anchors = mock(FacilityAnchorValidator.class);
        when(anchors.activeStorageAt(org.mockito.ArgumentMatchers.eq("world"), anyInt(), anyInt(), anyInt()))
                .thenReturn(Optional.of(storageFacility));
        listener = new BuildingListener(
                new BuildingPlacementSessions(60_000L),
                config,
                territories,
                facilities,
                mock(BuildingAuthorization.class),
                new FacilityMutationService(facilities, mock(org.aincraft.guilds.territory.persist.FacilityStore.class)),
                anchors,
                mock(WaystoneAccess.class),
                new WaystoneSelections(60_000L),
                mock(org.bukkit.plugin.PluginManager.class),
                storageOpener);
    }

    @Test
    void rightClickOnStorageAnchorDelegatesToOpener() {
        when(storageOpener.tryOpen(eq(player), eq(storageFacility)))
                .thenReturn(StorageFacilityOpener.Result.opened());
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, anchor,
                org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);

        listener.onInteract(event);

        verify(storageOpener).tryOpen(player, storageFacility);
        assertTrue(event.isCancelled());
    }

    @Test
    void deniedStorageInteractionCancelsEvent() {
        when(storageOpener.tryOpen(eq(player), eq(storageFacility)))
                .thenReturn(StorageFacilityOpener.Result.denied("You are not a member of the governing guild."));
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, anchor,
                org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);

        listener.onInteract(event);

        assertTrue(event.isCancelled());
    }
}
