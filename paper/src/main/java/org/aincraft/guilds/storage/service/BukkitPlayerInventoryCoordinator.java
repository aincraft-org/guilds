package org.aincraft.guilds.storage.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Bukkit-backed inventory coordinator that always runs on the main thread. */
public final class BukkitPlayerInventoryCoordinator implements PlayerInventoryCoordinator {
    private final MainThreadExecutor mainThreadExecutor;

    public BukkitPlayerInventoryCoordinator(MainThreadExecutor mainThreadExecutor) {
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
    }

    @Override
    public void removeMatching(UUID playerId, ItemStack item, Consumer<Boolean> onComplete) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(item, "item");
        mainThreadExecutor.run(() -> {
            boolean success = false;
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                success = player.getInventory().removeItem(item.clone()).isEmpty();
            }
            if (onComplete != null) {
                onComplete.accept(success);
            }
        });
    }

    @Override
    public void giveItem(UUID playerId, ItemStack item, Consumer<Boolean> onComplete) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(item, "item");
        mainThreadExecutor.run(() -> {
            boolean success = false;
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                success = player.getInventory().addItem(item.clone()).isEmpty();
            }
            if (onComplete != null) {
                onComplete.accept(success);
            }
        });
    }
}
