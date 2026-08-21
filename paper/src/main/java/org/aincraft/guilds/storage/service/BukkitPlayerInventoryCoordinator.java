package org.aincraft.guilds.storage.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

/** Bukkit-backed inventory coordinator that always runs on the main thread. */
public final class BukkitPlayerInventoryCoordinator implements PlayerInventoryCoordinator {
    private final MainThreadExecutor mainThreadExecutor;

    public BukkitPlayerInventoryCoordinator(MainThreadExecutor mainThreadExecutor) {
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
    }

    @Override
    public void removeMatching(UUID playerId, ItemStack item, Runnable onComplete) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(item, "item");
        mainThreadExecutor.run(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.getInventory().removeItem(item.clone());
            }
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    @Override
    public void giveItem(UUID playerId, ItemStack item, Runnable onComplete) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(item, "item");
        mainThreadExecutor.run(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.getInventory().addItem(item.clone());
            }
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }
}
