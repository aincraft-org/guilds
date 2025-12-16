package org.aincraft.commands;

import org.aincraft.GuildService;
import org.aincraft.commands.components.*;
import org.aincraft.subregion.SubregionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

/**
 * Main command executor and router for all guild commands.
 * Routes subcommands to appropriate component handlers.
 */
public class GuildCommandExecutor implements CommandExecutor {
    private final Map<String, GuildCommand> commands = new HashMap<>();

    /**
     * Creates a new GuildCommandExecutor and registers all command components.
     *
     * @param guildService the guild service instance
     * @param subregionService the subregion service instance
     */
    public GuildCommandExecutor(GuildService guildService, SubregionService subregionService) {
        registerCommand(new CreateComponent(guildService));
        registerCommand(new JoinComponent(guildService));
        registerCommand(new LeaveComponent(guildService));
        registerCommand(new DeleteComponent(guildService));
        registerCommand(new KickComponent(guildService));
        registerCommand(new InfoComponent(guildService));
        registerCommand(new ListComponent(guildService));
        registerCommand(new ClaimComponent(guildService));
        registerCommand(new UnclaimComponent(guildService, subregionService));
        registerCommand(new RoleComponent(guildService));
    }

    /**
     * Registers a command component.
     *
     * @param command the command to register
     */
    private void registerCommand(GuildCommand command) {
        commands.put(command.getName().toLowerCase(), command);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        GuildCommand guildCommand = commands.get(args[0].toLowerCase());
        if (guildCommand == null) {
            sender.sendMessage(MessageFormatter.format("<red>Unknown subcommand: <gold>%s</gold></red>", args[0]));
            showUsage(sender);
            return true;
        }

        return guildCommand.execute(sender, args);
    }

    /**
     * Shows the main usage menu.
     *
     * @param sender the command sender
     */
    private void showUsage(CommandSender sender) {
        sender.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Guild Commands", ""));
        for (GuildCommand cmd : commands.values()) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, cmd.getUsage(), cmd.getName()));
        }
    }
}
