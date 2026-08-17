package org.aincraft.guilds.services.impl;

import dev.mintychochip.territory.permission.BlockProtection;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Location;
import org.aincraft.guilds.services.GuildService;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuildHearthstoneServiceImplTest {

    private JavaPlugin plugin;
    private Server server;
    private GuildService guildService;
    private BlockProtection blockProtection;
    private GuildHearthstoneServiceImpl service;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        guildService = mock(GuildService.class);
        blockProtection = mock(BlockProtection.class);
        service = new GuildHearthstoneServiceImpl(plugin, guildService, blockProtection, 30);
    }

    @Test
    void teleport_denied_whenNoGuild() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(server.getPlayer(uuid)).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(guildService.getAllGuilds()).thenReturn(List.of());

        boolean result = service.teleportToGuildSpawn(uuid);

        assertFalse(result);
        verify(player).sendMessage("§cYou are not in a guild.");
    }

    @Test
    void teleport_denied_whenSpawnNotSet() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(server.getPlayer(uuid)).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(uuid);
        Guild guild = new Guild();
        guild.setId(UUID.randomUUID().toString());
        guild.setName("g");
        guild.setResidents(Set.of(uuid));
        when(guildService.getAllGuilds()).thenReturn(List.of(guild));
        when(guildService.canTeleportToSpawn(uuid, "g")).thenReturn(true);
        when(guildService.getGuildSpawn("g")).thenReturn(Optional.empty());

        boolean result = service.teleportToGuildSpawn(uuid);

        assertFalse(result);
        verify(player).sendMessage("§cGuild spawn is not set.");
    }

    @Test
    void teleport_denied_whenDestinationProtected() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(server.getPlayer(uuid)).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(uuid);
        Guild guild = new Guild();
        guild.setId(UUID.randomUUID().toString());
        guild.setName("g");
        guild.setResidents(Set.of(uuid));
        when(guildService.getAllGuilds()).thenReturn(List.of(guild));
        when(guildService.canTeleportToSpawn(uuid, "g")).thenReturn(true);
        Location model = new Location();
        model.setWorld("world");
        model.setX(0);
        model.setY(64);
        model.setZ(0);
        model.setYaw(0);
        model.setPitch(0);
        when(guildService.getGuildSpawn("g")).thenReturn(Optional.of(model));
        World world = mock(World.class);
        when(server.getWorld("world")).thenReturn(world);
        when(blockProtection.canTeleportInto(any(), anyInt(), anyInt(), any())).thenReturn(false);

        boolean result = service.teleportToGuildSpawn(uuid);

        assertFalse(result);
        verify(player).sendMessage("§cDestination is protected.");
        verify(player, never()).teleport(any(org.bukkit.Location.class));
    }

    @Test
    void teleport_success_setsCooldownAndTeleports() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(server.getPlayer(uuid)).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(uuid);
        Guild guild = new Guild();
        guild.setId(UUID.randomUUID().toString());
        guild.setName("g");
        guild.setResidents(Set.of(uuid));
        when(guildService.getAllGuilds()).thenReturn(List.of(guild));
        when(guildService.canTeleportToSpawn(uuid, "g")).thenReturn(true);
        Location model = new Location();
        model.setWorld("world");
        model.setX(0);
        model.setY(64);
        model.setZ(0);
        model.setYaw(0);
        model.setPitch(0);
        when(guildService.getGuildSpawn("g")).thenReturn(Optional.of(model));
        World world = mock(World.class);
        when(server.getWorld("world")).thenReturn(world);
        when(blockProtection.canTeleportInto(any(), anyInt(), anyInt(), any())).thenReturn(true);
        when(player.teleport(any(org.bukkit.Location.class))).thenReturn(true);

        boolean result = service.teleportToGuildSpawn(uuid);

        assertTrue(result);
        verify(player, times(1)).teleport(any(org.bukkit.Location.class));
    }
}
