package org.aincraft.commands.components.region;

import com.google.inject.Inject;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.service.GuildMemberService;
import org.aincraft.subregion.RegionPermissionService;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionType;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Utility class for common region command validation and operations.
 * Centralizes repeated validation logic across region subcommands.
 */
public class RegionCommandHelper {
    private final GuildMemberService memberService;
    private final SubregionService subregionService;
    private final RegionPermissionService permissionService;
    private final SubregionTypeRegistry typeRegistry;

    @Inject
    public RegionCommandHelper(GuildMemberService memberService, SubregionService subregionService,
                              RegionPermissionService permissionService, SubregionTypeRegistry typeRegistry) {
        this.memberService = memberService;
        this.subregionService = subregionService;
        this.permissionService = permissionService;
        this.typeRegistry = typeRegistry;
    }

    /**
     * Gets player's guild or sends error message.
     *
     * @param player the player
     * @return Guild or null if player not in guild
     */
    public Guild requireGuild(Player player) {
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
        }
        return guild;
    }

    /**
     * Gets region by name or sends error message.
     *
     * @param guild the guild
     * @param regionName the region name
     * @param player player to send error to
     * @return Subregion or null if not found
     */
    public Subregion requireRegion(Guild guild, String regionName, Player player) {
        var regionOpt = subregionService.getSubregionByName(guild.getId(), regionName);
        if (regionOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Region not found: " + regionName));
            return null;
        }
        return regionOpt.get();
    }

    /**
     * Gets online player or sends error message.
     *
     * @param playerName the player name
     * @param sender player to send error to
     * @return Player or null if not online
     */
    public Player requirePlayer(String playerName, Player sender) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Player not found or not online"));
        }
        return player;
    }

    /**
     * Checks modify permission for region or sends error message.
     *
     * @param region the region
     * @param playerId the player ID
     * @param player player to send error to
     * @return true if permission granted
     */
    public boolean requireModifyPermission(Subregion region, UUID playerId, Player player) {
        if (!permissionService.canModifyPermissions(region, playerId)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "You don't have permission to modify region permissions"));
            return false;
        }
        return true;
    }

    /**
     * Formats type ID for display with display name fallback.
     *
     * @param typeId the type ID
     * @return formatted display name
     */
    public String formatTypeDisplayName(String typeId) {
        if (typeId == null || typeId.isEmpty()) {
            return "None";
        }
        return typeRegistry.getType(typeId)
                .map(SubregionType::getDisplayName)
                .orElse(typeId);
    }

    /**
     * Validates region type is registered or sends error.
     *
     * @param typeId the type ID
     * @param player player to send error to
     * @return true if valid
     */
    public boolean validateRegionType(String typeId, Player player) {
        if (!typeRegistry.isRegistered(typeId)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Unknown region type: " + typeId + ". Use /g region types to see available types."));
            return false;
        }
        return true;
    }

    /**
     * Formats large numbers with K/M suffixes.
     *
     * @param number the number to format
     * @return formatted number string
     */
    public String formatNumber(long number) {
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }
}
