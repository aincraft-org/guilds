package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.Objects;
import org.aincraft.AcceptInviteResult;
import org.aincraft.Guild;
import org.aincraft.InviteService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can accept guild invites"));
            return true;
        }

        // Check permission
        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to use this command"));
            return true;
        }

        // Check for usage subcommand
        if (args.length >= 2 && args[1].equalsIgnoreCase("usage")) {
            showUsage(player);
            return true;
        }

        // Validate args
        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            player.sendMessage(MessageFormatter.format("<gray>Use <gold>/g accept usage</gold> for detailed help</gray>"));
            return true;
        }

        // Get guild by name
        String guildName = args[1];
        Guild guild = lifecycleService.getGuildByName(guildName);
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

    private void showUsage(Player player) {
        player.sendMessage(MessageFormatter.format("<green><bold>Accept Command Usage</bold></green>"));
        player.sendMessage(MessageFormatter.format("<gray>─────────────────────────</gray>"));
        player.sendMessage(MessageFormatter.format("<gold>/g accept <guild></gold> <gray>- Accept an invite to join a guild</gray>"));
        player.sendMessage(MessageFormatter.format("<gold>/g accept usage</gold> <gray>- Show this help message</gray>"));
        player.sendMessage(MessageFormatter.format(""));
        player.sendMessage(MessageFormatter.format("<green><bold>Examples:</bold></green>"));
        player.sendMessage(MessageFormatter.format("<gray>• /g accept Warriors</gray>"));
        player.sendMessage(MessageFormatter.format("<gray>• /g accept \"Guild Name\"</gray>"));
        player.sendMessage(MessageFormatter.format(""));
        player.sendMessage(MessageFormatter.format("<green><bold>Requirements:</bold></green>"));
        player.sendMessage(MessageFormatter.format("<gray>• You must have a pending invite to the guild</gray>"));
        player.sendMessage(MessageFormatter.format("<gray>• You cannot already be in a guild</gray>"));
        player.sendMessage(MessageFormatter.format("<gray>• The guild must not be full</gray>"));
        player.sendMessage(MessageFormatter.format("<gray>• The invite must not have expired</gray>"));
        player.sendMessage(MessageFormatter.format(""));
        player.sendMessage(MessageFormatter.format("<green><bold>Related Commands:</bold></green>"));
        player.sendMessage(MessageFormatter.format("<gray>• /g invites - View your pending invites</gray>"));
        player.sendMessage(MessageFormatter.format("<gray>• /g decline <guild> - Decline a guild invite</gray>"));
        player.sendMessage(MessageFormatter.format("<gray>• /g leave - Leave your current guild</gray>"));
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
