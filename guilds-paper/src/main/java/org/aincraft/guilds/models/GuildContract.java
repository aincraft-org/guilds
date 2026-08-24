package org.aincraft.guilds.models;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A guild contract: a posting by a contracting guild that needs materials for its
 * next level-up, which another guild can fulfill in exchange for the escrowed payment.
 */
public class GuildContract {

    /** Lifecycle of a contract. */
    public enum Status {
        OPEN,
        FULFILLED,
        CANCELLED
    }

    private String id;
    private String contractingGuildId;
    private String resourceType;
    private int amount;
    private double payment;
    private int filled;
    private Status status;
    private String fulfilledByGuildId;
    private LocalDateTime createdAt;
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContractingGuildId() {
        return contractingGuildId;
    }

    public void setContractingGuildId(String contractingGuildId) {
        this.contractingGuildId = contractingGuildId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public double getPayment() {
        return payment;
    }

    public void setPayment(double payment) {
        this.payment = payment;
    }

    public int getFilled() {
        return filled;
    }

    public void setFilled(int filled) {
        this.filled = filled;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getFulfilledByGuildId() {
        return fulfilledByGuildId;
    }

    public void setFulfilledByGuildId(String fulfilledByGuildId) {
        this.fulfilledByGuildId = fulfilledByGuildId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getFulfilledAt() {
        return fulfilledAt;
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

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