package org.aincraft.guilds.storage.persist;

import org.aincraft.guilds.territory.storage.OpaqueItemPayload;

import java.time.Instant;
import java.util.UUID;

/** Durable obligation to return a deposit item to the player after a failed mutation. */
public record StorageDepositRestorationRecord(
        UUID depositOperationId,
        String guildId,
        UUID actorUuid,
        String tabId,
        int slotIndex,
        String facilityId,
        OpaqueItemPayload item,
        StorageDepositRestorationStatus status,
        Instant createdAt,
        Instant updatedAt) {}
