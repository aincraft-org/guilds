package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.Objects;
import org.aincraft.AcceptInviteResult;
import org.aincraft.Guild;
import org.aincraft.InviteService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildLifecycleService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command component for accepting guild invites.
 */
public class AcceptComponent implements GuildCommand {

    private final InviteService inviteService;
    private final GuildLifecycleService lifecycleService;

    @Inject
    public AcceptComponent(InviteService inviteService, GuildLifecycleService lifecycleService) {
        this.inviteService = Objects.requireNonNull(inviteService, "Invite service cannot be null");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "Lifecycle service cannot be null");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        // Check permission
        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        // Check for usage subcommand
        if (args.length >= 2 && args[1].equalsIgnoreCase("usage")) {
            showUsage(player);
            return true;
        }

        // Validate args
        if (args.length < 2) {
            Messages.send(player, MessageKey.ERROR_USAGE, getUsage());
            return true;
        }

        // Get guild by name
        String guildName = args[1];
        Guild guild = lifecycleService.getGuildByName(guildName);
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_GUILD_NOT_FOUND, guildName);
            return true;
        }

        // Accept invite
        AcceptInviteResult result = inviteService.acceptInvite(guild.getId(), player.getUniqueId());

        // Handle result
        switch (result.getStatus()) {
            case SUCCESS -> Messages.send(player, MessageKey.INVITE_ACCEPTED, guild.getName());
            case EXPIRED -> Messages.send(player, MessageKey.INVITE_EXPIRED, guild.getName());
            case NOT_FOUND -> Messages.send(player, MessageKey.INVITE_NOT_FOUND, guild.getName());
            case ALREADY_IN_GUILD -> Messages.send(player, MessageKey.ERROR_ALREADY_IN_GUILD);
            case GUILD_FULL -> Messages.send(player, MessageKey.ERROR_GUILD_FULL);
            case GUILD_NOT_FOUND -> Messages.send(player, MessageKey.ERROR_GUILD_NOT_FOUND, guild.getName());
            case FAILURE -> Messages.send(player, MessageKey.ERROR_NO_PERMISSION, result.getReason());
        }

        return true;
    }

    private void showUsage(Player player) {
        player.sendMessage(Messages.get(MessageKey.INFO_HEADER));
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
        return "/g accept <guild|usage>";
    }
}
