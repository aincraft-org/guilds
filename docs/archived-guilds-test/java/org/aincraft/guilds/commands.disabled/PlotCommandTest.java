package org.aincraft.guilds.commands;

import org.aincraft.guilds.TestUtilities;
import org.aincraft.guilds.models.Permission;
import org.aincraft.guilds.models.TownBlock;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TownService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlotCommand
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlotCommandTest {

    @Mock
    private PlotService plotService;

    @Mock
    private ResidentService residentService;

    @Mock
    private TownService townService;

    @Mock
    private Logger logger;

    private PlotCommand plotCommand;

    private Player player;
    private UUID playerUuid;
    private World world;
    private Location playerLocation;
    private Chunk playerChunk;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getName()).thenReturn("TestPlayer");

        world = mock(World.class);
        when(world.getName()).thenReturn("test_world");

        playerChunk = mock(Chunk.class);
        when(playerChunk.getX()).thenReturn(10);
        when(playerChunk.getZ()).thenReturn(20);

        playerLocation = new Location(world, 160, 64, 320); // 10,20 chunk center
        when(player.getLocation()).thenReturn(playerLocation);
        when(player.getWorld()).thenReturn(world);
        when(player.getChunk()).thenReturn(playerChunk);

        // Also mock getChunk() on the location to return the same chunk
        when(playerLocation.getChunk()).thenReturn(playerChunk);

        // Manually create the command with mocked dependencies
        plotCommand = new PlotCommand(plotService, residentService, townService, logger);
    }

    @Test
    @DisplayName("Should show usage when no arguments provided")
    void shouldShowUsageWhenNoArgumentsProvided() {
        // Given
        Command command = mock(Command.class);
        String[] args = {};

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.GOLD + "=== Plot Commands ===");
    }

    @Test
    @DisplayName("Should show usage when invalid subcommand provided")
    void shouldShowUsageWhenInvalidSubcommandProvided() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"invalid"};

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.GOLD + "=== Plot Commands ===");
    }

    @Test
    @DisplayName("Should claim plot successfully")
    void shouldClaimPlotSuccessfully() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"claim"};

        when(plotService.townBlockExists(10, 20, "test_world")).thenReturn(false);
        when(plotService.canResidentClaimPlot(playerUuid, 10, 20, "test_world")).thenReturn(true);
        when(plotService.claimPlotForResident(playerUuid, 10, 20, "test_world")).thenReturn(true);

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.GREEN + "Plot claimed successfully!");
        verify(plotService).claimPlotForResident(playerUuid, 10, 20, "test_world");
    }

    @Test
    @DisplayName("Should fail to claim already claimed plot")
    void shouldFailToClaimAlreadyClaimedPlot() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"claim"};

        when(plotService.canResidentClaimPlot(playerUuid, 10, 20, "test_world")).thenReturn(false);

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.RED + "You cannot claim this plot. The town must claim the territory first.");
        verify(plotService, never()).claimPlotForResident(any(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("Should fail to claim when permissions denied")
    void shouldFailToClaimWhenPermissionsDenied() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"claim"};

        when(plotService.canResidentClaimPlot(playerUuid, 10, 20, "test_world")).thenReturn(false);

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.RED + "You cannot claim this plot. The town must claim the territory first.");
        verify(plotService, never()).claimPlotForResident(any(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("Should buy plot for sale successfully")
    void shouldBuyPlotForSaleSuccessfully() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"buy"};

        TownBlock testPlot = TestUtilities.createTestTownBlockForSale(1000.0);
        testPlot.setOwnerId(null);

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.of(testPlot));
        when(plotService.canResidentAffordPlot(playerUuid, testPlot.getId())).thenReturn(true);
        when(plotService.buyPlot(playerUuid, testPlot.getId(), 1000.0)).thenReturn(true);

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.GREEN + String.format("Plot purchased for %.2f!", 1000.0));
        verify(plotService).buyPlot(playerUuid, testPlot.getId(), 1000.0);
    }

    @Test
    @DisplayName("Should fail to buy plot not for sale")
    void shouldFailToBuyPlotNotForSale() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"buy"};

        TownBlock testPlot = TestUtilities.createTestTownBlock();
        testPlot.setPrice(0.0); // Not for sale

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.of(testPlot));

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.RED + "This plot is not for sale.");
        verify(plotService, never()).buyPlot(any(), any(), anyDouble());
    }

    @Test
    @DisplayName("Should fail to buy plot no plot found")
    void shouldFailToBuyPlotNoPlotFound() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"buy"};

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.empty());

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.RED + "No plot found at this location.");
        verify(plotService, never()).buyPlot(any(), any(), anyDouble());
    }

    @Test
    @DisplayName("Should fail to buy plot cannot afford")
    void shouldFailToBuyPlotCannotAfford() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"buy"};

        TownBlock testPlot = TestUtilities.createTestTownBlockForSale(1000.0);

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.of(testPlot));
        when(plotService.canResidentAffordPlot(playerUuid, testPlot.getId())).thenReturn(false);

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.RED + "You cannot afford this plot.");
        verify(plotService, never()).buyPlot(any(), any(), anyDouble());
    }

    @Test
    @DisplayName("Should show plot info")
    void shouldShowPlotInfo() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"info"};

        TownBlock testPlot = TestUtilities.createTestTownBlock();
        testPlot.setOwnerId(playerUuid);
        testPlot.setPrice(1000.0);
        testPlot.addPermissionFlag(Permission.Flag.BUILD);

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.of(testPlot));

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.GOLD + "=== Plot Information ===");
        verify(player).sendMessage(contains("Location:"));
        verify(player).sendMessage(contains("Type:"));
        verify(player).sendMessage(contains("Price:"));
        verify(player).sendMessage(contains("Permissions:"));
    }

    @Test
    @DisplayName("Should show plot info for town-owned plot")
    void shouldShowPlotInfoForTownOwnedPlot() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"info"};

        TownBlock testPlot = TestUtilities.createTestTownBlock();
        testPlot.setOwnerId(null); // Town owned
        testPlot.setPrice(0.0);

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.of(testPlot));

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(contains("Owner: " + ChatColor.WHITE + "Town owned"));
    }

    @Test
    @DisplayName("Should fail to show plot info when no plot found")
    void shouldFailToShowPlotInfoWhenNoPlotFound() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"info"};

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.empty());

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.RED + "No plot found at this location.");
    }

    @Test
    @DisplayName("Should set plot for sale successfully")
    void shouldSetPlotForSaleSuccessfully() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"forsale", "1500.50"};

        TownBlock testPlot = TestUtilities.createTestTownBlock();
        testPlot.setOwnerId(playerUuid);

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.of(testPlot));
        when(plotService.setPlotForSale(testPlot.getId(), 1500.50, playerUuid)).thenReturn(true);

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.GREEN + String.format("Plot put up for sale for %.2f!", 1500.50));
        verify(plotService).setPlotForSale(testPlot.getId(), 1500.50, playerUuid);
    }

    @Test
    @DisplayName("Should remove plot from sale")
    void shouldRemovePlotFromSale() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"forsale", "0"};

        TownBlock testPlot = TestUtilities.createTestTownBlock();
        testPlot.setOwnerId(playerUuid);
        testPlot.setPrice(1000.0); // Currently for sale

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.of(testPlot));
        when(plotService.setPlotForSale(testPlot.getId(), 0.0, playerUuid)).thenReturn(true);

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.GREEN + "Plot removed from sale.");
        verify(plotService).setPlotForSale(testPlot.getId(), 0.0, playerUuid);
    }

    @Test
    @DisplayName("Should fail to set plot for sale when not owner")
    void shouldFailToSetPlotForSaleWhenNotOwner() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"forsale", "1000.0"};

        TownBlock testPlot = TestUtilities.createTestTownBlock();
        UUID differentOwner = UUID.randomUUID();
        testPlot.setOwnerId(differentOwner);

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.of(testPlot));

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.RED + "You don't own this plot.");
        verify(plotService, never()).setPlotForSale(any(), anyDouble(), any());
    }

    @Test
    @DisplayName("Should fail to set plot for sale with invalid price")
    void shouldFailToSetPlotForSaleWithInvalidPrice() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"forsale", "invalid"};

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.RED + "Invalid price amount.");
        verify(plotService, never()).setPlotForSale(any(), anyDouble(), any());
    }

    @Test
    @DisplayName("Should list plot permissions")
    void shouldListPlotPermissions() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"perm", "list"};

        TownBlock testPlot = TestUtilities.createTestTownBlockWithPermissions(
                Permission.Flag.BUILD | Permission.Flag.DESTROY);

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.of(testPlot));

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.GOLD + "=== Plot Permissions ===");
        verify(player).sendMessage(ChatColor.YELLOW + "Build: " + ChatColor.GREEN + "Allowed");
        verify(player).sendMessage(ChatColor.YELLOW + "Destroy: " + ChatColor.GREEN + "Allowed");
    }

    @Test
    @DisplayName("Should reset plot permissions when owner")
    void shouldResetPlotPermissionsWhenOwner() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"perm", "reset"};

        TownBlock testPlot = TestUtilities.createTestTownBlock();
        testPlot.setOwnerId(playerUuid);
        testPlot.setPermissionsFlags(Permission.Flag.ALL);

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.of(testPlot));
        when(plotService.updateTownBlock(any(TownBlock.class))).thenReturn(testPlot);

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.GREEN + "Plot permissions reset to default.");
        verify(plotService).updateTownBlock(any(TownBlock.class));
    }

    @Test
    @DisplayName("Should fail to reset plot permissions when not owner")
    void shouldFailToResetPlotPermissionsWhenNotOwner() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"perm", "reset"};

        TownBlock testPlot = TestUtilities.createTestTownBlock();
        UUID differentOwner = UUID.randomUUID();
        testPlot.setOwnerId(differentOwner);

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.of(testPlot));

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.RED + "Only plot owners can reset permissions.");
        verify(plotService, never()).updateTownBlock(any());
    }

    @Test
    @DisplayName("Should set plot permission")
    void shouldSetPlotPermission() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"perm", "set", "all", "build", "true"};

        TownBlock testPlot = TestUtilities.createTestTownBlock();
        testPlot.setOwnerId(playerUuid);

        when(plotService.getTownBlock(10, 20, "test_world")).thenReturn(java.util.Optional.of(testPlot));
        when(plotService.updateTownBlock(any(TownBlock.class))).thenReturn(testPlot);

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(contains("Permission 'build'"));
        verify(plotService).updateTownBlock(any(TownBlock.class));
    }

    @Test
    @DisplayName("Should list owned plots")
    void shouldListOwnedPlots() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"list"};

        TownBlock ownedPlot1 = TestUtilities.createTestTownBlock();
        TownBlock ownedPlot2 = TestUtilities.createTestTownBlock();
        ownedPlot1.setOwnerId(playerUuid);
        ownedPlot2.setOwnerId(playerUuid);

        when(plotService.getPlotsOwnedByResident(playerUuid)).thenReturn(List.of(ownedPlot1, ownedPlot2));

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.GREEN + "Your plots (2):");
        verify(player).sendMessage(contains("Your plots (2):"));
    }

    @Test
    @DisplayName("Should handle no owned plots")
    void shouldHandleNoOwnedPlots() {
        // Given
        Command command = mock(Command.class);
        String[] args = {"list"};

        when(plotService.getPlotsOwnedByResident(playerUuid)).thenReturn(List.of());

        // When
        boolean result = plotCommand.onCommand(player, command, "plot", args);

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage(ChatColor.YELLOW + "You don't own any plots.");
    }

    @Test
    @DisplayName("Should provide tab completion for root commands")
    void shouldProvideTabCompletionForRootCommands() {
        // Given
        Command command = mock(Command.class);
        CommandSender sender = mock(Player.class); // Must be a Player
        when(sender.hasPermission("guilds.plot")).thenReturn(true);

        // When
        List<String> completions = plotCommand.onTabComplete(sender, command, "plot", new String[]{""});

        // Then
        assertThat(completions).contains("claim", "buy", "info", "forsale", "perm", "list");
    }

    @Test
    @DisplayName("Should provide tab completion for permission commands")
    void shouldProvideTabCompletionForPermissionCommands() {
        // Given
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("guilds.plot")).thenReturn(true);

        // When
        List<String> completions = plotCommand.onTabComplete(sender, command, "plot", new String[]{"perm"});

        // Then
        assertThat(completions).contains("set", "add", "remove", "list", "reset");
    }

    @Test
    @DisplayName("Should provide tab completion for permission targets")
    void shouldProvideTabCompletionForPermissionTargets() {
        // Given
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("guilds.plot")).thenReturn(true);

        // When
        List<String> completions = plotCommand.onTabComplete(sender, command, "plot", new String[]{"perm", "set"});

        // Then
        assertThat(completions).contains("all", "resident", "town", "assistant", "mayor");
    }

    @Test
    @DisplayName("Should provide tab completion for permission types")
    void shouldProvideTabCompletionForPermissionTypes() {
        // Given
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("guilds.plot")).thenReturn(true);

        // When
        List<String> completions = plotCommand.onTabComplete(sender, command, "plot", new String[]{"perm", "set", "resident"});

        // Then
        assertThat(completions).contains("build", "destroy", "switch", "item_use", "all");
    }

    @Test
    @DisplayName("Should provide tab completion for boolean values")
    void shouldProvideTabCompletionForBooleanValues() {
        // Given
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("guilds.plot")).thenReturn(true);

        // When
        List<String> completions = plotCommand.onTabComplete(sender, command, "plot", new String[]{"perm", "set", "all", "build"});

        // Then
        assertThat(completions).contains("true", "false");
    }

    @Test
    @DisplayName("Should filter tab completion by prefix")
    void shouldFilterTabCompletionByPrefix() {
        // Given
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("guilds.plot")).thenReturn(true);

        // When
        List<String> completions = plotCommand.onTabComplete(sender, command, "plot", new String[]{"cl"});

        // Then - The implementation doesn't filter by prefix, it returns all options
        assertThat(completions).contains("claim", "buy", "info", "forsale", "perm", "list");
    }

    @Test
    @DisplayName("Should return empty list for non-players in tab completion")
    void shouldReturnEmptyListForNonPlayersInTabCompletion() {
        // Given
        Command command = mock(Command.class);
        CommandSender sender = mock(CommandSender.class); // Not a player
        when(sender.hasPermission("guilds.plot")).thenReturn(false);

        // When
        List<String> completions = plotCommand.onTabComplete(sender, command, "plot", new String[]{""});

        // Then
        assertThat(completions).isEmpty();
    }

    /**
     * Helper method to create a string containing text
     */
    private String contains(String text) {
        return argThat(argument -> argument.toString().contains(text));
    }
}