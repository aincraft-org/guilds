package org.aincraft.commands.components.region;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionType;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.aincraft.subregion.RegionTypeLimit;
import org.aincraft.subregion.RegionTypeLimitRepository;
import org.bukkit.entity.Player;

/**
 * Handles region type management: types, settype, limit.
 * Single Responsibility: Manages region type assignments and type limits.
 */
public class RegionTypeComponent {
    private final SubregionService subregionService;
    private final SubregionTypeRegistry typeRegistry;
    private final RegionTypeLimitRepository limitRepository;
    private final RegionCommandHelper helper;

    @Inject
    public RegionTypeComponent(SubregionService subregionService, SubregionTypeRegistry typeRegistry,
                              RegionTypeLimitRepository limitRepository, RegionCommandHelper helper) {
        this.subregionService = subregionService;
        this.typeRegistry = typeRegistry;
        this.limitRepository = limitRepository;
        this.helper = helper;
    }

    /**
     * Lists all available region types.
     *
     * @param player the player
     * @return true if handled
     */
    public boolean handleTypes(Player player) {
        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Region Types", " (" + typeRegistry.size() + ")"));

        for (SubregionType type : typeRegistry.getAllTypes()) {
            String builtIn = typeRegistry.isBuiltIn(type.getId()) ? " <gray>(built-in)</gray>" : "";
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>• <gold>" + type.getId() + "</gold> - " + type.getDisplayName() + builtIn));
            if (!type.getDescription().isEmpty()) {
                player.sendMessage(MessageFormatter.deserialize("  <dark_gray>" + type.getDescription() + "</dark_gray>"));
            }
        }

        return true;
    }

    /**
     * Changes a region's type.
     *
     * @param player the player
     * @param args command args [2] = region name, [3] = type ID
     * @return true if handled
     */
    public boolean handleSetType(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region settype <name> <type>"));
            return true;
        }

        Guild guild = helper.requireGuild(player);
        if (guild == null) {
            return true;
        }

        String regionName = args[2];
        String typeId = args[3].toLowerCase();

        // Special cases: "none" or "clear" removes the type
        if (typeId.equals("none") || typeId.equals("clear")) {
            if (subregionService.setSubregionType(guild.getId(), player.getUniqueId(), regionName, null)) {
                player.sendMessage(MessageFormatter.deserialize(
                        "<green>Cleared type from region <gold>" + regionName + "</gold></green>"));
            } else {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                        "Failed to update region type. Region may not exist or you lack permission."));
            }
            return true;
        }

        // Validate type exists
        if (!helper.validateRegionType(typeId, player)) {
            return true;
        }

        if (subregionService.setSubregionType(guild.getId(), player.getUniqueId(), regionName, typeId)) {
            String displayName = helper.formatTypeDisplayName(typeId);
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Set type of region <gold>" + regionName + "</gold> to <yellow>" + displayName + "</yellow></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Failed to update region type. Region may not exist or you lack permission."));
        }

        return true;
    }

    /**
     * Manages volume limits for region types.
     *
     * @param player the player
     * @param args command args [2+] = type, volume, or remove
     * @return true if handled
     */
    public boolean handleLimit(Player player, String[] args) {
        if (args.length == 2) {
            return listAllLimits(player);
        }

        String typeId = args[2].toLowerCase();

        // Validate type exists
        if (!helper.validateRegionType(typeId, player)) {
            return true;
        }

        if (args.length == 3) {
            return showTypeLimit(player, typeId);
        }

        // Set or remove limit (op only)
        if (!player.isOp()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only operators can modify type limits"));
            return true;
        }

        String action = args[3].toLowerCase();

        if (action.equals("remove")) {
            limitRepository.delete(typeId);
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Removed volume limit for type <gold>" + typeId + "</gold></green>"));
            return true;
        }

        // Try to parse as volume
        long volume;
        try {
            volume = Long.parseLong(action);
        } catch (NumberFormatException e) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid volume. Use a number or 'remove'."));
            return true;
        }

        if (volume <= 0) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Volume must be positive"));
            return true;
        }

        RegionTypeLimit limit = new RegionTypeLimit(typeId, volume);
        limitRepository.save(limit);

        String displayName = helper.formatTypeDisplayName(typeId);
        player.sendMessage(MessageFormatter.deserialize(
                "<green>Set volume limit for <gold>" + displayName + "</gold> to <yellow>" +
                helper.formatNumber(volume) + "</yellow> blocks</green>"));

        return true;
    }

    /**
     * Lists all type volume limits.
     */
    private boolean listAllLimits(Player player) {
        var limits = limitRepository.findAll();
        if (limits.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "No type volume limits configured"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Region Type Limits", " (" + limits.size() + ")"));
        for (RegionTypeLimit limit : limits) {
            String displayName = helper.formatTypeDisplayName(limit.typeId());
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>• <gold>" + limit.typeId() + "</gold> (" + displayName + "): <yellow>" +
                    helper.formatNumber(limit.maxTotalVolume()) + "</yellow> blocks max</gray>"));
        }
        return true;
    }

    /**
     * Shows limit and usage for a specific type.
     */
    private boolean showTypeLimit(Player player, String typeId) {
        var limitOpt = limitRepository.findByTypeId(typeId);
        String displayName = helper.formatTypeDisplayName(typeId);

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Type Limit: " + displayName, ""));

        if (limitOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Limit", "No limit configured"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Max Volume",
                    helper.formatNumber(limitOpt.get().maxTotalVolume()) + " blocks"));
        }

        return true;
    }
}
