package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.*;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Command component for viewing guild invites (received and sent).
 */
public class InvitesComponent implements GuildCommand {

    private final InviteService inviteService;
    private final GuildService guildService;

    @Inject
    public InvitesComponent(InviteService inviteService, GuildService guildService) {
        this.inviteService = Objects.requireNonNull(inviteService, "Invite service cannot be null");
        this.guildService = Objects.requireNonNull(guildService, "Guild service cannot be null");
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can view invites"));
            return true;
        }

        // Check permission
        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to use this command"));
            return true;
        }

        // Show received invites
        List<GuildInvite> receivedInvites = inviteService.getReceivedInvites(player.getUniqueId());

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Guild Invites", ""));

        if (receivedInvites.isEmpty()) {
            player.sendMessage(MessageFormatter.format("<gray>No pending invites</gray>"));
        } else {
            player.sendMessage(MessageFormatter.format("<gold>Received Invites:</gold>"));
            for (GuildInvite invite : receivedInvites) {
                Guild guild = guildService.getGuildById(invite.guildId());
                if (guild != null) {
                    OfflinePlayer inviter = Bukkit.getOfflinePlayer(invite.inviterId());
                    String timeLeft = formatTimeLeft(invite.remainingMillis());

                    player.sendMessage(MessageFormatter.format(
                        "  <gray>•</gray> <gold>" + guild.getName() + "</gold> <gray>(from <white>" +
                        inviter.getName() + "</white>) - expires in <yellow>" + timeLeft + "</yellow></gray>"
                    ));
                }
            }
        }

        // Show sent invites if in guild with INVITE permission
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild != null && guildService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.INVITE)) {
            List<GuildInvite> sentInvites = inviteService.getSentInvites(guild.getId());

            if (!sentInvites.isEmpty()) {
                player.sendMessage("");
                player.sendMessage(MessageFormatter.format("<gold>Sent Invites (" + guild.getName() + "):</gold>"));
                for (GuildInvite invite : sentInvites) {
                    OfflinePlayer invitee = Bukkit.getOfflinePlayer(invite.inviteeId());
                    String timeLeft = formatTimeLeft(invite.remainingMillis());

                    player.sendMessage(MessageFormatter.format(
                        "  <gray>•</gray> <white>" + invitee.getName() + "</white> <gray>- expires in <yellow>" +
                        timeLeft + "</yellow></gray>"
                    ));
                }
            }
        }

        return true;
    }

    /**
     * Formats remaining time in a human-readable format.
     */
    private String formatTimeLeft(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }

    @Override
    public String getName() {
        return "invites";
    }

    @Override
    public String getPermission() {
        return "guilds.use";
    }

    @Override
    public String getUsage() {
        return "/g invites";
    }
}
