package dev.mintychochip.territory.building;

import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.FacilityType;
import dev.mintychochip.territory.model.SettlementFacility;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.persist.FacilityStore;
import dev.mintychochip.territory.registry.FacilityRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityMutationServiceTest {
    private FacilityRegistry live;
    private MemoryStore store;
    private FacilityMutationService service;
    private SettlementFacility waystone;

    @BeforeEach
    void setUp() {
        Territory territory = new Territory("t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))));
        live = new FacilityRegistry(new TerritoryRegistry(List.of(territory)));
        store = new MemoryStore();
        service = new FacilityMutationService(live, store);
        waystone = new SettlementFacility(
                "north", "North", "t1", FacilityType.WAYSTONE, "world", 5, 64, 5);
    }

    @Test
    void registerPublishesOnlyAfterStoreAcceptsCandidate() throws Exception {
        store.fail = true;
        assertThrows(IOException.class, () -> service.register(waystone));
        assertTrue(live.list().isEmpty());

        store.fail = false;
        service.register(waystone);

        assertEquals(List.of(waystone), store.saved);
        assertEquals(List.of(waystone), live.list());
    }

    @Test
    void failedRemovalLeavesLiveFacilityPresent() throws Exception {
        service.register(waystone);
        store.fail = true;

        assertThrows(IOException.class, () -> service.remove(waystone.id()));

        assertEquals(Optional.of(waystone), live.get(waystone.id()));
    }

    @Test
    void rejectedCandidateDoesNotWriteOrMutateLiveState() throws Exception {
        service.register(waystone);
        int writes = store.writes;
        SettlementFacility duplicateLocation = new SettlementFacility(
                "other", "Other", "t1", FacilityType.TRADING_POST, "world", 5, 64, 5);

        assertThrows(IllegalArgumentException.class, () -> service.register(duplicateLocation));

        assertEquals(writes, store.writes);
        assertEquals(List.of(waystone), live.list());
    }

    @Test
    void unknownRemovalDoesNotWrite() throws Exception {
        assertEquals(Optional.empty(), service.remove("missing"));
        assertEquals(0, store.writes);
    }

    private static final class MemoryStore implements FacilityStore {
        private List<SettlementFacility> saved = List.of();
        private int writes;
        private boolean fail;

        @Override
        public void save(Collection<SettlementFacility> facilities) throws IOException {
            writes++;
            if (fail) {
                throw new IOException("forced failure");
            }
            saved = List.copyOf(facilities);
        }
    }
}
