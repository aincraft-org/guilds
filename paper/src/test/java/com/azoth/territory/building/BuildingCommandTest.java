package com.azoth.territory.building;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.FacilityType;
import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.model.Territory;
import com.azoth.territory.registry.FacilityRegistry;
import com.azoth.territory.registry.TerritoryRegistry;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildingCommandTest {
    private UUID playerId;
    private Player player;
    private BuildingPlacementSessions sessions;
    private FacilityRegistry facilities;
    private BuildingCommand command;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.hasPermission("azoth.territory.building.manage")).thenReturn(true);
        Territory territory = new Territory("t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))));
        TerritoryRegistry territories = new TerritoryRegistry(List.of(territory));
        facilities = new FacilityRegistry(territories);
        BuildingConfig config = new BuildingConfig(60_000L,
                Map.of(FacilityType.WAYSTONE, Set.of(Material.LODESTONE),
                        FacilityType.TRADING_POST, Set.of(Material.BELL)), 100L, 60_000L);
        sessions = new BuildingPlacementSessions(config.placementTimeoutMillis());
        FacilityAnchorValidator anchors = new FacilityAnchorValidator(
                mock(Server.class), territories, facilities, config);
        BuildingAuthorization authorization = mock(BuildingAuthorization.class);
        FacilityMutationService mutations = new FacilityMutationService(facilities, ignored -> { });
        command = new BuildingCommand(sessions, facilities, territories, anchors,
                authorization, mutations, config, new WaystoneSelections(60_000L),
                mock(WaystoneTravelService.class));
    }

    @Test
    void createStartsCommandThenClickSession() {
        command.execute(player, "territory",
                new String[]{"create", "waystone", "North", "North", "Gate"});

        BuildingPlacement placement = sessions.current(playerId, System.currentTimeMillis())
                .orElseThrow();
        assertEquals(FacilityType.WAYSTONE, placement.type());
        assertEquals("north", placement.id());
        assertEquals("North Gate", placement.name());
        verify(player, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void cancelAndCompletionAreAvailable() {
        sessions.begin(playerId, FacilityType.WAYSTONE, "north", "North", 1_000L);

        command.execute(player, "territory", new String[]{"cancel"});

        assertTrue(sessions.current(playerId, 1_001L).isEmpty());
    }

    @Test
    void infoReportsRegisteredFacility() throws Exception {
        SettlementFacility market = new SettlementFacility(
                "market", "Market", "t1", FacilityType.TRADING_POST, "world", 5, 64, 5);
        facilities.register(market);

        command.execute(player, "territory", new String[]{"info", "market"});

        verify(player, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
}
