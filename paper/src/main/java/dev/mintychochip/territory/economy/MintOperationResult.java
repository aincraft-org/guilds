package dev.mintychochip.territory.economy;

import java.math.BigDecimal;
import java.util.Optional;

/** Result of an asynchronous Mint account operation. */
public record MintOperationResult(Status status, BigDecimal value, Optional<String> diagnosticCode,
                                  Optional<String> receiptIdentifier) {
    public enum Status { COMMITTED, INSUFFICIENT_FUNDS, UNAVAILABLE, REJECTED }

    public MintOperationResult {
        value = value == null ? null : value.stripTrailingZeros();
        diagnosticCode = diagnosticCode == null ? Optional.empty() : diagnosticCode;
        receiptIdentifier = receiptIdentifier == null ? Optional.empty() : receiptIdentifier;
    }

    public MintOperationResult(Status status, BigDecimal value, String diagnosticCode, String receiptIdentifier) {
        this(status, value, Optional.ofNullable(diagnosticCode), Optional.ofNullable(receiptIdentifier));
    }
}
