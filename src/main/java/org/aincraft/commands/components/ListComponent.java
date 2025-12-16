package org.aincraft.commands.components;

import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Component for listing all guilds.
 */
public class ListComponent implements GuildCommand {
    private final GuildService guildService;
    private static final int GUILDS_PER_PAGE = 5;

    public ListComponent(GuildService guildService) {
        this.guildService = guildService;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getPermission() {
        return "guilds.list";
    }

    @Override
    public String getUsage() {
        return "/g list [page]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to list guilds"));
            return true;
        }

        List<Guild> allGuilds = guildService.listAllGuilds();

        if (allGuilds.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "No guilds exist yet"));
            return true;
        }

        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) {
                    page = 1;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid page number"));
                return true;
            }
        }

        displayGuildList(player, allGuilds, page);
        return true;
    }

    /**
     * Displays a paginated list of guilds.
     *
     * @param player the player to send the list to
     * @param guilds the list of all guilds
     * @param page the page number to display
     */
    private void displayGuildList(Player player, List<Guild> guilds, int page) {
        int totalPages = (guilds.size() + GUILDS_PER_PAGE - 1) / GUILDS_PER_PAGE;

        if (page > totalPages) {
            page = totalPages;
        }

        int startIndex = (page - 1) * GUILDS_PER_PAGE;
        int endIndex = Math.min(startIndex + GUILDS_PER_PAGE, guilds.size());

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Guilds (Page " + page + "/" + totalPages + ")", ""));

        for (int i = startIndex; i < endIndex; i++) {
            Guild guild = guilds.get(i);
            String ownerName = org.bukkit.Bukkit.getOfflinePlayer(guild.getOwnerId()).getName();
            if (ownerName == null) {
                ownerName = guild.getOwnerId().toString();
            }

            player.sendMessage(MessageFormatter.deserialize("<yellow>" + (i + 1) + ". <gold>" + guild.getName() +
                "<gray> [" + guild.getMemberCount() + "/" + guild.getMaxMembers() + "] " +
                "<white>Owner: " + ownerName));
        }

        if (totalPages > 1) {
            player.sendMessage(MessageFormatter.deserialize("<gray>Use /g list " + (page + 1) + " for next page"));
        }
    }
}
