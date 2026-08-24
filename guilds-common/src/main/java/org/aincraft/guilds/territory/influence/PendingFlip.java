package org.aincraft.guilds.territory.influence;

/** Journal marker for an in-flight takeover flip (spec §6). */
record PendingFlip(
        String territoryId,
        String oldOwnerGuildId,
        String newOwnerGuildId,
        long flipAtEpochMs,
        long cooldownUntilEpochMs
) {
}
