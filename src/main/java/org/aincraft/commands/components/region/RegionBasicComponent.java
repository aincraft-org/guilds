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
import dev.mintychochip.mint.Mint;
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
            Mint.sendMessage(player, "<error>Usage: /g region delete <name></error>");
            return true;
        }

        Guild guild = helper.requireGuild(player);
        if (guild == null) {
            return true;
        }

        String name = args[2];
        if (subregionService.deleteSubregion(guild.getId(), player.getUniqueId(), name)) {
            Mint.sendMessage(player, "<success>Region <secondary>" + name + "</secondary> deleted</success>");
        } else {
            Mint.sendMessage(player, "<error>Failed to delete region. It may not exist or you lack permission.</error>");
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
            Mint.sendMessage(player, "<neutral>List is empty</neutral>");
            return true;
        }

        Mint.sendMessage(player, "<info>=== List ===</info>");

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
                Mint.sendMessage(player, "<error>Usage: /g region info <name> or stand inside a region</error>");
                return true;
            }
            name = atLocation.get().getName();
        }

        Subregion region = helper.requireRegion(guild, name, player);
        if (region == null) {
            return true;
        }

        Mint.sendMessage(player, "<info>Region: <secondary>" + region.getName() + "</secondary></info>");

        // Type info
        if (region.getType() != null) {
            String displayName = helper.formatTypeDisplayName(region.getType());
            var typeOpt = typeRegistry.getType(region.getType());
            if (typeOpt.isPresent()) {
                var type = typeOpt.get();
                Mint.sendMessage(player, "<info>Type: <secondary>" + displayName + "</secondary> - " + type.getDescription() + "</info>");
            } else {
                Mint.sendMessage(player, "<info>Type: <secondary>" + region.getType() + "</secondary></info>");
            }
        }

        Mint.sendMessage(player, "<info>World: <secondary>" + region.getWorld() + "</secondary></info>");
        Mint.sendMessage(player, "<info>Bounds: <primary>" +
                region.getMinX() + "," + region.getMinY() + "," + region.getMinZ() + "</primary> to <primary>" +
                region.getMaxX() + "," + region.getMaxY() + "," + region.getMaxZ() + "</primary></info>");
        Mint.sendMessage(player, "<info>Volume: <primary>" + region.getVolume() + "</primary> blocks</info>");
        Mint.sendMessage(player, "<info>Created: <primary>" + new Date(region.getCreatedAt()).toString() + "</primary></info>");
        Mint.sendMessage(player, "<info>Owners: <primary>" + region.getOwners().size() + "</primary> player(s)</info>");

        if (region.getPermissions() != 0) {
            Mint.sendMessage(player, "<info>Custom Permissions: <primary>" + region.getPermissions() + "</primary></info>");
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
            Mint.sendMessage(player, "<error>Usage: /g region visualize <name></error>");
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

        Mint.sendMessage(player, "<info>" + regionName + "</info>");

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
