package dev.mintychochip.territory.economy;

import java.util.Objects;

/**
 * Immutable atomic-settlement outcome.
 *
 * @param status settlement status
 */
public record SettlementResult(PaymentRail.SettlementStatus status) {
    /** Validates the settlement status. */
    public SettlementResult {
        Objects.requireNonNull(status, "status");
    }

    /**
     * Creates a settlement result for the supplied status.
     *
     * @param status settlement status
     * @return a settlement result
     */
    public static SettlementResult of(PaymentRail.SettlementStatus status) {
        return new SettlementResult(status);
    }
}
