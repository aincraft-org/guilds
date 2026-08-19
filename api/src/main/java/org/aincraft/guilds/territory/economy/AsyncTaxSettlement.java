package org.aincraft.guilds.territory.economy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Asynchronous, platform-neutral seam for settling a tax payment. */
@FunctionalInterface
public interface AsyncTaxSettlement {
    CompletionStage<AsyncSettlementResult> settle(
            UUID payerId, String guildId, BigDecimal amount, String idempotencyKey);

    /** Validates the required settlement inputs before dispatching to an implementation. */
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
