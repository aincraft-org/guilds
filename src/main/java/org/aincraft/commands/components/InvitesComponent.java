package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import org.aincraft.Guild;
import org.aincraft.GuildInvite;
import org.aincraft.GuildPermission;
import org.aincraft.InviteService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command component for viewing guild invites (received and sent).
 */
public class InvitesComponent implements GuildCommand {

    private final InviteService inviteService;
    private final GuildMemberService memberService;
    private final GuildLifecycleService lifecycleService;
    private final PermissionService permissionService;

    @Inject
    public InvitesComponent(InviteService inviteService, GuildMemberService memberService,
                           GuildLifecycleService lifecycleService, PermissionService permissionService) {
        this.inviteService = Objects.requireNonNull(inviteService, "Invite service cannot be null");
        this.memberService = Objects.requireNonNull(memberService, "Member service cannot be null");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "Lifecycle service cannot be null");
        this.permissionService = Objects.requireNonNull(permissionService, "Permission service cannot be null");
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
                Guild guild = lifecycleService.getGuildById(invite.guildId());
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
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild != null && permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.INVITE)) {
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
