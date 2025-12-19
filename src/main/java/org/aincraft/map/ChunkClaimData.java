package org.aincraft.map;

import java.util.UUID;

/**
 * Immutable data transfer object for chunk claim information.
 */
public record ChunkClaimData(UUID guildId, UUID claimedBy, long claimedAt) {
}
