package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import org.aincraft.Guild;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
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
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        List<Guild> allGuilds = lifecycleService.listAllGuilds();

        if (allGuilds.isEmpty()) {
            Messages.send(player, MessageKey.LIST_EMPTY);
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
                Messages.send(player, MessageKey.LIST_INVALID_PAGE);
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

        Messages.send(player, MessageKey.LIST_HEADER, page, totalPages);

        for (int i = startIndex; i < endIndex; i++) {
            Guild guild = guilds.get(i);
            String ownerName = org.bukkit.Bukkit.getOfflinePlayer(guild.getOwnerId()).getName();
            if (ownerName == null) {
                ownerName = guild.getOwnerId().toString();
            }

            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<yellow>" + (i + 1) + ". <gold>" + guild.getName() +
                    "<gray> [" + guild.getMemberCount() + "/" + guild.getMaxMembers() + "] " +
                    "<white>Owner: " + ownerName));
        }

        if (totalPages > 1) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<gray>Use /g list " + (page + 1) + " for next page"));
        }
    }
}
