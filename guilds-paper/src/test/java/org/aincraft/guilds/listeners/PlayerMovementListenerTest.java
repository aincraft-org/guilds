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
import java.util.Map;
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
    private static final TravelCurrencyConfig CURRENCY_CONFIG = rewardConfig(43L);
    @Test
    void territoryEntryAwardsEachPlayerAndRepeatedMovementIsHarmless() {
        TerritoryRegistry territories = territoryRegistry();
        TravelCurrencyService currencyService = mock(TravelCurrencyService.class);
        when(currencyService.award(any(), any(TravelCurrencyRewardSource.class), anyString(), anyLong(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(
                        new TravelCurrencyService.RewardResult(
                                TravelCurrencyService.RewardStatus.AWARDED, null)));
        PlayerMovementListener listener = listener(territories, currencyService,
                mock(JavaPlugin.class));

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
                eq(CURRENCY_CONFIG.rewardAmount(TravelCurrencyRewardSource.EXPLORATION_MILESTONE)),
                anyLong());
        verify(currencyService, times(1)).award(
                eq(secondUuid),
                eq(TravelCurrencyRewardSource.EXPLORATION_MILESTONE),
                eq("territory:territory-1:" + secondUuid),
                eq(CURRENCY_CONFIG.rewardAmount(TravelCurrencyRewardSource.EXPLORATION_MILESTONE)),
                anyLong());
    }

    @Test
    void exceptionalAwardIsObservedAndLoggedWithoutThrowing() {
        TerritoryRegistry territories = territoryRegistry();
        TravelCurrencyService currencyService = mock(TravelCurrencyService.class);
        JavaPlugin plugin = mock(JavaPlugin.class);
        java.util.logging.Logger logger = mock(java.util.logging.Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        IllegalStateException failure = new IllegalStateException("wallet unavailable");
        when(currencyService.award(any(), any(TravelCurrencyRewardSource.class), anyString(), anyLong(), anyLong()))
                .thenReturn(CompletableFuture.failedFuture(failure));
        PlayerMovementListener listener = listener(territories, currencyService, plugin);
        World world = world();
        Chunk chunk = mock(Chunk.class);
        Player player = player();

        listener.onPlayerMove(move(player, world, chunk, -1, 5));

        verify(logger).log(
                eq(java.util.logging.Level.WARNING),
                org.mockito.ArgumentMatchers.contains("source=EXPLORATION_MILESTONE"),
                org.mockito.ArgumentMatchers.same(failure));
    }

    @Test
    void actorlessMovementDoesNotAward() {
        TravelCurrencyService currencyService = mock(TravelCurrencyService.class);
        PlayerMovementListener listener = listener(territoryRegistry(), currencyService,
                mock(JavaPlugin.class));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(null);

        listener.onPlayerMove(move(player, world(), mock(Chunk.class), -1, 5));

        verify(currencyService, org.mockito.Mockito.never())
                .award(any(), any(), anyString(), anyLong(), anyLong());
    }

    @Test
    void cleanupClearsTerritoryObservationForReconnect() {
        TravelCurrencyService currencyService = mock(TravelCurrencyService.class);
        when(currencyService.award(any(), any(TravelCurrencyRewardSource.class), anyString(), anyLong(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(
                        new TravelCurrencyService.RewardResult(
                                TravelCurrencyService.RewardStatus.AWARDED, null)));
        PlayerMovementListener listener = listener(territoryRegistry(), currencyService,
                mock(JavaPlugin.class));
        World world = world();
        Chunk chunk = mock(Chunk.class);
        Player player = player();
        UUID playerUuid = player.getUniqueId();

        listener.onPlayerMove(move(player, world, chunk, -1, 5));
        listener.cleanupOfflinePlayer(playerUuid);
        listener.onPlayerMove(move(player, world, chunk, -1, 5));

        verify(currencyService, times(2)).award(
                eq(playerUuid),
                eq(TravelCurrencyRewardSource.EXPLORATION_MILESTONE),
                eq("territory:territory-1:" + playerUuid),
                eq(CURRENCY_CONFIG.rewardAmount(TravelCurrencyRewardSource.EXPLORATION_MILESTONE)),
                anyLong());
    }

    private static TerritoryRegistry territoryRegistry() {
        return new TerritoryRegistry(List.of(new Territory(
                "territory-1", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100))))));
    }

    private static PlayerMovementListener listener(TerritoryRegistry territories,
                                                   TravelCurrencyService currencyService,
                                                   JavaPlugin plugin) {
        return new PlayerMovementListener(
                plugin,
                mock(PlotService.class),
                mock(GuildService.class),
                mock(ResidentService.class),
                mock(PlotTypeHandlerManager.class),
                mock(PlotTypeRegistry.class),
                territories,
                currencyService,
                CURRENCY_CONFIG);
    }

    private static World world() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        return world;
    }
    private static TravelCurrencyConfig rewardConfig(long amount) {
        TravelCurrencyConfig defaults = TravelCurrencyConfig.defaults();
        return new TravelCurrencyConfig(defaults.starterBalance(), defaults.maximumBalance(),
                defaults.baseCost(), defaults.distanceDivisor(), defaults.modeMultipliers(),
                defaults.reservationDurationMillis(),
                Map.of(TravelCurrencyRewardSource.EXPLORATION_MILESTONE, amount));
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
