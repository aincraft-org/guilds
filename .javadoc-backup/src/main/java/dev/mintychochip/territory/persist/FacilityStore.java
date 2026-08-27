package dev.mintychochip.territory.persist;

import dev.mintychochip.territory.model.SettlementFacility;

import java.io.IOException;
import java.util.Collection;

/** Durable snapshot store for settlement facility metadata. */
@FunctionalInterface
public interface FacilityStore {
    void save(Collection<SettlementFacility> facilities) throws IOException;
}
