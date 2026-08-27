package dev.mintychochip.territory.economy;

import dev.mintychochip.territory.decree.GoodsCatalog;
import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.Government;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.permission.GovernanceRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
class EconomyBridgeExpenseTest {
    private static final String TERRITORY = "t1";
    private static final Boundary SQUARE = Boundary.ofPolygon(List.of(
            new BlockPos(0, 0), new BlockPos(10, 0), new BlockPos(10, 10), new BlockPos(0, 10)));

    private static EconomyBridge bridge(PaymentRail rail) {
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
    @Test
    void concurrentSameKeyDebitsOnlyOnce() throws Exception {
        BlockingRail rail = new BlockingRail();
        EconomyBridge bridge = bridge(rail);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ExpenseReport> first = executor.submit(
                    () -> bridge.chargeExpense(TERRITORY, ExpenseKind.UPKEEP, 5.0, "same-key"));
            assertTrue(rail.started.await(5, TimeUnit.SECONDS));

            Future<ExpenseReport> second = executor.submit(
                    () -> bridge.chargeExpense(TERRITORY, ExpenseKind.UPKEEP, 5.0, "same-key"));

            assertEquals(ExpenseOutcome.RECONCILIATION_REQUIRED,
                    second.get(5, TimeUnit.SECONDS).outcome());
            rail.release.countDown();
            assertEquals(ExpenseOutcome.DEBITED, first.get(5, TimeUnit.SECONDS).outcome());
            assertEquals(1, rail.debitCalls.get());
        } finally {
            rail.release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void thrownRailDebitLeavesPendingAndReturnsReconciliation() {
        RuntimeException failure = new IllegalStateException("rail unavailable");
        ExpenseLedger ledger = new ExpenseLedger();
        TerritoryRegistry territories = territories();
        EconomyBridge bridge = new EconomyBridge(
                territories,
                new GovernanceRegistry(territories),
                GoodsCatalog.defaultCatalog(),
                new ThrowingRail(failure),
                false,
                ledger);

        ExpenseReport report = bridge.chargeExpense(TERRITORY, ExpenseKind.UPKEEP, 5.0, "throwing-key");

        assertEquals(ExpenseOutcome.RECONCILIATION_REQUIRED, report.outcome());
        assertEquals(ExpenseJournalState.PENDING, ledger.find("throwing-key").orElseThrow().state());
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
    private static final class BlockingRail implements PaymentRail {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger debitCalls = new AtomicInteger();

        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
            return new SettlementResult(PaymentRail.SettlementStatus.SETTLED);
        }

        @Override
        public TreasuryDebitResult debitTreasury(String territoryId, double amount) {
            debitCalls.incrementAndGet();
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            }
            return new TreasuryDebitResult(TreasuryDebitStatus.DEBITED);
        }

        @Override
        public boolean available() {
            return true;
        }
    }

    private static final class ThrowingRail implements PaymentRail {
        private final RuntimeException failure;

        private ThrowingRail(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
            return new SettlementResult(PaymentRail.SettlementStatus.SETTLED);
        }

        @Override
        public TreasuryDebitResult debitTreasury(String territoryId, double amount) {
            throw failure;
        }

        @Override
        public boolean available() {
            return true;
        }
    }

}
