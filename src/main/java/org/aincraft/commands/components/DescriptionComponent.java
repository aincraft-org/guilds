package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to change guild description"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return false;
        }

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        // Check if player has EDIT_GUILD_INFO permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.EDIT_GUILD_INFO)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to change guild description"));
            return true;
        }

        String descriptionInput = args[1].toLowerCase();

        // Handle clear command
        if (descriptionInput.equals("clear")) {
            guild.setDescription(null);
            lifecycleService.save(guild);
            player.sendMessage(MessageFormatter.deserialize("<green>Guild description cleared</green>"));
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
        player.sendMessage(MessageFormatter.deserialize("<green>Guild description set to: <gold>" + description + "</gold></green>"));
        return true;
    }
}
