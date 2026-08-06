package com.azoth.territory.persist;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.FacilityType;
import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.model.Territory;
import com.azoth.territory.registry.FacilityRegistry;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityStoreTest {

    @TempDir
    Path tempDir;

    private static Territory territory() {
        return new Territory("t1", "T1", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))));
    }

    private static FacilityRegistry registry() {
        return new FacilityRegistry(new TerritoryRegistry(List.of(territory())));
    }

    @Test
    void savesAndLoadsFacilityDirectory() throws Exception {
        Path file = tempDir.resolve("facilities.json");
        FacilityRegistry source = registry();
        SettlementFacility market = new SettlementFacility(
                "market", "Market", "t1", FacilityType.TRADING_POST, "world", 5, 64, 5);
        SettlementFacility storage = new SettlementFacility(
                "storage", "Storage", "t1", FacilityType.STORAGE, "world", 6, 64, 6);
        source.replaceAll(List.of(market, storage));

        new FacilityStore(file).save(source);

        FacilityRegistry loaded = registry();
        new FacilityStore(file).loadInto(loaded);
        assertEquals(source.list(), loaded.list());
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void missingFileLoadsEmpty() throws Exception {
        FacilityRegistry loaded = registry();
        new FacilityStore(tempDir.resolve("missing.json")).loadInto(loaded);
        assertTrue(loaded.list().isEmpty());
    }

    @Test
    void malformedFileFailsWithoutPartialMutation() throws Exception {
        Path file = tempDir.resolve("bad.json");
        Files.writeString(file, "{ not json");
        FacilityRegistry loaded = registry();

        assertThrows(java.io.IOException.class, () -> new FacilityStore(file).loadInto(loaded));
        assertTrue(loaded.list().isEmpty());
    }
}
