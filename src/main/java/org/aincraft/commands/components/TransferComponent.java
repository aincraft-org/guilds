package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Component for transferring guild ownership to another guild member.
 */
public class TransferComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final GuildLifecycleService lifecycleService;

    @Inject
    public TransferComponent(GuildMemberService memberService, GuildLifecycleService lifecycleService) {
        this.memberService = memberService;
        this.lifecycleService = lifecycleService;
    }

    @Override
    public String getName() {
        return "transfer";
    }

    @Override
    public String getPermission() {
        return "guilds.transfer";
    }

    @Override
    public String getUsage() {
        return "/g transfer <player-name>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to transfer guild ownership"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return false;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You are not in a guild"));
            return true;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Only the guild owner can transfer ownership"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        if (target == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Player not found"));
            return true;
        }

        // Check if trying to transfer to yourself
        if (player.getUniqueId().equals(target.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You cannot transfer ownership to yourself"));
            return true;
        }

        // Check if target is a guild member
        if (!guild.isMember(target.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ The target player is not a guild member"));
            return true;
        }

        // Transfer ownership
        if (guild.transferOwnership(target.getUniqueId())) {
            lifecycleService.save(guild);

            // Notify old owner
            player.sendMessage(MessageFormatter.deserialize("<green>✓ Guild ownership transferred to <gold>" + target.getName() + "</gold></green>"));

            // Notify new owner if online
            Player newOwner = Bukkit.getPlayer(target.getUniqueId());
            if (newOwner != null && newOwner.isOnline()) {
                newOwner.sendMessage(MessageFormatter.deserialize(
                    "<green>You are now the owner of <gold>" + guild.getName() + "</gold></green>"
                ));
            }

            // Broadcast to guild members
            Component broadcastMessage = Component.text()
                .append(Component.text("[Guild] ", NamedTextColor.GRAY))
                .append(Component.text(player.getName() + " transferred ownership to " + target.getName(), NamedTextColor.YELLOW))
                .build();

            for (UUID memberId : guild.getMembers()) {
                Player member = player.getServer().getPlayer(memberId);
                if (member != null && member.isOnline() && !member.equals(player) && !member.getUniqueId().equals(target.getUniqueId())) {
                    member.sendMessage(broadcastMessage);
                }
            }

            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Failed to transfer ownership"));
        return true;
    }
}
