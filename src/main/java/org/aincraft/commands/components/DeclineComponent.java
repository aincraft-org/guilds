package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.GuildService;
import org.aincraft.InviteService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;

/**
 * Command component for declining guild invites.
 */
public class DeclineComponent implements GuildCommand {

    private final InviteService inviteService;
    private final GuildService guildService;

    @Inject
    public DeclineComponent(InviteService inviteService, GuildService guildService) {
        this.inviteService = Objects.requireNonNull(inviteService, "Invite service cannot be null");
        this.guildService = Objects.requireNonNull(guildService, "Guild service cannot be null");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can decline guild invites"));
            return true;
        }

        // Check permission
        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to use this command"));
            return true;
        }

        // Validate args
        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return true;
        }

        // Get guild by name
        String guildName = args[1];
        Guild guild = guildService.getGuildByName(guildName);
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild not found: " + guildName));
            return true;
        }

        // Decline invite
        boolean declined = inviteService.declineInvite(guild.getId(), player.getUniqueId());

        if (declined) {
            player.sendMessage(MessageFormatter.format(
                "<gray>Declined invite to <gold>" + guild.getName() + "</gold></gray>"
            ));
        } else {
            player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, "You don't have a pending invite to " + guild.getName()
            ));
        }

        return true;
    }

    @Override
    public String getName() {
        return "decline";
    }

    @Override
    public String getPermission() {
        return "guilds.join";
    }

    @Override
    public String getUsage() {
        return "/g decline <guild>";
    }
}
