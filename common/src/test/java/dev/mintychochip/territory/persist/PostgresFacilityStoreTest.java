package dev.mintychochip.territory.persist;

import dev.mintychochip.territory.PostgresTestDatabase;
import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.FacilityType;
import dev.mintychochip.territory.model.SettlementFacility;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.registry.FacilityRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgresFacilityStoreTest {
    private PostgresDatabase database;

    @BeforeEach
    void setUp() throws Exception {
        database = PostgresTestDatabase.open();
        try (var connection = database.connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM facilities");
        }
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void roundTripPreservesEveryFacilityType() throws Exception {
        TerritoryRegistry territories = new TerritoryRegistry(List.of(new Territory(
                "t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))))));
        SettlementFacility waystone = facility("waystone", FacilityType.WAYSTONE, 5);
        SettlementFacility market = facility("market", FacilityType.TRADING_POST, 6);
        SettlementFacility storage = facility("storage", FacilityType.STORAGE, 7);
        PostgresFacilityStore store = new PostgresFacilityStore(database);

        store.save(List.of(waystone, market, storage));
        FacilityRegistry reloaded = new FacilityRegistry(territories);
        store.loadInto(reloaded);

        assertEquals(List.of(market, storage, waystone), reloaded.list());
    }

    private static SettlementFacility facility(String id, FacilityType type, int x) {
        return new SettlementFacility(id, id, "t1", type, "world", x, 64, 5);
    }
}
