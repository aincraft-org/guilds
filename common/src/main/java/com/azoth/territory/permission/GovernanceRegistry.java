package com.azoth.territory.permission;

import com.azoth.territory.model.Government;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.model.Policy;
import com.azoth.territory.model.PolicyRules;
import com.azoth.territory.model.Territory;
import com.azoth.territory.registry.TerritoryRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolution of governing bodies against a territory registry and a
 * {@link GovernanceSource} (implemented by the guilds subsystem).
 * <p>
 * Resolution rules:
 * <ul>
 *   <li>Territory sovereignty: the bound guild's alliance (nation) if the
 *       governing guild is a nation member; else the guild (guild) itself;
 *       else the territory's own local government.</li>
 *   <li>Holder membership: first guild (by id) that lists the holder as a member.</li>
 * </ul>
 * There is no parallel in-memory guild/alliance world — everything resolves
 * through the source. Pure domain — no Bukkit.
 */
public final class GovernanceRegistry {
    private final TerritoryRegistry territories;
    private final GovernanceSource source;

    public GovernanceRegistry(TerritoryRegistry territories, GovernanceSource source) {
        this.territories = Objects.requireNonNull(territories, "territories");
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * Registry without a guilds backing (economy/persistence tests, or when the
     * guilds subsystem is unavailable): everything resolves to territory-local
     * government.
     */
    public GovernanceRegistry(TerritoryRegistry territories) {
        this(territories, GovernanceSource.none());
    }

    public TerritoryRegistry territories() {
        return territories;
    }

    public GovernanceSource source() {
        return source;
    }

    /**
     * Guilds that list {@code holderId} as a member (stable id order).
     */
    public List<GuildBody> guildsForMember(String holderId) {
        return source.guildsForMember(holderId);
    }

    /**
     * First guild (by id) that contains the holder, if any.
     */
    public Optional<GuildBody> primaryGuildForMember(String holderId) {
        List<GuildBody> list = source.guildsForMember(holderId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * The guild bound to a territory, if the binding resolves.
     */
    public Optional<GuildBody> governingGuildForTerritory(String territoryId) {
        if (territoryId == null || territoryId.isBlank()) {
            return Optional.empty();
        }
        Optional<Territory> t = territories.get(territoryId.trim());
        if (t.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> guildId = t.get().governedByGuildId();
        return guildId.flatMap(source::guild);
    }

    /**
     * The guild governing the territory at a world location, if bound and resolvable.
     */
    public Optional<GuildBody> governingGuildAt(String worldId, int blockX, int blockZ) {
        LookupResult hit = territories.resolve(worldId, blockX, blockZ);
        if (!hit.isContained()) {
            return Optional.empty();
        }
        return governingGuildForTerritory(hit.territoryId().orElseThrow());
    }

    /**
     * Governing body for a holder via guild membership (guild government).
     */
    public GoverningBody resolveForHolder(String holderId) {
        return primaryGuildForMember(holderId)
                .map(GoverningBody::ofGuild)
                .orElse(GoverningBody.none());
    }

    /**
     * Governing body for a territory: the bound guild's alliance if the guild
     * is a nation member, else the guild, else territory-local government.
     */
    public GoverningBody resolveForTerritory(String territoryId) {
        Optional<GuildBody> guild = governingGuildForTerritory(territoryId);
        if (guild.isPresent()) {
            Optional<AllianceBody> alliance = source.allianceContainingGuild(guild.get().id());
            if (alliance.isPresent()) {
                return GoverningBody.ofAlliance(alliance.get());
            }
            return GoverningBody.ofGuild(guild.get());
        }
        if (territoryId == null || territoryId.isBlank()) {
            return GoverningBody.none();
        }
        Optional<Territory> t = territories.get(territoryId.trim());
        return t.map(GoverningBody::ofTerritory).orElse(GoverningBody.none());
    }

    /**
     * Effective government for a territory id (alliance overrides guild, guild
     * overrides local).
     */
    public Government effectiveGovernmentForTerritory(String territoryId) {
        return resolveForTerritory(territoryId).government();
    }

    /**
     * Spatial resolve then governing body for that territory.
     */
    public GoverningBody resolveAt(String worldId, int blockX, int blockZ) {
        LookupResult hit = territories.resolve(worldId, blockX, blockZ);
        if (!hit.isContained()) {
            return GoverningBody.none();
        }
        return resolveForTerritory(hit.territoryId().orElseThrow());
    }

    // ── Policy operations under the effective government ─────────────────
    // Guild/alliance-bound territories decide policies under the derived
    // government (form + role holders), not the territory-local attachment.
    // These are the entry points for bound territories; the model-level
    // Territory.proposePolicy/castPolicyVote/decreePolicy use the persisted
    // local government only.

    /**
     * Propose a policy under the effective government of the territory
     * (alliance over guild over local). Proposer must be in the electorate.
     */
    public Policy proposePolicy(
            String territoryId,
            String policyId,
            String title,
            String body,
            String proposerId,
            long nowEpochMs
    ) {
        return proposePolicy(territoryId, policyId, title, body, proposerId, nowEpochMs,
                com.azoth.territory.decree.DecreeEffects.empty());
    }

    /**
     * Propose a policy with structured decree effects under the effective government.
     */
    public Policy proposePolicy(
            String territoryId,
            String policyId,
            String title,
            String body,
            String proposerId,
            long nowEpochMs,
            com.azoth.territory.decree.DecreeEffects effects
    ) {
        Territory t = requireTerritory(territoryId);
        Government gov = resolveForTerritory(territoryId).government();
        Policy p = PolicyRules.propose(gov, policyId, title, body, proposerId, nowEpochMs, effects);
        if (t.policy(p.id()).isPresent()) {
            throw new IllegalArgumentException("policy already exists: " + p.id());
        }
        List<Policy> next = new java.util.ArrayList<>(t.policies());
        next.add(p);
        territories.register(t.withPolicies(next));
        return p;
    }

    /**
     * Cast a vote (majority forms) under the effective government.
     */
    public Policy castPolicyVote(
            String territoryId,
            String policyId,
            String voterId,
            com.azoth.territory.model.VoteChoice choice,
            long nowEpochMs
    ) {
        Territory t = requireTerritory(territoryId);
        Policy existing = t.policy(policyId).orElseThrow(
                () -> new IllegalArgumentException("unknown policy: " + policyId));
        Government gov = resolveForTerritory(territoryId).government();
        Policy updated = PolicyRules.castVote(gov, existing, voterId, choice, nowEpochMs);
        territories.register(replacePolicy(t, updated));
        return updated;
    }

    /**
     * Decree pass/reject (monarchy) under the effective government.
     */
    public Policy decreePolicy(
            String territoryId,
            String policyId,
            String authorityId,
            boolean pass,
            long nowEpochMs
    ) {
        Territory t = requireTerritory(territoryId);
        Policy existing = t.policy(policyId).orElseThrow(
                () -> new IllegalArgumentException("unknown policy: " + policyId));
        Government gov = resolveForTerritory(territoryId).government();
        Policy updated = PolicyRules.decree(gov, existing, authorityId, pass, nowEpochMs);
        territories.register(replacePolicy(t, updated));
        return updated;
    }

    private Territory requireTerritory(String territoryId) {
        if (territoryId == null || territoryId.isBlank()) {
            throw new IllegalArgumentException("territoryId is required");
        }
        return territories.get(territoryId.trim()).orElseThrow(
                () -> new IllegalArgumentException("unknown territory: " + territoryId));
    }

    private static Territory replacePolicy(Territory t, Policy updated) {
        List<Policy> next = new java.util.ArrayList<>(t.policies());
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(updated.id())) {
                next.set(i, updated);
                break;
            }
        }
        return t.withPolicies(next);
    }
}
