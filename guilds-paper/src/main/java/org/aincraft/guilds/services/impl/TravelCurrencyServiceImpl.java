package org.aincraft.guilds.services.impl;

import org.aincraft.guilds.config.TravelCurrencyConfig;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.services.travel.TravelCurrencyRewardSource;
import org.aincraft.guilds.services.travel.TravelCurrencyService;
import org.aincraft.guilds.services.travel.WalletSnapshot;
import org.aincraft.guilds.territory.persist.SqlStatements;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

/** SQL-backed, asynchronous implementation of personal travel currency. */
public final class TravelCurrencyServiceImpl implements TravelCurrencyService {
    private static final String RESERVED = "RESERVED";
    private static final String COMMITTED = "COMMITTED";
    private static final String RELEASED = "RELEASED";

    private final DatabaseManager databaseManager;
    private final TravelCurrencyConfig config;
    private final Executor executor;
    private final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> tripLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> awardLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> activeTrips = new ConcurrentHashMap<>();

    public TravelCurrencyServiceImpl(DatabaseManager databaseManager, TravelCurrencyConfig config) {
        this(databaseManager, config, ForkJoinPool.commonPool());
    }

    public TravelCurrencyServiceImpl(DatabaseManager databaseManager, TravelCurrencyConfig config,
                                     Executor executor) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.config = Objects.requireNonNull(config, "config");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public CompletionStage<WalletSnapshot> wallet(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return supplyAsync(() -> withPlayerLock(playerId, () -> walletInTransaction(playerId)));
    }

    @Override
    public CompletionStage<ReserveResult> reserve(UUID playerId, String tripId, long amount, long nowMillis) {
        if (amount <= 0L || amount > config.maximumBalance()) {
            return CompletableFuture.completedFuture(
                    new ReserveResult(ReserveStatus.INVALID_AMOUNT, null, 0L));
        }
        if (playerId == null || tripId == null || tripId.isBlank()) {
            return CompletableFuture.completedFuture(new ReserveResult(ReserveStatus.FAILED, null, 0L));
        }
        final long expiresAt;
        try {
            expiresAt = Math.addExact(nowMillis, config.reservationDurationMillis());
        } catch (ArithmeticException e) {
            return CompletableFuture.completedFuture(new ReserveResult(ReserveStatus.FAILED, null, 0L));
        }

        return supplyAsync(() -> {
            Object tripLock = tripLocks.computeIfAbsent(tripId, ignored -> new Object());
            synchronized (tripLock) {
                return withPlayerLock(playerId, () -> {
                    try {
                        ReserveResult result = reserveInTransaction(
                                playerId, tripId, amount, nowMillis, expiresAt);
                        if (result.status() == ReserveStatus.RESERVED) {
                            activeTrips.put(tripId, result.reservationId());
                        }
                        return result;
                    } catch (RuntimeException e) {
                        return new ReserveResult(ReserveStatus.FAILED, null, 0L);
                    }
                });
            }
        });
    }

    @Override
    public CompletionStage<ReservationResult> commit(String reservationId, long nowMillis) {
        if (reservationId == null || reservationId.isBlank()) {
            return CompletableFuture.completedFuture(new ReservationResult(ReservationStatus.NOT_FOUND));
        }
        return supplyAsync(() -> {
            try {
                ReservationResult result = databaseManager.executeTransactionWithResult(
                        connection -> commitInTransaction(connection, reservationId, nowMillis))
                        .orElseThrow(() -> new IllegalStateException("commit transaction failed"));
                removeActiveTrip(reservationId, result.status());
                return result;
            } catch (RuntimeException e) {
                return new ReservationResult(ReservationStatus.NOT_FOUND);
            }
        });
    }

    @Override
    public CompletionStage<ReservationResult> release(String reservationId, long nowMillis) {
        if (reservationId == null || reservationId.isBlank()) {
            return CompletableFuture.completedFuture(new ReservationResult(ReservationStatus.NOT_FOUND));
        }
        return supplyAsync(() -> {
            try {
                ReservationResult result = databaseManager.executeTransactionWithResult(
                        connection -> releaseInTransaction(connection, reservationId, nowMillis))
                        .orElseThrow(() -> new IllegalStateException("release transaction failed"));
                removeActiveTrip(reservationId, result.status());
                return result;
            } catch (RuntimeException e) {
                return new ReservationResult(ReservationStatus.NOT_FOUND);
            }
        });
    }

    @Override
    public CompletionStage<RewardResult> award(UUID playerId, TravelCurrencyRewardSource source,
                                                String eventId, long amount, long nowMillis) {
        if (playerId == null || source == null || eventId == null || eventId.isBlank()) {
            return CompletableFuture.completedFuture(new RewardResult(RewardStatus.FAILED, null));
        }
        if (amount <= 0L) {
            return CompletableFuture.completedFuture(new RewardResult(RewardStatus.INVALID_AMOUNT, null));
        }
        String eventKey = source.name() + "|" + eventId;
        return supplyAsync(() -> {
            Object awardLock = awardLocks.computeIfAbsent(eventKey, ignored -> new Object());
            synchronized (awardLock) {
                return withPlayerLock(playerId, () -> {
                    try {
                        return databaseManager.executeTransactionWithResult(connection ->
                                        awardInTransaction(connection, playerId, source, eventId, amount, nowMillis))
                                .orElseThrow(() -> new IllegalStateException("award transaction failed"));
                    } catch (RuntimeException e) {
                        return new RewardResult(RewardStatus.FAILED, null);
                    }
                });
            }
        });
    }

    @Override
    public CompletionStage<Integer> recoverExpired(long nowMillis) {
        return supplyAsync(() -> {
            try {
                RecoveryResult result = databaseManager.executeTransactionWithResult(
                                connection -> recoverInTransaction(connection, nowMillis))
                        .orElseThrow(() -> new IllegalStateException("recovery transaction failed"));
                for (String reservationId : result.reservationIds()) {
                    removeActiveTrip(reservationId, ReservationStatus.EXPIRED);
                }
                return result.count();
            } catch (RuntimeException e) {
                return 0;
            }
        });
    }

    private WalletSnapshot walletInTransaction(UUID playerId) {
        return databaseManager.executeTransactionWithResult(connection ->
                        ensureWallet(connection, playerId, System.currentTimeMillis()))
                .orElseThrow(() -> new IllegalStateException("wallet transaction failed"));
    }

    private ReserveResult reserveInTransaction(UUID playerId, String tripId, long amount,
                                                long nowMillis, long expiresAt) {
        return databaseManager.executeTransactionWithResult(connection -> {
            Optional<ReservationRecord> duplicate = selectReservationByTrip(connection, tripId);
            if (duplicate.isPresent()) {
                long balance = selectWallet(connection, playerId)
                        .map(WalletSnapshot::balance).orElse(0L);
                return new ReserveResult(ReserveStatus.DUPLICATE_TRIP, null, balance);
            }
            WalletSnapshot wallet = ensureWallet(connection, playerId, nowMillis);
            int updated = debitWallet(connection, playerId, amount, nowMillis);
            if (updated == 0) {
                WalletSnapshot unchanged = selectWallet(connection, playerId)
                        .orElseThrow(() -> new SQLException("wallet disappeared during reserve"));
                return new ReserveResult(ReserveStatus.INSUFFICIENT, null, unchanged.balance());
            }
            String reservationId = UUID.randomUUID().toString();
            insertReservation(connection, reservationId, tripId, playerId, amount, expiresAt, nowMillis);
            WalletSnapshot after = selectWallet(connection, playerId)
                    .orElseThrow(() -> new SQLException("wallet missing after reserve"));
            return new ReserveResult(ReserveStatus.RESERVED, reservationId, after.balance());
        }).orElseThrow(() -> new IllegalStateException("reserve transaction failed"));
    }

    private ReservationResult commitInTransaction(Connection connection, String reservationId, long nowMillis)
            throws SQLException {
        Optional<ReservationRecord> selected = selectReservation(connection, reservationId);
        if (selected.isEmpty()) {
            return new ReservationResult(ReservationStatus.NOT_FOUND);
        }
        ReservationRecord reservation = selected.get();
        if (COMMITTED.equals(reservation.status())) {
            return new ReservationResult(ReservationStatus.ALREADY_COMMITTED);
        }
        if (RELEASED.equals(reservation.status())) {
            return new ReservationResult(ReservationStatus.ALREADY_RELEASED);
        }
        if (!RESERVED.equals(reservation.status())) {
            return new ReservationResult(ReservationStatus.NOT_FOUND);
        }
        if (reservation.expiresAt() <= nowMillis) {
            recoverReservation(connection, reservation, nowMillis);
            return new ReservationResult(ReservationStatus.EXPIRED);
        }
        int updated = updateReservation(connection, "travel/commit-reservation.sql", nowMillis, reservationId);
        if (updated == 1) {
            return new ReservationResult(ReservationStatus.COMMITTED);
        }
        return reservationResultAfterRace(connection, reservationId);
    }

    private ReservationResult releaseInTransaction(Connection connection, String reservationId, long nowMillis)
            throws SQLException {
        Optional<ReservationRecord> selected = selectReservation(connection, reservationId);
        if (selected.isEmpty()) {
            return new ReservationResult(ReservationStatus.NOT_FOUND);
        }
        ReservationRecord reservation = selected.get();
        if (COMMITTED.equals(reservation.status())) {
            return new ReservationResult(ReservationStatus.ALREADY_COMMITTED);
        }
        if (RELEASED.equals(reservation.status())) {
            return new ReservationResult(ReservationStatus.ALREADY_RELEASED);
        }
        if (!RESERVED.equals(reservation.status())) {
            return new ReservationResult(ReservationStatus.NOT_FOUND);
        }
        if (reservation.expiresAt() <= nowMillis) {
            recoverReservation(connection, reservation, nowMillis);
            return new ReservationResult(ReservationStatus.EXPIRED);
        }
        int updated = updateReservation(connection, "travel/release-reservation.sql", nowMillis, reservationId);
        if (updated == 1) {
            creditWallet(connection, reservation.playerId(), reservation.amount(), nowMillis);
            return new ReservationResult(ReservationStatus.RELEASED);
        }
        return reservationResultAfterRace(connection, reservationId);
    }

    private RewardResult awardInTransaction(Connection connection, UUID playerId,
                                            TravelCurrencyRewardSource source, String eventId,
                                            long amount, long nowMillis) throws SQLException {
        if (awardExists(connection, source, eventId)) {
            return new RewardResult(RewardStatus.DUPLICATE, selectWallet(connection, playerId).orElse(null));
        }
        ensureWallet(connection, playerId, nowMillis);
        int inserted;
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("travel/insert-award.sql"))) {
            statement.setString(1, source.name());
            statement.setString(2, eventId);
            statement.setString(3, playerId.toString());
            statement.setLong(4, amount);
            statement.setString(5, timestamp(nowMillis));
            inserted = statement.executeUpdate();
        }
        if (inserted != 1) {
            throw new SQLException("award insert affected " + inserted + " rows");
        }
        creditWallet(connection, playerId, Math.min(amount, config.maximumBalance()), nowMillis);
        return new RewardResult(RewardStatus.AWARDED,
                selectWallet(connection, playerId).orElseThrow(() -> new SQLException("wallet missing after award")));
    }

    private RecoveryResult recoverInTransaction(Connection connection, long nowMillis) throws SQLException {
        List<ReservationRecord> expired = selectExpiredReservations(connection, nowMillis);
        int recovered = 0;
        List<String> reservationIds = new ArrayList<>();
        for (ReservationRecord reservation : expired) {
            int updated = updateReservation(connection, "travel/recover-reservation.sql", nowMillis,
                    reservation.reservationId());
            if (updated == 1) {
                creditWallet(connection, reservation.playerId(), reservation.amount(), nowMillis);
                recovered++;
                reservationIds.add(reservation.reservationId());
            }
        }
        return new RecoveryResult(recovered, List.copyOf(reservationIds));
    }

    private void recoverReservation(Connection connection, ReservationRecord reservation, long nowMillis)
            throws SQLException {
        int updated = updateReservation(connection, "travel/recover-reservation.sql", nowMillis,
                reservation.reservationId());
        if (updated != 1) {
            throw new SQLException("reservation changed during expiry recovery");
        }
        creditWallet(connection, reservation.playerId(), reservation.amount(), nowMillis);
    }

    private ReservationResult reservationResultAfterRace(Connection connection, String reservationId)
            throws SQLException {
        Optional<ReservationRecord> current = selectReservation(connection, reservationId);
        if (current.isEmpty()) {
            return new ReservationResult(ReservationStatus.NOT_FOUND);
        }
        return switch (current.get().status()) {
            case COMMITTED -> new ReservationResult(ReservationStatus.ALREADY_COMMITTED);
            case RELEASED -> new ReservationResult(ReservationStatus.ALREADY_RELEASED);
            default -> new ReservationResult(ReservationStatus.NOT_FOUND);
        };
    }

    private WalletSnapshot ensureWallet(Connection connection, UUID playerId, long nowMillis) throws SQLException {
        Optional<WalletSnapshot> existing = selectWallet(connection, playerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("travel/insert-wallet.sql"))) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, config.starterBalance());
            statement.setString(3, timestamp(nowMillis));
            statement.setString(4, timestamp(nowMillis));
            int inserted = statement.executeUpdate();
            if (inserted != 1) {
                throw new SQLException("wallet insert affected " + inserted + " rows");
            }
        }
        return new WalletSnapshot(playerId, config.starterBalance());
    }

    private int debitWallet(Connection connection, UUID playerId, long amount, long nowMillis)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("travel/update-wallet-balance.sql"))) {
            statement.setLong(1, amount);
            statement.setString(2, timestamp(nowMillis));
            statement.setString(3, playerId.toString());
            statement.setLong(4, amount);
            return statement.executeUpdate();
        }
    }

    private void creditWallet(Connection connection, UUID playerId, long amount, long nowMillis)
            throws SQLException {
        if (amount < 0L || amount > config.maximumBalance()) {
            throw new SQLException("invalid wallet credit amount");
        }
        ensureWallet(connection, playerId, nowMillis);
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("travel/credit-wallet-balance.sql"))) {
            statement.setLong(1, amount);
            statement.setLong(2, config.maximumBalance());
            statement.setLong(3, amount);
            statement.setLong(4, config.maximumBalance());
            statement.setLong(5, amount);
            statement.setLong(6, config.maximumBalance());
            statement.setLong(7, amount);
            statement.setString(8, timestamp(nowMillis));
            statement.setString(9, playerId.toString());
            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new SQLException("wallet credit affected " + updated + " rows");
            }
        }
    }

    private void insertReservation(Connection connection, String reservationId, String tripId, UUID playerId,
                                   long amount, long expiresAt, long createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("travel/insert-reservation.sql"))) {
            statement.setString(1, reservationId);
            statement.setString(2, tripId);
            statement.setString(3, playerId.toString());
            statement.setLong(4, amount);
            statement.setLong(5, expiresAt);
            statement.setLong(6, createdAt);
            int inserted = statement.executeUpdate();
            if (inserted != 1) {
                throw new SQLException("reservation insert affected " + inserted + " rows");
            }
        }
    }

    private int updateReservation(Connection connection, String resource, long timestamp, String reservationId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SqlStatements.load(resource))) {
            statement.setLong(1, timestamp);
            statement.setString(2, reservationId);
            return statement.executeUpdate();
        }
    }

    private Optional<WalletSnapshot> selectWallet(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("travel/select-wallet.sql"))) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new WalletSnapshot(playerId, resultSet.getLong("balance")));
            }
        }
    }

    private Optional<ReservationRecord> selectReservation(Connection connection, String reservationId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("travel/select-reservation.sql"))) {
            statement.setString(1, reservationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readReservation(resultSet)) : Optional.empty();
            }
        }
    }

    private Optional<ReservationRecord> selectReservationByTrip(Connection connection, String tripId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("travel/select-reservation-by-trip.sql"))) {
            statement.setString(1, tripId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readReservation(resultSet)) : Optional.empty();
            }
        }
    }

    private List<ReservationRecord> selectExpiredReservations(Connection connection, long nowMillis)
            throws SQLException {
        List<ReservationRecord> reservations = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                SqlStatements.load("travel/select-expired-reservations.sql"))) {
            statement.setLong(1, nowMillis);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    reservations.add(readReservation(resultSet));
                }
            }
        }
        return reservations;
    }

    private boolean awardExists(Connection connection, TravelCurrencyRewardSource source, String eventId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SqlStatements.load("travel/select-award.sql"))) {
            statement.setString(1, source.name());
            statement.setString(2, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static ReservationRecord readReservation(ResultSet resultSet) throws SQLException {
        return new ReservationRecord(
                resultSet.getString("reservation_id"),
                resultSet.getString("trip_id"),
                UUID.fromString(resultSet.getString("player_uuid")),
                resultSet.getLong("amount"),
                resultSet.getString("status"),
                resultSet.getLong("expires_at"));
    }

    private static String timestamp(long millis) {
        return Long.toString(millis);
    }

    private <T> CompletionStage<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    private <T> T withPlayerLock(UUID playerId, Supplier<T> operation) {
        Object lock = playerLocks.computeIfAbsent(playerId, ignored -> new Object());
        synchronized (lock) {
            return operation.get();
        }
    }

    private void removeActiveTrip(String reservationId, ReservationStatus status) {
        if (status == ReservationStatus.COMMITTED || status == ReservationStatus.RELEASED
                || status == ReservationStatus.EXPIRED || status == ReservationStatus.ALREADY_COMMITTED
                || status == ReservationStatus.ALREADY_RELEASED) {
            activeTrips.entrySet().removeIf(entry -> reservationId.equals(entry.getValue()));
        }
    }

    private record ReservationRecord(String reservationId, String tripId, UUID playerId,
                                     long amount, String status, long expiresAt) {
    }

    private record RecoveryResult(int count, List<String> reservationIds) {
    }
}
