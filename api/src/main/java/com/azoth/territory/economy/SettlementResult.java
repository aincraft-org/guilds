package com.azoth.territory.economy;

import java.util.Objects;

/** Immutable atomic-settlement outcome. */
public record SettlementResult(PaymentRail.SettlementStatus status) {
    public SettlementResult {
        Objects.requireNonNull(status, "status");
    }

    public static SettlementResult of(PaymentRail.SettlementStatus status) {
        return new SettlementResult(status);
    }
}
