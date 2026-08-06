package com.azoth.territory.economy;

import java.util.Objects;

/** Immutable result of a treasury-to-sink debit. */
public record TreasuryDebitResult(TreasuryDebitStatus status) {
    public TreasuryDebitResult {
        Objects.requireNonNull(status, "status");
    }
}
