package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to toggle guild settings"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return false;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only the guild owner can toggle guild settings"));
            return true;
        }

        String setting = args[1].toLowerCase();
        return switch (setting) {
            case "explosions", "explosion" -> toggleExplosions(player, guild);
            case "fire" -> toggleFire(player, guild);
            case "public" -> togglePublic(player, guild);
            default -> {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Unknown setting: " + setting));
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Available settings: explosions, fire, public"));
                yield true;
            }
        };
    }

    private boolean toggleExplosions(Player player, Guild guild) {
        boolean newValue = !guild.isExplosionsAllowed();
        guild.setExplosionsAllowed(newValue);
        lifecycleService.save(guild);

        String status = newValue ? "<green>enabled</green>" : "<red>disabled</red>";
        player.sendMessage(MessageFormatter.deserialize(
                "<green>Explosions " + status + " in guild territory</green>"));
        return true;
    }

    private boolean toggleFire(Player player, Guild guild) {
        boolean newValue = !guild.isFireAllowed();
        guild.setFireAllowed(newValue);
        lifecycleService.save(guild);

        String status = newValue ? "<green>enabled</green>" : "<red>disabled</red>";
        player.sendMessage(MessageFormatter.deserialize(
                "<green>Fire spread " + status + " in guild territory</green>"));
        return true;
    }

    private boolean togglePublic(Player player, Guild guild) {
        boolean newValue = !guild.isPublic();
        guild.setPublic(newValue);
        lifecycleService.save(guild);

        String status = newValue ? "<green>public</green>" : "<gold>private</gold>";
        player.sendMessage(MessageFormatter.deserialize(
                "<green>Guild is now " + status + "</green>"));
        return true;
    }
}
