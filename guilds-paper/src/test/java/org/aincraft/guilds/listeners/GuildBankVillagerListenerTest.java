package org.aincraft.guilds.listeners;

import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.MintGuildBankService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuildBankVillagerListenerTest {
    @Test
    void joiningPlayerGetsStartingMintAccount() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        JavaPlugin plugin = mock(JavaPlugin.class);
        MintGuildBankService bank = mock(MintGuildBankService.class);
        AtomicReference<UUID> ensured = new AtomicReference<>();
        when(bank.ensurePlayerAccount(playerId)).thenAnswer(invocation -> {
            ensured.set(invocation.getArgument(0));
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        });

        GuildBankVillagerListener listener = new GuildBankVillagerListener(
                plugin, mock(GuildService.class), mock(ResidentService.class), mock(PlotService.class), bank, "GUILD_BANK");
        listener.onPlayerJoin(new PlayerJoinEvent(player, "join"));

        assertEquals(playerId, ensured.get());
        verify(bank).ensurePlayerAccount(playerId);
    }
}
