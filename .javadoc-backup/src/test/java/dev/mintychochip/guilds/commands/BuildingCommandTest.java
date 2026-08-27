package dev.mintychochip.guilds.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.mintychochip.guilds.commands.brigadier.BuildingCommand;
import dev.mintychochip.territory.building.BuildingAuthorization;
import dev.mintychochip.territory.building.BuildingConfig;
import dev.mintychochip.territory.building.BuildingPlacement;
import dev.mintychochip.territory.building.BuildingPlacementSessions;
import dev.mintychochip.territory.building.FacilityAnchorValidator;
import dev.mintychochip.territory.building.FacilityMutationService;
import dev.mintychochip.territory.building.WaystoneSelections;
import dev.mintychochip.territory.building.WaystoneTravelService;
import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.FacilityType;
import dev.mintychochip.territory.model.SettlementFacility;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.registry.FacilityRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for building command. */
class BuildingCommandTest {
    /** The player id. */
    private UUID playerId;
    /** The player. */
    private Player player;
    /** The source. */
    private CommandSourceStack source;
    /** The dispatcher. */
    private CommandDispatcher<CommandSourceStack> dispatcher;
    /** The sessions. */
    private BuildingPlacementSessions sessions;
    /** The facilities. */
    private FacilityRegistry facilities;
    /** The authorization. */
    private BuildingAuthorization authorization;
    /** The command. */
    private BuildingCommand command;

    /** Sets the up. */
    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.hasPermission("guilds.territory.building.manage")).thenReturn(true);
        source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
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
        authorization = mock(BuildingAuthorization.class);
        FacilityMutationService mutations = new FacilityMutationService(facilities, ignored -> { });
        command = new BuildingCommand(sessions, facilities, territories, anchors,
                authorization, mutations, config, new WaystoneSelections(60_000L),
                mock(WaystoneTravelService.class));
        dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.buildCommand());
    }

    /** Builds the ing tree uses named literals. */
    @Test
    void buildingTreeUsesNamedLiterals() {
        LiteralCommandNode<CommandSourceStack> root = command.buildCommand();
        assertEquals("building", root.getLiteral());
        Set<String> children = root.getChildren().stream()
                .map(CommandNode::getName).collect(Collectors.toSet());
        assertEquals(Set.of("create", "cancel", "list", "info", "remove", "travel"), children);
    }

    /**
     * Creates a new starts command then click session.
     * @throws Exception if an error occurs
     */
    @Test
    void createStartsCommandThenClickSession() throws Exception {
        assertEquals(1, dispatcher.execute("building create waystone North North Gate", source));

        BuildingPlacement placement = sessions.current(playerId, System.currentTimeMillis())
                .orElseThrow();
        assertEquals(FacilityType.WAYSTONE, placement.type());
        assertEquals("north", placement.id());
        assertEquals("North Gate", placement.name());
        verify(player, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    /**
     * Returns whether cel and completion are available.
     * @throws Exception if an error occurs
     */
    @Test
    void cancelAndCompletionAreAvailable() throws Exception {
        sessions.begin(playerId, FacilityType.WAYSTONE, "north", "North", 1_000L);

        assertEquals(1, dispatcher.execute("building cancel", source));

        assertTrue(sessions.current(playerId, 1_001L).isEmpty());
    }

    /**
     * Performs the info reports registered facility operation.
     * @throws Exception if an error occurs
     */
    @Test
    void infoReportsRegisteredFacility() throws Exception {
        SettlementFacility market = new SettlementFacility(
                "market", "Market", "t1", FacilityType.TRADING_POST, "world", 5, 64, 5);
        facilities.register(market);
        when(authorization.canManage(any(Player.class), any(Territory.class))).thenReturn(true);

        assertEquals(1, dispatcher.execute("building info market", source));

        verify(player, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }
}
