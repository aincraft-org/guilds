package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WaystoneAccessTest {
    @Test
    void returnsOnlyActiveSameGuildWaystonesInStableOrder() {
        Territory first = territory("first", 0).withGoverningGuild("guild-a");
        Territory second = territory("second", 200).withGoverningGuild("guild-a");
        Territory enemy = territory("enemy", 400).withGoverningGuild("guild-b");
        TerritoryRegistry territories = new TerritoryRegistry(List.of(first, second, enemy));
        FacilityRegistry facilities = new FacilityRegistry(territories);
        SettlementFacility origin = facility("origin", "Origin", "first", FacilityType.WAYSTONE, 5);
        SettlementFacility zulu = facility("zulu", "Zulu", "second", FacilityType.WAYSTONE, 205);
        SettlementFacility alpha = facility("alpha", "Alpha", "second", FacilityType.WAYSTONE, 206);
        SettlementFacility hostile = facility("hostile", "Hostile", "enemy", FacilityType.WAYSTONE, 405);
        SettlementFacility market = facility("market", "Market", "second", FacilityType.TRADING_POST, 207);
        facilities.replaceAll(List.of(origin, zulu, alpha, hostile, market));
        FacilityAnchorValidator anchors = mock(FacilityAnchorValidator.class);
        for (SettlementFacility facility : facilities.list()) {
            when(anchors.validate(facility)).thenReturn(
                    new FacilityAnchorValidator.AnchorValidation(AnchorStatus.ACTIVE, facility));
        }
        BuildingAuthorization authorization = mock(BuildingAuthorization.class);
        UUID player = UUID.randomUUID();
        when(authorization.canUseWaystones(player, "guild-a")).thenReturn(true);

        List<SettlementFacility> reachable = new WaystoneAccess(
                facilities, territories, anchors, authorization).reachable(player, origin);

        assertEquals(List.of(alpha, zulu), reachable);
    }

    private static Territory territory(String id, int x) {
        return new Territory(id, id, "world", Boundary.ofPolygon(List.of(
                new BlockPos(x, 0), new BlockPos(x + 100, 0),
                new BlockPos(x + 100, 100), new BlockPos(x, 100))));
    }

    private static SettlementFacility facility(String id, String name, String territory,
                                                FacilityType type, int x) {
        return new SettlementFacility(id, name, territory, type, "world", x, 64, 5);
    }
}
