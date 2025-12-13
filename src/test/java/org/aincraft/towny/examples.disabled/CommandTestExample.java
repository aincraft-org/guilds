package org.aincraft.towny.examples;

import org.aincraft.towny.base.BaseIntegrationTest;
import org.aincraft.towny.commands.PlotCommand;
import org.aincraft.towny.factory.TestObjectFactory;
import org.aincraft.towny.models.TownBlock;
import org.aincraft.towny.services.PlotService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Example Bukkit plugin command test
 * Demonstrates proper testing of Bukkit commands using MockBukkit and JUnit 5
 */
class CommandTestExample extends BaseIntegrationTest {

    @Mock
    private PlotService plotService;

    private PlotCommand plotCommand;
    private Command mockCommand;
    private UUID playerUuid;

    @Override
    protected void setup() {
        // Create test command
        plotCommand = new PlotCommand(plotService);

        // Create mock command
        mockCommand = mock(Command.class);
        when(mockCommand.getName()).thenReturn("plot");

        // Get test player UUID
        playerUuid = mockPlayer.getUniqueId();
    }

    @Test
    @DisplayName("Should claim plot successfully when no conflicts exist")
    void shouldClaimPlotSuccessfully() {
        // Given - setup test conditions
        String[] args = {"claim"};

        // Mock plot service responses
        when(plotService.townBlockExists(0, 0, getMockBukkitWorldName())).thenReturn(false);
        when(plotService.canResidentClaimPlot(playerUuid, 0, 0, getMockBukkitWorldName())).thenReturn(true);
        when(plotService.claimPlotForResident(playerUuid, 0, 0, getMockBukkitWorldName())).thenReturn(true);

        // When - execute the command
        boolean result = plotCommand.onCommand(mockPlayer, mockCommand, "plot", args);

        // Then - verify the results
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(ChatColor.GREEN + "Plot claimed successfully!");
        verify(plotService).claimPlotForResident(playerUuid, 0, 0, getMockBukkitWorldName());
    }

    @Test
    @DisplayName("Should deny claim when plot already exists")
    void shouldDenyClaimWhenPlotExists() {
        // Given - plot already exists
        String[] args = {"claim"};
        when(plotService.townBlockExists(0, 0, getMockBukkitWorldName())).thenReturn(true);

        // When - execute the command
        boolean result = plotCommand.onCommand(mockPlayer, mockCommand, "plot", args);

        // Then - should be denied
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(ChatColor.RED + "Plot is already claimed!");
        verify(plotService, never()).claimPlotForResident(any(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("Should deny claim when resident lacks permission")
    void shouldDenyClaimWhenResidentLacksPermission() {
        // Given - resident lacks permission
        String[] args = {"claim"};
        when(plotService.townBlockExists(0, 0, getMockBukkitWorldName())).thenReturn(false);
        when(plotService.canResidentClaimPlot(playerUuid, 0, 0, getMockBukkitWorldName())).thenReturn(false);

        // When - execute the command
        boolean result = plotCommand.onCommand(mockPlayer, mockCommand, "plot", args);

        // Then - should be denied
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(ChatColor.RED + "You don't have permission to claim this plot!");
        verify(plotService, never()).claimPlotForResident(any(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("Should show plot information")
    void shouldShowPlotInformation() {
        // Given - existing plot with data
        String[] args = {"info"};
        TownBlock testPlot = TestObjectFactory.createTestTownBlock(0, 0, getMockBukkitWorldName(), "TestTown", null);
        testPlot.setPrice(1000.0);

        when(plotService.getTownBlock(0, 0, getMockBukkitWorldName())).thenReturn(testPlot);

        // When - execute the command
        boolean result = plotCommand.onCommand(mockPlayer, mockCommand, "plot", args);

        // Then - should show plot info
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(contains("Plot Information"));
        verify(mockPlayer).sendMessage(contains("Type:"));
        verify(mockPlayer).sendMessage(contains("Price:"));
    }

    @Test
    @DisplayName("Should handle invalid command arguments")
    void shouldHandleInvalidCommandArguments() {
        // Given - invalid arguments
        String[] args = {"invalid"};

        // When - execute the command
        boolean result = plotCommand.onCommand(mockPlayer, mockCommand, "plot", args);

        // Then - should show help
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(ChatColor.YELLOW + "Usage: /plot <claim|info|unclaim|set|for-sale>");
    }

    @Test
    @DisplayName("Should require player sender for plot commands")
    void shouldRequirePlayerSender() {
        // Given - console sender
        CommandSender consoleSender = mock(CommandSender.class);
        when(consoleSender.hasPermission(anyString())).thenReturn(true);

        String[] args = {"claim"};

        // When - console tries to execute
        boolean result = plotCommand.onCommand(consoleSender, mockCommand, "plot", args);

        // Then - should be denied
        assertThat(result).isTrue();
        verify(consoleSender).sendMessage(ChatColor.RED + "Only players can use this command!");
        verify(plotService, never()).townBlockExists(anyInt(), anyInt(), anyString());
    }
}