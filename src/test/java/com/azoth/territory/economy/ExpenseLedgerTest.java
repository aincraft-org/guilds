package com.azoth.territory.economy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpenseLedgerTest {

    @Test
    void storesAndLoadsImmutableSnapshotsThroughSink() {
        AtomicReference<List<ExpenseEntry>> snapshot = new AtomicReference<>();
        ExpenseLedger ledger = new ExpenseLedger(entries -> snapshot.set(List.copyOf(entries)));
        ExpenseEntry entry = new ExpenseEntry(
                "day-1", "t1", ExpenseKind.UPKEEP, 10.0,
                ExpenseJournalState.PENDING, ExpenseOutcome.RECONCILIATION_REQUIRED);

        ledger.put(entry);

        assertEquals(entry, ledger.find("day-1").orElseThrow());
        assertEquals(List.of(entry), snapshot.get());
        assertEquals(List.of(entry), ledger.entries());

        ledger.remove("day-1");
        assertEquals(List.of(), ledger.entries());
        assertEquals(List.of(), snapshot.get());
    }

    @Test
    void loadReplacesAllEntriesAtomically() {
        ExpenseLedger ledger = new ExpenseLedger();
        ledger.put(new ExpenseEntry(
                "old", "t1", ExpenseKind.OTHER, 1.0,
                ExpenseJournalState.DEBITED, ExpenseOutcome.DEBITED));

        ledger.load(List.of(new ExpenseEntry(
                "new", "t2", ExpenseKind.FORTIFICATION, 2.0,
                ExpenseJournalState.UNKNOWN, ExpenseOutcome.RECONCILIATION_REQUIRED)));

        assertEquals(List.of("new"), ledger.entries().stream().map(ExpenseEntry::idempotencyKey).toList());
    }

    @Test
    void sinkFailurePreventsMutation() {
        ExpenseLedger ledger = new ExpenseLedger(entries -> {
            throw new IllegalStateException("disk unavailable");
        });
        ExpenseEntry entry = new ExpenseEntry(
                "day-1", "t1", ExpenseKind.UPKEEP, 10.0,
                ExpenseJournalState.PENDING, ExpenseOutcome.RECONCILIATION_REQUIRED);

        assertThrows(IllegalStateException.class, () -> ledger.put(entry));
        assertEquals(List.of(), ledger.entries());
    }
}
