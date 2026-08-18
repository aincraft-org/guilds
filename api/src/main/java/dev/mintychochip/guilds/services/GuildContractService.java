package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.models.GuildContract;

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
        /** The successful. */
        private final boolean successful;
        /** The status. */
        private final ContractStatus status;
        /** The message. */
        private final String message;
        /** The contract. */
        private final GuildContract contract;

        /**
         * Creates a new contract result instance.
         * @param successful the successful
         * @param status the status
         * @param message the message
         * @param contract the contract
         */
        public ContractResult(boolean successful, ContractStatus status, String message, GuildContract contract) {
            this.successful = successful;
            this.status = status;
            this.message = message;
            this.contract = contract;
        }

        /**
         * Returns whether successful.
         * @return the result
         */
        public boolean isSuccessful() { return successful; }
        /**
         * Returns the status.
         * @return the result
         */
        public ContractStatus getStatus() { return status; }
        /**
         * Returns the message.
         * @return the result
         */
        public String getMessage() { return message; }
        /**
         * Returns the contract.
         * @return the result
         */
        public GuildContract getContract() { return contract; }
    }

    /** Result of a contract fulfillment attempt. */
    class FulfillResult {
        /** The successful. */
        private final boolean successful;
        /** The status. */
        private final FulfillStatus status;
        /** The message. */
        private final String message;
        /** The payment released. */
        private final double paymentReleased;

        /**
         * Creates a new fulfill result instance.
         * @param successful the successful
         * @param status the status
         * @param message the message
         * @param paymentReleased the payment released
         */
        public FulfillResult(boolean successful, FulfillStatus status, String message, double paymentReleased) {
            this.successful = successful;
            this.status = status;
            this.message = message;
            this.paymentReleased = paymentReleased;
        }

        /**
         * Returns whether successful.
         * @return the result
         */
        public boolean isSuccessful() { return successful; }
        /**
         * Returns the status.
         * @return the result
         */
        public FulfillStatus getStatus() { return status; }
        /**
         * Returns the message.
         * @return the result
         */
        public String getMessage() { return message; }
        /**
         * Returns the payment released.
         * @return the result
         */
        public double getPaymentReleased() { return paymentReleased; }
    }

    /** Contract creation statuses. */
    public enum ContractStatus {
        /** The created constant. */
        CREATED,
        /** The guild not found constant. */
        GUILD_NOT_FOUND,
        /** The invalid amount constant. */
        INVALID_AMOUNT,
        /** The invalid payment constant. */
        INVALID_PAYMENT,
        /** The insufficient funds constant. */
        INSUFFICIENT_FUNDS
    }

    /** Contract fulfillment statuses. */
    public enum FulfillStatus {
        /** The fulfilled constant. */
        FULFILLED,
        /** The not found constant. */
        NOT_FOUND,
        /** The not open constant. */
        NOT_OPEN,
        /** The self fulfill constant. */
        SELF_FULFILL
    }
}