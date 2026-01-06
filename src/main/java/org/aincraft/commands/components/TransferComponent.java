package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import dev.mintychochip.mint.Mint;
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
            Mint.sendMessage(sender, "<error>Only players can use this command</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have permission to transfer ownership</error>");
            return true;
        }

        if (args.length < 2) {
            Mint.sendMessage(player, "<error>Usage: /g transfer <player-name></error>");
            return false;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());

        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            Mint.sendMessage(player, "<error>Only guild owner can do this</error>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        if (target == null) {
            Mint.sendMessage(player, "<error>Player not found</error>");
            return true;
        }

        // Check if trying to transfer to yourself
        if (player.getUniqueId().equals(target.getUniqueId())) {
            Mint.sendMessage(player, "<error>You cannot transfer ownership to yourself</error>");
            return true;
        }

        // Check if target is a guild member
        if (!guild.isMember(target.getUniqueId())) {
            Mint.sendMessage(player, "<error>Target player is not a guild member</error>");
            return true;
        }

        // Transfer ownership
        if (guild.transferOwnership(target.getUniqueId())) {
            lifecycleService.save(guild);

            // Notify old owner
            Mint.sendMessage(player, "<success>Guild ownership transferred to <secondary>" + target.getName() + "</secondary></success>");

            // Notify new owner if online
            Player newOwner = Bukkit.getPlayer(target.getUniqueId());
            if (newOwner != null && newOwner.isOnline()) {
                Mint.sendMessage(newOwner, "<success>You are now the owner of <secondary>" + guild.getName() + "</secondary></success>");
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

        Mint.sendMessage(player, "<error>Usage: /g transfer <player-name></error>");
        return true;
    }
}
