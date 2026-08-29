package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.AllianceService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.territory.model.FastTravelMode;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Pure endpoint filtering and authorization policy for all fast-travel modes. */
public final class FastTravelAccess {
    private final FacilityRegistry facilities;
    private final TerritoryRegistry territories;
    private final FacilityAnchorValidator anchors;
    private final BuildingAuthorization authorization;
    private final FastTravelFacilityValidator facilityValidator;
    private final GuildService guilds;
    private final ResidentService residents;
    private final Function<UUID, FastTravelSnapshot> snapshots;
    private final TechTreeService techTree;
    private final AllianceService alliances;

    public FastTravelAccess(FacilityRegistry facilities, TerritoryRegistry territories,
                            FacilityAnchorValidator anchors, BuildingAuthorization authorization) {
        this(facilities, territories, anchors, authorization, null,
                (TechTreeService) null, (GuildService) null,
                (ResidentService) null, (AllianceService) null);
    }

    /** Full dependency constructor in the service-oriented parameter order. */
    public FastTravelAccess(FacilityRegistry facilities, TerritoryRegistry territories,
                            FacilityAnchorValidator anchors, BuildingAuthorization authorization,
                            TechTreeService techTree, GuildService guilds,
                            ResidentService residents, AllianceService alliances) {
        this(facilities, territories, anchors, authorization, null,
                guilds, residents, techTree, alliances);
    }
    /** Full composition seam including live activity. */
    public FastTravelAccess(FacilityRegistry facilities, TerritoryRegistry territories,
                            FacilityAnchorValidator anchors, BuildingAuthorization authorization,
                            FastTravelFacilityValidator facilityValidator,
                            TechTreeService techTree, GuildService guilds,
                            ResidentService residents, AllianceService alliances) {
        this(facilities, territories, anchors, authorization, facilityValidator,
                guilds, residents, techTree, alliances, null);
    }

    /** Full composition seam including live activity and immutable identity snapshots. */
    public FastTravelAccess(FacilityRegistry facilities, TerritoryRegistry territories,
                            FacilityAnchorValidator anchors, BuildingAuthorization authorization,
                            FastTravelFacilityValidator facilityValidator,
                            TechTreeService techTree, GuildService guilds,
                            ResidentService residents, AllianceService alliances,
                            Function<UUID, FastTravelSnapshot> snapshots) {
        this(facilities, territories, anchors, authorization, facilityValidator,
                guilds, residents, techTree, alliances, snapshots);
    }
    public FastTravelAccess(FacilityRegistry facilities, TerritoryRegistry territories,
                            FacilityAnchorValidator anchors, BuildingAuthorization authorization,
                            FastTravelFacilityValidator facilityValidator,
                            GuildService guilds, ResidentService residents,
                            TechTreeService techTree, AllianceService alliances) {
        this(facilities, territories, anchors, authorization, facilityValidator,
                guilds, residents, techTree, alliances, null);
    }


    public FastTravelAccess(FacilityRegistry facilities, TerritoryRegistry territories,
                            FacilityAnchorValidator anchors, BuildingAuthorization authorization,
                            GuildService guilds, ResidentService residents,
                            TechTreeService techTree, AllianceService alliances) {
        this(facilities, territories, anchors, authorization, null,
                guilds, residents, techTree, alliances, null);
    }

    private FastTravelAccess(FacilityRegistry facilities, TerritoryRegistry territories,
                             FacilityAnchorValidator anchors, BuildingAuthorization authorization,
                             FastTravelFacilityValidator facilityValidator,
                             GuildService guilds, ResidentService residents,
                             TechTreeService techTree, AllianceService alliances,
                             Function<UUID, FastTravelSnapshot> snapshots) {
        this.facilities = Objects.requireNonNull(facilities, "facilities");
        this.territories = Objects.requireNonNull(territories, "territories");
        this.anchors = Objects.requireNonNull(anchors, "anchors");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.facilityValidator = facilityValidator;
        this.guilds = guilds;
        this.residents = residents;
        this.snapshots = snapshots;
        this.techTree = techTree;
        this.alliances = alliances;
    }

    /**
     * Returns the legacy same-governing-guild waystone destinations. Waystones
     * deliberately do not inherit transport boundary policy.
     */
    public List<SettlementFacility> reachable(UUID playerId, SettlementFacility origin) {
        if (origin == null || origin.type() != FacilityType.WAYSTONE
                || !anchors.validate(origin).active()) {
            return List.of();
        }
        Territory originTerritory = territories.get(origin.territoryId()).orElse(null);
        String guildId = originTerritory == null
                ? null : originTerritory.governedByGuildId().orElse(null);
        if (guildId == null || !authorization.canUseWaystones(playerId, guildId)) {
            return List.of();
        }
        return facilities.list().stream()
                .filter(facility -> facility.type() == FacilityType.WAYSTONE)
                .filter(facility -> !facility.id().equals(origin.id()))
                .filter(facility -> anchors.validate(facility).active())
                .filter(facility -> territories.get(facility.territoryId())
                        .flatMap(Territory::governedByGuildId)
                        .filter(guildId::equals).isPresent())
                .sorted(Comparator.comparing(SettlementFacility::name,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SettlementFacility::id))
                .toList();
    }

    /** Returns all currently eligible destinations for an interactive endpoint. */
    public List<SettlementFacility> destinations(UUID playerId, SettlementFacility origin) {
        if (origin == null) {
            return List.of();
        }
        FastTravelMode mode = FastTravelMode.fromFacilityType(origin.type()).orElse(null);
        if (mode == null) {
            return List.of();
        }
        if (mode == FastTravelMode.WAYSTONE) {
            return reachable(playerId, origin);
        }
        boolean terminalFlow = origin.type() == FacilityType.TELEPORT_TERMINAL;
        return facilities.list().stream()
                .filter(candidate -> !candidate.id().equals(origin.id()))
                .filter(candidate -> terminalFlow
                        ? candidate.type() == FacilityType.GUILD_CRYSTAL
                        : FastTravelMode.fromFacilityType(candidate.type())
                                .filter(mode::equals).isPresent())
                .filter(candidate -> authorize(playerId, origin, candidate).allowed())
                .sorted(Comparator.comparing(SettlementFacility::name,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SettlementFacility::id))
                .toList();
    }

    /** Evaluates all synchronous gates before route and currency work. */
    public AccessDecision authorize(UUID playerId, SettlementFacility origin,
                                    SettlementFacility destination) {
        if (origin == null || !isActive(origin)) {
            return denied(AccessResult.INACTIVE_ORIGIN, null);
        }
        if (destination == null || !isActive(destination)) {
            return denied(AccessResult.INACTIVE_DESTINATION, null);
        }
        if (origin.id().equals(destination.id())) {
            return denied(AccessResult.TYPE_MISMATCH, null);
        }
        Optional<FastTravelMode> originMode = FastTravelMode.fromFacilityType(origin.type());
        Optional<FastTravelMode> destinationMode = FastTravelMode.fromFacilityType(destination.type());
        boolean terminalFlow = origin.type() == FacilityType.TELEPORT_TERMINAL
                && destination.type() == FacilityType.GUILD_CRYSTAL;
        if (originMode.isEmpty() || destinationMode.isEmpty()
                || (!terminalFlow && originMode.get() != destinationMode.get())) {
            return denied(AccessResult.TYPE_MISMATCH, originMode.orElse(null));
        }
        FastTravelMode selectedMode = terminalFlow ? FastTravelMode.CRYSTAL : originMode.get();

        Territory originTerritory = territories.get(origin.territoryId()).orElse(null);
        Territory destinationTerritory = territories.get(destination.territoryId()).orElse(null);
        if (originTerritory == null || destinationTerritory == null) {
            return denied(AccessResult.INACTIVE_ORIGIN, selectedMode);
        }
        String originGuild = originTerritory.governedByGuildId().orElse(null);
        String destinationGuild = destinationTerritory.governedByGuildId().orElse(null);
        FastTravelSnapshot snapshot = snapshotFor(playerId);
        FastTravelMode mode = selectedMode;
        if (mode == FastTravelMode.WAYSTONE) {
            if (originGuild == null || destinationGuild == null
                    || !originGuild.equals(destinationGuild)) {
                return denied(AccessResult.NON_ALLIED_DESTINATION, mode);
            }
            boolean canUseWaystones = snapshot == null
                    ? authorization.canUseWaystones(playerId, originGuild)
                    : snapshot.canUseWaystones(originGuild);
            if (!canUseWaystones) {
                return denied(AccessResult.NON_ALLIED_DESTINATION, mode);
            }
            return allowed(mode, originGuild, originGuild, destinationGuild,
                    originTerritory, destinationTerritory);
        }

        String travelerGuild = travelerGuild(playerId, snapshot).orElse(null);
        if (travelerGuild == null) {
            return denied(AccessResult.MISSING_MEMBERSHIP, mode);
        }
        if (originGuild == null || destinationGuild == null) {
            return denied(AccessResult.MISSING_MEMBERSHIP, mode);
        }
        String travelerCapability = switch (mode) {
            case CRYSTAL, LOCAL_TERMINAL -> "fast_travel";
            case BOAT -> "boat_travel";
            case AIRSHIP -> "airship_travel";
            case WAYSTONE -> "fast_travel";
        };
        if (!hasCapability(travelerGuild, travelerCapability, snapshot)
                || !hasCapability(originGuild, endpointCapability(mode), snapshot)
                || !hasCapability(destinationGuild, endpointCapability(mode), snapshot)) {
            return denied(AccessResult.MISSING_CAPABILITY, mode);
        }
        boolean sameGuild = travelerGuild.equals(originGuild) && travelerGuild.equals(destinationGuild);
        if (mode == FastTravelMode.CRYSTAL && !sameGuild
                && !hasCapability(travelerGuild, "remote_crystal", snapshot)) {
            return denied(AccessResult.MISSING_CAPABILITY, mode);
        }
        boolean allied = sameGuild || (allied(travelerGuild, originGuild, snapshot)
                && allied(travelerGuild, destinationGuild, snapshot)
                && allied(originGuild, destinationGuild, snapshot));
        if (!allied) {
            return denied(AccessResult.NON_ALLIED_DESTINATION, mode);
        }

        if (!origin.worldId().equals(destination.worldId())) {
            return denied(AccessResult.WORLD_MISMATCH, mode);
        }
        boolean sameTerritory = origin.territoryId().equals(destination.territoryId());
        boolean localTerminal = terminalFlow && sameGuild && sameTerritory;
        if (!localTerminal && sameTerritory) {
            return denied(AccessResult.SAME_TERRITORY_REMOTE, mode);
        }
        if (!sameTerritory && (!originTerritory.fastTravelPolicy().allowsCrossTerritory(mode)
                || !destinationTerritory.fastTravelPolicy().allowsCrossTerritory(mode))) {
            return denied(AccessResult.POLICY_DENIED, mode);
        }
        return allowed(mode, travelerGuild, originGuild, destinationGuild,
                originTerritory, destinationTerritory);
    }

    private boolean isActive(SettlementFacility facility) {
        if (!isTransport(facility.type())) {
            return anchors.validate(facility).active();
        }
        return facilityValidator != null && facilityValidator.isActive(facility);
    }

    private boolean isTransport(FacilityType type) {
        return type == FacilityType.GUILD_CRYSTAL
                || type == FacilityType.TELEPORT_TERMINAL
                || type == FacilityType.BOAT
                || type == FacilityType.AIRSHIP;
    }

    private String endpointCapability(FastTravelMode mode) {
        return switch (mode) {
            case CRYSTAL, LOCAL_TERMINAL -> "fast_travel";
            case BOAT -> "boat_travel";
            case AIRSHIP -> "airship_travel";
            case WAYSTONE -> "fast_travel";
        };
    }


    private FastTravelSnapshot snapshotFor(UUID playerId) {
        return snapshots == null || playerId == null ? null : snapshots.apply(playerId);
    }

    private Optional<String> travelerGuild(UUID playerId, FastTravelSnapshot snapshot) {
        if (snapshot != null) {
            return snapshot.travelerGuildId();
        }
        if (playerId == null || residents == null || guilds == null) {
            return Optional.empty();
        }
        return residents.getResident(playerId)
                .filter(Resident::hasGuild)
                .map(Resident::getGuild)
                .flatMap(guilds::getGuild)
                .map(Guild::getId);
    }

    private boolean hasCapability(String guildId, String nodeId, FastTravelSnapshot snapshot) {
        if (snapshot != null) {
            return snapshot.hasCapability(guildId, nodeId);
        }
        if (techTree == null || guilds == null) {
            return false;
        }
        Guild guild = guilds.getGuildById(guildId).orElse(null);
        return guild != null && techTree.hasCapability(guild, nodeId);
    }

    private boolean allied(String firstGuildId, String secondGuildId, FastTravelSnapshot snapshot) {
        if (snapshot != null) {
            return snapshot.allied(firstGuildId, secondGuildId);
        }
        if (alliances == null) {
            return false;
        }
        return alliances.getAllAlliances().stream()
                .anyMatch(alliance -> alliance.hasGuild(firstGuildId)
                        && alliance.hasGuild(secondGuildId));
    }
    private static AccessDecision denied(AccessResult result, FastTravelMode mode) {
        return new AccessDecision(result, mode, null, null, null, null, null, null);
    }

    private static AccessDecision allowed(FastTravelMode mode, String travelerGuild,
                                         String originGuild, String destinationGuild,
                                         Territory originTerritory, Territory destinationTerritory) {
        return new AccessDecision(AccessResult.ALLOWED, mode, travelerGuild, originGuild,
                destinationGuild, originTerritory, destinationTerritory, null);
    }
    public interface FastTravelSnapshot {
        Optional<String> travelerGuildId();
        default boolean canUseWaystones(String guildId) {
            return travelerGuildId().filter(guildId::equals).isPresent();
        }

        boolean hasCapability(String guildId, String nodeId);

        boolean allied(String firstGuildId, String secondGuildId);
    }


    public enum AccessResult {
        ALLOWED,
        INACTIVE_ORIGIN,
        INACTIVE_DESTINATION,
        TYPE_MISMATCH,
        MISSING_MEMBERSHIP,
        MISSING_CAPABILITY,
        NON_ALLIED_DESTINATION,
        SAME_TERRITORY_REMOTE,
        POLICY_DENIED,
        WORLD_MISMATCH
    }

    public record AccessDecision(AccessResult result, FastTravelMode mode,
                                 String travelerGuildId, String originGuildId,
                                 String destinationGuildId, Territory originTerritory,
                                 Territory destinationTerritory, String detail) {
        public boolean allowed() {
            return result == AccessResult.ALLOWED;
        }

        public boolean isAllowed() {
            return allowed();
        }
    }
}
