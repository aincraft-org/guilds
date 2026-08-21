package org.aincraft.guilds.storage.service;

import java.util.UUID;

/** Validates physical storage facility access for guild storage mutations. */
@FunctionalInterface
public interface StorageFacilityAccessValidator {
    StorageResult<Void> validateMutationAccess(UUID actor, String guildId, String facilityId);

    static StorageFacilityAccessValidator permitAll() {
        return (actor, guildId, facilityId) -> StorageResult.success(null);
    }
}
