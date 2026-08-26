package dev.mintychochip.guilds.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.mintychochip.guilds.GuildsGovernanceSource;
import dev.mintychochip.guilds.commands.brigadier.AllianceBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.ChatBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildBroadcastBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildLevelBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildPermBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildsGeneralBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.MapBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.PermBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.PlotBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.PlotTypeBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.QuestBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.SpecializationBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.TechTreeBrigadierCommand;
import dev.mintychochip.guilds.gui.TechTreeGUI;
import dev.mintychochip.guilds.plot.PlotTypeRegistry;
import dev.mintychochip.guilds.services.AllianceService;
import dev.mintychochip.guilds.services.ChatService;
import dev.mintychochip.guilds.services.GuildLevelService;
import dev.mintychochip.guilds.services.GuildProjectService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.guilds.services.PermissionService;
import dev.mintychochip.guilds.services.PlotService;
import dev.mintychochip.guilds.services.QuestService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.ResourceService;
import dev.mintychochip.guilds.services.SpecializationService;
import dev.mintychochip.guilds.services.TechTreeService;
import dev.mintychochip.guilds.GuildsPlugin;
import dev.mintychochip.guilds.commands.brigadier.TerritoryBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.TerritoryCommand;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for live brigadier command tree. */
@ExtendWith(MockitoExtension.class)
class LiveBrigadierCommandTreeTest {
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
    /** The tech tree gui. */
    @Mock private TechTreeGUI techTreeGui;
    /** The chat. */
    @Mock private ChatService chat;
    /** The alliances. */
    @Mock private AllianceService alliances;
    /** The specializations. */
    @Mock private SpecializationService specializations;
    /** The quests. */
    @Mock private QuestService quests;
    /** The levels. */
    @Mock private GuildLevelService levels;
    /** The resources. */
    @Mock private ResourceService resources;

    /** The tech tree command. */
    private TechTreeBrigadierCommand techTreeCommand;

    /** Sets the up. */
    @BeforeEach
    void setUp() {
        techTreeCommand = new TechTreeBrigadierCommand(techTree, projects, guilds, residents, techTreeGui);
    }

    /** Performs the every live root has named literals not agreedy catch all operation. */
    @Test
    void everyLiveRootHasNamedLiteralsNotAGreedyCatchAll() {
        assertTree("guild", guildCommand().buildCommand(),
                "create", "join", "leave", "delete", "claim", "unclaim", "list",
                "info", "spawn", "setspawn", "toggle", "bank", "techtree", "government");
        assertTree("plot", plotCommand().buildCommand(),
                "claim", "unclaim", "info", "forsale", "buy", "perm", "set", "list");
        assertTree("guilds", generalCommand().buildCommand(),
                "version", "time", "top", "prices", "chat", "universe");
        assertTree("guildlevel", levelCommand().buildCommand(),
                "level", "deposit", "bank", "upgrade", "contributions", "top");
        assertTree("guildsmap", mapCommand().buildCommand(),
                "help");
        assertTree("perm", permCommand().buildCommand(),
                "check", "build", "destroy", "plot", "guild", "flags", "here");
        assertTree("techtree", techTreeCommand.buildCommand(),
                "info", "start", "unlock", "clear", "complete", "list");
        assertTree("alliance", allianceCommand().buildCommand(),
                "create", "invite", "join", "leave", "list", "info", "ally",
                "enemy", "kick", "set", "minister", "government");
        assertTree("specialize", specializeCommand().buildCommand(), "reset");
        assertTree("quest", questCommand().buildCommand(), "progress", "refresh");
        assertTree("territory", territoryCommand().buildCommand(),
                "lookup", "here", "list", "reload", "save", "web", "govern",
                "influence", "declare", "standing", "upkeep", "invasion", "building");

        assertEquals("plottype", new PlotTypeBrigadierCommand().buildCommand().getLiteral());
        assertTrue(childNames(new PlotTypeBrigadierCommand().buildCommand()).isEmpty());
        assertEquals("broadcast", new GuildBroadcastBrigadierCommand().buildCommand().getLiteral());
        assertTrue(childNames(new GuildBroadcastBrigadierCommand().buildCommand()).isEmpty());
        assertEquals("guildperm", guildPermCommand().buildCommand().getLiteral());
        assertTrue(childNames(guildPermCommand().buildCommand()).isEmpty());
        assertEquals("tc", chatCommand().buildCommand().getLiteral());
        assertEquals(Set.of("message"), childNames(chatCommand().buildCommand()));
    }

    /**
     * Performs the every live root executes areal command string operation.
     * @throws Exception if an error occurs
     */
    @Test
    void everyLiveRootExecutesARealCommandString() throws Exception {
        CommandSender sender = permittedSender();
        executeAndHear(guildCommand().buildCommand(), "guild", sender);
        executeAndHear(plotCommand().buildCommand(), "plot", sender);
        executeAndHear(generalCommand().buildCommand(), "guilds", sender);
        executeAndHear(permCommand().buildCommand(), "perm", sender);
        executeAndHear(new PlotTypeBrigadierCommand().buildCommand(), "plottype", sender);
        executeAndHear(new GuildBroadcastBrigadierCommand().buildCommand(), "broadcast", sender);
        executeAndHear(guildPermCommand().buildCommand(), "guildperm", sender);
        executeAndHear(territoryCommand().buildCommand(), "territory list", sender);

        Player player = permittedPlayer();
        executeAndHear(levelCommand().buildCommand(), "guildlevel", player);
        executeAndHear(mapCommand().buildCommand(), "guildsmap", player);
        executeAndHear(mapCommand().buildCommand(), "guildsmap help", player);
        executeAndHear(techTreeCommand.buildCommand(), "techtree", player);
        executeAndHear(chatCommand().buildCommand(), "tc", player);
        executeAndHear(allianceCommand().buildCommand(), "alliance", player);
        executeAndHear(specializeCommand().buildCommand(), "specialize", player);

        when(residents.getResident("console")).thenReturn(Optional.empty());
        CommandSender console = permittedSender();
        when(console.getName()).thenReturn("console");
        executeAndHear(questCommand().buildCommand(), "quest", console);
    }

    /**
     * Performs the assert tree operation.
     * @param root the root
     * @param node the node
     * @param  the 
     */
    private void assertTree(String root, LiteralCommandNode<CommandSourceStack> node, String... children) {
        assertEquals(root, node.getLiteral());
        assertFalse(childNames(node).contains("args"), root + " must not use a greedy args catch-all");
        assertTrue(childNames(node).containsAll(List.of(children)),
                () -> root + " missing children; had " + childNames(node));
    }

    /**
     * Performs the execute and hear operation.
     * @param node the node
     * @param input the input
     * @param sender the sender
     * @throws Exception if an error occurs
     */
    private void executeAndHear(LiteralCommandNode<CommandSourceStack> node, String input, CommandSender sender)
            throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        dispatcher.execute(input, source);
        try {
            verify(sender, atLeastOnce()).sendMessage(any(String.class));
        } catch (AssertionError ignored) {
            verify(sender, atLeastOnce()).sendMessage(any(Component.class));
        }
    }

    /**
     * Performs the permitted sender operation.
     * @return the result
     */
    private CommandSender permittedSender() {
        CommandSender sender = mock(CommandSender.class);
        lenient().when(sender.hasPermission(anyString())).thenReturn(true);
        return sender;
    }

    /**
     * Performs the permitted player operation.
     * @return the result
     */
    private Player permittedPlayer() {
        Player player = mock(Player.class);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        lenient().when(player.hasPermission(anyString())).thenReturn(true);
        lenient().when(player.getUniqueId()).thenReturn(id);
        lenient().when(player.getName()).thenReturn("TestPlayer");
        lenient().when(residents.getResident(id)).thenReturn(Optional.empty());
        return player;
    }

    /**
     * Performs the child names operation.
     * @param node the node
     * @return the result
     */
    private static Set<String> childNames(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());
    }

    /**
     * Performs the guild command operation.
     * @return the result
     */
    private GuildBrigadierCommand guildCommand() {
        return new GuildBrigadierCommand(plugin, residents, guilds, plots, permissions,
                techTreeCommand, plotTypes, governance);
    }

    /**
     * Performs the plot command operation.
     * @return the result
     */
    private PlotBrigadierCommand plotCommand() {
        return new PlotBrigadierCommand(plugin, residents, guilds, plots, permissions, plotTypes);
    }

    /**
     * Performs the general command operation.
     * @return the result
     */
    private GuildsGeneralBrigadierCommand generalCommand() {
        return new GuildsGeneralBrigadierCommand(plugin, residents, guilds, plots, permissions);
    }

    /**
     * Performs the level command operation.
     * @return the result
     */
    private GuildLevelBrigadierCommand levelCommand() {
        return new GuildLevelBrigadierCommand(plugin, residents, guilds, plots, permissions, levels, resources);
    }

    /**
     * Performs the map command operation.
     * @return the result
     */
    private MapBrigadierCommand mapCommand() {
        return new MapBrigadierCommand(plugin, residents, guilds, plots, permissions);
    }

    /**
     * Performs the perm command operation.
     * @return the result
     */
    private PermBrigadierCommand permCommand() {
        return new PermBrigadierCommand(plugin, permissions, plots, guilds);
    }

    /**
     * Performs the guild perm command operation.
     * @return the result
     */
    private GuildPermBrigadierCommand guildPermCommand() {
        return new GuildPermBrigadierCommand(plugin, residents, guilds, plots, permissions);
    }

    /**
     * Performs the chat command operation.
     * @return the result
     */
    private ChatBrigadierCommand chatCommand() {
        return new ChatBrigadierCommand(plugin, chat, guilds, residents);
    }

    /**
     * Performs the alliance command operation.
     * @return the result
     */
    private AllianceBrigadierCommand allianceCommand() {
        return new AllianceBrigadierCommand(plugin, alliances, guilds, residents, governance);
    }

    /**
     * Performs the specialize command operation.
     * @return the result
     */
    private SpecializationBrigadierCommand specializeCommand() {
        return new SpecializationBrigadierCommand(plugin, specializations, guilds, residents);
    }

    /**
     * Performs the quest command operation.
     * @return the result
     */
    private QuestBrigadierCommand questCommand() {
        return new QuestBrigadierCommand(plugin, quests, guilds, residents);
    }

    /**
     * Performs the territory command operation.
     * @return the result
     */
    private TerritoryBrigadierCommand territoryCommand() {
        GuildsPlugin host = mock(GuildsPlugin.class);
        lenient().when(host.getRegistry()).thenReturn(new TerritoryRegistry());
        return new TerritoryBrigadierCommand(new TerritoryCommand(host));
    }
}
