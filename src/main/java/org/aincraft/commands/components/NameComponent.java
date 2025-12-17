package org.aincraft.commands.components;

import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command component for setting a guild's name.
 * Usage: /g name <name>
 */
public class NameComponent implements GuildCommand {
    private final GuildService guildService;

    public NameComponent(GuildService guildService) {
        this.guildService = guildService;
    }

    @Override
    public String getName() {
        return "name";
    }

    @Override
    public String getPermission() {
        return "guilds.name";
    }

    @Override
    public String getUsage() {
        return "/g name <name>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to change guild name"));
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
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only the guild owner can change guild name"));
            return true;
        }

        // Join all args from index 1 onwards to allow multi-word names
        StringBuilder name = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) {
                name.append(" ");
            }
            name.append(args[i]);
        }

        String newName = name.toString().trim();

        // Validate name
        if (newName.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild name cannot be empty"));
            return true;
        }

        if (newName.length() > 32) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild name cannot be longer than 32 characters"));
            return true;
        }

        // Check if name already exists
        if (guildService.getGuildByName(newName) != null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "A guild with that name already exists"));
            return true;
        }

        guild.setName(newName);
        guildService.save(guild);
        player.sendMessage(MessageFormatter.deserialize("<green>Guild name changed to: <gold>" + newName + "</gold></green>"));
        return true;
    }
}
