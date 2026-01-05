package org.aincraft.commands.components.region;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
import org.bukkit.entity.Player;

/**
 * Handles region owner management: addowner, removeowner.
 * Single Responsibility: Manages region owner assignments.
 */
public class RegionOwnerComponent {
    private final SubregionService subregionService;
    private final RegionCommandHelper helper;

    @Inject
    public RegionOwnerComponent(SubregionService subregionService, RegionCommandHelper helper) {
        this.subregionService = subregionService;
        this.helper = helper;
    }

    /**
     * Adds a player as region owner.
     *
     * @param player the player
     * @param args command args [2] = region name, [3] = target player name
     * @return true if handled
     */
    public boolean handleAddOwner(Player player, String[] args) {
        if (args.length < 4) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region addowner <region> <player>");
            return true;
        }

        Guild guild = helper.requireGuild(player);
        if (guild == null) {
            return true;
        }

        String regionName = args[2];
        String targetName = args[3];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        Player target = helper.requirePlayer(targetName, player);
        if (target == null) {
            return true;
        }

        if (subregionService.addSubregionOwner(guild.getId(), player.getUniqueId(), regionName, target.getUniqueId())) {
            Messages.send(player, MessageKey.REGION_OWNER_ADDED, target.getName(), regionName);
        } else {
            Messages.send(player, MessageKey.ERROR_USAGE, "Failed to add owner. Region may not exist or you lack permission.");
        }

        return true;
    }

    /**
     * Removes a player as region owner.
     *
     * @param player the player
     * @param args command args [2] = region name, [3] = target player name
     * @return true if handled
     */
    public boolean handleRemoveOwner(Player player, String[] args) {
        if (args.length < 4) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region removeowner <region> <player>");
            return true;
        }

        Guild guild = helper.requireGuild(player);
        if (guild == null) {
            return true;
        }

        String regionName = args[2];
        String targetName = args[3];

        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        Player target = helper.requirePlayer(targetName, player);
        if (target == null) {
            return true;
        }

        if (subregionService.removeSubregionOwner(guild.getId(), player.getUniqueId(), regionName, target.getUniqueId())) {
            Messages.send(player, MessageKey.REGION_OWNER_REMOVED, target.getName(), regionName);
        } else {
            Messages.send(player, MessageKey.ERROR_USAGE, "Failed to remove owner. Region may not exist, you lack permission, or can't remove the creator.");
        }

        return true;
    }
}
