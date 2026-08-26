package dev.mintychochip.territory.building;

import dev.mintychochip.territory.model.FacilityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Main-thread placement sessions with deterministic caller-supplied time. */
public final class BuildingPlacementSessions {
    private final long timeoutMillis;
    private final Map<UUID, BuildingPlacement> sessions = new HashMap<>();

    public BuildingPlacementSessions(long timeoutMillis) {
        if (timeoutMillis <= 0L) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        this.timeoutMillis = timeoutMillis;
    }

    public void begin(UUID playerId, FacilityType type, String id, String name, long nowMillis) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId is required");
        }
        sessions.put(playerId, new BuildingPlacement(
                type, id == null ? null : id.trim().toLowerCase(java.util.Locale.ROOT),
                name, Math.addExact(nowMillis, timeoutMillis)));
    }

    public Optional<BuildingPlacement> current(UUID playerId, long nowMillis) {
        BuildingPlacement placement = sessions.get(playerId);
        if (placement == null) {
            return Optional.empty();
        }
        if (nowMillis >= placement.expiresAtMillis()) {
            sessions.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(placement);
    }

    public boolean cancel(UUID playerId) {
        return sessions.remove(playerId) != null;
    }

    public void complete(UUID playerId) {
        sessions.remove(playerId);
    }
}
