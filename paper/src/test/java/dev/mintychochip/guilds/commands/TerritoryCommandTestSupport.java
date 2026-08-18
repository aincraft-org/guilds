package dev.mintychochip.guilds.commands;

import dev.mintychochip.guilds.commands.brigadier.TerritoryBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.TerritoryCommand;

import com.mojang.brigadier.CommandDispatcher;
import dev.mintychochip.guilds.GuildsPlugin;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** territory command test support. */
final class TerritoryCommandTestSupport {
    /** Creates a new territory command test support instance. */
    private TerritoryCommandTestSupport() {
    }

    /**
     * Performs the execute operation.
     * @param plugin the plugin
     * @param sender the sender
     * @param input the input
     * @throws Exception if an error occurs
     */
    static void execute(GuildsPlugin plugin, CommandSender sender, String input) throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new TerritoryBrigadierCommand(new TerritoryCommand(plugin)).buildCommand());
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        dispatcher.execute(input, source);
    }
}
