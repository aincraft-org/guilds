package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command component for setting a guild's description.
 * Usage: /g description <description> or /g description clear
 */
public class DescriptionComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final GuildLifecycleService lifecycleService;
    private final PermissionService permissionService;

    @Inject
    public DescriptionComponent(GuildMemberService memberService, GuildLifecycleService lifecycleService,
                               PermissionService permissionService) {
        this.memberService = memberService;
        this.lifecycleService = lifecycleService;
        this.permissionService = permissionService;
    }

    @Override
    public String getName() {
        return "description";
    }

    @Override
    public String getPermission() {
        return "guilds.description";
    }

    @Override
    public String getUsage() {
        return "/g description <description> or /g description clear";
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

        if (args.length < 2) {
            Messages.send(player, MessageKey.ERROR_USAGE, getUsage());
            return false;
        }

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        // Check if player has EDIT_GUILD_INFO permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.EDIT_GUILD_INFO)) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        String descriptionInput = args[1].toLowerCase();

        // Handle clear command
        if (descriptionInput.equals("clear")) {
            guild.setDescription(null);
            lifecycleService.save(guild);
            Messages.send(player, MessageKey.GUILD_DESCRIPTION_CLEARED);
            return true;
        }

        // Join all args from index 1 onwards to allow multi-word descriptions
        StringBuilder description = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) {
                description.append(" ");
            }
            description.append(args[i]);
        }

        guild.setDescription(description.toString());
        lifecycleService.save(guild);
        Messages.send(player, MessageKey.GUILD_DESCRIPTION_SET, description.toString());
        return true;
    }
}
