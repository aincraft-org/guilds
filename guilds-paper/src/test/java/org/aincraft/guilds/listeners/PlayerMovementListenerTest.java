package org.aincraft.guilds.listeners;

import org.aincraft.guilds.config.TravelCurrencyConfig;
import org.aincraft.guilds.plot.PlotTypeHandlerManager;
import org.aincraft.guilds.plot.PlotTypeRegistry;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.travel.TravelCurrencyRewardSource;
import org.aincraft.guilds.services.travel.TravelCurrencyService;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerMovementListenerTest {
    @Test
    void territoryEntryAwardsEachPlayerAndRepeatedMovementIsHarmless() {
        TerritoryRegistry territories = new TerritoryRegistry(List.of(new Territory(
                "territory-1", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100))))));
        TravelCurrencyService currencyService = mock(TravelCurrencyService.class);
        when(currencyService.award(any(), any(TravelCurrencyRewardSource.class), anyString(), anyLong(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(null));
        PlayerMovementListener listener = new PlayerMovementListener(
                mock(JavaPlugin.class),
                mock(PlotService.class),
                mock(GuildService.class),
                mock(ResidentService.class),
                mock(PlotTypeHandlerManager.class),
                mock(PlotTypeRegistry.class),
                territories,
                currencyService,
                TravelCurrencyConfig.defaults());

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Chunk chunk = mock(Chunk.class);
        Player first = player();
        UUID firstUuid = first.getUniqueId();
        Player second = player();
        UUID secondUuid = second.getUniqueId();
        listener.onPlayerMove(move(first, world, chunk, -1, 5));
        listener.onPlayerMove(move(first, world, chunk, 5, 6));
        listener.onPlayerMove(move(second, world, chunk, -1, 5));

        verify(currencyService, times(1)).award(
                eq(firstUuid),
                eq(TravelCurrencyRewardSource.EXPLORATION_MILESTONE),
                eq("territory:territory-1:" + firstUuid),
                eq(TravelCurrencyConfig.defaults().rewardAmount(TravelCurrencyRewardSource.EXPLORATION_MILESTONE)),
                anyLong());
        verify(currencyService, times(1)).award(
                eq(secondUuid),
                eq(TravelCurrencyRewardSource.EXPLORATION_MILESTONE),
                eq("territory:territory-1:" + secondUuid),
                eq(TravelCurrencyConfig.defaults().rewardAmount(TravelCurrencyRewardSource.EXPLORATION_MILESTONE)),
                anyLong());
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static PlayerMoveEvent move(Player player, World world, Chunk chunk, int fromX, int toX) {
        Location from = mock(Location.class);
        when(from.getWorld()).thenReturn(world);
        when(from.getBlockX()).thenReturn(fromX);
        when(from.getBlockZ()).thenReturn(5);
        when(from.getChunk()).thenReturn(chunk);

        Location to = mock(Location.class);
        when(to.getWorld()).thenReturn(world);
        when(to.getBlockX()).thenReturn(toX);
        when(to.getBlockZ()).thenReturn(5);
        when(to.getChunk()).thenReturn(chunk);

        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        return event;
    }
}
