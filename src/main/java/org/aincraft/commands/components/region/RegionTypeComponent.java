package org.aincraft.commands.components.region;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import org.aincraft.Guild;
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
        Mint.sendMessage(player, "<primary>=== Region Types ===</primary>");

        for (SubregionType type : typeRegistry.getAllTypes()) {
            String builtIn = typeRegistry.isBuiltIn(type.getId()) ? " (built-in)" : "";
            Mint.sendMessage(player, "<neutral>• <secondary>" + type.getId() + "</secondary> - " + type.getDisplayName() + builtIn + "</neutral>");
            if (!type.getDescription().isEmpty()) {
                Mint.sendMessage(player, "<neutral>  " + type.getDescription() + "</neutral>");
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
            Mint.sendMessage(player, "<error>Usage: /g region settype <name> <type></error>");
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
                Mint.sendMessage(player, "<success>Region type cleared (set to None)</success>");
            } else {
                Mint.sendMessage(player, "<error>Failed to update region type. Region may not exist or you lack permission.</error>");
            }
            return true;
        }

        // Validate type exists
        if (!helper.validateRegionType(typeId, player)) {
            return true;
        }

        if (subregionService.setSubregionType(guild.getId(), player.getUniqueId(), regionName, typeId)) {
            String displayName = helper.formatTypeDisplayName(typeId);
            Mint.sendMessage(player, String.format("<info>Region <secondary>%s</secondary> type set to <secondary>%s</secondary></info>", regionName, displayName));
        } else {
            Mint.sendMessage(player, "<error>Failed to update region type. Region may not exist or you lack permission.</error>");
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
            Mint.sendMessage(player, "<error>You don't have permission to do that</error>");
            return true;
        }

        String action = args[3].toLowerCase();

        if (action.equals("remove")) {
            limitRepository.delete(typeId);
            Mint.sendMessage(player, String.format("<success>Limit for <secondary>%s</secondary> removed</success>", typeId));
            return true;
        }

        // Try to parse as volume
        long volume;
        try {
            volume = Long.parseLong(action);
        } catch (NumberFormatException e) {
            Mint.sendMessage(player, "<error>Invalid volume. Use a number or 'remove'.</error>");
            return true;
        }

        if (volume <= 0) {
            Mint.sendMessage(player, "<error>Volume must be positive</error>");
            return true;
        }

        RegionTypeLimit limit = new RegionTypeLimit(typeId, volume);
        limitRepository.save(limit);

        String displayName = helper.formatTypeDisplayName(typeId);
        Mint.sendMessage(player, String.format("<success>Limit for <secondary>%s</secondary> set to <secondary>%s</secondary></success>", displayName, helper.formatNumber(volume)));

        return true;
    }

    /**
     * Lists all type volume limits.
     */
    private boolean listAllLimits(Player player) {
        var limits = limitRepository.findAll();
        if (limits.isEmpty()) {
            Mint.sendMessage(player, "<neutral>List is empty</neutral>");
            return true;
        }

        Mint.sendMessage(player, "<primary>=== Type Limits ===</primary>");
        for (RegionTypeLimit limit : limits) {
            String displayName = helper.formatTypeDisplayName(limit.typeId());
            Mint.sendMessage(player, String.format("<neutral>• <secondary>%s</secondary> (%s): <warning>%s</warning> blocks max</neutral>",
                    limit.typeId(), displayName, helper.formatNumber(limit.maxTotalVolume())));
        }
        return true;
    }

    /**
     * Shows limit and usage for a specific type.
     */
    private boolean showTypeLimit(Player player, String typeId) {
        var limitOpt = limitRepository.findByTypeId(typeId);
        String displayName = helper.formatTypeDisplayName(typeId);

        Mint.sendMessage(player, String.format("<info>%s</info>", displayName));

        if (limitOpt.isEmpty()) {
            Mint.sendMessage(player, "<info>No limit configured</info>");
        } else {
            Mint.sendMessage(player, String.format("<info>Max Volume: %s blocks</info>", helper.formatNumber(limitOpt.get().maxTotalVolume())));
        }

        return true;
    }
}
