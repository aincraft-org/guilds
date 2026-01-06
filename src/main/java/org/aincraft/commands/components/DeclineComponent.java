package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.Objects;
import org.aincraft.Guild;
import org.aincraft.InviteService;
import org.aincraft.commands.GuildCommand;
import dev.mintychochip.mint.Mint;
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
            Mint.sendMessage(sender, "<error>Only players can use this command</error>");
            return true;
        }

        // Check permission
        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have <accent>permission</accent> to decline invites</error>");
            return true;
        }

        // Validate args
        if (args.length < 2) {
            Mint.sendMessage(player, "<error>Usage: <accent>/g decline <guild></accent></error>");
            return true;
        }

        // Get guild by name
        String guildName = args[1];
        Guild guild = lifecycleService.getGuildByName(guildName);
        if (guild == null) {
            Mint.sendMessage(player, "<error>Guild not found: <secondary>" + guildName + "</secondary></error>");
            return true;
        }

        // Decline invite
        boolean declined = inviteService.declineInvite(guild.getId(), player.getUniqueId());

        if (declined) {
            Mint.sendMessage(player, "<warning>You declined the invite from <secondary>" + guild.getName() + "</secondary></warning>");
        } else {
            Mint.sendMessage(player, "<error>No invite found from <secondary>" + guild.getName() + "</secondary></error>");
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
