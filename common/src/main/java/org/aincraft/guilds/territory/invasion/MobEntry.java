package org.aincraft.guilds.territory.invasion;

public record MobEntry(String entityType, int count) {
    public MobEntry {
        if (entityType == null || entityType.isBlank()) throw new IllegalArgumentException("entityType must not be blank");
        if (count <= 0) throw new IllegalArgumentException("count must be positive");
    }
}
