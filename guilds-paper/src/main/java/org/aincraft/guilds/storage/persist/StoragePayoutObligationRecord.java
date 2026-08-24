package org.aincraft.guilds.storage.persist;

import org.aincraft.guilds.territory.storage.OpaqueItemPayload;

import java.time.Instant;
import java.util.UUID;

/** Durable payout obligation created when a withdraw commits before player delivery. */
public record StoragePayoutObligationRecord(
        UUID withdrawOperationId,
        String guildId,
        UUID actorUuid,
        String tabId,
        int slotIndex,
        String facilityId,
        OpaqueItemPayload item,
        StoragePayoutObligationStatus status,
        UUID reinsertOperationId,
        UUID deliveryToken,
        Instant createdAt,
        Instant updatedAt) {}
