package com.azoth.territory.economy;

import java.util.Objects;

/**
 * Immutable result of a treasury-to-sink debit.
 *
 * @param status debit status
 */
public record TreasuryDebitResult(TreasuryDebitStatus status) {
    /** Validates the debit status. */
    public TreasuryDebitResult {
        Objects.requireNonNull(status, "status");
    }
}
