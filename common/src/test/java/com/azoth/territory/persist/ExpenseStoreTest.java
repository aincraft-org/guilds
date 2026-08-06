package com.azoth.territory.persist;

import com.azoth.territory.economy.ExpenseEntry;
import com.azoth.territory.economy.ExpenseJournalState;
import com.azoth.territory.economy.ExpenseKind;
import com.azoth.territory.economy.ExpenseOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpenseStoreTest {

    @Test
    void roundTripsExpenseState(@TempDir Path tempDir) throws IOException {
        ExpenseStore store = new ExpenseStore(tempDir.resolve("expenses.json"));
        List<ExpenseEntry> entries = List.of(
                new ExpenseEntry("u1", "t1", ExpenseKind.UPKEEP, 4.5,
                        ExpenseJournalState.DEBITED, ExpenseOutcome.DEBITED),
                new ExpenseEntry("f1", "t1", ExpenseKind.FORTIFICATION, 7.0,
                        ExpenseJournalState.PENDING, ExpenseOutcome.RECONCILIATION_REQUIRED));

        store.save(entries);

        assertEquals(entries, store.load());
    }

    @Test
    void missingFileLoadsEmpty(@TempDir Path tempDir) throws IOException {
        ExpenseStore store = new ExpenseStore(tempDir.resolve("missing.json"));

        assertEquals(List.of(), store.load());
    }

    @Test
    void malformedFileIsRejected(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("expenses.json");
        Files.writeString(file, "{\"expenses\":{}}");

        assertThrows(IOException.class, () -> new ExpenseStore(file).load());
    }
}
