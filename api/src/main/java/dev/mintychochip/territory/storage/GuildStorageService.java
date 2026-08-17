package dev.mintychochip.territory.storage;

import java.util.List;
import java.util.UUID;

/** Guild-owned item bank accessed only at a storage facility. */
public interface GuildStorageService {
    /**
     * Opens a bank view if the actor is at an allowed storage facility.
     *
     * @param actor player
     * @param worldId world
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return opened snapshot or a denial
     */
    StorageResult open(UUID actor, String worldId, int x, int y, int z);

    /**
     * Replaces the bank contents after a GUI edit.
     *
     * @param actor player who holds the session
     * @param guildId owning guild
     * @param expectedRevision revision from the opened snapshot
     * @param slots occupied slots
     * @return saved snapshot or a denial
     */
    StorageResult save(UUID actor, String guildId, int expectedRevision, List<StorageSlot> slots);

    /**
     * Releases the exclusive viewer session.
     *
     * @param actor player
     * @param guildId owning guild
     * @return closed or a denial
     */
    StorageResult close(UUID actor, String guildId);

    /**
     * Credits one slot at a storage facility.
     *
     * @param actor player
     * @param worldId world
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @param slotIndex destination slot
     * @param item payload
     * @return deposited snapshot or a denial
     */
    StorageResult deposit(UUID actor, String worldId, int x, int y, int z,
                          int slotIndex, OpaqueItemPayload item);

    /**
     * Removes one slot at a storage facility.
     *
     * @param actor player
     * @param worldId world
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @param slotIndex source slot
     * @return withdrawn item or a denial
     */
    StorageResult withdraw(UUID actor, String worldId, int x, int y, int z, int slotIndex);
}
