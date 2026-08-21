package org.aincraft.guilds.storage.service;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.function.Consumer;

/** Main-thread player inventory mutations for storage compensation. */
public interface PlayerInventoryCoordinator {
    void removeMatching(UUID playerId, ItemStack item, Consumer<Boolean> onComplete);

    void giveItem(UUID playerId, ItemStack item, Consumer<Boolean> onComplete);
}
