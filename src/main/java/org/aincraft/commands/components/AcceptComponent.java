package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.Objects;
import org.aincraft.AcceptInviteResult;
import org.aincraft.Guild;
import org.aincraft.InviteService;
import org.aincraft.commands.GuildCommand;
import dev.mintychochip.mint.Mint;
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
            Mint.sendMessage(sender, "<error>Only players can use this command</error>");
            return true;
        }

        // Check permission
        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have <accent>permission</accent> to accept invites</error>");
            return true;
        }

        // Check for usage subcommand
        if (args.length >= 2 && args[1].equalsIgnoreCase("usage")) {
            showUsage(player);
            return true;
        }

        // Validate args
        if (args.length < 2) {
            Mint.sendMessage(player, "<error>Usage: <accent>/g accept <guild|usage></accent></error>");
            return true;
        }

        // Get guild by name
        String guildName = args[1];
        Guild guild = lifecycleService.getGuildByName(guildName);
        if (guild == null) {
            Mint.sendMessage(player, "<error>Guild not found: <secondary>" + guildName + "</secondary></error>");
            return true;
        }

        // Accept invite
        AcceptInviteResult result = inviteService.acceptInvite(guild.getId(), player.getUniqueId());

        // Handle result
        switch (result.getStatus()) {
            case SUCCESS -> Mint.sendMessage(player, "<success>You joined <secondary>" + guild.getName() + "</secondary>!</success>");
            case EXPIRED -> Mint.sendMessage(player, "<warning>That invite has expired</warning>");
            case NOT_FOUND -> Mint.sendMessage(player, "<error>No invite found from <secondary>" + guildName + "</secondary></error>");
            case ALREADY_IN_GUILD -> Mint.sendMessage(player, "<error>You are already in a <secondary>guild</secondary></error>");
            case GUILD_FULL -> Mint.sendMessage(player, "<error>Guild is full (reached <accent>max members</accent>)</error>");
            case GUILD_NOT_FOUND -> Mint.sendMessage(player, "<error>Guild not found: <secondary>" + guildName + "</secondary></error>");
            case FAILURE -> Mint.sendMessage(player, "<error>" + result.getReason() + "</error>");
        }

        return true;
    }

    private void showUsage(Player player) {
        Mint.sendMessage(player, "<info>Accept Invite Commands</info>");
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
