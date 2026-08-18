package dev.mintychochip.guilds.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.mintychochip.guilds.GuildsPlugin;
import dev.mintychochip.guilds.commands.brigadier.TerritoryBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.TerritoryCommand;
import dev.mintychochip.territory.economy.GoodsCatalog;
import dev.mintychochip.territory.economy.EconomyBridge;
import dev.mintychochip.territory.economy.PaymentRail;
import dev.mintychochip.territory.economy.SettlementResult;
import dev.mintychochip.territory.economy.TreasuryDebitResult;
import dev.mintychochip.territory.economy.TreasuryDebitStatus;
import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.Government;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.permission.GovernanceRegistry;
import dev.mintychochip.territory.registry.FacilityRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import dev.mintychochip.territory.upkeep.UpkeepConfig;
import dev.mintychochip.territory.upkeep.UpkeepEngine;
import dev.mintychochip.territory.upkeep.UpkeepState;
import dev.mintychochip.territory.upkeep.UpkeepStatus;
import dev.mintychochip.territory.upkeep.UpkeepStore;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for territory brigadier command. */
class TerritoryBrigadierCommandTest {
    /** Performs the root literal is territory with every current subcommand operation. */
    @Test
    void rootLiteralIsTerritoryWithEveryCurrentSubcommand() {
        LiteralCommandNode<CommandSourceStack> root = new TerritoryBrigadierCommand(
                new TerritoryCommand(mock(GuildsPlugin.class))).buildCommand();

        assertEquals("territory", root.getLiteral());
        assertNull(root.getChild("args"), "greedy args shim must not remain");
        Set<String> children = childNames(root);
        assertTrue(children.containsAll(List.of(
                "lookup", "here", "list", "reload", "save", "web", "govern",
                "influence", "declare", "standing", "upkeep", "invasion")),
                () -> "missing subcommands: " + children);
        assertTrue(!children.contains("building"), "buildings belong on /guilds building");
    }

    /** Performs the invasion node suggests start stop and status operation. */
    @Test
    void invasionNodeSuggestsStartStopAndStatus() {
        LiteralCommandNode<CommandSourceStack> root = new TerritoryBrigadierCommand(
                new TerritoryCommand(mock(GuildsPlugin.class))).buildCommand();
        CommandNode<CommandSourceStack> invasion = root.getChild("invasion");
        assertNotNull(invasion);
        assertEquals(Set.of("start", "stop", "status"), childNames(invasion));
    }

    /** Influence and declare stay on territory. */
    @Test
    void influenceDeclareStayOnTerritory() {
        LiteralCommandNode<CommandSourceStack> root = new TerritoryBrigadierCommand(
                new TerritoryCommand(mock(GuildsPlugin.class))).buildCommand();
        assertTrue(childNames(root.getChild("influence")).containsAll(Set.of("set", "reset")));
        assertNotNull(root.getChild("declare").getChild("cancel"));
        assertNull(root.getChild("building"));
    }

    /**
     * Performs the executing upkeep through brigadier writes existing output operation.
     * @throws Exception if an error occurs
     */
    @Test
    void executingUpkeepThroughBrigadierWritesExistingOutput() throws Exception {
        TerritoryRegistry territories = new TerritoryRegistry();
        territories.register(new Territory(
                "everfall", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(10, 0),
                        new BlockPos(10, 10), new BlockPos(0, 10))))
                .withGovernment(Government.monarchy("king"))
                .withGoverningGuild("guild-1"));
        GovernanceRegistry governance = new GovernanceRegistry(territories);
        EconomyBridge economy = new EconomyBridge(
                territories, governance, GoodsCatalog.defaultCatalog(), new TestRail(), false);
        MemoryStore store = new MemoryStore(List.of(new UpkeepState(
                "everfall", 25.0, UpkeepStatus.GRACE,
                2_000L, 2_500L, "upkeep:everfall:1000", null)));
        UpkeepEngine engine = new UpkeepEngine(
                territories, economy, new FacilityRegistry(territories),
                new UpkeepConfig(25.0, 0.0, 0.0, 0.0, 1_000L, 500L),
                store, ignored -> 0);
        engine.recover(1_000L);

        GuildsPlugin plugin = mock(GuildsPlugin.class);
        when(plugin.getUpkeepEngine()).thenReturn(engine);
        CommandSender sender = mock(CommandSender.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);

        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(
                new TerritoryBrigadierCommand(new TerritoryCommand(plugin)));
        assertEquals(1, dispatcher.execute("territory upkeep everfall", source));
        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
        assertEquals(UpkeepStatus.GRACE, engine.state("everfall").orElseThrow().status());
    }

    /**
     * Performs the top level suggestions include current subcommands operation.
     * @throws Exception if an error occurs
     */
    @Test
    void topLevelSuggestionsIncludeCurrentSubcommands() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher(
                new TerritoryBrigadierCommand(new TerritoryCommand(mock(GuildsPlugin.class))));
        CommandSourceStack source = mock(CommandSourceStack.class);
        ParseResults<CommandSourceStack> parsed = dispatcher.parse("territory ", source);
        Set<String> suggestions = dispatcher.getCompletionSuggestions(parsed).join()
                .getList().stream().map(Suggestion::getText).collect(Collectors.toSet());
        assertTrue(suggestions.containsAll(List.of(
                "lookup", "here", "list", "reload", "save", "web", "govern",
                "influence", "declare", "standing", "upkeep", "invasion")),
                () -> "suggestions were " + suggestions);
    }

    /**
     * Performs the dispatcher operation.
     * @param command the command
     * @return the result
     */
    private static CommandDispatcher<CommandSourceStack> dispatcher(TerritoryBrigadierCommand command) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.buildCommand());
        return dispatcher;
    }

    /**
     * Performs the child names operation.
     * @param node the node
     * @return the result
     */
    private static Set<String> childNames(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());
    }

    /** Persistence store for memory. */
    private static final class MemoryStore implements UpkeepStore {
        /** The states. */
        private Collection<UpkeepState> states;

        /**
         * Creates a new memory store instance.
         * @param states the states
         */
        private MemoryStore(Collection<UpkeepState> states) {
            this.states = List.copyOf(states);
        }

        /**
         * loads the data.
         * @return the result
         */
        @Override
        public Collection<UpkeepState> load() {
            return List.copyOf(states);
        }

        /**
         * saves the data.
         * @param states the states
         * @throws IOException if an error occurs
         */
        @Override
        public void save(Collection<UpkeepState> states) throws IOException {
            this.states = List.copyOf(states);
        }
    }

    /** test rail. */
    private static final class TestRail implements PaymentRail {
        /**
         * Sets the tle.
         * @param payerId the payer id
         * @param territoryId the territory id
         * @param amount the amount
         * @return the result
         */
        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
            return new SettlementResult(PaymentRail.SettlementStatus.SETTLED);
        }

        /**
         * Performs the debit treasury operation.
         * @param territoryId the territory id
         * @param amount the amount
         * @return the result
         */
        @Override
        public TreasuryDebitResult debitTreasury(String territoryId, double amount) {
            return new TreasuryDebitResult(TreasuryDebitStatus.DEBITED);
        }

        /**
         * Performs the available operation.
         * @return the result
         */
        @Override
        public boolean available() {
            return true;
        }
    }
}
