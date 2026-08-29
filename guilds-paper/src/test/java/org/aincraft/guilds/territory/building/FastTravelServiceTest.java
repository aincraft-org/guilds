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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Test
    void commitFailureAfterArrivalReleasesReservation() {
        RecordingCurrency currency = new RecordingCurrency(
                TravelCurrencyService.ReservationStatus.RELEASED);
        TravelHarness harness = harness(currency);

        assertEquals(FastTravelService.StartResult.STARTED,
                harness.service.start(harness.player, harness.origin, "south",
                        System.currentTimeMillis())
                        .toCompletableFuture().join());
        harness.warmup.get().run();

        assertEquals(1, currency.releaseCount);
        assertTrue(!harness.service.isPending(harness.playerId));
    }

    @Test
    void alreadyCommittedAfterArrivalDoesNotReleaseReservation() {
        RecordingCurrency currency = new RecordingCurrency(
                TravelCurrencyService.ReservationStatus.ALREADY_COMMITTED);
        TravelHarness harness = harness(currency);

        assertEquals(FastTravelService.StartResult.STARTED,
                harness.service.start(harness.player, harness.origin, "south",
                        System.currentTimeMillis())
                        .toCompletableFuture().join());
        harness.warmup.get().run();
        assertEquals(1, currency.commitCount);
        assertEquals(0, currency.releaseCount);
        assertTrue(!harness.service.isPending(harness.playerId));
    }

    @Test
    void teleportThrowWhileCommittingReleasesReservation() {
        RecordingCurrency currency = new RecordingCurrency(
                TravelCurrencyService.ReservationStatus.COMMITTED);
        TravelHarness harness = harness(currency);
        doThrow(new IllegalStateException("teleport failed")).when(harness.player)
                .teleport(any(Location.class));

        assertEquals(FastTravelService.StartResult.STARTED,
                harness.service.start(harness.player, harness.origin, "south",
                        System.currentTimeMillis())
                        .toCompletableFuture().join());
        harness.warmup.get().run();

        assertEquals(1, currency.releaseCount);
        assertTrue(!harness.service.isPending(harness.playerId));
    }

    @Test
    void synchronousStartExceptionCompletesAndRemovesAttempt() {
        TravelHarness harness = harness(new RecordingCurrency(
                TravelCurrencyService.ReservationStatus.COMMITTED));
        when(harness.access.authorize(harness.playerId, harness.origin, harness.destination))
                .thenThrow(new IllegalStateException("access failed"));

        CompletionStage<FastTravelService.StartResult> result =
                harness.service.start(harness.player, harness.origin, "south", 1_000L);

        assertEquals(FastTravelService.StartResult.RESERVATION_FAILED,
                result.toCompletableFuture().join());
        assertTrue(!harness.service.isPending(harness.playerId));
    }

    @Test
    void startOffMainThreadFailsClosedBeforePlayerAccess() {
        TravelHarness harness = harness(new RecordingCurrency(
                TravelCurrencyService.ReservationStatus.COMMITTED));
        when(harness.server.isPrimaryThread()).thenReturn(false);

        assertEquals(FastTravelService.StartResult.RESERVATION_FAILED,
                harness.service.start(harness.player, harness.origin, "south", 1_000L)
                        .toCompletableFuture().join());
        verify(harness.player, never()).getUniqueId();
    }

    @Test
    void cooldownUpdatesAndReadsAreSafeConcurrently() throws Exception {
        TravelHarness harness = harness(new RecordingCurrency(
                TravelCurrencyService.ReservationStatus.COMMITTED));
        var setter = FastTravelService.class.getDeclaredMethod(
                "setCooldown", UUID.class,
                org.aincraft.guilds.territory.model.FastTravelMode.class,
                String.class, long.class);
        setter.setAccessible(true);
        java.util.concurrent.ExecutorService workers =
                java.util.concurrent.Executors.newFixedThreadPool(4);
        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < 100; i++) {
                final long now = i;
                futures.add(workers.submit(() -> {
                    try {
                        setter.invoke(harness.service, harness.playerId,
                                org.aincraft.guilds.territory.model.FastTravelMode.WAYSTONE,
                                null, now);
                    } catch (ReflectiveOperationException exception) {
                        throw new AssertionError(exception);
                    }
                    assertTrue(harness.service.remainingCooldownMillis(
                            harness.playerId,
                            org.aincraft.guilds.territory.model.FastTravelMode.WAYSTONE,
                            now) >= 0L);
                }));
            }
            for (var future : futures) {
                future.get();
            }
        } finally {
            workers.shutdown();
            assertTrue(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    private static TravelHarness harness(RecordingCurrency currency) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        FacilityRegistry facilities = mock(FacilityRegistry.class);
        FacilityAnchorValidator anchors = mock(FacilityAnchorValidator.class);
        FastTravelAccess access = mock(FastTravelAccess.class);
        SafeLandingResolver landings = mock(SafeLandingResolver.class);
        BlockProtection protection = mock(BlockProtection.class);
        SettlementFacility origin = facility("origin", 5);
        SettlementFacility destination = facility("south", 10);
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location landing = new Location(world, 10.5, 65, 5.5);
        AtomicReference<Runnable> warmup = new AtomicReference<>();
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.isPrimaryThread()).thenReturn(true);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong()))
                .thenAnswer(invocation -> {
                    warmup.set(invocation.getArgument(1));
                    return task;
                });
        when(player.getUniqueId()).thenReturn(playerId);
        when(server.getPlayer(playerId)).thenReturn(player);
        when(player.teleport(any(Location.class))).thenReturn(true);
        when(facilities.get("origin")).thenReturn(java.util.Optional.of(origin));
        when(facilities.get("south")).thenReturn(java.util.Optional.of(destination));
        FastTravelAccess.AccessDecision decision = new FastTravelAccess.AccessDecision(
                FastTravelAccess.AccessResult.ALLOWED,
                org.aincraft.guilds.territory.model.FastTravelMode.WAYSTONE,
                "guild-a", "guild-a", "guild-a", null, null, null);
        when(access.authorize(playerId, origin, destination)).thenReturn(decision);
        when(landings.find(destination)).thenReturn(java.util.Optional.of(landing));
        when(protection.canTeleportInto(any(), anyInt(), anyInt(), any())).thenReturn(true);
        BuildingConfig config = new BuildingConfig(60_000L,
                Map.of(FacilityType.WAYSTONE, Set.of(org.bukkit.Material.LODESTONE)),
                100L, 60_000L);
        FastTravelCostCalculator costs = new FastTravelCostCalculator(
                org.aincraft.guilds.config.TravelCurrencyConfig.defaults());
        FastTravelService service = new FastTravelService(plugin, facilities, anchors, access,
                landings, protection, config, currency, costs, null);
        return new TravelHarness(service, player, playerId, origin, destination, access,
                server, warmup);
    }

    private record TravelHarness(FastTravelService service, Player player, UUID playerId,
                                 SettlementFacility origin, SettlementFacility destination,
                                 FastTravelAccess access, Server server,
                                 AtomicReference<Runnable> warmup) {
    }

    private static final class RecordingCurrency extends ImmediateCurrency {
        private final TravelCurrencyService.ReservationStatus commitStatus;
        private int commitCount;
        private int releaseCount;

        private RecordingCurrency(TravelCurrencyService.ReservationStatus commitStatus) {
            this.commitStatus = commitStatus;
        }

        @Override
        public CompletionStage<ReservationResult> commit(String reservationId, long nowMillis) {
            commitCount++;
            return CompletableFuture.completedFuture(new ReservationResult(commitStatus));
        }

        @Override
        public CompletionStage<ReservationResult> release(String reservationId, long nowMillis) {
            releaseCount++;
            return CompletableFuture.completedFuture(new ReservationResult(
                    ReservationStatus.RELEASED));
        }
    }

    private static class ImmediateCurrency implements TravelCurrencyService {
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
