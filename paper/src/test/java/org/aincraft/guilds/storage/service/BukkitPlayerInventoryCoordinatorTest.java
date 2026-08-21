package org.aincraft.guilds.storage.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitPlayerInventoryCoordinatorTest {
    @Test
    void removeMatchingDelegatesToMainThreadExecutorAndInventory() {
        UUID playerId = UUID.randomUUID();
        ItemStack item = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(item.clone()).thenReturn(clone);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        AtomicBoolean scheduled = new AtomicBoolean();
        MainThreadExecutor executor = task -> {
            scheduled.set(true);
            task.run();
        };
        BukkitPlayerInventoryCoordinator coordinator = new BukkitPlayerInventoryCoordinator(executor);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            coordinator.removeMatching(playerId, item, changed -> {});
        }
        assertTrue(scheduled.get());
        verify(inventory).removeItem(clone);
    }

    @Test
    void giveItemDelegatesToMainThreadExecutorAndInventory() {
        UUID playerId = UUID.randomUUID();
        ItemStack item = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(item.clone()).thenReturn(clone);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.addItem(clone)).thenReturn(new HashMap<>());
        MainThreadExecutor executor = Runnable::run;
        BukkitPlayerInventoryCoordinator coordinator = new BukkitPlayerInventoryCoordinator(executor);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            coordinator.giveItem(playerId, item, changed -> {});
        }
        verify(inventory).addItem(clone);
    }
}
