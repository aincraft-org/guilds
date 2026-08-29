package org.aincraft.guilds.territory.persist;

import org.aincraft.guilds.territory.PostgresTestDatabase;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
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
        TerritoryRegistry territories = territories();
        SettlementFacility waystone = facility("waystone", FacilityType.WAYSTONE, 5);
        SettlementFacility market = facility("market", FacilityType.TRADING_POST, 6);
        SettlementFacility storage = facility("storage", FacilityType.STORAGE, 7);
        SettlementFacility bank = facility("bank", FacilityType.BANK, 8);
        SettlementFacility crystal = facility("crystal", FacilityType.GUILD_CRYSTAL, 9);
        SettlementFacility terminal = facility("terminal", FacilityType.TELEPORT_TERMINAL, 10);
        SettlementFacility boat = facility("boat", FacilityType.BOAT, 11);
        SettlementFacility airship = facility("airship", FacilityType.AIRSHIP, 12);
        PostgresFacilityStore store = new PostgresFacilityStore(database);

        store.save(List.of(waystone, market, storage, bank, crystal, terminal, boat, airship));
        FacilityRegistry reloaded = new FacilityRegistry(territories);
        store.loadInto(reloaded);

        assertEquals(List.of(
                airship, bank, boat, crystal, market, storage, terminal, waystone), reloaded.list());
    }

    @Test
    void oldFacilityDocumentRemainsReadable() throws Exception {
        try (var connection = database.connection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO facilities (id, doc) VALUES ('legacy', "
                    + "'{\"id\":\"legacy\",\"name\":\"Legacy\",\"territoryId\":\"t1\","
                    + "\"type\":\"WAYSTONE\",\"worldId\":\"world\",\"x\":5,\"y\":64,\"z\":5}')");
        }
        FacilityRegistry reloaded = new FacilityRegistry(territories());
        new PostgresFacilityStore(database).loadInto(reloaded);

        assertEquals(FacilityType.WAYSTONE, reloaded.list().getFirst().type());
    }

    private static TerritoryRegistry territories() {
        return new TerritoryRegistry(List.of(new Territory(
                "t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))))));
    }

    private static SettlementFacility facility(String id, FacilityType type, int x) {
        return new SettlementFacility(id, id, "t1", type, "world", x, 64, 5);
    }
}
