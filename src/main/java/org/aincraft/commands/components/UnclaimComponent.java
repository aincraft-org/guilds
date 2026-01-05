package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
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
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.CLAIM_NO_PERMISSION);
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        // Check if "all" argument is provided
        if (args.length > 1 && "all".equalsIgnoreCase(args[1])) {
            // Check UNCLAIM_ALL permission BEFORE subregion check
            if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.UNCLAIM_ALL)) {
                Messages.send(player, MessageKey.CLAIM_NO_PERMISSION);
                return true;
            }

            // Check for any subregions before unclaiming all
            List<Subregion> allSubregions = subregionService.getGuildSubregions(guild.getId());
            if (!allSubregions.isEmpty()) {
                player.sendMessage(Messages.get(MessageKey.CLAIM_CANNOT_UNCLAIM_HOMEBLOCK, allSubregions.size()));
                for (Subregion region : allSubregions) {
                    player.sendMessage(Messages.get(MessageKey.ERROR_NO_PERMISSION, region.getName()));
                }
                player.sendMessage(Messages.get(MessageKey.ERROR_NO_PERMISSION, "/g region delete <name>"));
                return true;
            }

            if (!territoryService.unclaimAll(guild.getId(), player.getUniqueId())) {
                Messages.send(player, MessageKey.CLAIM_NO_PERMISSION);
                return true;
            }
            Messages.send(player, MessageKey.CLAIM_UNCLAIM_ALL, guild.getName());
        } else {
            // Unclaim single chunk at player's location
            ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());

            Guild chunkOwner = territoryService.getChunkOwner(chunk);
            if (chunkOwner == null) {
                Messages.send(player, MessageKey.CLAIM_NOT_OWNED);
                return true;
            }

            if (!chunkOwner.getId().equals(guild.getId())) {
                Messages.send(player, MessageKey.CLAIM_NOT_OWNED);
                return true;
            }

            // Check for subregions in this chunk
            List<Subregion> subregions = subregionService.getSubregionsInChunk(chunk);
            if (!subregions.isEmpty()) {
                player.sendMessage(Messages.get(MessageKey.CLAIM_CANNOT_UNCLAIM_HOMEBLOCK, subregions.size()));
                for (Subregion region : subregions) {
                    player.sendMessage(Messages.get(MessageKey.ERROR_NO_PERMISSION, region.getName()));
                }
                player.sendMessage(Messages.get(MessageKey.ERROR_NO_PERMISSION, "/g region delete <name>"));
                return true;
            }

            if (territoryService.unclaimChunk(guild.getId(), player.getUniqueId(), chunk)) {
                Messages.send(player, MessageKey.CLAIM_UNCLAIMED, chunk.x(), chunk.z());
            } else {
                Messages.send(player, MessageKey.CLAIM_NO_PERMISSION);
            }
        }

        return true;
    }
}
