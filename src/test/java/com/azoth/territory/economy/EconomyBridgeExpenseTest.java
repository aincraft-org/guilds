package com.azoth.territory.economy;

import com.azoth.territory.decree.GoodsCatalog;
import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.Territory;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyBridgeExpenseTest {

    private static final String TERRITORY = "t1";
    private static final Boundary SQUARE = Boundary.ofPolygon(List.of(
            new BlockPos(0, 0), new BlockPos(10, 0), new BlockPos(10, 10), new BlockPos(0, 10)));

    private static EconomyBridge bridge(RecordingRail rail) {
        TerritoryRegistry territories = new TerritoryRegistry();
        territories.register(new Territory(TERRITORY, "T", "world", SQUARE)
                .withGovernment(Government.monarchy("king")));
        return new EconomyBridge(
                territories,
                new GovernanceRegistry(territories),
                GoodsCatalog.defaultCatalog(),
                rail,
                false);
    }

    @Test
    void rejectsUnknownTerritoryWithoutCallingRail() {
        RecordingRail rail = new RecordingRail(TreasuryDebitStatus.DEBITED);

        ExpenseReport report = bridge(rail).chargeExpense("missing", ExpenseKind.UPKEEP, 5.0, "u1");

        assertEquals(ExpenseOutcome.NO_TERRITORY, report.outcome());
        assertEquals(0, rail.debitCalls.get());
    }
    @Test
    void rejectsAnarchyTerritoryWithoutCallingRail() {
        TerritoryRegistry territories = new TerritoryRegistry();
        territories.register(new Territory(TERRITORY, "T", "world", SQUARE));
        RecordingRail rail = new RecordingRail(TreasuryDebitStatus.DEBITED);
        EconomyBridge bridge = new EconomyBridge(
                territories,
                new GovernanceRegistry(territories),
                GoodsCatalog.defaultCatalog(),
                rail,
                false);

        assertEquals(ExpenseOutcome.NO_GOVERNMENT,
                bridge.chargeExpense(TERRITORY, ExpenseKind.UPKEEP, 5.0, "u1").outcome());
        assertEquals(0, rail.debitCalls.get());
    }


    @Test
    void rejectsInvalidAmountAndKey() {
        RecordingRail rail = new RecordingRail(TreasuryDebitStatus.DEBITED);
        EconomyBridge bridge = bridge(rail);

        assertEquals(ExpenseOutcome.INVALID_AMOUNT,
                bridge.chargeExpense(TERRITORY, ExpenseKind.UPKEEP, 0.0, "u1").outcome());
        assertEquals(ExpenseOutcome.INVALID_AMOUNT,
                bridge.chargeExpense(TERRITORY, null, 5.0, "u2").outcome());
        assertEquals(ExpenseOutcome.INVALID_AMOUNT,
                bridge.chargeExpense(TERRITORY, ExpenseKind.UPKEEP, 5.0, "").outcome());
        assertEquals(0, rail.debitCalls.get());
    }

    @Test
    void duplicateSuccessfulExpenseDoesNotDebitAgain() {
        RecordingRail rail = new RecordingRail(TreasuryDebitStatus.DEBITED);
        EconomyBridge bridge = bridge(rail);

        assertEquals(ExpenseOutcome.DEBITED,
                bridge.chargeExpense(TERRITORY, ExpenseKind.UPKEEP, 10.0, "day-1").outcome());
        assertEquals(ExpenseOutcome.ALREADY_APPLIED,
                bridge.chargeExpense(TERRITORY, ExpenseKind.UPKEEP, 10.0, "day-1").outcome());
        assertEquals(1, rail.debitCalls.get());
    }

    @Test
    void mapsRailFailureAndAllowsRetry() {
        RecordingRail rail = new RecordingRail(TreasuryDebitStatus.INSUFFICIENT_FUNDS,
                TreasuryDebitStatus.DEBITED);
        EconomyBridge bridge = bridge(rail);

        assertEquals(ExpenseOutcome.INSUFFICIENT_FUNDS,
                bridge.chargeExpense(TERRITORY, ExpenseKind.UPKEEP, 10.0, "day-1").outcome());
        assertEquals(ExpenseOutcome.DEBITED,
                bridge.chargeExpense(TERRITORY, ExpenseKind.UPKEEP, 10.0, "day-1").outcome());
        assertEquals(2, rail.debitCalls.get());
    }

    @Test
    void pendingJournalEntryRequiresReconciliationAfterRestart() {
        RecordingRail rail = new RecordingRail(TreasuryDebitStatus.DEBITED);
        ExpenseLedger ledger = new ExpenseLedger();
        ledger.load(List.of(new ExpenseEntry(
                "day-1", TERRITORY, ExpenseKind.UPKEEP, 10.0,
                ExpenseJournalState.PENDING, ExpenseOutcome.RECONCILIATION_REQUIRED)));
        TerritoryRegistry territories = territories();
        EconomyBridge bridge = new EconomyBridge(
                territories, new GovernanceRegistry(territories), GoodsCatalog.defaultCatalog(), rail, false, ledger);

        assertEquals(ExpenseOutcome.RECONCILIATION_REQUIRED,
                bridge.chargeExpense(TERRITORY, ExpenseKind.UPKEEP, 10.0, "day-1").outcome());
        assertEquals(0, rail.debitCalls.get());
    }

    @Test
    void persistenceFailureAfterDebitRetainsPendingForReconciliation() {
        AtomicInteger snapshots = new AtomicInteger();
        ExpenseLedger ledger = new ExpenseLedger(entries -> {
            if (snapshots.incrementAndGet() == 2) {
                throw new IllegalStateException("disk unavailable");
            }
        });
        RecordingRail rail = new RecordingRail(TreasuryDebitStatus.DEBITED);
        TerritoryRegistry territories = territories();
        EconomyBridge bridge = new EconomyBridge(
                territories,
                new GovernanceRegistry(territories),
                GoodsCatalog.defaultCatalog(),
                rail,
                false,
                ledger);

        assertEquals(ExpenseOutcome.RECONCILIATION_REQUIRED,
                bridge.chargeExpense(TERRITORY, ExpenseKind.UPKEEP, 5.0, "u1").outcome());
        assertEquals(1, rail.debitCalls.get());
        assertEquals(ExpenseJournalState.PENDING, ledger.find("u1").orElseThrow().state());
    }


    private static TerritoryRegistry territories() {
        TerritoryRegistry territories = new TerritoryRegistry();
        territories.register(new Territory(TERRITORY, "T", "world", SQUARE)
                .withGovernment(Government.monarchy("king")));
        return territories;
    }

    private static final class RecordingRail implements PaymentRail {
        private final TreasuryDebitStatus[] results;
        private final AtomicInteger debitCalls = new AtomicInteger();

        private RecordingRail(TreasuryDebitStatus... results) {
            this.results = results;
        }

        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
            return new SettlementResult(PaymentRail.SettlementStatus.SETTLED);
        }

        @Override
        public TreasuryDebitResult debitTreasury(String territoryId, double amount) {
            int index = Math.min(debitCalls.getAndIncrement(), results.length - 1);
            return new TreasuryDebitResult(results[index]);
        }

        @Override
        public boolean available() {
            return true;
        }
    }
}
