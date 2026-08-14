package com.azoth.territory.invasion;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InvasionRecord(UUID invasionId, String guildId, String guildName, String worldId,
                             double x, double y, double z, InvasionStatus status,
                             int wave, List<UUID> currentWaveEntities, GuildDamage damage, long updatedAt) {
    public InvasionRecord {
        Objects.requireNonNull(invasionId); Objects.requireNonNull(status); Objects.requireNonNull(damage);
        if (guildId == null || guildId.isBlank() || guildName == null || guildName.isBlank() || worldId == null || worldId.isBlank())
            throw new IllegalArgumentException("guild and world identifiers must not be blank");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("coordinates must be finite");
        if (wave < 0 || wave > 2) throw new IllegalArgumentException("wave must be between 0 and 2");
        Objects.requireNonNull(currentWaveEntities); currentWaveEntities = List.copyOf(currentWaveEntities);
    }
}
