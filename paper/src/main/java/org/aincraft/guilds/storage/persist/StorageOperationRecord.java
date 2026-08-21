package org.aincraft.guilds.storage.persist;

import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.aincraft.guilds.territory.storage.StorageSlot;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Durable guild storage mutation journal row. */
public record StorageOperationRecord(
        UUID operationId,
        String guildId,
        String operationType,
        UUID actorUuid,
        String tabId,
        int slotIndex,
        String facilityId,
        StorageOperationStatus status,
        String resultStatus,
        String resultError,
        OpaqueItemPayload resultItem,
        StorageSlot resultSlot,
        Instant createdAt,
        Instant updatedAt) {

    public Optional<OpaqueItemPayload> resultItemOptional() {
        return Optional.ofNullable(resultItem);
    }

    public Optional<StorageSlot> resultSlotOptional() {
        return Optional.ofNullable(resultSlot);
    }
}
