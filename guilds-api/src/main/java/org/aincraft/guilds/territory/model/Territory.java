package org.aincraft.guilds.territory.model;

import org.aincraft.guilds.territory.decree.DecreeEffects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A large map region with an outer boundary, nested zones
 * (Wilderness, Claimable, …), optional government/sovereignty, and policies.
 */
public final class Territory {
    private final String id;
    private final String name;
    private final String worldId;
    private final Boundary boundary;
    private final Map<String, Zone> zones;
    private final ZoneType defaultZoneType;
    private final Government government;
    private final Map<String, Policy> policies;
    private final String governedByGuildId;
    private final FastTravelPolicy fastTravelPolicy;


    public Territory(String id, String name, String worldId, Boundary boundary) {
        this(id, name, worldId, boundary, List.of(), ZoneType.WILDERNESS,
                Government.anarchy(), List.of(), null, FastTravelPolicy.defaults());
    }

    public Territory(
            String id,
            String name,
            String worldId,
            Boundary boundary,
            Collection<Zone> zones,
            ZoneType defaultZoneType
    ) {
        this(id, name, worldId, boundary, zones, defaultZoneType,
                Government.anarchy(), List.of(), null, FastTravelPolicy.defaults());
    }

    public Territory(
            String id,
            String name,
            String worldId,
            Boundary boundary,
            Collection<Zone> zones,
            ZoneType defaultZoneType,
            Government government
    ) {
        this(id, name, worldId, boundary, zones, defaultZoneType,
                government, List.of(), null, FastTravelPolicy.defaults());
    }

    public Territory(
            String id,
            String name,
            String worldId,
            Boundary boundary,
            Collection<Zone> zones,
            ZoneType defaultZoneType,
            Government government,
            Collection<Policy> policies
    ) {
        this(id, name, worldId, boundary, zones, defaultZoneType,
                government, policies, null, FastTravelPolicy.defaults());
    }

    public Territory(
            String id,
            String name,
            String worldId,
            Boundary boundary,
            Collection<Zone> zones,
            ZoneType defaultZoneType,
            Government government,
            Collection<Policy> policies,
            String governedByGuildId
    ) {
        this(id, name, worldId, boundary, zones, defaultZoneType, government, policies,
                governedByGuildId, FastTravelPolicy.defaults());
    }

    public Territory(
            String id,
            String name,
            String worldId,
            Boundary boundary,
            Collection<Zone> zones,
            ZoneType defaultZoneType,
            Government government,
            Collection<Policy> policies,
            String governedByGuildId,
            FastTravelPolicy fastTravelPolicy
    ) {
        this.id = requireId(id);
        this.name = name == null || name.isBlank() ? this.id : name.trim();
        this.worldId = requireId(worldId);
        this.boundary = Objects.requireNonNull(boundary, "boundary");
        if (boundary.isEmpty()) {
            throw new IllegalArgumentException("territory boundary must not be empty: " + this.id);
        }
        this.defaultZoneType = defaultZoneType == null ? ZoneType.WILDERNESS : defaultZoneType;
        Map<String, Zone> zoneMap = new LinkedHashMap<>();
        if (zones != null) {
            for (Zone z : zones) {
                if (zoneMap.put(z.id(), z) != null) {
                    throw new IllegalArgumentException("duplicate zone id in territory " + this.id + ": " + z.id());
                }
            }
        }
        validateZonesDoNotOverlap(zoneMap.values());
        this.zones = Collections.unmodifiableMap(zoneMap);
        this.government = government == null ? Government.anarchy() : government;
        Map<String, Policy> policyMap = new LinkedHashMap<>();
        if (policies != null) {
            for (Policy p : policies) {
                if (policyMap.put(p.id(), p) != null) {
                    throw new IllegalArgumentException("duplicate policy id in territory " + this.id + ": " + p.id());
                }
            }
        }
        this.policies = Collections.unmodifiableMap(policyMap);
        this.governedByGuildId = governedByGuildId == null || governedByGuildId.isBlank()
                ? null
                : governedByGuildId.trim();
        this.fastTravelPolicy = Objects.requireNonNull(fastTravelPolicy, "fastTravelPolicy");
    }

    static void validateZonesDoNotOverlap(Collection<Zone> zones) {
        if (zones == null || zones.size() < 2) {
            return;
        }
        List<Zone> list = new ArrayList<>(zones);
        for (int i = 0; i < list.size(); i++) {
            Zone a = list.get(i);
            for (int j = i + 1; j < list.size(); j++) {
                Zone b = list.get(j);
                if (a.boundary().overlaps(b.boundary())) {
                    throw new IllegalArgumentException(
                            "zones must not overlap: '" + a.id() + "' and '" + b.id() + "'"
                    );
                }
            }
        }
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        return id.trim();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String worldId() {
        return worldId;
    }

    public Boundary boundary() {
        return boundary;
    }

    public ZoneType defaultZoneType() {
        return defaultZoneType;
    }

    public Government government() {
        return government;
    }

    public GovernmentForm governmentForm() {
        return government.form();
    }

    public FastTravelPolicy fastTravelPolicy() {
        return fastTravelPolicy;
    }

    public List<Zone> zones() {
        return List.copyOf(zones.values());
    }

    public Optional<Zone> zone(String zoneId) {
        return Optional.ofNullable(zones.get(zoneId));
    }

    public List<Policy> policies() {
        return List.copyOf(policies.values());
    }

    /**
     * Guild (guild) id that governs this territory, if bound. The guilds
     * subsystem is the source of truth for that guild's government and
     * permissions; the territory only records the binding.
     */
    public Optional<String> governedByGuildId() {
        return Optional.ofNullable(governedByGuildId);
    }

    /**
     * Bind a governing guild (guild) to this territory.
     */
    public Territory withGoverningGuild(String guildId) {
        String next = guildId == null || guildId.isBlank() ? null : guildId.trim();
        return copyWith(zones.values(), government, policies.values(), next);
    }

    /**
     * Remove the governing-guild binding (falls back to territory-local government).
     */
    public Territory withoutGoverningGuild() {
        return withGoverningGuild(null);
    }

    public Optional<Policy> policy(String policyId) {
        return Optional.ofNullable(policies.get(policyId));
    }

    public boolean contains(int blockX, int blockZ) {
        return boundary.contains(blockX, blockZ);
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.x(), pos.z());
    }

    public ZoneResolution resolveZone(int blockX, int blockZ) {
        Zone best = null;
        for (Zone z : zones.values()) {
            if (!z.contains(blockX, blockZ)) {
                continue;
            }
            if (best == null || compareZones(z, best) > 0) {
                best = z;
            }
        }
        if (best != null) {
            return new ZoneResolution(best.id(), best.name(), best.type(), false);
        }
        return new ZoneResolution(null, "default", defaultZoneType, true);
    }

    private static int compareZones(Zone a, Zone b) {
        int p = Integer.compare(a.priority(), b.priority());
        if (p != 0) {
            return p;
        }
        int t = Integer.compare(typeRank(a.type()), typeRank(b.type()));
        if (t != 0) {
            return t;
        }
        return a.id().compareTo(b.id());
    }

    private static int typeRank(ZoneType type) {
        return type == ZoneType.CLAIMABLE ? 1 : 0;
    }

    private Territory copyWith(
            Collection<Zone> nextZones,
            Government nextGov,
            Collection<Policy> nextPolicies
    ) {
        return copyWith(nextZones, nextGov, nextPolicies, governedByGuildId);
    }

    private Territory copyWith(
            Collection<Zone> nextZones,
            Government nextGov,
            Collection<Policy> nextPolicies,
            String nextGoverningGuildId
    ) {
        return new Territory(
                id, name, worldId, boundary, nextZones, defaultZoneType, nextGov, nextPolicies,
                nextGoverningGuildId, fastTravelPolicy
        );
    }

    public Territory withFastTravelPolicy(FastTravelPolicy nextPolicy) {
        return new Territory(
                id, name, worldId, boundary, zones.values(), defaultZoneType, government, policies.values(),
                governedByGuildId, Objects.requireNonNull(nextPolicy, "fastTravelPolicy")
        );
    }

    public Territory withZone(Zone zone) {
        Objects.requireNonNull(zone, "zone");
        Map<String, Zone> next = new LinkedHashMap<>(zones);
        next.put(zone.id(), zone);
        return copyWith(next.values(), government, policies.values());
    }

    public Territory withoutZone(String zoneId) {
        Map<String, Zone> next = new LinkedHashMap<>(zones);
        next.remove(zoneId);
        return copyWith(next.values(), government, policies.values());
    }

    public Territory withGovernment(Government gov) {
        return copyWith(zones.values(), gov == null ? Government.anarchy() : gov, policies.values());
    }

    public Territory withoutGovernment() {
        return withGovernment(Government.anarchy());
    }

    public Territory withPolicies(Collection<Policy> nextPolicies) {
        return copyWith(zones.values(), government, nextPolicies == null ? List.of() : nextPolicies);
    }

    /**
     * Propose a policy without structured effects (delegates with empty effects).
     */
    public Territory proposePolicy(
            String policyId,
            String title,
            String body,
            String proposerId,
            long nowEpochMs
    ) {
        return proposePolicy(policyId, title, body, proposerId, nowEpochMs, DecreeEffects.empty());
    }

    /**
     * Propose a policy under this territory's government (proposer must be eligible).
     */
    public Territory proposePolicy(
            String policyId,
            String title,
            String body,
            String proposerId,
            long nowEpochMs,
            DecreeEffects effects
    ) {
        Policy p = PolicyRules.propose(government, policyId, title, body, proposerId, nowEpochMs, effects);
        if (policies.containsKey(p.id())) {
            throw new IllegalArgumentException("policy already exists: " + p.id());
        }
        Map<String, Policy> next = new LinkedHashMap<>(policies);
        next.put(p.id(), p);
        return copyWith(zones.values(), government, next.values());
    }

    /**
     * Cast a vote (majority forms) or reject if form uses decree.
     */
    public Territory castPolicyVote(
            String policyId,
            String voterId,
            VoteChoice choice,
            long nowEpochMs
    ) {
        Policy existing = requirePolicy(policyId);
        Policy updated = PolicyRules.castVote(government, existing, voterId, choice, nowEpochMs);
        return replacePolicy(updated);
    }

    /**
     * Decree pass/reject for monarchy (single-seat decree forms).
     */
    public Territory decreePolicy(
            String policyId,
            String authorityId,
            boolean pass,
            long nowEpochMs
    ) {
        Policy existing = requirePolicy(policyId);
        Policy updated = PolicyRules.decree(government, existing, authorityId, pass, nowEpochMs);
        return replacePolicy(updated);
    }

    private Policy requirePolicy(String policyId) {
        Policy existing = policies.get(policyId);
        if (existing == null) {
            throw new IllegalArgumentException("unknown policy: " + policyId);
        }
        return existing;
    }

    private Territory replacePolicy(Policy updated) {
        Map<String, Policy> next = new LinkedHashMap<>(policies);
        next.put(updated.id(), updated);
        return copyWith(zones.values(), government, next.values());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Territory that)) {
            return false;
        }
        return id.equals(that.id)
                && name.equals(that.name)
                && worldId.equals(that.worldId)
                && boundary.equals(that.boundary)
                && zones.equals(that.zones)
                && defaultZoneType == that.defaultZoneType
                && government.equals(that.government)
                && policies.equals(that.policies)
                && Objects.equals(governedByGuildId, that.governedByGuildId)
                && fastTravelPolicy.equals(that.fastTravelPolicy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, worldId, boundary, zones, defaultZoneType,
                government, policies, governedByGuildId, fastTravelPolicy);
    }

    @Override
    public String toString() {
        return "Territory{id='" + id + "', world='" + worldId
                + "', zones=" + zones.size()
                + ", government=" + government.form()
                + ", policies=" + policies.size()
                + ", governingGuild=" + (governedByGuildId == null ? "none" : governedByGuildId)
                + ", fastTravelPolicy=" + fastTravelPolicy + '}';
    }

    public record ZoneResolution(String zoneId, String zoneName, ZoneType type, boolean isDefault) {
    }
}
