package org.aincraft.commands.components;

import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Component for viewing guild information.
 */
public class InfoComponent implements GuildCommand {
    private final GuildService guildService;

    public InfoComponent(GuildService guildService) {
        this.guildService = guildService;
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String getPermission() {
        return "guilds.info";
    }

    @Override
    public String getUsage() {
        return "/g info [guild-name]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to view guild info"));
            return true;
        }

        Guild guild = null;

        if (args.length >= 2) {
            // Look up guild by name
            guild = guildService.getGuildByName(args[1]);
        } else {
            // Use player's current guild
            guild = guildService.getPlayerGuild(player.getUniqueId());
        }

        if (guild == null) {
            if (args.length >= 2) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Guild '" + args[1] + "' not found"));
            } else {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You are not in a guild"));
            }
            return true;
        }

        displayGuildInfo(player, guild);
        return true;
    }

    /**
     * Displays formatted guild information.
     *
     * @param player the player to send the info to
     * @param guild the guild to display
     */
    private void displayGuildInfo(Player player, Guild guild) {
        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Guild Information", ""));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Name", guild.getName()));

        if (guild.getDescription() != null && !guild.getDescription().isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Description", guild.getDescription()));
        }

        String ownerName = org.bukkit.Bukkit.getOfflinePlayer(guild.getOwnerId()).getName() != null ?
             org.bukkit.Bukkit.getOfflinePlayer(guild.getOwnerId()).getName() : guild.getOwnerId().toString();
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Owner", ownerName));

        player.sendMessage(MessageFormatter.deserialize("<yellow>Members<reset>: <white>" +
            guild.getMemberCount() + "<gray>/<white>" + guild.getMaxMembers()));

        String dateCreated = new SimpleDateFormat("yyyy-MM-dd").format(new Date(guild.getCreatedAt()));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Created", dateCreated));
    }
}
