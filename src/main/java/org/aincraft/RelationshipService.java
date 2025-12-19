package org.aincraft;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.aincraft.storage.GuildRelationshipRepository;
import org.aincraft.storage.GuildRepository;

/**
 * Service layer for guild relationship operations.
 * Single Responsibility: Guild relationship business logic.
 * Open/Closed: Depends on abstractions, not implementations.
 * Dependency Inversion: Injected dependencies via constructor.
 */
public class RelationshipService {
    private static final int DEFAULT_MAX_ALLIES = 10;

    private final GuildRelationshipRepository relationshipRepository;
    private final GuildRepository guildRepository;
    private final int maxAllies;

    @Inject
    public RelationshipService(GuildRelationshipRepository relationshipRepository,
                             GuildRepository guildRepository) {
        this.relationshipRepository = Objects.requireNonNull(relationshipRepository);
        this.guildRepository = Objects.requireNonNull(guildRepository);
        this.maxAllies = DEFAULT_MAX_ALLIES;
    }

    /**
     * Proposes an alliance to another guild.
     *
     * @param sourceGuildId the guild proposing the alliance
     * @param targetGuildId the guild receiving the proposal
     * @param proposerId the player proposing (must be owner/have permission)
     * @return the created relationship, or null if failed
     */
    public GuildRelationship proposeAlliance(UUID sourceGuildId, UUID targetGuildId, UUID proposerId) {
        Objects.requireNonNull(sourceGuildId, "Source guild ID cannot be null");
        Objects.requireNonNull(targetGuildId, "Target guild ID cannot be null");
        Objects.requireNonNull(proposerId, "Proposer ID cannot be null");

        if (sourceGuildId.equals(targetGuildId)) {
            return null; // Can't ally with self
        }

        // Check both guilds exist
        if (guildRepository.findById(sourceGuildId).isEmpty() ||
            guildRepository.findById(targetGuildId).isEmpty()) {
            return null;
        }

        // Check if already have a relationship
        Optional<GuildRelationship> existing = relationshipRepository.findRelationship(sourceGuildId, targetGuildId);
        if (existing.isPresent()) {
            return null; // Already have a relationship
        }

        // Check max allies limit
        long allyCount = getAllies(sourceGuildId).size();
        if (allyCount >= maxAllies) {
            return null; // Too many allies
        }

        GuildRelationship relationship = new GuildRelationship(
            sourceGuildId, targetGuildId, RelationType.ALLY, proposerId
        );
        relationshipRepository.save(relationship);
        return relationship;
    }

    /**
     * Accepts a pending alliance request.
     *
     * @param targetGuildId the guild accepting the alliance
     * @param sourceGuildId the guild that proposed
     * @param accepterId the player accepting (must be owner/have permission)
     * @return true if accepted, false otherwise
     */
    public boolean acceptAlliance(UUID targetGuildId, UUID sourceGuildId, UUID accepterId) {
        Objects.requireNonNull(targetGuildId, "Target guild ID cannot be null");
        Objects.requireNonNull(sourceGuildId, "Source guild ID cannot be null");
        Objects.requireNonNull(accepterId, "Accepter ID cannot be null");

        // Find pending alliance request
        List<GuildRelationship> pendingRequests = relationshipRepository.findByType(
            targetGuildId, RelationType.ALLY, RelationStatus.PENDING
        );

        Optional<GuildRelationship> request = pendingRequests.stream()
            .filter(r -> r.getSourceGuildId().equals(sourceGuildId))
            .findFirst();

        if (request.isEmpty()) {
            return false;
        }

        GuildRelationship relationship = request.get();
        if (relationship.accept()) {
            relationshipRepository.save(relationship);
            return true;
        }

        return false;
    }

    /**
     * Rejects a pending alliance request.
     *
     * @param targetGuildId the guild rejecting
     * @param sourceGuildId the guild that proposed
     * @param rejecterId the player rejecting
     * @return true if rejected, false otherwise
     */
    public boolean rejectAlliance(UUID targetGuildId, UUID sourceGuildId, UUID rejecterId) {
        Objects.requireNonNull(targetGuildId, "Target guild ID cannot be null");
        Objects.requireNonNull(sourceGuildId, "Source guild ID cannot be null");
        Objects.requireNonNull(rejecterId, "Rejecter ID cannot be null");

        List<GuildRelationship> pendingRequests = relationshipRepository.findByType(
            targetGuildId, RelationType.ALLY, RelationStatus.PENDING
        );

        Optional<GuildRelationship> request = pendingRequests.stream()
            .filter(r -> r.getSourceGuildId().equals(sourceGuildId))
            .findFirst();

        if (request.isEmpty()) {
            return false;
        }

        GuildRelationship relationship = request.get();
        if (relationship.reject()) {
            relationshipRepository.save(relationship);
            return true;
        }

        return false;
    }

    /**
     * Breaks an active alliance.
     *
     * @param guildId the guild breaking the alliance
     * @param allyGuildId the ally guild
     * @param breakerId the player breaking the alliance
     * @return true if broken, false otherwise
     */
    public boolean breakAlliance(UUID guildId, UUID allyGuildId, UUID breakerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(allyGuildId, "Ally guild ID cannot be null");
        Objects.requireNonNull(breakerId, "Breaker ID cannot be null");

        Optional<GuildRelationship> relationship = relationshipRepository.findRelationship(guildId, allyGuildId);
        if (relationship.isEmpty() || relationship.get().getRelationType() != RelationType.ALLY) {
            return false;
        }

        GuildRelationship rel = relationship.get();
        if (rel.cancel()) {
            relationshipRepository.save(rel);
            return true;
        }

        return false;
    }

    /**
     * Declares another guild as an enemy.
     *
     * @param sourceGuildId the guild declaring
     * @param targetGuildId the guild being declared as enemy
     * @param declarerId the player declaring
     * @return the created relationship, or null if failed
     */
    public GuildRelationship declareEnemy(UUID sourceGuildId, UUID targetGuildId, UUID declarerId) {
        Objects.requireNonNull(sourceGuildId, "Source guild ID cannot be null");
        Objects.requireNonNull(targetGuildId, "Target guild ID cannot be null");
        Objects.requireNonNull(declarerId, "Declarer ID cannot be null");

        if (sourceGuildId.equals(targetGuildId)) {
            return null; // Can't be enemy with self
        }

        // Check both guilds exist
        if (guildRepository.findById(sourceGuildId).isEmpty() ||
            guildRepository.findById(targetGuildId).isEmpty()) {
            return null;
        }

        // Check if already have a relationship
        Optional<GuildRelationship> existing = relationshipRepository.findRelationship(sourceGuildId, targetGuildId);
        if (existing.isPresent()) {
            // Can't declare enemy if allied
            if (existing.get().getRelationType() == RelationType.ALLY &&
                existing.get().getStatus() == RelationStatus.ACTIVE) {
                return null;
            }
            // Cancel existing relationship first
            existing.get().cancel();
            relationshipRepository.save(existing.get());
        }

        // Create bidirectional enemy relationships
        GuildRelationship relationship1 = new GuildRelationship(
            sourceGuildId, targetGuildId, RelationType.ENEMY, declarerId
        );
        relationshipRepository.save(relationship1);

        // Create reverse relationship for symmetry
        GuildRelationship relationship2 = new GuildRelationship(
            targetGuildId, sourceGuildId, RelationType.ENEMY, declarerId
        );
        relationshipRepository.save(relationship2);

        return relationship1;
    }

    /**
     * Declares neutral status with another guild (removes relationship).
     *
     * @param sourceGuildId the guild declaring neutral
     * @param targetGuildId the other guild
     * @param declarerId the player declaring
     * @return true if relationship removed, false otherwise
     */
    public boolean declareNeutral(UUID sourceGuildId, UUID targetGuildId, UUID declarerId) {
        Objects.requireNonNull(sourceGuildId, "Source guild ID cannot be null");
        Objects.requireNonNull(targetGuildId, "Target guild ID cannot be null");
        Objects.requireNonNull(declarerId, "Declarer ID cannot be null");

        Optional<GuildRelationship> relationship = relationshipRepository.findRelationship(sourceGuildId, targetGuildId);
        if (relationship.isEmpty()) {
            return false;
        }

        // For enemies, remove both directions
        if (relationship.get().getRelationType() == RelationType.ENEMY) {
            List<GuildRelationship> allRelations = relationshipRepository.findAllByGuild(sourceGuildId);
            allRelations.stream()
                .filter(r -> r.getRelationType() == RelationType.ENEMY)
                .filter(r -> r.involves(targetGuildId))
                .forEach(r -> relationshipRepository.delete(r.getId()));
            return true;
        }

        // For allies or pending, just cancel the single relationship
        relationshipRepository.delete(relationship.get().getId());
        return true;
    }

    /**
     * Gets the relationship type between two guilds.
     *
     * @param guildId1 first guild ID
     * @param guildId2 second guild ID
     * @return the relation type, or null if no active relationship
     */
    public RelationType getRelationType(UUID guildId1, UUID guildId2) {
        Objects.requireNonNull(guildId1, "Guild ID 1 cannot be null");
        Objects.requireNonNull(guildId2, "Guild ID 2 cannot be null");

        Optional<GuildRelationship> relationship = relationshipRepository.findRelationship(guildId1, guildId2);
        return relationship.map(GuildRelationship::getRelationType).orElse(null);
    }

    /**
     * Gets all active allies for a guild.
     *
     * @param guildId the guild ID
     * @return list of ally guild IDs
     */
    public List<UUID> getAllies(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        return relationshipRepository.findByType(guildId, RelationType.ALLY, RelationStatus.ACTIVE)
            .stream()
            .map(r -> r.getOtherGuild(guildId))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Gets all active enemies for a guild.
     *
     * @param guildId the guild ID
     * @return list of enemy guild IDs
     */
    public List<UUID> getEnemies(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        return relationshipRepository.findByType(guildId, RelationType.ENEMY, RelationStatus.ACTIVE)
            .stream()
            .map(r -> r.getOtherGuild(guildId))
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * Gets pending alliance requests for a guild (incoming).
     *
     * @param guildId the guild ID
     * @return list of relationships where this guild is the target
     */
    public List<GuildRelationship> getPendingAllyRequests(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        return relationshipRepository.findByType(guildId, RelationType.ALLY, RelationStatus.PENDING)
            .stream()
            .filter(r -> r.getTargetGuildId().equals(guildId))
            .collect(Collectors.toList());
    }

    /**
     * Gets sent alliance requests (outgoing).
     *
     * @param guildId the guild ID
     * @return list of relationships where this guild is the source
     */
    public List<GuildRelationship> getSentAllyRequests(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        return relationshipRepository.findByType(guildId, RelationType.ALLY, RelationStatus.PENDING)
            .stream()
            .filter(r -> r.getSourceGuildId().equals(guildId))
            .collect(Collectors.toList());
    }

    /**
     * Checks if two guilds are allies.
     *
     * @param guildId1 first guild ID
     * @param guildId2 second guild ID
     * @return true if allied, false otherwise
     */
    public boolean areAllies(UUID guildId1, UUID guildId2) {
        RelationType type = getRelationType(guildId1, guildId2);
        return type == RelationType.ALLY;
    }

    /**
     * Checks if two guilds are enemies.
     *
     * @param guildId1 first guild ID
     * @param guildId2 second guild ID
     * @return true if enemies, false otherwise
     */
    public boolean areEnemies(UUID guildId1, UUID guildId2) {
        RelationType type = getRelationType(guildId1, guildId2);
        return type == RelationType.ENEMY;
    }
}
