package org.aincraft.guilds.services;

import org.aincraft.guilds.models.GuildContract;

import java.util.List;
import java.util.Optional;

/**
 * Service API for guild contracts — cross-guild level-up material contracts.
 *
 * <p>A contracting guild posts a contract for materials it needs to level up, and the
 * payment is held in escrow (debited from the contracting guild's balance at post time).
 * Another guild fulfills the contract by supplying the materials; the escrowed payment is
 * then released to the fulfilling guild's balance. Cancelling an open contract refunds the
 * escrow to the contracting guild.
 */
public interface GuildContractService {

    /**
     * Post a new contract, debiting {@code payment} from the contracting guild's balance
     * into escrow. Fails if the guild cannot afford the payment.
     *
     * @param contractingGuildId Contracting guild id
     * @param resourceType       Material type (e.g. DIAMOND)
     * @param amount             Total units needed (must be &gt; 0)
     * @param payment            Total payment held in escrow (must be &gt; 0)
     * @return Creation result
     */
    ContractResult createContract(String contractingGuildId, String resourceType, int amount, double payment);

    /**
     * Get all currently open contracts.
     *
     * @return List of open contracts
     */
    List<GuildContract> getOpenContracts();

    /**
     * Get all contracts posted by a specific guild.
     *
     * @param guildId Guild id
     * @return List of contracts posted by the guild
     */
    List<GuildContract> getContractsForGuild(String guildId);

    /**
     * Get a single contract by id.
     *
     * @param contractId Contract id
     * @return Contract if found
     */
    Optional<GuildContract> getContract(String contractId);

    /**
     * Fulfill an open contract on behalf of a fulfilling guild.
     *
     * <p>Atomically: adds the contract's {@code amount} of {@code resourceType} to the
     * contracting guild's {@code upgrade_progress}, credits the escrowed {@code payment} to
     * the fulfilling guild's balance, and marks the contract {@code FULFILLED}.
     *
     * @param contractId        Contract to fulfill
     * @param fulfillingGuildId Guild supplying the materials
     * @return Fulfillment result
     */
    FulfillResult fulfillContract(String contractId, String fulfillingGuildId);

    /**
     * Cancel an open contract, refunding the escrowed payment to the contracting guild.
     * Only the contracting guild may cancel.
     *
     * @param contractId        Contract to cancel
     * @param contractingGuildId Guild requesting the cancellation
     * @return True if the contract was cancelled
     */
    boolean cancelContract(String contractId, String contractingGuildId);

    /** Result of a contract creation attempt. */
    class ContractResult {
        private final boolean successful;
        private final ContractStatus status;
        private final String message;
        private final GuildContract contract;

        public ContractResult(boolean successful, ContractStatus status, String message, GuildContract contract) {
            this.successful = successful;
            this.status = status;
            this.message = message;
            this.contract = contract;
        }

        public boolean isSuccessful() { return successful; }
        public ContractStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public GuildContract getContract() { return contract; }
    }

    /** Result of a contract fulfillment attempt. */
    class FulfillResult {
        private final boolean successful;
        private final FulfillStatus status;
        private final String message;
        private final double paymentReleased;

        public FulfillResult(boolean successful, FulfillStatus status, String message, double paymentReleased) {
            this.successful = successful;
            this.status = status;
            this.message = message;
            this.paymentReleased = paymentReleased;
        }

        public boolean isSuccessful() { return successful; }
        public FulfillStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public double getPaymentReleased() { return paymentReleased; }
    }

    /** Contract creation statuses. */
    public enum ContractStatus {
        CREATED,
        GUILD_NOT_FOUND,
        INVALID_AMOUNT,
        INVALID_PAYMENT,
        INSUFFICIENT_FUNDS
    }

    /** Contract fulfillment statuses. */
    public enum FulfillStatus {
        FULFILLED,
        NOT_FOUND,
        NOT_OPEN,
        SELF_FULFILL
    }
}