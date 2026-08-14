package com.azoth.territory.invasion;

import java.util.List;
import java.util.UUID;

public record InvasionState(UUID invasionId, String guildId, String guildName, String worldId,
                            double x, double y, double z, InvasionStatus status, int wave,
                            List<UUID> currentWaveEntities, GuildDamage damage, long updatedAt) {
    public InvasionState {
        currentWaveEntities = List.copyOf(currentWaveEntities);
    }

    public static InvasionState from(InvasionRecord r) {
        return new InvasionState(r.invasionId(), r.guildId(), r.guildName(), r.worldId(), r.x(), r.y(), r.z(), r.status(), r.wave(), r.currentWaveEntities(), r.damage(), r.updatedAt());
    }
}
