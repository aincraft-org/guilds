package org.aincraft.guilds.services;

import org.aincraft.guilds.GuildsServiceTestFixture;
import org.aincraft.guilds.config.TravelCurrencyConfig;
import org.aincraft.guilds.services.impl.TravelCurrencyServiceImpl;
import org.aincraft.guilds.services.travel.TravelCurrencyRewardSource;
import org.aincraft.guilds.services.travel.TravelCurrencyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TravelCurrencyServiceImplTest {
    @TempDir
    Path tempDir;

    private GuildsServiceTestFixture.Services services;
    private TravelCurrencyService currency;

    @BeforeEach
    void setUp() throws Exception {
        services = GuildsServiceTestFixture.create(tempDir);
        clearTravelData();
        currency = new TravelCurrencyServiceImpl(
                services.databaseManager(), TravelCurrencyConfig.defaults());
    }

    @AfterEach
    void tearDown() {
        if (services != null) {
            services.databaseManager().shutdown();
        }
    }

    @Test
    void firstWalletGetsStarterAndAwardsClampAtCap() {
        UUID player = UUID.randomUUID();

        assertEquals(10L, currency.wallet(player).toCompletableFuture().join().balance());
        TravelCurrencyService.RewardResult awarded = currency.award(
                player, TravelCurrencyRewardSource.QUEST_COMPLETION, "quest-1", 2_000L, 1L)
                .toCompletableFuture().join();
        assertEquals(TravelCurrencyService.RewardStatus.AWARDED, awarded.status());
        assertEquals(1_000L, awarded.wallet().balance());

        TravelCurrencyService.RewardResult duplicate = currency.award(
                player, TravelCurrencyRewardSource.QUEST_COMPLETION, "quest-1", 20L, 2L)
                .toCompletableFuture().join();
        assertEquals(TravelCurrencyService.RewardStatus.DUPLICATE, duplicate.status());
        assertEquals(1_000L, duplicate.wallet().balance());
    }

    @Test
    void concurrentReservationsCannotOverspendAndRejectedReserveLeavesWalletUnchanged() {
        UUID player = UUID.randomUUID();
        CompletableFuture<TravelCurrencyService.ReserveResult> first = currency.reserve(
                player, "trip-a", 7L, 10L).toCompletableFuture();
        CompletableFuture<TravelCurrencyService.ReserveResult> second = currency.reserve(
                player, "trip-b", 7L, 10L).toCompletableFuture();
        CompletableFuture.allOf(first, second).join();

        List<TravelCurrencyService.ReserveStatus> statuses = List.of(first.join().status(), second.join().status());
        assertEquals(1L, statuses.stream().filter(status -> status == TravelCurrencyService.ReserveStatus.RESERVED).count());
        assertEquals(1L, statuses.stream().filter(status -> status == TravelCurrencyService.ReserveStatus.INSUFFICIENT).count());
        assertEquals(3L, currency.wallet(player).toCompletableFuture().join().balance());

        TravelCurrencyService.ReserveResult invalid = currency.reserve(
                player, "trip-invalid", 0L, 10L).toCompletableFuture().join();
        assertEquals(TravelCurrencyService.ReserveStatus.INVALID_AMOUNT, invalid.status());
        assertEquals(3L, currency.wallet(player).toCompletableFuture().join().balance());
    }

    @Test
    void duplicateTripAndCommitReleaseAreIdempotent() {
        UUID player = UUID.randomUUID();
        TravelCurrencyService.ReserveResult reserved = currency.reserve(
                player, "trip-unique", 2L, 100L).toCompletableFuture().join();
        assertEquals(TravelCurrencyService.ReserveStatus.RESERVED, reserved.status());
        TravelCurrencyService.ReserveResult duplicate = currency.reserve(
                player, "trip-unique", 2L, 100L).toCompletableFuture().join();
        assertEquals(TravelCurrencyService.ReserveStatus.DUPLICATE_TRIP, duplicate.status());

        String reservationId = reserved.reservationId();
        assertNotNull(reservationId);
        assertEquals(TravelCurrencyService.ReservationStatus.COMMITTED,
                currency.commit(reservationId, 101L).toCompletableFuture().join().status());
        assertEquals(TravelCurrencyService.ReservationStatus.ALREADY_COMMITTED,
                currency.commit(reservationId, 102L).toCompletableFuture().join().status());

        TravelCurrencyService.ReserveResult toRelease = currency.reserve(
                player, "trip-release", 2L, 200L).toCompletableFuture().join();
        assertEquals(TravelCurrencyService.ReservationStatus.RELEASED,
                currency.release(toRelease.reservationId(), 201L).toCompletableFuture().join().status());
        assertEquals(TravelCurrencyService.ReservationStatus.ALREADY_RELEASED,
                currency.release(toRelease.reservationId(), 202L).toCompletableFuture().join().status());
        assertEquals(8L, currency.wallet(player).toCompletableFuture().join().balance());
    }

    @Test
    void expiryRecoveryReleasesReservationExactlyOnce() {
        UUID player = UUID.randomUUID();
        TravelCurrencyService.ReserveResult reserved = currency.reserve(
                player, "trip-expiring", 6L, 1_000L).toCompletableFuture().join();

        assertEquals(1, currency.recoverExpired(31_001L).toCompletableFuture().join());
        assertEquals(10L, currency.wallet(player).toCompletableFuture().join().balance());
        assertEquals(0, currency.recoverExpired(31_002L).toCompletableFuture().join());
        assertEquals(TravelCurrencyService.ReservationStatus.ALREADY_RELEASED,
                currency.release(reserved.reservationId(), 31_003L).toCompletableFuture().join().status());
    }

    private void clearTravelData() throws Exception {
        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM travel_currency_reservations");
            statement.executeUpdate("DELETE FROM travel_currency_awards");
            statement.executeUpdate("DELETE FROM player_travel_wallets");
        }
    }
}
