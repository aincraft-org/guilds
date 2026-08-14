package com.azoth.territory.building;

import com.azoth.territory.model.FacilityType;
import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.permission.BlockProtection;
import com.azoth.territory.registry.FacilityRegistry;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WaystoneTravelServiceTest {
    @Test
    void startSchedulesWarmupAndMovementCanCancel() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(task);
        FacilityRegistry facilities = mock(FacilityRegistry.class);
        FacilityAnchorValidator anchors = mock(FacilityAnchorValidator.class);
        WaystoneAccess access = mock(WaystoneAccess.class);
        SafeLandingResolver landings = mock(SafeLandingResolver.class);
        BlockProtection protection = mock(BlockProtection.class);
        BuildingConfig config = new BuildingConfig(60_000L,
                Map.of(FacilityType.WAYSTONE, Set.of(org.bukkit.Material.LODESTONE)), 100L, 60_000L);
        SettlementFacility origin = facility("origin", 5);
        SettlementFacility destination = facility("south", 10);
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location landing = new Location(world, 10.5, 65, 5.5);
        when(player.getUniqueId()).thenReturn(playerId);
        when(anchors.validate(origin)).thenReturn(
                new FacilityAnchorValidator.AnchorValidation(AnchorStatus.ACTIVE, origin));
        when(access.reachable(playerId, origin)).thenReturn(List.of(destination));
        when(landings.find(destination)).thenReturn(java.util.Optional.of(landing));
        when(protection.canTeleportInto("world", 10, 5, playerId.toString())).thenReturn(true);
        WaystoneTravelService service = new WaystoneTravelService(plugin, facilities, anchors,
                access, landings, protection, config);

        assertEquals(WaystoneTravelService.StartResult.STARTED,
                service.start(player, origin, "south", 1_000L));
        assertTrue(service.isPending(playerId));
        service.cancel(playerId, WaystoneTravelService.CancelReason.MOVED);
        assertTrue(!service.isPending(playerId));
    }

    private static SettlementFacility facility(String id, int x) {
        return new SettlementFacility(id, id, "t1", FacilityType.WAYSTONE, "world", x, 64, 5);
    }
}
