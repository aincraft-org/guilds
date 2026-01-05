package org.aincraft.commands.components.region;

import com.google.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.Guild;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.aincraft.subregion.RegionVisualizer;
import org.bukkit.entity.Player;

/**
 * Handles basic region CRUD operations: delete, list, info, visualize.
 * Single Responsibility: Manages region basic information and display.
 */
public class RegionBasicComponent {
    private final SubregionService subregionService;
    private final RegionVisualizer visualizer;
    private final SubregionTypeRegistry typeRegistry;
    private final RegionCommandHelper helper;

    @Inject
    public RegionBasicComponent(SubregionService subregionService, RegionVisualizer visualizer,
                               SubregionTypeRegistry typeRegistry, RegionCommandHelper helper) {
        this.subregionService = subregionService;
        this.visualizer = visualizer;
        this.typeRegistry = typeRegistry;
        this.helper = helper;
    }

    /**
     * Deletes a region.
     *
     * @param player the player
     * @param args command args [2] = region name
     * @return true if handled
     */
    public boolean handleDelete(Player player, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region delete <name>");
            return true;
        }

        Guild guild = helper.requireGuild(player);
        if (guild == null) {
            return true;
        }

        String name = args[2];
        if (subregionService.deleteSubregion(guild.getId(), player.getUniqueId(), name)) {
            Messages.send(player, MessageKey.REGION_DELETED, name);
        } else {
            Messages.send(player, MessageKey.ERROR_USAGE, "Failed to delete region. It may not exist or you lack permission.");
        }

        return true;
    }

    /**
     * Lists all regions in player's guild.
     *
     * @param player the player
     * @return true if handled
     */
    public boolean handleList(Player player) {
        Guild guild = helper.requireGuild(player);
        if (guild == null) {
            return true;
        }

        List<Subregion> regions = subregionService.getGuildSubregions(guild.getId());

        if (regions.isEmpty()) {
            Messages.send(player, MessageKey.LIST_EMPTY);
            return true;
        }

        Messages.send(player, MessageKey.LIST_HEADER);

        for (Subregion region : regions) {
            Component regionLine = buildRegionListItem(region);
            player.sendMessage(regionLine);
        }

        return true;
    }

    /**
     * Shows detailed information about a region.
     *
     * @param player the player
     * @param args command args [2] = optional region name
     * @return true if handled
     */
    public boolean handleInfo(Player player, String[] args) {
        Guild guild = helper.requireGuild(player);
        if (guild == null) {
            return true;
        }

        String name;
        if (args.length >= 3) {
            name = args[2];
        } else {
            // Try to find region at player's location
            Optional<Subregion> atLocation = subregionService.getSubregionAt(player.getLocation());
            if (atLocation.isEmpty()) {
                Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region info <name> or stand inside a region");
                return true;
            }
            name = atLocation.get().getName();
        }

        Subregion region = helper.requireRegion(guild, name, player);
        if (region == null) {
            return true;
        }

        Messages.send(player, MessageKey.INFO_HEADER, "Region: " + region.getName());

        // Type info
        if (region.getType() != null) {
            String displayName = helper.formatTypeDisplayName(region.getType());
            var typeOpt = typeRegistry.getType(region.getType());
            if (typeOpt.isPresent()) {
                var type = typeOpt.get();
                Messages.send(player, MessageKey.INFO_HEADER, "Type: " + displayName + " - " + type.getDescription());
            } else {
                Messages.send(player, MessageKey.INFO_HEADER, "Type: " + region.getType());
            }
        }

        Messages.send(player, MessageKey.INFO_HEADER, "World: " + region.getWorld());
        Messages.send(player, MessageKey.INFO_HEADER, "Bounds: " +
                region.getMinX() + "," + region.getMinY() + "," + region.getMinZ() + " to " +
                region.getMaxX() + "," + region.getMaxY() + "," + region.getMaxZ());
        Messages.send(player, MessageKey.INFO_HEADER, "Volume: " + region.getVolume() + " blocks");
        Messages.send(player, MessageKey.INFO_HEADER, "Created: " + new Date(region.getCreatedAt()).toString());
        Messages.send(player, MessageKey.INFO_HEADER, "Owners: " + region.getOwners().size() + " player(s)");

        if (region.getPermissions() != 0) {
            Messages.send(player, MessageKey.INFO_HEADER, "Custom Permissions: " + region.getPermissions());
        }

        return true;
    }

    /**
     * Visualizes region boundaries for player.
     *
     * @param player the player
     * @param args command args [2] = region name
     * @return true if handled
     */
    public boolean handleVisualize(Player player, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "Usage: /g region visualize <name>");
            return true;
        }

        Guild guild = helper.requireGuild(player);
        if (guild == null) {
            return true;
        }

        String regionName = args[2];
        Subregion region = helper.requireRegion(guild, regionName, player);
        if (region == null) {
            return true;
        }

        // Start visualization
        visualizer.visualizeRegion(player, region);

        Messages.send(player, MessageKey.INFO_HEADER, regionName);

        return true;
    }

    /**
     * Builds a single region list item with hover and click events.
     */
    private Component buildRegionListItem(Subregion region) {
        String typeBadge = "";
        if (region.getType() != null) {
            String displayName = helper.formatTypeDisplayName(region.getType());
            typeBadge = "[" + displayName.toUpperCase() + "] ";
        }

        Component hoverText = Component.text()
                .append(Component.text("Click to visualize boundaries\n", NamedTextColor.AQUA))
                .append(Component.text("Shift+Click to view full info\n\n", NamedTextColor.GREEN))
                .append(Component.text("World: ", NamedTextColor.GRAY))
                .append(Component.text(region.getWorld(), NamedTextColor.YELLOW))
                .append(Component.text("\nVolume: ", NamedTextColor.GRAY))
                .append(Component.text(region.getVolume() + " blocks", NamedTextColor.YELLOW))
                .append(Component.text("\nPosition: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("(%d, %d, %d) to (%d, %d, %d)",
                        region.getMinX(), region.getMinY(), region.getMinZ(),
                        region.getMaxX(), region.getMaxY(), region.getMaxZ()),
                        NamedTextColor.YELLOW))
                .build();

        return Component.text()
                .append(Component.text("• ", NamedTextColor.GRAY))
                .append(Component.text(typeBadge, NamedTextColor.YELLOW))
                .append(Component.text(region.getName(), NamedTextColor.GOLD)
                        .hoverEvent(HoverEvent.showText(hoverText))
                        .clickEvent(ClickEvent.runCommand("/g region visualize " + region.getName())))
                .append(Component.text(" - " + region.getWorld() + " (" + region.getVolume() + " blocks)", NamedTextColor.GRAY))
                .build();
    }
}
