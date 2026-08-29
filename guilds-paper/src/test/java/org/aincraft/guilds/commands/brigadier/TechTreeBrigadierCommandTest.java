package org.aincraft.guilds.commands.brigadier;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.aincraft.guilds.config.TravelCurrencyConfig;
import org.aincraft.guilds.gui.TechTreeGUI;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Resident;
import org.aincraft.guilds.services.GuildProjectService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.services.travel.TravelCurrencyRewardSource;
import org.aincraft.guilds.services.travel.TravelCurrencyService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TechTreeBrigadierCommandTest {
    private static final TravelCurrencyConfig CURRENCY_CONFIG = rewardConfig(41L);
    private static final Logger LOGGER = Logger.getLogger(TechTreeBrigadierCommand.class.getName());
    @Test
    void successfulCompletionAwardsTheInitiatingPlayer() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String guildId = "guild-project-test";
        String projectId = "better_storage";
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);

        Guild guild = new Guild("Project Guild", playerUuid);
        guild.setId(guildId);
        Resident resident = new Resident(playerUuid, "initiator");
        resident.setGuild(guild.getName());

        TravelCurrencyService currencyService = mock(TravelCurrencyService.class);
        when(currencyService.award(any(), any(TravelCurrencyRewardSource.class), anyString(), anyLong(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(
                        new TravelCurrencyService.RewardResult(
                                TravelCurrencyService.RewardStatus.AWARDED, null)));
        GuildProjectService projectService = mock(GuildProjectService.class);
        when(projectService.getActiveProjectId(guild))
                .thenReturn(Optional.of(projectId), Optional.empty());
        when(projectService.completeActiveProject(guild)).thenReturn(true, false);

        ResidentService residentService = mock(ResidentService.class);
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(resident));
        GuildService guildService = mock(GuildService.class);
        when(guildService.getGuild(guild.getName())).thenReturn(Optional.of(guild));

        TechTreeBrigadierCommand command = command(currencyService, projectService, guildService, residentService);
        CommandContext<CommandSourceStack> context = playerContext(player);

        assertEquals(1, invokeComplete(command, context));
        assertEquals(0, invokeComplete(command, context));
        var order = inOrder(projectService);
        order.verify(projectService).getActiveProjectId(guild);
        order.verify(projectService).completeActiveProject(guild);
        verify(currencyService, org.mockito.Mockito.times(1)).award(
                eq(playerUuid),
                eq(TravelCurrencyRewardSource.GUILD_ACTIVITY),
                eq("project:" + guildId + ":" + projectId),
                eq(CURRENCY_CONFIG.rewardAmount(TravelCurrencyRewardSource.GUILD_ACTIVITY)),
                anyLong());
    }

    @Test
    void exceptionalAwardIsObservedAndLoggedAfterSuccessfulCompletion() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        String guildId = "guild-project-failure";
        String projectId = "project";
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        Guild guild = new Guild("Project Guild", playerUuid);
        guild.setId(guildId);
        Resident resident = new Resident(playerUuid, "initiator");
        resident.setGuild(guild.getName());

        IllegalStateException failure = new IllegalStateException("wallet unavailable");
        TravelCurrencyService currencyService = mock(TravelCurrencyService.class);
        when(currencyService.award(any(), any(TravelCurrencyRewardSource.class), anyString(), anyLong(), anyLong()))
                .thenReturn(CompletableFuture.failedFuture(failure));
        GuildProjectService projectService = mock(GuildProjectService.class);
        when(projectService.getActiveProjectId(guild)).thenReturn(Optional.of(projectId));
        when(projectService.completeActiveProject(guild)).thenReturn(true);
        ResidentService residentService = mock(ResidentService.class);
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(resident));
        GuildService guildService = mock(GuildService.class);
        when(guildService.getGuild(guild.getName())).thenReturn(Optional.of(guild));

        Logger logger = LOGGER;
        CapturingHandler handler = new CapturingHandler();
        boolean useParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            TechTreeBrigadierCommand command =
                    command(currencyService, projectService, guildService, residentService);
            assertEquals(1, invokeComplete(command, playerContext(player)));
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(useParentHandlers);
        }

        assertNotNull(handler.record);
        assertTrue(handler.record.getMessage().contains("source=GUILD_ACTIVITY"));
        assertTrue(handler.record.getMessage().contains("eventId=project:" + guildId + ":" + projectId));
        assertSame(failure, handler.record.getThrown());
    }

    @Test
    void failedCompletionDoesNotAward() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        Guild guild = new Guild("Project Guild", playerUuid);
        Resident resident = new Resident(playerUuid, "initiator");
        resident.setGuild(guild.getName());

        GuildProjectService projectService = mock(GuildProjectService.class);
        when(projectService.getActiveProjectId(guild)).thenReturn(Optional.of("project"));
        when(projectService.completeActiveProject(guild)).thenReturn(false);
        ResidentService residentService = mock(ResidentService.class);
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(resident));
        GuildService guildService = mock(GuildService.class);
        when(guildService.getGuild(guild.getName())).thenReturn(Optional.of(guild));
        TravelCurrencyService currencyService = mock(TravelCurrencyService.class);

        TechTreeBrigadierCommand command = command(currencyService, projectService, guildService, residentService);

        assertEquals(0, invokeComplete(command, playerContext(player)));
        verify(currencyService, never()).award(any(), any(), anyString(), anyLong(), anyLong());
    }

    @Test
    void nonPlayerSenderDoesNotAward() throws Exception {
        CommandSender sender = mock(CommandSender.class);
        CommandContext<CommandSourceStack> context = mock(CommandContext.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(context.getSource()).thenReturn(source);
        when(source.getSender()).thenReturn(sender);
        TravelCurrencyService currencyService = mock(TravelCurrencyService.class);

        TechTreeBrigadierCommand command = command(currencyService, mock(GuildProjectService.class),
                mock(GuildService.class), mock(ResidentService.class));

        assertEquals(0, invokeComplete(command, context));
        verify(currencyService, never()).award(any(), any(), anyString(), anyLong(), anyLong());
    }

    private static TechTreeBrigadierCommand command(TravelCurrencyService currencyService,
                                                      GuildProjectService projectService,
                                                      GuildService guildService,
                                                      ResidentService residentService) {
        return new TechTreeBrigadierCommand(mock(TechTreeService.class), projectService, guildService,
                residentService, mock(TechTreeGUI.class), currencyService, CURRENCY_CONFIG);
    }

    private static CommandContext<CommandSourceStack> playerContext(Player player) {
        CommandContext<CommandSourceStack> context = mock(CommandContext.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(context.getSource()).thenReturn(source);
        when(source.getSender()).thenReturn(player);
        return context;
    }

    private static int invokeComplete(TechTreeBrigadierCommand command,
                                      CommandContext<CommandSourceStack> context) throws Exception {
        Method method = TechTreeBrigadierCommand.class.getDeclaredMethod("handleComplete", CommandContext.class);
        method.setAccessible(true);
        return (int) method.invoke(command, context);
    }
    private static final class CapturingHandler extends Handler {
        private LogRecord record;

        @Override
        public void publish(LogRecord record) {
            this.record = record;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static TravelCurrencyConfig rewardConfig(long amount) {
        TravelCurrencyConfig defaults = TravelCurrencyConfig.defaults();
        return new TravelCurrencyConfig(defaults.starterBalance(), defaults.maximumBalance(),
                defaults.baseCost(), defaults.distanceDivisor(), defaults.modeMultipliers(),
                defaults.reservationDurationMillis(),
                Map.of(TravelCurrencyRewardSource.GUILD_ACTIVITY, amount));
    }
}
