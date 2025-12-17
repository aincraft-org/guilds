package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.*;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;

/**
 * Command component for accepting guild invites.
 */
public class AcceptComponent implements GuildCommand {

    private final InviteService inviteService;
    private final GuildService guildService;

    @Inject
    public AcceptComponent(InviteService inviteService, GuildService guildService) {
        this.inviteService = Objects.requireNonNull(inviteService, "Invite service cannot be null");
        this.guildService = Objects.requireNonNull(guildService, "Guild service cannot be null");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can accept guild invites"));
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

        // Accept invite
        AcceptInviteResult result = inviteService.acceptInvite(guild.getId(), player.getUniqueId());

        // Handle result
        switch (result.getStatus()) {
            case SUCCESS -> player.sendMessage(MessageFormatter.format(
                "<green>✓ You have joined <gold>" + guild.getName() + "</gold>!</green>"
            ));
            case EXPIRED -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, "The invite to " + guild.getName() + " has expired"
            ));
            case NOT_FOUND -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, "You don't have a pending invite to " + guild.getName()
            ));
            case ALREADY_IN_GUILD -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, "You are already in a guild"
            ));
            case GUILD_FULL -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, guild.getName() + " is now full"
            ));
            case GUILD_NOT_FOUND -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, guild.getName() + " no longer exists"
            ));
            case FAILURE -> player.sendMessage(MessageFormatter.format(
                MessageFormatter.ERROR, "Failed to accept invite: " + result.getReason()
            ));
        }

        return true;
    }

    @Override
    public String getName() {
        return "accept";
    }

    @Override
    public String getPermission() {
        return "guilds.join";
    }

    @Override
    public String getUsage() {
        return "/g accept <guild>";
    }
}
