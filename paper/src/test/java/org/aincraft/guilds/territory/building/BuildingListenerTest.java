package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.persist.FacilityStore;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuildingListenerTest {
    private Player player;
    private World world;
    private Block anchor;
    private Territory territory;
    private FacilityRegistry facilities;
    private BuildingPlacementSessions sessions;
    private BuildingAuthorization authorization;
    private MemoryStore store;
    private BuildingListener listener;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        world = mock(World.class);
        anchor = mock(Block.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(world.getName()).thenReturn("world");
        when(anchor.getWorld()).thenReturn(world);
        when(anchor.getX()).thenReturn(5);
        when(anchor.getY()).thenReturn(64);
        when(anchor.getZ()).thenReturn(5);
        territory = new Territory("t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))));
        TerritoryRegistry territories = new TerritoryRegistry(List.of(territory));
        facilities = new FacilityRegistry(territories);
        BuildingConfig config = new BuildingConfig(60_000L,
                Map.of(FacilityType.WAYSTONE, Set.of(Material.LODESTONE)), 100L, 60_000L);
        sessions = new BuildingPlacementSessions(60_000L);
        authorization = mock(BuildingAuthorization.class);
        store = new MemoryStore();
        listener = new BuildingListener(sessions, config, territories, facilities,
                authorization, new FacilityMutationService(facilities, store),
                mock(FacilityAnchorValidator.class), mock(WaystoneAccess.class),
                new WaystoneSelections(60_000L), mock(org.bukkit.plugin.PluginManager.class), null);
    }

    @Test
    void commandThenClickRegistersExactAnchorDurably() {
        sessions.begin(player.getUniqueId(), FacilityType.WAYSTONE,
                "north", "North", System.currentTimeMillis());
        when(anchor.getType()).thenReturn(Material.LODESTONE);
        when(authorization.canManage(player, territory)).thenReturn(true);
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, anchor,
                org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);

        listener.onInteract(event);

        SettlementFacility registered = facilities.get("north").orElseThrow();
        assertEquals(List.of(registered), store.saved);
        assertEquals(5, registered.x());
        assertTrue(event.isCancelled());
    }

    @Test
    void wrongMaterialKeepsPlacementSession() {
        sessions.begin(player.getUniqueId(), FacilityType.WAYSTONE,
                "north", "North", System.currentTimeMillis());
        when(anchor.getType()).thenReturn(Material.STONE);
        PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, anchor,
                org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);

        listener.onInteract(event);

        assertTrue(sessions.current(player.getUniqueId(), System.currentTimeMillis()).isPresent());
        assertTrue(facilities.list().isEmpty());
    }

    @Test
    void authorizedBreakRemovesBeforeAllowingBreak() throws Exception {
        SettlementFacility facility = new SettlementFacility(
                "north", "North", "t1", FacilityType.WAYSTONE, "world", 5, 64, 5);
        new FacilityMutationService(facilities, store).register(facility);
        when(authorization.canManage(player, territory)).thenReturn(true);
        BlockBreakEvent event = new BlockBreakEvent(anchor, player);

        listener.onBreak(event);

        assertFalse(event.isCancelled());
        assertTrue(facilities.list().isEmpty());
    }

    @Test
    void persistenceFailureCancelsBreakAndPreservesFacility() throws Exception {
        SettlementFacility facility = new SettlementFacility(
                "north", "North", "t1", FacilityType.WAYSTONE, "world", 5, 64, 5);
        new FacilityMutationService(facilities, store).register(facility);
        store.fail = true;
        when(authorization.canManage(player, territory)).thenReturn(true);
        BlockBreakEvent event = new BlockBreakEvent(anchor, player);

        listener.onBreak(event);

        assertTrue(event.isCancelled());
        assertEquals(java.util.Optional.of(facility), facilities.get("north"));
    }

    private static final class MemoryStore implements FacilityStore {
        private List<SettlementFacility> saved = List.of();
        private boolean fail;

        @Override
        public void save(Collection<SettlementFacility> facilities) throws IOException {
            if (fail) throw new IOException("forced failure");
            saved = List.copyOf(facilities);
        }
    }
}
