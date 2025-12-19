package org.aincraft.storage;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.UUID;
import org.aincraft.GuildRelationship;
import org.aincraft.RelationStatus;
import org.aincraft.RelationType;

/**
 * In-memory implementation for testing.
 */
public class InMemoryGuildRelationshipRepository implements GuildRelationshipRepository {
    private final Map<String, GuildRelationship> relationshipsById = new ConcurrentHashMap<>();

    @Override
    public void save(GuildRelationship relationship) {
        Objects.requireNonNull(relationship, "Relationship cannot be null");
        relationshipsById.put(relationship.getId(), relationship);
    }

    @Override
    public void delete(String relationshipId) {
        Objects.requireNonNull(relationshipId, "Relationship ID cannot be null");
        relationshipsById.remove(relationshipId);
    }

    @Override
    public Optional<GuildRelationship> findById(String id) {
        Objects.requireNonNull(id, "ID cannot be null");
        return Optional.ofNullable(relationshipsById.get(id));
    }

    @Override
    public Optional<GuildRelationship> findRelationship(UUID guildId1, UUID guildId2) {
        Objects.requireNonNull(guildId1, "Guild ID 1 cannot be null");
        Objects.requireNonNull(guildId2, "Guild ID 2 cannot be null");

        return relationshipsById.values().stream()
            .filter(r -> r.getStatus() == RelationStatus.ACTIVE)
            .filter(r -> (r.getSourceGuildId().equals(guildId1) && r.getTargetGuildId().equals(guildId2))
                      || (r.getSourceGuildId().equals(guildId2) && r.getTargetGuildId().equals(guildId1)))
            .findFirst();
    }

    @Override
    public List<GuildRelationship> findAllByGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        return relationshipsById.values().stream()
            .filter(r -> r.involves(guildId))
            .collect(Collectors.toList());
    }

    @Override
    public List<GuildRelationship> findByType(UUID guildId, RelationType type, RelationStatus status) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(type, "Relation type cannot be null");
        Objects.requireNonNull(status, "Status cannot be null");

        return relationshipsById.values().stream()
            .filter(r -> r.involves(guildId))
            .filter(r -> r.getRelationType() == type)
            .filter(r -> r.getStatus() == status)
            .collect(Collectors.toList());
    }

    @Override
    public void deleteAllByGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        relationshipsById.entrySet().removeIf(entry -> entry.getValue().involves(guildId));
    }

    /**
     * Clears all relationships. Useful for test cleanup.
     */
    public void clear() {
        relationshipsById.clear();
    }

    /**
     * Returns the total number of relationships.
     */
    public int size() {
        return relationshipsById.size();
    }
}
