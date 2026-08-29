package org.aincraft.guilds.services;

import org.aincraft.guilds.GuildsServiceTestFixture;
import org.aincraft.guilds.config.TravelCurrencyConfig;
import org.aincraft.guilds.database.DatabaseManager;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

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
        currency.wallet(player).toCompletableFuture().join();
        TravelCurrencyService firstService = new TravelCurrencyServiceImpl(
                services.databaseManager(), TravelCurrencyConfig.defaults());
        TravelCurrencyService secondService = new TravelCurrencyServiceImpl(
                services.databaseManager(), TravelCurrencyConfig.defaults());
        CompletableFuture<TravelCurrencyService.ReserveResult> first = firstService.reserve(
                player, "trip-a", 7L, 10L).toCompletableFuture();
        CompletableFuture<TravelCurrencyService.ReserveResult> second = secondService.reserve(
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
    void duplicateTripCreatesRequestedStarterWalletWithoutDebit() throws Exception {
        UUID requestedPlayer = UUID.randomUUID();
        UUID existingPlayer = UUID.randomUUID();
        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO travel_currency_reservations
                         (reservation_id, trip_id, player_uuid, amount, status, expires_at, created_at)
                     VALUES (?, ?, ?, ?, 'RESERVED', ?, ?)
                     """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, "trip-existing");
            statement.setString(3, existingPlayer.toString());
            statement.setLong(4, 1L);
            statement.setLong(5, 100_000L);
            statement.setLong(6, 1L);
            statement.executeUpdate();
        }

        TravelCurrencyService.ReserveResult duplicate = currency.reserve(
                requestedPlayer, "trip-existing", 4L, 10L).toCompletableFuture().join();
        assertEquals(TravelCurrencyService.ReserveStatus.DUPLICATE_TRIP, duplicate.status());
        assertEquals(10L, currency.wallet(requestedPlayer).toCompletableFuture().join().balance());
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

    @Test
    void commitAndReleasePropagateTransactionFailure() {
        DatabaseManager failedDatabase = mock(DatabaseManager.class);
        doReturn(Optional.empty()).when(failedDatabase).executeTransactionWithResult(any());
        TravelCurrencyService failedService = new TravelCurrencyServiceImpl(
                failedDatabase, TravelCurrencyConfig.defaults(), Runnable::run);

        assertThrows(CompletionException.class,
                () -> failedService.commit("reservation", 1L).toCompletableFuture().join());
        assertThrows(CompletionException.class,
                () -> failedService.release("reservation", 1L).toCompletableFuture().join());
    }

    @Test
    void recoveryPropagatesTransactionFailure() {
        DatabaseManager failedDatabase = mock(DatabaseManager.class);
        doReturn(Optional.empty()).when(failedDatabase).executeTransactionWithResult(any());
        TravelCurrencyService failedService = new TravelCurrencyServiceImpl(
                failedDatabase, TravelCurrencyConfig.defaults(), Runnable::run);

        assertThrows(CompletionException.class,
                () -> failedService.recoverExpired(1L).toCompletableFuture().join());
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
