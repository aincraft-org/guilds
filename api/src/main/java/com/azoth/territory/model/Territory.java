package com.azoth.territory.model;

import com.azoth.territory.decree.DecreeEffects;

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

    /** Creates a wilderness territory with anarchy government.
     * @param id territory identifier
     * @param name display name
     * @param worldId world identifier
     * @param boundary outer boundary
     */
    public Territory(String id, String name, String worldId, Boundary boundary) {
        this(id, name, worldId, boundary, List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), null);
    }

    /** Creates a territory with zones and default government.
     * @param id territory identifier
     * @param name display name
     * @param worldId world identifier
     * @param boundary outer boundary
     * @param zones nested zones
     * @param defaultZoneType fallback zone type
     */
    public Territory(
            String id,
            String name,
            String worldId,
            Boundary boundary,
            Collection<Zone> zones,
            ZoneType defaultZoneType
    ) {
        this(id, name, worldId, boundary, zones, defaultZoneType, Government.anarchy(), List.of(), null);
    }

    /** Creates a territory with a government.
     * @param id territory identifier
     * @param name display name
     * @param worldId world identifier
     * @param boundary outer boundary
     * @param zones nested zones
     * @param defaultZoneType fallback zone type
     * @param government territory government
     */
    public Territory(
            String id,
            String name,
            String worldId,
            Boundary boundary,
            Collection<Zone> zones,
            ZoneType defaultZoneType,
            Government government
    ) {
        this(id, name, worldId, boundary, zones, defaultZoneType, government, List.of(), null);
    }

    /** Creates a territory with policies.
     * @param id territory identifier
     * @param name display name
     * @param worldId world identifier
     * @param boundary outer boundary
     * @param zones nested zones
     * @param defaultZoneType fallback zone type
     * @param government territory government
     * @param policies policies
     */
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
        this(id, name, worldId, boundary, zones, defaultZoneType, government, policies, null);
    }

    /**
     * Creates a fully configured territory.
     *
     * @param id territory identifier
     * @param name display name
     * @param worldId world identifier
     * @param boundary outer boundary
     * @param zones nested zones
     * @param defaultZoneType fallback zone type
     * @param government territory government
     * @param policies policies
     * @param governedByGuildId governing guild identifier, if any
     */
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

    /** Returns the territory identifier.
     * @return territory identifier
     */
    public String id() {
        return id;
    }

    /** Returns the display name.
     * @return display name
     */
    public String name() {
        return name;
    }

    /** Returns the world identifier.
     * @return world identifier
     */
    public String worldId() {
        return worldId;
    }

    /** Returns the outer boundary.
     * @return outer boundary
     */
    public Boundary boundary() {
        return boundary;
    }

    /** Returns the default zone type.
     * @return default zone type
     */
    public ZoneType defaultZoneType() {
        return defaultZoneType;
    }

    /** Returns the territory government.
     * @return territory government
     */
    public Government government() {
        return government;
    }

    /** Returns the government form.
     * @return government form
     */
    public GovernmentForm governmentForm() {
        return government.form();
    }

    /** Returns immutable nested zones.
     * @return immutable zones
     */
    public List<Zone> zones() {
        return List.copyOf(zones.values());
    }

    /** Finds a nested zone by identifier.
     * @param zoneId zone identifier
     * @return matching zone, if present
     */
    public Optional<Zone> zone(String zoneId) {
        return Optional.ofNullable(zones.get(zoneId));
    }

    /** Returns immutable policies.
     * @return immutable policies
     */
    public List<Policy> policies() {
        return List.copyOf(policies.values());
    }

    /**
     * Returns the governing guild identifier, if bound.
     *
     * @return the governing guild identifier, if any
     */
    public Optional<String> governedByGuildId() {
        return Optional.ofNullable(governedByGuildId);
    }

    /**
     * Binds a governing guild to this territory.
     *
     * @param guildId governing guild identifier, or {@code null} to clear
     * @return updated territory
     */
    public Territory withGoverningGuild(String guildId) {
        String next = guildId == null || guildId.isBlank() ? null : guildId.trim();
        return copyWith(zones.values(), government, policies.values(), next);
    }

    /**
     * Removes the governing-guild binding.
     *
     * @return updated territory
     */
    public Territory withoutGoverningGuild() {
        return withGoverningGuild(null);
    }

    /** Finds a policy by identifier.
     * @param policyId policy identifier
     * @return matching policy, if present
     */
    public Optional<Policy> policy(String policyId) {
        return Optional.ofNullable(policies.get(policyId));
    }

    /** Reports whether block coordinates are contained.
     * @param blockX block X coordinate
     * @param blockZ block Z coordinate
     * @return whether the block is contained
     */
    public boolean contains(int blockX, int blockZ) {
        return boundary.contains(blockX, blockZ);
    }

    /** Reports whether a block position is contained.
     * @param pos block position
     * @return whether the position is contained
     */
    public boolean contains(BlockPos pos) {
        return contains(pos.x(), pos.z());
    }

    /** Resolves the highest-priority matching zone.
     * @param blockX block X coordinate
     * @param blockZ block Z coordinate
     * @return zone resolution
     */
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
                nextGoverningGuildId
        );
    }
    /** Replaces the territory government.
     * @param next replacement government
     * @return updated territory
     */
    public Territory withGovernment(Government next) {
        return copyWith(zones.values(), next == null ? Government.anarchy() : next, policies.values());
    }

    /** Adds or replaces a zone.
     * @param zone zone to add
     * @return updated territory
     */
    public Territory withZone(Zone zone) {
        Objects.requireNonNull(zone, "zone");
        Map<String, Zone> next = new LinkedHashMap<>(zones);
        next.put(zone.id(), zone);
        return copyWith(next.values(), government, policies.values());
    }

    /** Removes a zone.
     * @param zoneId zone identifier
     * @return updated territory
     */
    public Territory withoutZone(String zoneId) {
        Map<String, Zone> next = new LinkedHashMap<>(zones);
        next.remove(zoneId);
        return copyWith(next.values(), government, policies.values());
    }

    /** Removes the government.
     * @return updated territory
     */
    public Territory withoutGovernment() {
        return withGovernment(Government.anarchy());
    }

    /** Replaces all policies.
     * @param nextPolicies replacement policies
     * @return updated territory
     */
    public Territory withPolicies(Collection<Policy> nextPolicies) {
        return copyWith(zones.values(), government, nextPolicies == null ? List.of() : nextPolicies);
    }

    /**
     * Proposes a policy without structured effects (delegates with empty effects).
     *
     * @param policyId policy identifier
     * @param title policy title
     * @param body policy body
     * @param proposerId proposer identifier
     * @param nowEpochMs proposal time
     * @return updated territory
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
     * @param policyId policy identifier
     * @param title policy title
     * @param body policy body
     * @param proposerId proposer identifier
     * @param nowEpochMs proposal time
     * @param effects decree effects
     * @return updated territory
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

    /** Casts a majority-form vote.
     * @param policyId policy identifier
     * @param voterId voter identifier
     * @param choice vote choice
     * @param nowEpochMs vote time
     * @return updated territory
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

    /** Records a monarchy decree.
     * @param policyId policy identifier
     * @param authorityId authority identifier
     * @param pass whether to pass
     * @param nowEpochMs decree time
     * @return updated territory
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

    /** @param o object to compare
     * @return whether both territories are equal
     */
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
                && Objects.equals(governedByGuildId, that.governedByGuildId);
    }

    /** @return hash code for this territory */
    @Override
    public int hashCode() {
        return Objects.hash(id, name, worldId, boundary, zones, defaultZoneType,
                government, policies, governedByGuildId);
    }

    /** @return concise textual representation */
    @Override
    public String toString() {
        return "Territory{id='" + id + "', world='" + worldId
                + "', zones=" + zones.size()
                + ", government=" + government.form()
                + ", policies=" + policies.size()
                + ", governingGuild=" + (governedByGuildId == null ? "none" : governedByGuildId) + '}';
    }

    /** Resolution of a location against a zone.
     * @param zoneId resolved zone identifier, or {@code null} for default
     * @param zoneName resolved zone name
     * @param type resolved zone type
     * @param isDefault whether the default zone was selected
     */
    public record ZoneResolution(String zoneId, String zoneName, ZoneType type, boolean isDefault) {
    }
}
