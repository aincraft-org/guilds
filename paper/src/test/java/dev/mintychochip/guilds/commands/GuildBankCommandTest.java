package dev.mintychochip.guilds.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.mintychochip.guilds.GuildsGovernanceSource;
import dev.mintychochip.guilds.commands.brigadier.GuildBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.TechTreeBrigadierCommand;
import dev.mintychochip.guilds.gui.TechTreeGUI;
import dev.mintychochip.guilds.plot.PlotTypeRegistry;
import dev.mintychochip.guilds.services.GuildProjectService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.guilds.services.PermissionService;
import dev.mintychochip.guilds.services.PlotService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.TechTreeService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for guild bank command. */
@ExtendWith(MockitoExtension.class)
class GuildBankCommandTest {
    /** The plugin. */
    @Mock private JavaPlugin plugin;
    /** The residents. */
    @Mock private ResidentService residents;
    /** The guilds. */
    @Mock private GuildService guilds;
    /** The plots. */
    @Mock private PlotService plots;
    /** The permissions. */
    @Mock private PermissionService permissions;
    /** The plot types. */
    @Mock private PlotTypeRegistry plotTypes;
    /** The governance. */
    @Mock private GuildsGovernanceSource governance;
    /** The tech tree. */
    @Mock private TechTreeService techTree;
    /** The projects. */
    @Mock private GuildProjectService projects;
    /** The gui. */
    @Mock private TechTreeGUI gui;

    /**
     * Performs the bank literals exist and bank execute reports unavailable without mint operation.
     * @throws Exception if an error occurs
     */
    @Test
    void bankLiteralsExistAndBankExecuteReportsUnavailableWithoutMint() throws Exception {
        GuildBrigadierCommand command = new GuildBrigadierCommand(plugin, residents, guilds, plots,
                permissions, new TechTreeBrigadierCommand(techTree, projects, guilds, residents, gui),
                plotTypes, governance);
        LiteralCommandNode<CommandSourceStack> root = command.buildCommand();
        assertEquals("guild", root.getLiteral());
        assertTrue(root.getChildren().stream().map(node -> node.getName()).collect(Collectors.toSet())
                .contains("bank"));
        Set<String> bankChildren = root.getChild("bank").getChildren().stream()
                .map(node -> node.getName()).collect(Collectors.toSet());
        assertTrue(bankChildren.containsAll(Set.of("open", "deposit", "withdraw")));

        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(root);
        assertEquals(0, dispatcher.execute("guild bank", source));
        verify(player).sendMessage("§cMint guild banks are unavailable.");
    }
}
