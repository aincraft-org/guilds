package com.azoth.territory;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.FacilityType;
import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.model.Territory;
import com.azoth.territory.persist.PostgresFacilityStore;
import com.azoth.territory.registry.FacilityRegistry;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginFacilityWiringTest {
    @Test
    void facilityRegistryIsASeparateLocationDirectory() throws Exception {
        TerritoryRegistry territories = new TerritoryRegistry(List.of(testTerritory("guild-territory")));
        FacilityRegistry facilities = new FacilityRegistry(territories);
        SettlementFacility storage = new SettlementFacility(
                "guild-storage", "Guild Storage", "guild-territory",
                FacilityType.STORAGE, "world", 5, 64, 5);

        facilities.register(storage);

        assertEquals(Optional.of(storage), facilities.resolve("world", 5, 64, 5));
        assertEquals(FacilityType.STORAGE, facilities.get("guild-storage").orElseThrow().type());
        assertTrue(PostgresFacilityStore.class.getDeclaredMethod("loadInto", FacilityRegistry.class) != null);
        assertTrue(PostgresFacilityStore.class.getDeclaredMethod("save", FacilityRegistry.class) != null);
    }

    @Test
    void pluginWiresFacilitiesAroundTerritoryPersistence() throws Exception {
        String source = readMainSource();
        assertTrue(source.contains("private FacilityRegistry facilityRegistry;"));
        assertTrue(source.contains("private PostgresFacilityStore facilityStore;"));
        assertTrue(source.contains("public FacilityRegistry getFacilityRegistry()"));
        assertTrue(source.contains("public PostgresFacilityStore getFacilityStore()"));
        assertTrue(source.contains("new FacilityRegistry(registry)"));
        assertTrue(source.contains("new PostgresFacilityStore(database)"));
        assertTrue(source.contains("new BukkitEconomyBridge(economyBridge, facilityRegistry)"));

        int territoryLoad = source.indexOf("store.loadInto(registry)");
        int facilityLoad = source.indexOf("facilityStore.loadInto(facilityRegistry)");
        int guildsConstruction = source.indexOf("constructGuildsSubsystem()");
        assertTrue(territoryLoad >= 0);
        assertTrue(facilityLoad > territoryLoad);
        assertTrue(guildsConstruction > facilityLoad);

        int facilitySave = source.indexOf("facilityStore.save(facilityRegistry)");
        int territorySave = source.indexOf("store.save(registry)");
        int databaseClose = source.lastIndexOf("database.close()");
        assertTrue(facilitySave >= 0);
        assertTrue(territorySave > facilitySave);
        assertTrue(databaseClose > territorySave);
    }

    private static Territory testTerritory(String id) {
        return new Territory(
                id,
                "Guild Territory",
                "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0),
                        new BlockPos(10, 0),
                        new BlockPos(10, 10),
                        new BlockPos(0, 10))));
    }

    private static String readMainSource() throws Exception {
        Path source = Path.of("src/main/java/com/azoth/territory/AzothTerritoryPlugin.java");
        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("Cannot locate " + source.toAbsolutePath());
        }
        return Files.readString(source, StandardCharsets.UTF_8);
    }
}
