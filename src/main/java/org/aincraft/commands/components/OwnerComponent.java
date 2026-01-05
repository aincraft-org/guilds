package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
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
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        // Get the owner's UUID
        UUID ownerId = guild.getOwnerId();
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);

        // Display owner information
        Messages.send(player, MessageKey.LIST_HEADER, "Guild Owner");
        Messages.send(player, MessageKey.INFO_HEADER, owner.getName());

        return true;
    }
}
