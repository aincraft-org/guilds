package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.services.travel.TravelCurrencyService;
import org.aincraft.guilds.services.travel.TravelCurrencyRewardSource;
import org.aincraft.guilds.services.travel.WalletSnapshot;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.permission.BlockProtection;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FastTravelServiceTest {
    @Test
    void startSchedulesWarmupAndMovementCanCancel() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.isPrimaryThread()).thenReturn(true);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenReturn(task);
        FacilityRegistry facilities = mock(FacilityRegistry.class);
        FacilityAnchorValidator anchors = mock(FacilityAnchorValidator.class);
        FastTravelAccess access = mock(FastTravelAccess.class);
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
        when(facilities.get("south")).thenReturn(java.util.Optional.of(destination));
        when(access.authorize(playerId, origin, destination)).thenReturn(
                new FastTravelAccess.AccessDecision(FastTravelAccess.AccessResult.ALLOWED,
                        org.aincraft.guilds.territory.model.FastTravelMode.WAYSTONE,
                        "guild-a", "guild-a", "guild-a", null, null, null));
        when(landings.find(destination)).thenReturn(java.util.Optional.of(landing));
        when(protection.canTeleportInto(any(), anyInt(), anyInt(), any())).thenReturn(true);
        TravelCurrencyService currency = new ImmediateCurrency();
        FastTravelCostCalculator cost = new FastTravelCostCalculator(
                org.aincraft.guilds.config.TravelCurrencyConfig.defaults());
        FastTravelService service = new FastTravelService(plugin, facilities, anchors,
                access, landings, protection, config, currency, cost, null);

        assertEquals(FastTravelService.StartResult.STARTED,
                service.start(player, origin, "south", 1_000L).toCompletableFuture().join());
        assertTrue(service.isPending(playerId));
        service.cancel(playerId, FastTravelService.CancelReason.MOVED);
        assertTrue(!service.isPending(playerId));
    }

    private static final class ImmediateCurrency implements TravelCurrencyService {
        @Override
        public java.util.concurrent.CompletionStage<WalletSnapshot> wallet(UUID playerId) {
            return java.util.concurrent.CompletableFuture.completedFuture(new WalletSnapshot(playerId, 100L));
        }

        @Override
        public java.util.concurrent.CompletionStage<ReserveResult> reserve(
                UUID playerId, String tripId, long amount, long nowMillis) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new ReserveResult(ReserveStatus.RESERVED, "reservation", 99L));
        }

        @Override
        public java.util.concurrent.CompletionStage<ReservationResult> commit(
                String reservationId, long nowMillis) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new ReservationResult(ReservationStatus.COMMITTED));
        }

        @Override
        public java.util.concurrent.CompletionStage<ReservationResult> release(
                String reservationId, long nowMillis) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new ReservationResult(ReservationStatus.RELEASED));
        }

        @Override
        public java.util.concurrent.CompletionStage<RewardResult> award(
                UUID playerId, TravelCurrencyRewardSource source, String eventId,
                long amount, long nowMillis) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new RewardResult(RewardStatus.AWARDED, new WalletSnapshot(playerId, 100L)));
        }

        @Override
        public java.util.concurrent.CompletionStage<Integer> recoverExpired(long nowMillis) {
            return java.util.concurrent.CompletableFuture.completedFuture(0);
        }
    }

    private static SettlementFacility facility(String id, int x) {
        return new SettlementFacility(id, id, "t1", FacilityType.WAYSTONE, "world", x, 64, 5);
    }
}
