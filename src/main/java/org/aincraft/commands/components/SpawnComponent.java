package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
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
            Mint.sendMessage(sender, "<error>This command is for players only</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>No permission</error>");
            return true;
        }

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        // Get spawn location
        Location spawnLocation = spawnService.getGuildSpawnLocation(guild.getId());
        if (spawnLocation == null) {
            Mint.sendMessage(player, "<error>No guild spawn set</error>");
            return true;
        }

        // Teleport player
        player.teleport(spawnLocation);
        Mint.sendMessage(player, "<success>Teleported to guild spawn</success>");
        return true;
    }
}
