package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.commands.GuildCommand;
import dev.mintychochip.mint.Mint;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.service.TerritoryService;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for unclaiming the chunk the player is standing in.
 * Blocks unclaim if subregions exist in the chunk.
 */
public class UnclaimComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final TerritoryService territoryService;
    private final SubregionService subregionService;
    private final PermissionService permissionService;

    @Inject
    public UnclaimComponent(GuildMemberService memberService, TerritoryService territoryService, SubregionService subregionService, PermissionService permissionService) {
        this.memberService = memberService;
        this.territoryService = territoryService;
        this.subregionService = subregionService;
        this.permissionService = permissionService;
    }

    @Override
    public String getName() {
        return "unclaim";
    }

    @Override
    public String getPermission() {
        return "guilds.unclaim";
    }

    @Override
    public String getUsage() {
        return "/g unclaim [all]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>Only players can use this command</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have permission to unclaim chunks</error>");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        // Check if "all" argument is provided
        if (args.length > 1 && "all".equalsIgnoreCase(args[1])) {
            // Check UNCLAIM_ALL permission BEFORE subregion check
            if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.UNCLAIM_ALL)) {
                Mint.sendMessage(player, "<error>You don't have permission to unclaim all chunks</error>");
                return true;
            }

            // Check for any subregions before unclaiming all
            List<Subregion> allSubregions = subregionService.getGuildSubregions(guild.getId());
            if (!allSubregions.isEmpty()) {
                Mint.sendMessage(player, "<error>Cannot unclaim chunks - guild contains <primary>" + allSubregions.size() + "</primary> subregion(s)</error>");
                for (Subregion region : allSubregions) {
                    Mint.sendMessage(player, "<error>- <secondary>" + region.getName() + "</secondary></error>");
                }
                Mint.sendMessage(player, "<error>Use /g region delete <name> to remove subregions first</error>");
                return true;
            }

            if (!territoryService.unclaimAll(guild.getId(), player.getUniqueId())) {
                Mint.sendMessage(player, "<error>You don't have permission to unclaim all chunks</error>");
                return true;
            }
            Mint.sendMessage(player, "<success>Unclaimed all <primary>" + guild.getName() + "</primary> chunks</success>");
        } else {
            // Unclaim single chunk at player's location
            ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());

            Guild chunkOwner = territoryService.getChunkOwner(chunk);
            if (chunkOwner == null) {
                Mint.sendMessage(player, "<error>This chunk is not owned by your guild</error>");
                return true;
            }

            if (!chunkOwner.getId().equals(guild.getId())) {
                Mint.sendMessage(player, "<error>This chunk is not owned by your guild</error>");
                return true;
            }

            // Check for subregions in this chunk
            List<Subregion> subregions = subregionService.getSubregionsInChunk(chunk);
            if (!subregions.isEmpty()) {
                Mint.sendMessage(player, "<error>Cannot unclaim this chunk - it contains <primary>" + subregions.size() + "</primary> subregion(s)</error>");
                for (Subregion region : subregions) {
                    Mint.sendMessage(player, "<error>- <secondary>" + region.getName() + "</secondary></error>");
                }
                Mint.sendMessage(player, "<error>Use /g region delete <name> to remove subregions first</error>");
                return true;
            }

            if (territoryService.unclaimChunk(guild.getId(), player.getUniqueId(), chunk)) {
                Mint.sendMessage(player, "<success>Unclaimed chunk at <primary>" + chunk.x() + "</primary>, <primary>" + chunk.z() + "</primary></success>");
            } else {
                Mint.sendMessage(player, "<error>You don't have permission to unclaim this chunk</error>");
            }
        }

        return true;
    }
}
