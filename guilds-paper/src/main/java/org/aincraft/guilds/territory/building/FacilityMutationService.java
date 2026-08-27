package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.persist.FacilityStore;
import org.aincraft.guilds.territory.registry.FacilityRegistry;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Serializes candidate validation, durable save, and live facility publication. */
public final class FacilityMutationService {
    private final FacilityRegistry live;
    private final FacilityStore store;
    private final Consumer<SettlementFacility> afterRegister;
    private final Consumer<SettlementFacility> afterRemove;

    public FacilityMutationService(FacilityRegistry live, FacilityStore store) {
        this(live, store, facility -> {}, facility -> {});
    }

    public FacilityMutationService(FacilityRegistry live, FacilityStore store,
                                   Consumer<SettlementFacility> afterRegister,
                                   Consumer<SettlementFacility> afterRemove) {
        this.live = Objects.requireNonNull(live, "live");
        this.store = Objects.requireNonNull(store, "store");
        this.afterRegister = afterRegister == null ? facility -> {} : afterRegister;
        this.afterRemove = afterRemove == null ? facility -> {} : afterRemove;
    }

    public synchronized SettlementFacility register(SettlementFacility facility) throws IOException {
        Objects.requireNonNull(facility, "facility");
        FacilityRegistry candidate = live.copy();
        candidate.register(facility);
        store.save(candidate.list());
        live.replaceAll(candidate.list());
        afterRegister.accept(facility);
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
        afterRemove.accept(existing);
        return Optional.of(existing);
    }
}
