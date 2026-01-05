package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.service.SpawnService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command component for setting a guild's spawn location.
 * Usage: /g setspawn
 */
public class SetspawnComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final SpawnService spawnService;
    private final PermissionService permissionService;

    @Inject
    public SetspawnComponent(GuildMemberService memberService, SpawnService spawnService, PermissionService permissionService) {
        this.memberService = memberService;
        this.spawnService = spawnService;
        this.permissionService = permissionService;
    }

    @Override
    public String getName() {
        return "setspawn";
    }

    @Override
    public String getPermission() {
        return "guilds.setspawn";
    }

    @Override
    public String getUsage() {
        return "/g setspawn";
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

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        // Check if player has MANAGE_SPAWN permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_SPAWN)) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        // Check if guild has homeblock before attempting to set spawn
        if (!guild.hasHomeblock()) {
            Messages.send(player, MessageKey.SPAWN_NO_HOMEBLOCK);
            return true;
        }

        // Attempt to set spawn
        org.bukkit.Location originalLoc = player.getLocation();
        if (spawnService.setGuildSpawn(guild.getId(), player.getUniqueId(), originalLoc)) {
            // Check if location was adjusted
            org.bukkit.Location actual = spawnService.getGuildSpawnLocation(guild.getId());
            if (actual != null && !isSameLocation(originalLoc, actual)) {
                Messages.send(player, MessageKey.SPAWN_SET);
            } else {
                Messages.send(player, MessageKey.SPAWN_SET);
            }
            return true;
        }

        Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
        return true;
    }

    private boolean isSameLocation(org.bukkit.Location loc1, org.bukkit.Location loc2) {
        return Math.abs(loc1.getX() - loc2.getX()) < 0.1 &&
               Math.abs(loc1.getY() - loc2.getY()) < 0.1 &&
               Math.abs(loc1.getZ() - loc2.getZ()) < 0.1;
    }
}
