package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to check guild owner"));
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        // Get the owner's UUID
        UUID ownerId = guild.getOwnerId();
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);

        // Display owner information
        player.sendMessage(MessageFormatter.format("<green><bold>Guild Owner</bold></green>"));
        player.sendMessage(MessageFormatter.format("<gray>─────────────────────────</gray>"));
        player.sendMessage(MessageFormatter.format("<gold>" + owner.getName() + "</gold>"));

        return true;
    }
}
