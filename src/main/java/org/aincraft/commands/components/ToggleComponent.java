package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import org.aincraft.Guild;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.commands.GuildCommand;
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
            Mint.sendMessage(sender, "<error>This command is for players only</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>No permission</error>");
            return true;
        }

        if (args.length < 2) {
            Mint.sendMessage(player, "<error>Usage: " + getUsage() + "</error>");
            return false;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            Mint.sendMessage(player, "<error>You are not the guild owner</error>");
            return true;
        }

        String setting = args[1].toLowerCase();
        return switch (setting) {
            case "explosions", "explosion" -> toggleExplosions(player, guild);
            case "fire" -> toggleFire(player, guild);
            case "public" -> togglePublic(player, guild);
            default -> {
                Mint.sendMessage(player, "<error>Usage: " + getUsage() + "</error>");
                yield true;
            }
        };
    }

    private boolean toggleExplosions(Player player, Guild guild) {
        boolean newValue = !guild.isExplosionsAllowed();
        guild.setExplosionsAllowed(newValue);
        lifecycleService.save(guild);

        Mint.sendMessage(player, "<info>Explosions " + (newValue ? "enabled" : "disabled") + "</info>");
        return true;
    }

    private boolean toggleFire(Player player, Guild guild) {
        boolean newValue = !guild.isFireAllowed();
        guild.setFireAllowed(newValue);
        lifecycleService.save(guild);

        Mint.sendMessage(player, "<info>Fire spread " + (newValue ? "enabled" : "disabled") + "</info>");
        return true;
    }

    private boolean togglePublic(Player player, Guild guild) {
        boolean newValue = !guild.isPublic();
        guild.setPublic(newValue);
        lifecycleService.save(guild);

        Mint.sendMessage(player, "<info>Guild is now " + (newValue ? "public" : "private") + "</info>");
        return true;
    }
}
