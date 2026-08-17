package com.azoth.territory.economy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Asynchronous, platform-neutral seam for settling a tax payment. */
@FunctionalInterface
public interface AsyncTaxSettlement {
    /**
     * Settles a tax payment asynchronously.
     *
     * @param payerId identifier of the payer
     * @param guildId identifier of the guild receiving the settlement
     * @param amount positive settlement amount
     * @param idempotencyKey key preventing duplicate settlement
     * @return a stage completed with the settlement outcome
     */
    CompletionStage<AsyncSettlementResult> settle(
            UUID payerId, String guildId, BigDecimal amount, String idempotencyKey);

    /**
     * Validates the required settlement inputs before dispatching to an implementation.
     *
     * @param payerId identifier of the payer
     * @param guildId identifier of the guild
     * @param amount settlement amount
     * @param idempotencyKey idempotency key
     */
    static void validate(UUID payerId, String guildId, BigDecimal amount, String idempotencyKey) {
        Objects.requireNonNull(payerId, "payerId");
        requireText(guildId, "guildId");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        requireText(idempotencyKey, "idempotencyKey");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
