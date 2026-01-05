package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command component for toggling guild settings.
 * Usage: /g toggle <explosions|fire>
 */
public class ToggleComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final GuildLifecycleService lifecycleService;

    @Inject
    public ToggleComponent(GuildMemberService memberService, GuildLifecycleService lifecycleService) {
        this.memberService = memberService;
        this.lifecycleService = lifecycleService;
    }

    @Override
    public String getName() {
        return "toggle";
    }

    @Override
    public String getPermission() {
        return "guilds.toggle";
    }

    @Override
    public String getUsage() {
        return "/g toggle <explosions|fire|public>";
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

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            Messages.send(player, MessageKey.ERROR_NOT_GUILD_OWNER);
            return true;
        }

        String setting = args[1].toLowerCase();
        return switch (setting) {
            case "explosions", "explosion" -> toggleExplosions(player, guild);
            case "fire" -> toggleFire(player, guild);
            case "public" -> togglePublic(player, guild);
            default -> {
                Messages.send(player, MessageKey.ERROR_USAGE, getUsage());
                yield true;
            }
        };
    }

    private boolean toggleExplosions(Player player, Guild guild) {
        boolean newValue = !guild.isExplosionsAllowed();
        guild.setExplosionsAllowed(newValue);
        lifecycleService.save(guild);

        player.sendMessage(Messages.get(MessageKey.GUILD_COLOR_SET, "Explosions " + (newValue ? "enabled" : "disabled")));
        return true;
    }

    private boolean toggleFire(Player player, Guild guild) {
        boolean newValue = !guild.isFireAllowed();
        guild.setFireAllowed(newValue);
        lifecycleService.save(guild);

        player.sendMessage(Messages.get(MessageKey.GUILD_COLOR_SET, "Fire spread " + (newValue ? "enabled" : "disabled")));
        return true;
    }

    private boolean togglePublic(Player player, Guild guild) {
        boolean newValue = !guild.isPublic();
        guild.setPublic(newValue);
        lifecycleService.save(guild);

        player.sendMessage(Messages.get(MessageKey.GUILD_COLOR_SET, "Guild is now " + (newValue ? "public" : "private")));
        return true;
    }
}
