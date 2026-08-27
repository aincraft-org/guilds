package dev.mintychochip.guilds.services;

import dev.mintychochip.territory.economy.MintOperationResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Narrow asynchronous Mint contract consumed by guild-bank coordination. */
public interface MintTransferPort {
    /**
     * Performs the open account operation.
     * @param playerUuid the player uuid
     * @param guildId the guild id
     * @return the result
     */
    CompletionStage<MintOperationResult> openAccount(UUID playerUuid, String guildId);

    /**
     * Performs the balance operation.
     * @param guildId the guild id
     * @return the result
     */
    CompletionStage<MintOperationResult> balance(String guildId);

    /**
     * Performs the deposit operation.
     * @param playerUuid the player uuid
     * @param guildId the guild id
     * @param amount the amount
     * @param idempotencyKey the idempotency key
     * @return the result
     */
    CompletionStage<MintOperationResult> deposit(UUID playerUuid, String guildId, BigDecimal amount, String idempotencyKey);

    /**
     * Performs the withdraw operation.
     * @param playerUuid the player uuid
     * @param guildId the guild id
     * @param amount the amount
     * @param idempotencyKey the idempotency key
     * @return the result
     */
    CompletionStage<MintOperationResult> withdraw(UUID playerUuid, String guildId, BigDecimal amount, String idempotencyKey);

    /**
     * Performs the credit tax operation.
     * @param payerUuid the payer uuid
     * @param guildId the guild id
     * @param amount the amount
     * @param idempotencyKey the idempotency key
     * @return the result
     */
    CompletionStage<MintOperationResult> creditTax(UUID payerUuid, String guildId, BigDecimal amount, String idempotencyKey);
}
