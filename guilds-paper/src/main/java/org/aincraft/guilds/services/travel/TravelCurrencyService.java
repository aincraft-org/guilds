package org.aincraft.guilds.services.travel;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Asynchronous durable API for personal travel currency and reservations. */
public interface TravelCurrencyService {
    CompletionStage<WalletSnapshot> wallet(UUID playerId);

    CompletionStage<ReserveResult> reserve(UUID playerId, String tripId, long amount, long nowMillis);

    CompletionStage<ReservationResult> commit(String reservationId, long nowMillis);

    CompletionStage<ReservationResult> release(String reservationId, long nowMillis);

    CompletionStage<RewardResult> award(UUID playerId, TravelCurrencyRewardSource source,
                                        String eventId, long amount, long nowMillis);

    CompletionStage<Integer> recoverExpired(long nowMillis);

    enum ReserveStatus {
        RESERVED,
        INSUFFICIENT,
        DUPLICATE_TRIP,
        INVALID_AMOUNT,
        FAILED
    }

    enum ReservationStatus {
        COMMITTED,
        RELEASED,
        ALREADY_COMMITTED,
        ALREADY_RELEASED,
        EXPIRED,
        NOT_FOUND
    }

    enum RewardStatus {
        AWARDED,
        DUPLICATE,
        INVALID_AMOUNT,
        FAILED
    }

    record ReserveResult(ReserveStatus status, String reservationId, long balance) {
        public long postReservationBalance() {
            return balance;
        }

        public long postBalance() {
            return balance;
        }
    }

    record ReservationResult(ReservationStatus status) {
    }

    record RewardResult(RewardStatus status, WalletSnapshot wallet) {
        public WalletSnapshot walletSnapshot() {
            return wallet;
        }

        public WalletSnapshot resultingWallet() {
            return wallet;
        }
    }
}
