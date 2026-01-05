package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.Objects;
import org.aincraft.Guild;
import org.aincraft.InviteService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildLifecycleService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command component for declining guild invites.
 */
public class DeclineComponent implements GuildCommand {

    private final InviteService inviteService;
    private final GuildLifecycleService lifecycleService;

    @Inject
    public DeclineComponent(InviteService inviteService, GuildLifecycleService lifecycleService) {
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

        // Decline invite
        boolean declined = inviteService.declineInvite(guild.getId(), player.getUniqueId());

        if (declined) {
            Messages.send(player, MessageKey.INVITE_DECLINED, guild.getName());
        } else {
            Messages.send(player, MessageKey.INVITE_NOT_FOUND, guild.getName());
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
