package org.aincraft.commands.components;

import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command component for setting a guild's description.
 * Usage: /g description <description> or /g description clear
 */
public class DescriptionComponent implements GuildCommand {
    private final GuildService guildService;

    public DescriptionComponent(GuildService guildService) {
        this.guildService = guildService;
    }

    @Override
    public String getName() {
        return "description";
    }

    @Override
    public String getPermission() {
        return "guilds.description";
    }

    @Override
    public String getUsage() {
        return "/g description <description> or /g description clear";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to change guild description"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return false;
        }

        // Get player's guild
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        // Check if player is guild owner
        if (!guild.isOwner(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only the guild owner can change guild description"));
            return true;
        }

        String descriptionInput = args[1].toLowerCase();

        // Handle clear command
        if (descriptionInput.equals("clear")) {
            guild.setDescription(null);
            guildService.save(guild);
            player.sendMessage(MessageFormatter.deserialize("<green>Guild description cleared</green>"));
            return true;
        }

        // Join all args from index 1 onwards to allow multi-word descriptions
        StringBuilder description = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) {
                description.append(" ");
            }
            description.append(args[i]);
        }

        guild.setDescription(description.toString());
        guildService.save(guild);
        player.sendMessage(MessageFormatter.deserialize("<green>Guild description set to: <gold>" + description + "</gold></green>"));
        return true;
    }
}
