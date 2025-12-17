package org.aincraft.subregion;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing region type volume limits.
 */
public interface RegionTypeLimitRepository {

    /**
     * Saves or updates a type limit.
     */
    void save(RegionTypeLimit limit);

    /**
     * Deletes a type limit.
     */
    void delete(String typeId);

    /**
     * Finds a limit by type ID.
     */
    Optional<RegionTypeLimit> findByTypeId(String typeId);

    /**
     * Returns all configured limits.
     */
    List<RegionTypeLimit> findAll();
}
