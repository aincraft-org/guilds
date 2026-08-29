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

class FastTravelAccessTest {
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

        List<SettlementFacility> reachable = new FastTravelAccess(
                facilities, territories, anchors, authorization).reachable(player, origin);

        assertEquals(List.of(alpha, zulu), reachable);
    }

    @Test
    void rejectsEndpointTypeMismatchBeforeMembershipResolution() {
        Territory territory = territory("territory", 0).withGoverningGuild("guild-a");
        TerritoryRegistry territories = new TerritoryRegistry(List.of(territory));
        FacilityRegistry facilities = new FacilityRegistry(territories);
        SettlementFacility origin = facility("origin", "Origin", "territory", FacilityType.WAYSTONE, 5);
        SettlementFacility destination = facility("market", "Market", "territory",
                FacilityType.TRADING_POST, 6);
        facilities.replaceAll(List.of(origin, destination));
        FacilityAnchorValidator anchors = mock(FacilityAnchorValidator.class);
        when(anchors.validate(origin)).thenReturn(
                new FacilityAnchorValidator.AnchorValidation(AnchorStatus.ACTIVE, origin));
        when(anchors.validate(destination)).thenReturn(
                new FacilityAnchorValidator.AnchorValidation(AnchorStatus.ACTIVE, destination));

        FastTravelAccess.AccessDecision result = new FastTravelAccess(
                facilities, territories, anchors, mock(BuildingAuthorization.class))
                .authorize(UUID.randomUUID(), origin, destination);

        assertEquals(FastTravelAccess.AccessResult.TYPE_MISMATCH, result.result());
    }

    @Test
    void allowsLocalTerminalToOwnCrystalInOneTerritory() {
        Territory territory = territory("territory", 0).withGoverningGuild("guild-a");
        TerritoryRegistry territories = new TerritoryRegistry(List.of(territory));
        FacilityRegistry facilities = new FacilityRegistry(territories);
        SettlementFacility terminal = facility("terminal", "Terminal", "territory",
                FacilityType.TELEPORT_TERMINAL, 5);
        SettlementFacility crystal = facility("crystal", "Crystal", "territory",
                FacilityType.GUILD_CRYSTAL, 10);
        facilities.replaceAll(List.of(terminal, crystal));
        FacilityAnchorValidator anchors = mock(FacilityAnchorValidator.class);
        when(anchors.validate(terminal)).thenReturn(
                new FacilityAnchorValidator.AnchorValidation(AnchorStatus.ACTIVE, terminal));
        when(anchors.validate(crystal)).thenReturn(
                new FacilityAnchorValidator.AnchorValidation(AnchorStatus.ACTIVE, crystal));
        UUID playerId = UUID.randomUUID();
        org.aincraft.guilds.models.Resident resident = mock(org.aincraft.guilds.models.Resident.class);
        when(resident.hasGuild()).thenReturn(true);
        when(resident.getGuild()).thenReturn("guild-a");
        org.aincraft.guilds.models.Guild guild = mock(org.aincraft.guilds.models.Guild.class);
        when(guild.getId()).thenReturn("guild-a");
        org.aincraft.guilds.services.ResidentService residents =
                mock(org.aincraft.guilds.services.ResidentService.class);
        org.aincraft.guilds.services.GuildService guilds =
                mock(org.aincraft.guilds.services.GuildService.class);
        org.aincraft.guilds.services.TechTreeService tech =
                mock(org.aincraft.guilds.services.TechTreeService.class);
        when(residents.getResident(playerId)).thenReturn(java.util.Optional.of(resident));
        when(guilds.getGuild("guild-a")).thenReturn(java.util.Optional.of(guild));
        when(guilds.getGuildById("guild-a")).thenReturn(java.util.Optional.of(guild));
        when(tech.hasCapability(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);

        FastTravelAccess.AccessDecision result = new FastTravelAccess(
                facilities, territories, anchors, mock(BuildingAuthorization.class), tech, guilds,
                residents, mock(org.aincraft.guilds.services.AllianceService.class))
                .authorize(playerId, terminal, crystal);

        assertEquals(FastTravelAccess.AccessResult.ALLOWED, result.result());
        assertEquals(org.aincraft.guilds.territory.model.FastTravelMode.CRYSTAL, result.mode());
    }

    @Test
    void rejectsTransportWhenFacilityValidatorIsNotConfigured() {
        Territory territory = territory("territory", 0).withGoverningGuild("guild-a");
        TerritoryRegistry territories = new TerritoryRegistry(List.of(territory));
        FacilityRegistry facilities = new FacilityRegistry(territories);
        SettlementFacility terminal = facility("terminal", "Terminal", "territory",
                FacilityType.TELEPORT_TERMINAL, 5);
        SettlementFacility crystal = facility("crystal", "Crystal", "territory",
                FacilityType.GUILD_CRYSTAL, 10);
        facilities.replaceAll(List.of(terminal, crystal));
        FacilityAnchorValidator anchors = mock(FacilityAnchorValidator.class);
        when(anchors.validate(terminal)).thenReturn(
                new FacilityAnchorValidator.AnchorValidation(AnchorStatus.ACTIVE, terminal));
        when(anchors.validate(crystal)).thenReturn(
                new FacilityAnchorValidator.AnchorValidation(AnchorStatus.ACTIVE, crystal));

        FastTravelAccess.AccessDecision result = new FastTravelAccess(
                facilities, territories, anchors, mock(BuildingAuthorization.class), null,
                mock(org.aincraft.guilds.services.TechTreeService.class),
                mock(org.aincraft.guilds.services.GuildService.class),
                mock(org.aincraft.guilds.services.ResidentService.class),
                mock(org.aincraft.guilds.services.AllianceService.class))
                .authorize(UUID.randomUUID(), terminal, crystal);

        assertEquals(FastTravelAccess.AccessResult.INACTIVE_ORIGIN, result.result());
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
