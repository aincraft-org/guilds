package com.azoth.territory.permission;

import com.azoth.territory.model.Government;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.model.RegionGuild;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.TerritoryAlliance;
import com.azoth.territory.registry.TerritoryRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory wiring of region guilds and territory alliances against a territory registry.
 * <p>
 * Resolution rules:
 * <ul>
 *   <li>Territory sovereignty: first alliance that lists the territory (by alliance id order);
 *       otherwise the territory's own government.</li>
 *   <li>Holder membership: first guild (by guild id order) that lists the holder as a member.</li>
 * </ul>
 * Pure domain — no Bukkit.
 */
public final class GovernanceRegistry {
    private final TerritoryRegistry territories;
    private final Map<String, RegionGuild> guildsById = new ConcurrentHashMap<>();
    private final Map<String, TerritoryAlliance> alliancesById = new ConcurrentHashMap<>();

    public GovernanceRegistry(TerritoryRegistry territories) {
        this.territories = Objects.requireNonNull(territories, "territories");
    }

    public TerritoryRegistry territories() {
        return territories;
    }

    public synchronized void putGuild(RegionGuild guild) {
        Objects.requireNonNull(guild, "guild");
        guildsById.put(guild.id(), guild);
    }

    public synchronized boolean removeGuild(String guildId) {
        return guildsById.remove(guildId) != null;
    }

    public Optional<RegionGuild> guild(String guildId) {
        return Optional.ofNullable(guildsById.get(guildId));
    }

    public List<RegionGuild> guilds() {
        return List.copyOf(guildsById.values());
    }

    public synchronized void putAlliance(TerritoryAlliance alliance) {
        Objects.requireNonNull(alliance, "alliance");
        alliancesById.put(alliance.id(), alliance);
    }

    public synchronized boolean removeAlliance(String allianceId) {
        return alliancesById.remove(allianceId) != null;
    }

    public Optional<TerritoryAlliance> alliance(String allianceId) {
        return Optional.ofNullable(alliancesById.get(allianceId));
    }

    public List<TerritoryAlliance> alliances() {
        return List.copyOf(alliancesById.values());
    }

    /**
     * Guilds that list {@code holderId} as a member (stable id order).
     */
    public List<RegionGuild> guildsForMember(String holderId) {
        if (holderId == null || holderId.isBlank()) {
            return List.of();
        }
        String id = holderId.trim();
        List<RegionGuild> matches = new ArrayList<>();
        for (RegionGuild g : guildsById.values()) {
            if (g.containsMember(id)) {
                matches.add(g);
            }
        }
        matches.sort((a, b) -> a.id().compareTo(b.id()));
        return List.copyOf(matches);
    }

    /**
     * First guild (by id) that contains the holder, if any.
     */
    public Optional<RegionGuild> primaryGuildForMember(String holderId) {
        List<RegionGuild> list = guildsForMember(holderId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * Alliances that list {@code territoryId} (stable id order).
     */
    public List<TerritoryAlliance> alliancesForTerritory(String territoryId) {
        if (territoryId == null || territoryId.isBlank()) {
            return List.of();
        }
        String id = territoryId.trim();
        List<TerritoryAlliance> matches = new ArrayList<>();
        for (TerritoryAlliance a : alliancesById.values()) {
            if (a.containsTerritory(id)) {
                matches.add(a);
            }
        }
        matches.sort((a, b) -> a.id().compareTo(b.id()));
        return List.copyOf(matches);
    }

    /**
     * First alliance (by id) that includes the territory, if any.
     */
    public Optional<TerritoryAlliance> primaryAllianceForTerritory(String territoryId) {
        List<TerritoryAlliance> list = alliancesForTerritory(territoryId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
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
     * Governing body for a territory: alliance if member, else territory-local government.
     */
    public GoverningBody resolveForTerritory(String territoryId) {
        if (territoryId == null || territoryId.isBlank()) {
            return GoverningBody.none();
        }
        Optional<TerritoryAlliance> alliance = primaryAllianceForTerritory(territoryId);
        if (alliance.isPresent()) {
            return GoverningBody.ofAlliance(alliance.get());
        }
        Optional<Territory> t = territories.get(territoryId.trim());
        return t.map(GoverningBody::ofTerritory).orElse(GoverningBody.none());
    }

    /**
     * Effective government for a territory id (alliance overrides local).
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

    public Collection<RegionGuild> allGuilds() {
        return guilds();
    }

    public Collection<TerritoryAlliance> allAlliances() {
        return alliances();
    }
}
