package org.aincraft.guilds.storage.service;

import java.util.Optional;
import java.util.UUID;

/** Resolves a player's current block location for facility access checks. */
@FunctionalInterface
public interface PlayerLocationSource {
    Optional<BlockLocation> locationOf(UUID playerId);

    record BlockLocation(String worldId, int x, int y, int z) {}
}
