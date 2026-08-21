package org.aincraft.guilds.storage.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
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
                ItemStack requested = item.clone();
                ItemStack[] contents = player.getInventory().getContents();
                ItemStack[] snapshot = contents == null ? new ItemStack[0] : contents.clone();
                Map<Integer, ItemStack> notRemoved = player.getInventory().removeItem(requested);
                if (notRemoved.isEmpty()) {
                    success = true;
                } else {
                    player.getInventory().setContents(snapshot);
                }
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
                ItemStack requested = item.clone();
                ItemStack[] contents = player.getInventory().getContents();
                ItemStack[] snapshot = contents == null ? new ItemStack[0] : contents.clone();
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(requested);
                if (leftovers.isEmpty()) {
                    success = true;
                } else {
                    player.getInventory().setContents(snapshot);
                }
            }
            if (onComplete != null) {
                onComplete.accept(success);
            }
        });
    }
}
