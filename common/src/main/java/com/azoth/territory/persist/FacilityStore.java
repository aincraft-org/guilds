package com.azoth.territory.persist;

import com.azoth.territory.model.SettlementFacility;

import java.io.IOException;
import java.util.Collection;

/** Durable snapshot store for settlement facility metadata. */
@FunctionalInterface
public interface FacilityStore {
    void save(Collection<SettlementFacility> facilities) throws IOException;
}
