package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import dev.mintychochip.mint.Mint;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.TerritoryService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for claiming the chunk the player is standing in.
 */
public class ClaimComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final TerritoryService territoryService;

    @Inject
    public ClaimComponent(GuildMemberService memberService, TerritoryService territoryService) {
        this.memberService = memberService;
        this.territoryService = territoryService;
    }

    @Override
    public String getName() {
        return "claim";
    }

    @Override
    public String getPermission() {
        return "guilds.claim";
    }

    @Override
    public String getUsage() {
        return "/g claim";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>Only players can use this command</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(player, "<error>You don't have <accent>permission</accent> to claim chunks</error>");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());

        Guild existingOwner = territoryService.getChunkOwner(chunk);
        if (existingOwner != null) {
            if (existingOwner.getId().equals(guild.getId())) {
                Mint.sendMessage(player, "<warning>Your <secondary>guild</secondary> already owns this chunk</warning>");
            } else {
                Mint.sendMessage(player, "<error>This chunk is claimed by <secondary>" + existingOwner.getName() + "</secondary></error>");
            }
            return true;
        }

        org.aincraft.ClaimResult result = territoryService.claimChunk(guild.getId(), player.getUniqueId(), chunk);
        if (result.isSuccess()) {
            Mint.sendMessage(player, "<success>Claimed chunk at <primary>" + chunk.x() + "</primary>, <primary>" + chunk.z() + "</primary></success>");
        } else {
            Mint.sendMessage(player, "<error>" + result.getReason() + "</error>");
        }

        return true;
    }
}
