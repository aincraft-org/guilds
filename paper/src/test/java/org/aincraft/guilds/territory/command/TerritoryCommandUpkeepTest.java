package org.aincraft.guilds.territory.command;

import org.aincraft.guilds.GuildsPlugin;
import org.aincraft.guilds.territory.decree.GoodsCatalog;
import org.aincraft.guilds.territory.economy.EconomyBridge;
import org.aincraft.guilds.territory.economy.PaymentRail;
import org.aincraft.guilds.territory.economy.SettlementResult;
import org.aincraft.guilds.territory.economy.TreasuryDebitResult;
import org.aincraft.guilds.territory.economy.TreasuryDebitStatus;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.permission.GovernanceRegistry;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.aincraft.guilds.territory.upkeep.UpkeepConfig;
import org.aincraft.guilds.territory.upkeep.UpkeepEngine;
import org.aincraft.guilds.territory.upkeep.UpkeepState;
import org.aincraft.guilds.territory.upkeep.UpkeepStatus;
import org.aincraft.guilds.territory.upkeep.UpkeepStore;
import org.bukkit.command.Command;
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

class TerritoryCommandUpkeepTest {
    @Test
    void pluginExposesUpkeepEngine() throws Exception {
        Method getter = GuildsPlugin.class.getMethod("getUpkeepEngine");
        assertEquals(UpkeepEngine.class, getter.getReturnType());
    }

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
        TerritoryCommand command = new TerritoryCommand(plugin);
        CommandSender sender = mock(CommandSender.class);

        assertDoesNotThrow(() -> command.onCommand(sender, mock(Command.class), "territory",
                new String[]{"upkeep", "everfall"}));
        verify(sender, atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
        assertEquals(UpkeepStatus.GRACE, engine.state("everfall").orElseThrow().status());
    }

    private static final class MemoryStore implements UpkeepStore {
        private Collection<UpkeepState> states;

        private MemoryStore(Collection<UpkeepState> states) {
            this.states = List.copyOf(states);
        }

        @Override
        public Collection<UpkeepState> load() {
            return List.copyOf(states);
        }

        @Override
        public void save(Collection<UpkeepState> states) throws IOException {
            this.states = List.copyOf(states);
        }
    }

    private static final class TestRail implements PaymentRail {
        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
            return new SettlementResult(PaymentRail.SettlementStatus.SETTLED);
        }

        @Override
        public TreasuryDebitResult debitTreasury(String territoryId, double amount) {
            return new TreasuryDebitResult(TreasuryDebitStatus.DEBITED);
        }

        @Override
        public boolean available() {
            return true;
        }
    }
}
