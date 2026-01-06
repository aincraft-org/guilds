package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.service.GuildMemberService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for displaying the owner of the player's guild.
 */
public class OwnerComponent implements GuildCommand {
    private final GuildMemberService memberService;

    @Inject
    public OwnerComponent(GuildMemberService memberService) {
        this.memberService = memberService;
    }

    @Override
    public String getName() {
        return "owner";
    }

    @Override
    public String getPermission() {
        return "guilds.owner";
    }

    @Override
    public String getUsage() {
        return "/g owner";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have permission to use this command</error>");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        UUID ownerId = guild.getOwnerId();
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);

        Mint.sendMessage(player, "<primary>=== Guild Owner ===</primary>");
        Mint.sendMessage(player, "<info>=== " + owner.getName() + " ===</info>");

        return true;
    }
}
