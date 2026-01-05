package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.SpawnService;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command component for teleporting to a guild's spawn location.
 * Usage: /g spawn
 */
public class SpawnComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final SpawnService spawnService;

    @Inject
    public SpawnComponent(GuildMemberService memberService, SpawnService spawnService) {
        this.memberService = memberService;
        this.spawnService = spawnService;
    }

    @Override
    public String getName() {
        return "spawn";
    }

    @Override
    public String getPermission() {
        return "guilds.spawn";
    }

    @Override
    public String getUsage() {
        return "/g spawn";
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

        // Get spawn location
        Location spawnLocation = spawnService.getGuildSpawnLocation(guild.getId());
        if (spawnLocation == null) {
            Messages.send(player, MessageKey.SPAWN_NO_SPAWN);
            return true;
        }

        // Teleport player
        player.teleport(spawnLocation);
        Messages.send(player, MessageKey.SPAWN_TELEPORTED, guild.getName());
        return true;
    }
}
