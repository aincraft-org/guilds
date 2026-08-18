package dev.mintychochip.guilds.commands;

import dev.mintychochip.guilds.GuildsPlugin;
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
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for territory command upkeep. */
class TerritoryCommandUpkeepTest {
    /**
     * Performs the plugin exposes upkeep engine operation.
     * @throws Exception if an error occurs
     */
    @Test
    void pluginExposesUpkeepEngine() throws Exception {
        Method getter = GuildsPlugin.class.getMethod("getUpkeepEngine");
        assertEquals(UpkeepEngine.class, getter.getReturnType());
    }

    /**
     * Performs the command upkeep reads status without mutating operation.
     * @throws Exception if an error occurs
     */
    @Test
    void commandUpkeep_readsStatusWithoutMutating() throws Exception {
        TerritoryRegistry territories = new TerritoryRegistry();
        Territory territory = new Territory(
                "everfall", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(10, 0),
                        new BlockPos(10, 10), new BlockPos(0, 10))))
                .withGovernment(Government.monarchy("king"))
                .withGoverningGuild("guild-1");
        territories.register(territory);
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

        assertDoesNotThrow(() -> TerritoryCommandTestSupport.execute(
                plugin, sender, "territory upkeep everfall"));
        verify(sender, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
        assertEquals(UpkeepStatus.GRACE, engine.state("everfall").orElseThrow().status());
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
