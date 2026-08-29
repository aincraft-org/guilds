package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.persist.FacilityStore;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FacilityMutationServiceTest {
    @Test
    void registerAndRemoveNotifyBankerLifecycle() throws Exception {
        Territory territory = new Territory("t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))));
        FacilityRegistry live = new FacilityRegistry(new TerritoryRegistry(List.of(territory)));
        List<String> events = new ArrayList<>();
        FacilityMutationService mutations = new FacilityMutationService(live, new MemoryStore(),
                facility -> events.add("spawn:" + facility.id()),
                facility -> events.add("despawn:" + facility.id()));
        SettlementFacility bank = new SettlementFacility(
                "bank-1", "Bank", "t1", FacilityType.BANK, "world", 5, 64, 5);

        mutations.register(bank);
        mutations.remove("bank-1");

        assertEquals(List.of("spawn:bank-1", "despawn:bank-1"), events);
    }

    @Test
    void failedDurableSaveLeavesLiveRegistryAndCallbacksUntouched() throws Exception {
        Territory territory = new Territory("t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))));
        FacilityRegistry live = new FacilityRegistry(new TerritoryRegistry(List.of(territory)));
        SettlementFacility existing = new SettlementFacility(
                "existing", "Existing", "t1", FacilityType.BANK, "world", 5, 64, 5);
        live.register(existing);
        List<String> events = new ArrayList<>();
        FacilityMutationService mutations = new FacilityMutationService(live,
                ignored -> { throw new java.io.IOException("disk full"); },
                facility -> events.add("spawn:" + facility.id()),
                facility -> events.add("despawn:" + facility.id()));

        SettlementFacility candidate = new SettlementFacility(
                "candidate", "Candidate", "t1", FacilityType.BANK, "world", 6, 64, 5);
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> mutations.register(candidate));

        assertEquals(List.of(existing), live.list());
        assertEquals(List.of(), events);
    }

    @Test
    void validatorRunsBeforeDurableSaveAndReactivationUsesSameBoundary() throws Exception {
        Territory territory = new Territory("t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))));
        FacilityRegistry live = new FacilityRegistry(new TerritoryRegistry(List.of(territory)));
        FastTravelFacilityValidator validator = org.mockito.Mockito.mock(FastTravelFacilityValidator.class);
        org.mockito.Mockito.when(validator.validateCandidate(
                org.mockito.Mockito.any(), org.mockito.Mockito.any())).thenReturn(
                new FastTravelFacilityValidator.ValidationResult(AnchorStatus.ACTIVE, "ok"));
        MemoryStore store = new MemoryStore();
        FacilityMutationService mutations = new FacilityMutationService(live, store, validator);
        SettlementFacility facility = new SettlementFacility(
                "facility", "Facility", "t1", FacilityType.BANK, "world", 5, 64, 5);

        mutations.register(facility);
        mutations.reactivate("facility");

        org.mockito.Mockito.verify(validator, org.mockito.Mockito.times(2))
                .validateCandidate(org.mockito.Mockito.any(), org.mockito.Mockito.any());
        assertEquals(List.of(facility), live.list());
        assertEquals(2, store.saves);
    }

    private static final class MemoryStore implements FacilityStore {
        private int saves;

        @Override
        public void save(Collection<SettlementFacility> facilities) {
            saves++;
        }
    }
}
