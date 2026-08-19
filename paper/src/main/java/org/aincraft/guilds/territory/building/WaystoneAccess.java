package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pure filtering policy for same-governing-guild active waystones. */
public final class WaystoneAccess {
    private final FacilityRegistry facilities;
    private final TerritoryRegistry territories;
    private final FacilityAnchorValidator anchors;
    private final BuildingAuthorization authorization;

    public WaystoneAccess(FacilityRegistry facilities, TerritoryRegistry territories,
                          FacilityAnchorValidator anchors, BuildingAuthorization authorization) {
        this.facilities = Objects.requireNonNull(facilities, "facilities");
        this.territories = Objects.requireNonNull(territories, "territories");
        this.anchors = Objects.requireNonNull(anchors, "anchors");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

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
}
