package com.azoth.territory.persist;

import com.azoth.territory.economy.EconomyBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsUnresolvedTransactions() throws Exception {
        ReconciliationStore store = new ReconciliationStore(tempDir.resolve("reconciliation.json"));
        EconomyBridge.UnresolvedTransaction entry = new EconomyBridge.UnresolvedTransaction(
                "terr", UUID.randomUUID(), 2.5, 1_700_000_000_000L, "refund failed after charge");

        store.save(List.of(entry));

        assertEquals(List.of(entry), store.load());
    }

    @Test
    void missingFileLoadsEmptyQueue() throws Exception {
        ReconciliationStore store = new ReconciliationStore(tempDir.resolve("missing.json"));
        assertTrue(store.load().isEmpty());
    }
}
