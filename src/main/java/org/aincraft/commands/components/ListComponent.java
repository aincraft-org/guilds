package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import java.util.List;
import org.aincraft.Guild;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.commands.GuildCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for listing all guilds.
 */
public class ListComponent implements GuildCommand {
    private final GuildLifecycleService lifecycleService;
    private static final int GUILDS_PER_PAGE = 5;

    @Inject
    public ListComponent(GuildLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
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
            Mint.sendMessage(sender, "<error>This command is for players only</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have permission to use this command</error>");
            return true;
        }

        List<Guild> allGuilds = lifecycleService.listAllGuilds();

        if (allGuilds.isEmpty()) {
            Mint.sendMessage(player, "<neutral>List is empty</neutral>");
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
                Mint.sendMessage(player, "<error>Invalid page number</error>");
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

        Mint.sendMessage(player, "<primary>=== List (" + page + "/" + totalPages + ") ===</primary>");

        for (int i = startIndex; i < endIndex; i++) {
            Guild guild = guilds.get(i);
            String ownerName = org.bukkit.Bukkit.getOfflinePlayer(guild.getOwnerId()).getName();
            if (ownerName == null) {
                ownerName = guild.getOwnerId().toString();
            }

            Mint.sendMessage(player, "<info>" + (i + 1) + ". <secondary>" + guild.getName() +
                    "</secondary> <neutral>[" + guild.getMemberCount() + "/" + guild.getMaxMembers() + "] " +
                    "<neutral>Owner: <primary>" + ownerName + "</primary></neutral>");
        }

        if (totalPages > 1) {
            Mint.sendMessage(player, "<neutral>Use /g list " + (page + 1) + " for next page</neutral>");
        }
    }
}
