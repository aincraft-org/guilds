package org.aincraft.guilds.territory.upkeep;

import org.aincraft.guilds.territory.decree.GoodsCatalog;
import org.aincraft.guilds.territory.economy.EconomyBridge;
import org.aincraft.guilds.territory.economy.ExpenseOutcome;
import org.aincraft.guilds.territory.economy.ExpenseKind;
import org.aincraft.guilds.territory.economy.ExpenseReport;
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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpkeepEngineTest {
    private static final Boundary SQUARE = Boundary.ofPolygon(List.of(
            new BlockPos(0, 0), new BlockPos(10, 0),
            new BlockPos(10, 10), new BlockPos(0, 10)));

    @Test
    void ungovernedTerritoryDoesNotCharge() throws Exception {
        TerritoryRegistry territories = new TerritoryRegistry();
        territories.register(new Territory("t1", "T1", "world", SQUARE));
        RecordingRail rail = new RecordingRail(TreasuryDebitStatus.DEBITED);
        MemoryStore store = new MemoryStore();
        UpkeepEngine engine = engine(territories, rail, store);

        engine.recover(1_000L);
        engine.tick(1_000L);

        assertEquals(0, rail.debitCalls);
        assertEquals(List.of(), engine.all());
    }

    @Test
    void dueGovernedTerritoryChargesOnceWithStablePeriodKey() throws Exception {
        TerritoryRegistry territories = governedTerritories();
        RecordingRail rail = new RecordingRail(TreasuryDebitStatus.DEBITED);
        MemoryStore store = dueStore();
        UpkeepEngine engine = engine(territories, rail, store);

        engine.recover(1_000L);
        engine.tick(1_000L);
        engine.tick(1_000L);

        assertEquals(1, rail.debitCalls);
        assertEquals("upkeep:t1:1000", rail.keys.get(0));
        assertEquals(10.0, rail.amounts.get(0), 0.001);
        assertEquals(2_000L, engine.state("t1").orElseThrow().nextDueEpochMs());
    }

    @Test
    void insufficientFundsEntersGraceWithDeterministicDeadline() throws Exception {
        TerritoryRegistry territories = governedTerritories();
        RecordingRail rail = new RecordingRail(TreasuryDebitStatus.INSUFFICIENT_FUNDS);
        MemoryStore store = dueStore();
        UpkeepEngine engine = engine(territories, rail, store);

        engine.recover(1_000L);
        engine.tick(1_000L);

        UpkeepState state = engine.state("t1").orElseThrow();
        assertEquals(UpkeepStatus.GRACE, state.status());
        assertEquals(1_100L, state.graceDeadlineEpochMs());
        assertEquals(1_100L, state.nextDueEpochMs());
        assertEquals(ExpenseOutcome.INSUFFICIENT_FUNDS, state.lastOutcome());
    }

    @Test
    void failedChargeAfterGraceDeadlineSuspends() throws Exception {
        TerritoryRegistry territories = governedTerritories();
        RecordingRail rail = new RecordingRail(
                TreasuryDebitStatus.INSUFFICIENT_FUNDS,
                TreasuryDebitStatus.INSUFFICIENT_FUNDS);
        MemoryStore store = dueStore();
        UpkeepEngine engine = engine(territories, rail, store);

        engine.recover(1_000L);
        engine.tick(1_000L);
        engine.tick(1_100L);

        assertEquals(UpkeepStatus.SUSPENDED, engine.state("t1").orElseThrow().status());
        assertEquals(2, rail.debitCalls);
    }

    @Test
    void successfulLaterChargeReturnsCurrentAndAdvancesDueTime() throws Exception {
        TerritoryRegistry territories = governedTerritories();
        RecordingRail rail = new RecordingRail(
                TreasuryDebitStatus.INSUFFICIENT_FUNDS,
                TreasuryDebitStatus.DEBITED);
        MemoryStore store = dueStore();
        UpkeepEngine engine = engine(territories, rail, store);

        engine.recover(1_000L);
        engine.tick(1_000L);
        engine.tick(1_100L);

        UpkeepState state = engine.state("t1").orElseThrow();
        assertEquals(UpkeepStatus.CURRENT, state.status());
        assertEquals(2_100L, state.nextDueEpochMs());
        assertEquals(ExpenseOutcome.DEBITED, state.lastOutcome());
    }

    private static UpkeepEngine engine(
            TerritoryRegistry territories,
            RecordingRail rail,
            MemoryStore store
    ) {
        GovernanceRegistry governance = new GovernanceRegistry(territories);
        EconomyBridge economy = new RecordingEconomyBridge(territories, governance, rail);
        return new UpkeepEngine(
                territories,
                economy,
                new FacilityRegistry(territories),
                new UpkeepConfig(10.0, 0.0, 0.0, 0.0, 1_000L, 100L),
                store,
                ignored -> 0);
    }

    private static TerritoryRegistry governedTerritories() {
        TerritoryRegistry territories = new TerritoryRegistry();
        territories.register(new Territory("t1", "T1", "world", SQUARE)
                .withGovernment(Government.monarchy("king"))
                .withGoverningGuild("guild-1"));
        return territories;
    }

    private static MemoryStore dueStore() {
        MemoryStore store = new MemoryStore();
        store.snapshot = List.of(new UpkeepState(
                "t1", 10.0, UpkeepStatus.CURRENT, 1_000L, 0L, null, null));
        return store;
    }

    private static final class MemoryStore implements UpkeepStore {
        private Collection<UpkeepState> snapshot = List.of();

        @Override
        public Collection<UpkeepState> load() {
            return List.copyOf(snapshot);
        }

        @Override
        public void save(Collection<UpkeepState> states) throws IOException {
            snapshot = List.copyOf(states);
        }
    }

    private static final class RecordingEconomyBridge extends EconomyBridge {
        private final RecordingRail rail;

        private RecordingEconomyBridge(
                TerritoryRegistry territories,
                GovernanceRegistry governance,
                RecordingRail rail
        ) {
            super(territories, governance, GoodsCatalog.defaultCatalog(), rail, false);
            this.rail = rail;
        }

        @Override
        public ExpenseReport chargeExpense(
                String territoryId,
                ExpenseKind kind,
                double amount,
                String idempotencyKey
        ) {
            rail.keys.add(idempotencyKey);
            rail.amounts.add(amount);
            rail.debitCalls++;
            ExpenseOutcome outcome = switch (rail.nextStatus()) {
                case DEBITED -> ExpenseOutcome.DEBITED;
                case INSUFFICIENT_FUNDS -> ExpenseOutcome.INSUFFICIENT_FUNDS;
                case PROVIDER_UNAVAILABLE -> ExpenseOutcome.PROVIDER_UNAVAILABLE;
                case INVALID_AMOUNT -> ExpenseOutcome.INVALID_AMOUNT;
            };
            return new ExpenseReport(outcome, territoryId, kind, amount, idempotencyKey);
        }
    }

    private static final class RecordingRail implements PaymentRail {
        private final Deque<TreasuryDebitStatus> results = new ArrayDeque<>();
        private final List<String> keys = new ArrayList<>();
        private final List<Double> amounts = new ArrayList<>();
        private int debitCalls;

        private RecordingRail(TreasuryDebitStatus... statuses) {
            results.addAll(List.of(statuses));
        }

        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
            return new SettlementResult(PaymentRail.SettlementStatus.SETTLED);
        }

        private TreasuryDebitStatus nextStatus() {
            return results.isEmpty() ? TreasuryDebitStatus.DEBITED : results.removeFirst();
        }

        @Override
        public TreasuryDebitResult debitTreasury(String territoryId, double amount) {
            debitCalls++;
            amounts.add(amount);
            return new TreasuryDebitResult(nextStatus());
        }

        @Override
        public boolean available() {
            return true;
        }
    }
}
