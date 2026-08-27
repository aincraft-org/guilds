package org.aincraft.guilds.alliances;

import java.util.Set;
import java.util.UUID;

/**
 * Pending alliance that exists only until enough guilds have accepted.
 */
public record AllianceProposal(
        String name,
        String proposingGuildId,
        UUID proposingMayorUuid,
        Set<String> acceptedGuildIds,
        Set<String> invitedGuildIds
) {
    public AllianceProposal {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Alliance name is required");
        }
        if (proposingGuildId == null || proposingGuildId.isBlank()) {
            throw new IllegalArgumentException("Proposing guild is required");
        }
        if (proposingMayorUuid == null) {
            throw new IllegalArgumentException("Proposing mayor is required");
        }
        acceptedGuildIds = Set.copyOf(acceptedGuildIds);
        invitedGuildIds = Set.copyOf(invitedGuildIds);
    }

    public boolean involves(String guildId) {
        return acceptedGuildIds.contains(guildId) || invitedGuildIds.contains(guildId);
    }
}
