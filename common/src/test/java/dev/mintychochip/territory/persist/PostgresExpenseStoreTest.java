package dev.mintychochip.territory.persist;

import dev.mintychochip.territory.PostgresTestDatabase;
import dev.mintychochip.territory.economy.GoodsCatalog;
import dev.mintychochip.territory.economy.EconomyBridge;
import dev.mintychochip.territory.economy.ExpenseEntry;
import dev.mintychochip.territory.economy.ExpenseJournalState;
import dev.mintychochip.territory.economy.ExpenseKind;
import dev.mintychochip.territory.economy.ExpenseOutcome;
import dev.mintychochip.territory.economy.PaymentRail;
import dev.mintychochip.territory.economy.SettlementResult;
import dev.mintychochip.territory.economy.TreasuryDebitResult;
import dev.mintychochip.territory.economy.TreasuryDebitStatus;
import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.Government;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.permission.GovernanceRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgresExpenseStoreTest {
    private PostgresDatabase database;

    @BeforeEach
    void setUp() throws Exception {
        database = PostgresTestDatabase.open();
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void roundTripPreservesEntryAndRestartBridgeRecognizesDebit() throws Exception {
        ExpenseEntry entry = new ExpenseEntry(
                "upkeep:t1:1000", "t1", ExpenseKind.UPKEEP, 12.5,
                ExpenseJournalState.DEBITED, ExpenseOutcome.DEBITED);
        PostgresExpenseStore store = new PostgresExpenseStore(database);
        store.save(List.of(entry));

        assertEquals(List.of(entry), new PostgresExpenseStore(database).load());

        dev.mintychochip.territory.economy.ExpenseLedger ledger = new dev.mintychochip.territory.economy.ExpenseLedger();
        ledger.load(new PostgresExpenseStore(database).load());
        TerritoryRegistry territories = new TerritoryRegistry();
        territories.register(new Territory(
                "t1", "T1", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(10, 0),
                        new BlockPos(10, 10), new BlockPos(0, 10)))
        ).withGovernment(Government.monarchy("king")));
        EconomyBridge bridge = new EconomyBridge(
                territories,
                new GovernanceRegistry(territories),
                GoodsCatalog.defaultCatalog(),
                new TestRail(),
                false,
                ledger);

        assertEquals(ExpenseOutcome.ALREADY_APPLIED,
                bridge.chargeExpense("t1", ExpenseKind.UPKEEP, 12.5, entry.idempotencyKey()).outcome());
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
