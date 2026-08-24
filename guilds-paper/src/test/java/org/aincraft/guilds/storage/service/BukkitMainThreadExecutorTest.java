package org.aincraft.guilds.storage.service;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitMainThreadExecutorTest {
    @Test
    void runsDirectlyOnPrimaryThread() {
        Plugin plugin = mock(Plugin.class);
        AtomicBoolean ran = new AtomicBoolean();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            new BukkitMainThreadExecutor(plugin).run(() -> ran.set(true));
        }
        assertTrue(ran.get());
    }

    @Test
    void schedulesThroughBukkitWhenOffPrimaryThread() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        AtomicBoolean ran = new AtomicBoolean();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            new BukkitMainThreadExecutor(plugin).run(() -> ran.set(true));
            verify(scheduler).runTask(eq(plugin), task.capture());
            assertFalse(ran.get());
            task.getValue().run();
        }
        assertTrue(ran.get());
    }
}
