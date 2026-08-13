package org.aincraft.guilds.services;

import com.azoth.territory.economy.MintOperationResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Narrow asynchronous Mint contract consumed by guild-bank coordination. */
public interface MintTransferPort {
    CompletionStage<MintOperationResult> openAccount(String guildId);

    CompletionStage<MintOperationResult> balance(String guildId);

    CompletionStage<MintOperationResult> deposit(UUID playerUuid, String guildId, BigDecimal amount, String idempotencyKey);

    CompletionStage<MintOperationResult> withdraw(UUID playerUuid, String guildId, BigDecimal amount, String idempotencyKey);

    CompletionStage<MintOperationResult> creditTax(UUID payerUuid, String guildId, BigDecimal amount, String idempotencyKey);
}
