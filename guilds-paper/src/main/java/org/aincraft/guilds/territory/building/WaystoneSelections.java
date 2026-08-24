package org.aincraft.guilds.territory.building;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WaystoneSelections {
    private final long timeoutMillis;
    private final Map<UUID, Selection> selections = new HashMap<>();

    public WaystoneSelections(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public void select(UUID playerId, String originFacilityId, long nowMillis) {
        selections.put(playerId, new Selection(originFacilityId, nowMillis + timeoutMillis));
    }

    public Optional<String> origin(UUID playerId, long nowMillis) {
        Selection selection = selections.get(playerId);
        if (selection == null) return Optional.empty();
        if (nowMillis >= selection.expiresAtMillis()) {
            selections.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(selection.originId());
    }

    public void clear(UUID playerId) {
        selections.remove(playerId);
    }

    private record Selection(String originId, long expiresAtMillis) { }
}
