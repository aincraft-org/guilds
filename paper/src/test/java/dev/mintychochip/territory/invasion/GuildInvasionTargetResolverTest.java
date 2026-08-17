package dev.mintychochip.territory.invasion;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PlotService;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GuildInvasionTargetResolverTest {
    @Test
    void resolverRequiresEligibilityAndRejectsAmbiguousNames() {
        GuildService guilds = mock(GuildService.class);
        PlotService plots = mock(PlotService.class);
        Guild first = guild("Guild A");
        Guild second = guild("guild a");
        when(guilds.getAllGuilds()).thenReturn(List.of(first, second));
        assertTrue(new GuildInvasionTargetResolver(guilds, plots).resolve("GUILD A").isRejected());
    }

    @Test
    void resolverCoversClaimResidentWorldOwnershipAndFallback() {
        GuildService guilds = mock(GuildService.class);
        PlotService plots = mock(PlotService.class);
        Guild guild = guild("Guild A");
        UUID resident = UUID.randomUUID();
        guild.setResidents(Set.of(resident));
        GuildBlock claim = new GuildBlock(0, 0, "world", guild.getId());
        guild.setHomeBlock(claim);
        when(guilds.getAllGuilds()).thenReturn(List.of(guild));
        when(plots.getGuildBlocksInGuild(guild.getName())).thenReturn(List.of(claim));
        when(plots.getGuildBlock(0, 0, "world")).thenReturn(Optional.of(claim));
        Server server = mock(Server.class);
        World world = mock(World.class);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(63);
        when(server.getWorld("world")).thenReturn(world);
        try (var ignored = mockStatic(Bukkit.class)) {
            ignored.when(() -> Bukkit.getPlayer(resident)).thenReturn(player);
            ignored.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            var result = new GuildInvasionTargetResolver(guilds, plots).resolve("guild a");
            assertTrue(result.isResolved());
            assertEquals(64.0, result.target().orElseThrow().center().getY());
        }
    }

    private static Guild guild(String name) {
        Guild guild = new Guild(name, UUID.randomUUID());
        guild.setId(UUID.randomUUID().toString());
        return guild;
    }
}
