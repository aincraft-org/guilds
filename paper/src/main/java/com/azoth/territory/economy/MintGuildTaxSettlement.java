package com.azoth.territory.economy;

import org.aincraft.guilds.services.MintGuildBankService;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Async tax seam backed by the coordinated Mint guild-bank service. */
public final class MintGuildTaxSettlement implements AsyncTaxSettlement {
    private final MintGuildBankService bank;
    public MintGuildTaxSettlement(MintGuildBankService bank) { this.bank = Objects.requireNonNull(bank, "bank"); }
    @Override public CompletionStage<AsyncSettlementResult> settle(UUID payerId, String guildId, BigDecimal amount, String key) {
        AsyncTaxSettlement.validate(payerId, guildId, amount, key);
        return bank.creditTax(payerId, guildId, amount, key).thenApply(result -> switch (result.status()) {
            case COMMITTED -> new AsyncSettlementResult(AsyncSettlementResult.Status.COMMITTED,
                    result.diagnosticCode(), result.receiptIdentifier());
            case INSUFFICIENT_FUNDS -> new AsyncSettlementResult(AsyncSettlementResult.Status.INSUFFICIENT_FUNDS,
                    result.diagnosticCode(), result.receiptIdentifier());
            case CAPACITY_EXCEEDED -> new AsyncSettlementResult(AsyncSettlementResult.Status.REJECTED,
                    result.diagnosticCode(), result.receiptIdentifier());
            case UNAUTHORIZED, UNAVAILABLE, REJECTED -> new AsyncSettlementResult(AsyncSettlementResult.Status.UNAVAILABLE,
                    result.diagnosticCode(), result.receiptIdentifier());
        });
    }
}
