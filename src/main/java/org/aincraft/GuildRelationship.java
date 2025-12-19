package org.aincraft;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a relationship between two guilds.
 * Single Responsibility: Guild relationship data model.
 */
public class GuildRelationship {
    private final String id;
    private final UUID sourceGuildId;
    private final UUID targetGuildId;
    private final RelationType relationType;
    private RelationStatus status;
    private final long createdAt;
    private final UUID createdBy;

    /**
     * Creates a new guild relationship.
     *
     * @param sourceGuildId the guild initiating the relationship
     * @param targetGuildId the guild receiving the relationship
     * @param relationType the type of relationship
     * @param createdBy the player who created this relationship
     */
    public GuildRelationship(UUID sourceGuildId, UUID targetGuildId, RelationType relationType, UUID createdBy) {
        this.id = UUID.randomUUID().toString();
        this.sourceGuildId = Objects.requireNonNull(sourceGuildId, "Source guild ID cannot be null");
        this.targetGuildId = Objects.requireNonNull(targetGuildId, "Target guild ID cannot be null");
        this.relationType = Objects.requireNonNull(relationType, "Relation type cannot be null");
        this.createdBy = Objects.requireNonNull(createdBy, "Creator cannot be null");
        this.createdAt = System.currentTimeMillis();

        // Allies start as PENDING (require acceptance), enemies start as ACTIVE
        this.status = (relationType == RelationType.ALLY) ? RelationStatus.PENDING : RelationStatus.ACTIVE;
    }

    /**
     * Creates a relationship with existing data (for database restoration).
     *
     * @param id existing relationship ID
     * @param sourceGuildId the source guild ID
     * @param targetGuildId the target guild ID
     * @param relationType the type of relationship
     * @param status the status of the relationship
     * @param createdAt creation timestamp
     * @param createdBy creator UUID
     */
    public GuildRelationship(String id, UUID sourceGuildId, UUID targetGuildId,
                           RelationType relationType, RelationStatus status,
                           long createdAt, UUID createdBy) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.sourceGuildId = Objects.requireNonNull(sourceGuildId, "Source guild ID cannot be null");
        this.targetGuildId = Objects.requireNonNull(targetGuildId, "Target guild ID cannot be null");
        this.relationType = Objects.requireNonNull(relationType, "Relation type cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.createdAt = createdAt;
        this.createdBy = Objects.requireNonNull(createdBy, "Creator cannot be null");
    }

    /**
     * Accepts a pending alliance request.
     *
     * @return true if accepted, false if not pending or not an ally request
     */
    public boolean accept() {
        if (relationType == RelationType.ALLY && status == RelationStatus.PENDING) {
            status = RelationStatus.ACTIVE;
            return true;
        }
        return false;
    }

    /**
     * Rejects a pending alliance request.
     *
     * @return true if rejected, false if not pending or not an ally request
     */
    public boolean reject() {
        if (relationType == RelationType.ALLY && status == RelationStatus.PENDING) {
            status = RelationStatus.REJECTED;
            return true;
        }
        return false;
    }

    /**
     * Cancels/ends an active relationship.
     *
     * @return true if cancelled, false if not active
     */
    public boolean cancel() {
        if (status == RelationStatus.ACTIVE) {
            status = RelationStatus.CANCELLED;
            return true;
        }
        return false;
    }

    /**
     * Checks if this relationship involves the given guild (as source or target).
     *
     * @param guildId the guild ID to check
     * @return true if this relationship involves the guild
     */
    public boolean involves(UUID guildId) {
        return sourceGuildId.equals(guildId) || targetGuildId.equals(guildId);
    }

    /**
     * Gets the other guild ID in this relationship.
     *
     * @param guildId one of the guilds in the relationship
     * @return the other guild ID, or null if guildId is not part of this relationship
     */
    public UUID getOtherGuild(UUID guildId) {
        if (sourceGuildId.equals(guildId)) {
            return targetGuildId;
        } else if (targetGuildId.equals(guildId)) {
            return sourceGuildId;
        }
        return null;
    }

    public String getId() {
        return id;
    }

    public UUID getSourceGuildId() {
        return sourceGuildId;
    }

    public UUID getTargetGuildId() {
        return targetGuildId;
    }

    public RelationType getRelationType() {
        return relationType;
    }

    public RelationStatus getStatus() {
        return status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GuildRelationship)) return false;
        GuildRelationship that = (GuildRelationship) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "GuildRelationship{" +
                "id='" + id + '\'' +
                ", sourceGuildId='" + sourceGuildId + '\'' +
                ", targetGuildId='" + targetGuildId + '\'' +
                ", relationType=" + relationType +
                ", status=" + status +
                '}';
    }
}
