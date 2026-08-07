package com.azoth.territory;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.FacilityType;
import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.model.Territory;
import com.azoth.territory.persist.PostgresDatabase;
import com.azoth.territory.persist.PostgresFacilityStore;
import com.azoth.territory.registry.FacilityRegistry;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        int reloadMethod = source.indexOf("public void reloadTerritories()");
        int reloadTerritoryLoad = source.indexOf("store.loadInto(registry)", reloadMethod);
        int reloadFacilityLoad = source.indexOf("facilityStore.loadInto(facilityRegistry)", reloadTerritoryLoad);
        assertTrue(reloadMethod >= 0);
        assertTrue(reloadTerritoryLoad > reloadMethod);
        assertTrue(reloadFacilityLoad > reloadTerritoryLoad);
    }

    /**
     * Opt-in PostgreSQL integration test (skipped unless AZOTH_TEST_JDBC_URL is
     * set): a registered STORAGE facility must survive a real save/load round
     * trip through {@link PostgresFacilityStore}.
     */
    @Test
    void storageFacilitySurvivesPostgresSaveLoadRoundTrip() throws Exception {
        PostgresDatabase database = PostgresTestDatabase.open();
        try {
            TerritoryRegistry territories = new TerritoryRegistry(List.of(testTerritory("guild-territory")));
            FacilityRegistry facilities = new FacilityRegistry(territories);
            SettlementFacility storage = new SettlementFacility(
                    "guild-storage", "Guild Storage", "guild-territory",
                    FacilityType.STORAGE, "world", 5, 64, 5);
            facilities.register(storage);

            PostgresFacilityStore store = new PostgresFacilityStore(database);
            store.save(facilities);

            FacilityRegistry reloaded = new FacilityRegistry(territories);
            store.loadInto(reloaded);

            assertEquals(storage, reloaded.get("guild-storage").orElseThrow());
            assertEquals(Optional.of(storage), reloaded.resolve("world", 5, 64, 5));
        } finally {
            database.close();
        }
    }

    /**
     * Opt-in PostgreSQL integration test (skipped unless AZOTH_TEST_JDBC_URL is
     * set): a persisted facility whose territory no longer exists must surface
     * as {@link java.io.IOException} from {@code loadInto} so plugin startup
     * aborts through the mandatory persistence path (database closed, plugin
     * disabled) instead of escaping as an unchecked validation failure.
     */
    @Test
    void invalidPersistedFacilityFailsThroughCheckedLoadBoundary() throws Exception {
        PostgresDatabase database = PostgresTestDatabase.open();
        try {
            TerritoryRegistry territories = new TerritoryRegistry(List.of(testTerritory("guild-territory")));
            FacilityRegistry facilities = new FacilityRegistry(territories);
            PostgresFacilityStore store = new PostgresFacilityStore(database);
            store.save(facilities);

            try (Connection connection = database.connection();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO facilities (id, doc) VALUES (?, ?::jsonb)")) {
                insert.setString(1, "orphan-facility");
                insert.setString(2, "{\"id\":\"orphan-facility\",\"name\":\"Orphan\",\"territoryId\":\"ghost-territory\","
                        + "\"type\":\"STORAGE\",\"worldId\":\"world\",\"x\":5,\"y\":64,\"z\":5}");
                insert.executeUpdate();
            }

            IOException failure = assertThrows(IOException.class, () -> store.loadInto(facilities));
            assertTrue(failure.getMessage().contains("validate"));
        } finally {
            database.close();
        }
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
