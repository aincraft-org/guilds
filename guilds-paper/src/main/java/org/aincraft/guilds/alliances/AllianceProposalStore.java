package org.aincraft.guilds.alliances;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of pending alliance proposals. An alliance is persisted
 * only after {@link #accept(String, String, int)} reports {@code committed}.
 */
public final class AllianceProposalStore {

    public record AcceptOutcome(AllianceProposal proposal, boolean committed) {
    }

    private final Map<String, AllianceProposal> byName = new ConcurrentHashMap<>();

    public AllianceProposal propose(String name, String proposingGuildId, UUID mayorUuid, String targetGuildId) {
        requireName(name);
        requireGuildId(proposingGuildId, "Proposing guild is required");
        requireGuildId(targetGuildId, "Target guild is required");
        if (proposingGuildId.equals(targetGuildId)) {
            throw new IllegalArgumentException("A guild cannot form an alliance with itself");
        }
        String key = key(name);
        if (byName.containsKey(key)) {
            throw new IllegalArgumentException("A pending alliance named " + name + " already exists");
        }
        if (isGuildBusy(proposingGuildId) || isGuildBusy(targetGuildId)) {
            throw new IllegalArgumentException("A guild in that proposal is already part of a pending alliance");
        }
        AllianceProposal proposal = new AllianceProposal(
                name, proposingGuildId, mayorUuid, Set.of(proposingGuildId), Set.of(targetGuildId));
        byName.put(key, proposal);
        return proposal;
    }

    public AllianceProposal invite(String name, String byGuildId, String targetGuildId) {
        AllianceProposal current = require(name);
        if (!current.proposingGuildId().equals(byGuildId)) {
            throw new IllegalArgumentException("Only the proposing guild can invite others to a pending alliance");
        }
        requireGuildId(targetGuildId, "Target guild is required");
        if (current.involves(targetGuildId) || isGuildBusy(targetGuildId)) {
            throw new IllegalArgumentException("That guild is already part of a pending alliance");
        }
        Set<String> invited = new LinkedHashSet<>(current.invitedGuildIds());
        invited.add(targetGuildId);
        AllianceProposal updated = new AllianceProposal(
                current.name(),
                current.proposingGuildId(),
                current.proposingMayorUuid(),
                current.acceptedGuildIds(),
                invited);
        byName.put(key(name), updated);
        return updated;
    }

    public AcceptOutcome accept(String name, String acceptingGuildId, int minGuilds) {
        AllianceProposal current = require(name);
        if (!current.invitedGuildIds().contains(acceptingGuildId)) {
            throw new IllegalArgumentException("That guild has no pending invitation to " + current.name());
        }
        Set<String> invited = new LinkedHashSet<>(current.invitedGuildIds());
        invited.remove(acceptingGuildId);
        Set<String> accepted = new LinkedHashSet<>(current.acceptedGuildIds());
        accepted.add(acceptingGuildId);
        AllianceProposal updated = new AllianceProposal(
                current.name(),
                current.proposingGuildId(),
                current.proposingMayorUuid(),
                accepted,
                invited);
        int required = Math.max(2, minGuilds);
        boolean committed = updated.acceptedGuildIds().size() >= required;
        if (committed) {
            byName.remove(key(name));
        } else {
            byName.put(key(name), updated);
        }
        return new AcceptOutcome(updated, committed);
    }

    public Optional<AllianceProposal> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(key(name)));
    }

    public Optional<AllianceProposal> findByProposingGuild(String guildId) {
        if (guildId == null) {
            return Optional.empty();
        }
        return byName.values().stream()
                .filter(proposal -> proposal.proposingGuildId().equals(guildId))
                .findFirst();
    }

    public boolean isGuildBusy(String guildId) {
        if (guildId == null) {
            return false;
        }
        return byName.values().stream().anyMatch(proposal -> proposal.involves(guildId));
    }

    private AllianceProposal require(String name) {
        return get(name).orElseThrow(() -> new IllegalArgumentException("No pending alliance named " + name));
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Alliance name is required");
        }
    }

    private static void requireGuildId(String guildId, String message) {
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
