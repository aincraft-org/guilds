package dev.mintychochip.territory.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Public storage contracts reject blank payloads and report success only for completed operations. */
class StorageContractTest {

    @Test
    void opaquePayloadRejectsBlankFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpaqueItemPayload(" ", "fp", "payload"));
        assertThrows(IllegalArgumentException.class,
                () -> new OpaqueItemPayload("schema", "", "payload"));
        assertThrows(IllegalArgumentException.class,
                () -> new OpaqueItemPayload("schema", "fp", " "));
    }

    @Test
    void snapshotPreservesSlotsAndDefaultCapacity() {
        OpaqueItemPayload item = new OpaqueItemPayload("paper-itemstack-bytes-v1", "abc", "AQID");
        StorageSnapshot snapshot = new StorageSnapshot(
                "guild-1", "vault", StorageSnapshot.DEFAULT_CAPACITY, 1,
                List.of(new StorageSlot(0, item)), true, false);

        assertEquals(54, snapshot.capacitySlots());
        assertEquals(item, snapshot.slots().get(0).item());
        assertTrue(snapshot.canDeposit());
        assertFalse(snapshot.canWithdraw());
    }

    @Test
    void resultSucceededOnlyForCompletedStatuses() {
        assertTrue(StorageResult.denied(StorageStatus.DENIED_NO_FACILITY).status()
                == StorageStatus.DENIED_NO_FACILITY);
        assertFalse(StorageResult.denied(StorageStatus.DENIED_NO_PERMISSION).succeeded());
        assertTrue(new StorageResult(StorageStatus.OPENED, null, null).succeeded());
        assertTrue(new StorageResult(StorageStatus.SAVED, null, null).succeeded());
    }
}
