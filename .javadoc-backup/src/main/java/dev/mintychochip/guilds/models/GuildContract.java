package dev.mintychochip.guilds.models;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A guild contract: a posting by a contracting guild that needs materials for its
 * next level-up, which another guild can fulfill in exchange for the escrowed payment.
 */
public class GuildContract {

    /** Lifecycle of a contract. */
    public enum Status {
        /** The open constant. */
        OPEN,
        /** The fulfilled constant. */
        FULFILLED,
        /** The cancelled constant. */
        CANCELLED
    }

    /** The id. */
    private String id;
    /** The contracting guild id. */
    private String contractingGuildId;
    /** The resource type. */
    private String resourceType;
    /** The amount. */
    private int amount;
    /** The payment. */
    private double payment;
    /** The filled. */
    private int filled;
    /** The status. */
    private Status status;
    /** The fulfilled by guild id. */
    private String fulfilledByGuildId;
    /** The created at. */
    private LocalDateTime createdAt;
    /** The fulfilled at. */
    private LocalDateTime fulfilledAt;

    /**
     * Default constructor for database mapping.
     */
    public GuildContract() {
        this.status = Status.OPEN;
        this.filled = 0;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new contract.
     *
     * @param id                  Contract id
     * @param contractingGuildId  Guild needing the materials
     * @param resourceType        Material type (e.g. DIAMOND)
     * @param amount              Total units needed
     * @param payment             Total payment held in escrow
     */
    public GuildContract(String id, String contractingGuildId, String resourceType,
                         int amount, double payment) {
        this();
        this.id = id;
        this.contractingGuildId = contractingGuildId;
        this.resourceType = resourceType;
        this.amount = amount;
        this.payment = payment;
    }

    /**
     * Returns the id.
     * @return the result
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id.
     * @param id the id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the contracting guild id.
     * @return the result
     */
    public String getContractingGuildId() {
        return contractingGuildId;
    }

    /**
     * Sets the contracting guild id.
     * @param contractingGuildId the contracting guild id
     */
    public void setContractingGuildId(String contractingGuildId) {
        this.contractingGuildId = contractingGuildId;
    }

    /**
     * Returns the resource type.
     * @return the result
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Sets the resource type.
     * @param resourceType the resource type
     */
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    /**
     * Returns the amount.
     * @return the result
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Sets the amount.
     * @param amount the amount
     */
    public void setAmount(int amount) {
        this.amount = amount;
    }

    /**
     * Returns the payment.
     * @return the result
     */
    public double getPayment() {
        return payment;
    }

    /**
     * Sets the payment.
     * @param payment the payment
     */
    public void setPayment(double payment) {
        this.payment = payment;
    }

    /**
     * Returns the filled.
     * @return the result
     */
    public int getFilled() {
        return filled;
    }

    /**
     * Sets the filled.
     * @param filled the filled
     */
    public void setFilled(int filled) {
        this.filled = filled;
    }

    /**
     * Returns the status.
     * @return the result
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Sets the status.
     * @param status the status
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Returns the fulfilled by guild id.
     * @return the result
     */
    public String getFulfilledByGuildId() {
        return fulfilledByGuildId;
    }

    /**
     * Sets the fulfilled by guild id.
     * @param fulfilledByGuildId the fulfilled by guild id
     */
    public void setFulfilledByGuildId(String fulfilledByGuildId) {
        this.fulfilledByGuildId = fulfilledByGuildId;
    }

    /**
     * Returns the created at.
     * @return the result
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the created at.
     * @param createdAt the created at
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the fulfilled at.
     * @return the result
     */
    public LocalDateTime getFulfilledAt() {
        return fulfilledAt;
    }

    /**
     * Sets the fulfilled at.
     * @param fulfilledAt the fulfilled at
     */
    public void setFulfilledAt(LocalDateTime fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
    }

    /**
     * Whether this contract is still open for fulfillment.
     *
     * @return True if {@code status == OPEN}
     */
    public boolean isOpen() {
        return status == Status.OPEN;
    }

    /**
     * Indicates whether another object is equal to this one.
     * @param obj the obj
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        GuildContract that = (GuildContract) obj;
        return Objects.equals(id, that.id);
    }

    /** Returns a hash code for this object. */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /** Returns a string representation of this object. */
    @Override
    public String toString() {
        return "GuildContract{" +
                "id='" + id + '\'' +
                ", contractingGuildId='" + contractingGuildId + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", amount=" + amount +
                ", payment=" + payment +
                ", filled=" + filled +
                ", status=" + status +
                ", fulfilledByGuildId='" + fulfilledByGuildId + '\'' +
                '}';
    }
}