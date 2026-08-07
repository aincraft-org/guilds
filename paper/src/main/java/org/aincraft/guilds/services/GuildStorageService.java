package org.aincraft.guilds.services;

import com.azoth.territory.storage.GuildStoragePolicy;
import com.azoth.territory.storage.OpaqueItemPayload;
import com.azoth.territory.storage.StorageAddress;
import com.azoth.territory.storage.StorageOpenResult;
import com.azoth.territory.storage.StorageResult;
import com.azoth.territory.storage.StorageWithdrawResult;

import java.util.UUID;

/**
 * Facility-bound facade for guild storage. Every operation authorizes the
 * actor against the exact registered {@code STORAGE} facility at the given
 * world/block coordinates before any persistence mutation; the open/withdraw
 * wrappers carry their snapshot/payload only on {@code SUCCESS}.
 */
public interface GuildStorageService {
    StorageOpenResult open(UUID actor, String world, int blockX, int blockY, int blockZ);

    StorageResult deposit(UUID actor, StorageAddress address, OpaqueItemPayload payload,
                          String facilityId, String world, int blockX, int blockY, int blockZ);

    StorageWithdrawResult withdraw(UUID actor, StorageAddress address,
                                   String facilityId, String world, int blockX, int blockY, int blockZ);

    StorageResult setPolicy(UUID actor, String guildId, GuildStoragePolicy policy,
                            String facilityId, String world, int blockX, int blockY, int blockZ);

    StorageResult unlockTab(UUID actor, String guildId, String tabId, String displayName,
                            int ordinal, int capacitySlots, String facilityId,
                            String world, int blockX, int blockY, int blockZ);
}
