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
    private final FastTravelFacilityValidator fastTravelValidator;
    private final Consumer<SettlementFacility> afterRegister;
    private final Consumer<SettlementFacility> afterRemove;

    public FacilityMutationService(FacilityRegistry live, FacilityStore store) {
        this(live, store, null, facility -> {}, facility -> {});
    }

    public FacilityMutationService(FacilityRegistry live, FacilityStore store,
                                   Consumer<SettlementFacility> afterRegister,
                                   Consumer<SettlementFacility> afterRemove) {
        this(live, store, null, afterRegister, afterRemove);
    }

    public FacilityMutationService(FacilityRegistry live, FacilityStore store,
                                   FastTravelFacilityValidator fastTravelValidator) {
        this(live, store, fastTravelValidator, facility -> {}, facility -> {});
    }

    public FacilityMutationService(FacilityRegistry live, FacilityStore store,
                                   FastTravelFacilityValidator fastTravelValidator,
                                   Consumer<SettlementFacility> afterRegister,
                                   Consumer<SettlementFacility> afterRemove) {
        this.live = Objects.requireNonNull(live, "live");
        this.store = Objects.requireNonNull(store, "store");
        this.fastTravelValidator = fastTravelValidator;
        this.afterRegister = afterRegister == null ? facility -> {} : afterRegister;
        this.afterRemove = afterRemove == null ? facility -> {} : afterRemove;
    }

    /** Compatibility overload for callers that place callbacks before validators. */
    public FacilityMutationService(FacilityRegistry live, FacilityStore store,
                                   Consumer<SettlementFacility> afterRegister,
                                   Consumer<SettlementFacility> afterRemove,
                                   FastTravelFacilityValidator fastTravelValidator) {
        this(live, store, fastTravelValidator, afterRegister, afterRemove);
    }

    public synchronized SettlementFacility register(SettlementFacility facility) throws IOException {
        Objects.requireNonNull(facility, "facility");
        FacilityRegistry candidate = live.copy();
        candidate.register(facility);
        validateCandidate(facility, candidate);
        durableThenPublish(candidate);
        afterRegister.accept(facility);
        return facility;
    }

    public synchronized SettlementFacility replace(SettlementFacility facility) throws IOException {
        Objects.requireNonNull(facility, "facility");
        return replace(facility.id(), facility);
    }

    public synchronized SettlementFacility replace(String existingId, SettlementFacility replacement)
            throws IOException {
        if (existingId == null || existingId.isBlank()) {
            throw new IllegalArgumentException("existing facility id is required");
        }
        Objects.requireNonNull(replacement, "replacement");
        String normalized = existingId.trim();
        SettlementFacility existing = live.get(normalized).orElseThrow(
                () -> new IllegalArgumentException("unknown facility: " + normalized));
        FacilityRegistry candidate = live.copy();
        candidate.unregister(normalized);
        candidate.register(replacement);
        validateCandidate(replacement, candidate);
        durableThenPublish(candidate);
        afterRemove.accept(existing);
        afterRegister.accept(replacement);
        return replacement;
    }

    /** Revalidates and durably republishes a previously persisted facility. */
    public synchronized Optional<SettlementFacility> reactivate(String facilityId) throws IOException {
        if (facilityId == null || facilityId.isBlank()) {
            return Optional.empty();
        }
        String normalized = facilityId.trim();
        SettlementFacility existing = live.get(normalized).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        FacilityRegistry candidate = live.copy();
        validateCandidate(existing, candidate);
        durableThenPublish(candidate);
        return Optional.of(existing);
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
        validateCandidate(existing, candidate);
        durableThenPublish(candidate);
        afterRemove.accept(existing);
        return Optional.of(existing);
    }

    private void validateCandidate(SettlementFacility facility, FacilityRegistry candidate) {
        if (fastTravelValidator == null) {
            return;
        }
        FastTravelFacilityValidator.ValidationResult result =
                fastTravelValidator.validateCandidate(facility, candidate);
        if (!result.valid()) {
            throw new IllegalArgumentException(result.category() + ": " + result.message());
        }
    }

    private void durableThenPublish(FacilityRegistry candidate) throws IOException {
        store.save(candidate.list());
        live.replaceAll(candidate.list());
    }
}
