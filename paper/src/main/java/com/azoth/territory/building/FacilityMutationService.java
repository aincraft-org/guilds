package com.azoth.territory.building;

import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.persist.FacilityStore;
import com.azoth.territory.registry.FacilityRegistry;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/** Serializes candidate validation, durable save, and live facility publication. */
public final class FacilityMutationService {
    private final FacilityRegistry live;
    private final FacilityStore store;

    public FacilityMutationService(FacilityRegistry live, FacilityStore store) {
        this.live = Objects.requireNonNull(live, "live");
        this.store = Objects.requireNonNull(store, "store");
    }

    public synchronized SettlementFacility register(SettlementFacility facility) throws IOException {
        Objects.requireNonNull(facility, "facility");
        FacilityRegistry candidate = live.copy();
        candidate.register(facility);
        store.save(candidate.list());
        live.replaceAll(candidate.list());
        return facility;
    }

    public synchronized Optional<SettlementFacility> remove(String facilityId) throws IOException {
        if (facilityId == null || facilityId.isBlank()) {
            return Optional.empty();
        }
        String normalized = facilityId.trim();
        SettlementFacility existing = live.get(normalized).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        FacilityRegistry candidate = live.copy();
        candidate.unregister(normalized);
        store.save(candidate.list());
        live.replaceAll(candidate.list());
        return Optional.of(existing);
    }
}
