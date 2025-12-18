package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to use guild spawn"));
            return true;
        }

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You are not in a guild"));
            return true;
        }

        // Get spawn location
        Location spawnLocation = spawnService.getGuildSpawnLocation(guild.getId());
        if (spawnLocation == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Your guild does not have a spawn point set"));
            return true;
        }

        // Teleport player
        player.teleport(spawnLocation);
        player.sendMessage(MessageFormatter.deserialize(
            "<green>✓ Teleported to guild spawn at <gold>" +
            guild.getName() +
            "</gold>!</green>"
        ));
        return true;
    }
}
