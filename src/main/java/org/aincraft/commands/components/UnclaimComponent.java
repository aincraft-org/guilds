package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.service.GuildMemberService;
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

    @Inject
    public UnclaimComponent(GuildMemberService memberService, TerritoryService territoryService, SubregionService subregionService) {
        this.memberService = memberService;
        this.territoryService = territoryService;
        this.subregionService = subregionService;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to unclaim chunks"));
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        // Check if "all" argument is provided
        if (args.length > 1 && "all".equalsIgnoreCase(args[1])) {
            territoryService.unclaimAll(guild.getId());
            player.sendMessage(MessageFormatter.deserialize("<green>Unclaimed all chunks for <gold>" + guild.getName() + "</gold></green>"));
        } else {
            // Unclaim single chunk at player's location
            ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());

            Guild chunkOwner = territoryService.getChunkOwner(chunk);
            if (chunkOwner == null) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "This chunk is not claimed"));
                return true;
            }

            if (!chunkOwner.getId().equals(guild.getId())) {
                player.sendMessage(MessageFormatter.deserialize("<red>This chunk is claimed by <gold>" + chunkOwner.getName() +
                        "</gold>, not your guild</red>"));
                return true;
            }

            // Check for subregions in this chunk
            List<Subregion> subregions = subregionService.getSubregionsInChunk(chunk);
            if (!subregions.isEmpty()) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                        "Cannot unclaim chunk - it contains " + subregions.size() + " subregion(s):"));
                for (Subregion region : subregions) {
                    player.sendMessage(MessageFormatter.deserialize(
                            "<gray>  • <gold>" + region.getName() + "</gold></gray>"));
                }
                player.sendMessage(MessageFormatter.deserialize(
                        "<gray>Delete subregions first with <yellow>/g region delete <name></yellow></gray>"));
                return true;
            }

            if (territoryService.unclaimChunk(guild.getId(), player.getUniqueId(), chunk)) {
                player.sendMessage(MessageFormatter.deserialize("<green>Unclaimed chunk at <gold>" + chunk.x() + ", " + chunk.z() + "</gold></green>"));
            } else {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Failed to unclaim chunk. You may lack UNCLAIM permission."));
            }
        }

        return true;
    }
}
