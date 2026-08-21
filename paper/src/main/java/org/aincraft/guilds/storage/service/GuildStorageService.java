package org.aincraft.guilds.storage.service;

import org.aincraft.guilds.territory.storage.GuildStorageBank;
import org.aincraft.guilds.territory.storage.OpaqueItemPayload;
import org.aincraft.guilds.territory.storage.StoragePolicy;
import org.aincraft.guilds.territory.storage.StorageSlot;
import org.aincraft.guilds.territory.storage.StorageTab;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Role-gated guild item storage operations. */
public interface GuildStorageService {
    StorageResult<GuildStorageBank> getBank(UUID actor, String guildId);

    StorageResult<List<StorageTab>> getTabs(UUID actor, String guildId);

    StorageResult<Map<Integer, StorageSlot>> getSlots(UUID actor, String guildId, String tabId);

    StorageResult<StorageSlot> deposit(
            UUID actor,
            String guildId,
            String tabId,
            int slotIndex,
            OpaqueItemPayload item,
            String facilityId);

    StorageResult<OpaqueItemPayload> withdraw(
            UUID actor, String guildId, String tabId, int slotIndex, String facilityId);

    StorageResult<StoragePolicy> getPolicy(UUID actor, String guildId);

    StorageResult<StoragePolicy> updatePolicy(
            UUID actor, String guildId, String depositRole, String withdrawRole, String manageRole);
}
