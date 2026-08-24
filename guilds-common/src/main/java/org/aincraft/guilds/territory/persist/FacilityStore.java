package org.aincraft.guilds.territory.persist;

import org.aincraft.guilds.territory.model.SettlementFacility;

import java.io.IOException;
import java.util.Collection;

/** Durable snapshot store for settlement facility metadata. */
@FunctionalInterface
public interface FacilityStore {
    void save(Collection<SettlementFacility> facilities) throws IOException;
}
