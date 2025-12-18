package org.aincraft.commands.components.region;

import java.util.Optional;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.subregion.SelectionManager;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionType;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Handles region selection workflow: pos1, pos2, create, cancel.
 * Single Responsibility: Manages the region creation selection process.
 */
public class RegionSelectionComponent {
    private final SelectionManager selectionManager;
    private final SubregionService subregionService;
    private final PermissionService permissionService;
    private final SubregionTypeRegistry typeRegistry;
    private final GuildMemberService memberService;
    private final RegionCommandHelper helper;

    public RegionSelectionComponent(SelectionManager selectionManager, SubregionService subregionService,
                                   PermissionService permissionService, SubregionTypeRegistry typeRegistry,
                                   GuildMemberService memberService, RegionCommandHelper helper) {
        this.selectionManager = selectionManager;
        this.subregionService = subregionService;
        this.permissionService = permissionService;
        this.typeRegistry = typeRegistry;
        this.memberService = memberService;
        this.helper = helper;
    }

    /**
     * Initiates region creation workflow.
     *
     * @param player the player
     * @param args command args [2] = name, [3] = optional type
     * @return true if handled
     */
    public boolean handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region create <name> [type]"));
            return true;
        }

        Guild guild = helper.requireGuild(player);
        if (guild == null) {
            return true;
        }

        String name = args[2];
        String type = args.length >= 4 ? args[3].toLowerCase() : null;

        // Validate type if provided
        if (type != null && !helper.validateRegionType(type, player)) {
            return true;
        }

        // Validate permissions
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_REGIONS)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to manage regions"));
            return true;
        }

        // Validate name uniqueness
        if (subregionService.getSubregionByName(guild.getId(), name).isPresent()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "A region with that name already exists"));
            return true;
        }

        // Start the creation workflow
        selectionManager.startPendingCreation(player.getUniqueId(), name, type, guild.getId());

        String typeInfo = type != null
                ? " <yellow>[" + helper.formatTypeDisplayName(type) + "]</yellow>"
                : "";
        player.sendMessage(MessageFormatter.deserialize(
                "<green>Starting region creation for <gold>\"" + name + "\"</gold>" + typeInfo + "</green>"));
        player.sendMessage(MessageFormatter.deserialize(
                "<gray>Set position 1 with <yellow>/g region pos1</yellow></gray>"));

        return true;
    }

    /**
     * Sets first corner of region selection.
     *
     * @param player the player
     * @return true if handled
     */
    public boolean handlePos1(Player player) {
        if (!selectionManager.hasPendingCreation(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "No region creation in progress. Start with /g region create <name> [type]"));
            return true;
        }

        Location loc = player.getLocation();
        selectionManager.setPos1(player.getUniqueId(), loc);

        player.sendMessage(MessageFormatter.deserialize(
                "<green>Position 1 set at <gold>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</gold></green>"));

        if (selectionManager.hasCompleteSelection(player.getUniqueId())) {
            return finalizePendingCreation(player);
        } else {
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>Now set position 2 with <yellow>/g region pos2</yellow></gray>"));
        }

        return true;
    }

    /**
     * Sets second corner of region selection.
     *
     * @param player the player
     * @return true if handled
     */
    public boolean handlePos2(Player player) {
        if (!selectionManager.hasPendingCreation(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "No region creation in progress. Start with /g region create <name> [type]"));
            return true;
        }

        Location loc = player.getLocation();
        selectionManager.setPos2(player.getUniqueId(), loc);

        player.sendMessage(MessageFormatter.deserialize(
                "<green>Position 2 set at <gold>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</gold></green>"));

        if (selectionManager.hasCompleteSelection(player.getUniqueId())) {
            return finalizePendingCreation(player);
        } else {
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>Now set position 1 with <yellow>/g region pos1</yellow></gray>"));
        }

        return true;
    }

    /**
     * Cancels pending region creation.
     *
     * @param player the player
     * @return true if handled
     */
    public boolean handleCancel(Player player) {
        if (!selectionManager.hasPendingCreation(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "No region creation in progress"));
            return true;
        }

        selectionManager.clearSelection(player.getUniqueId());
        selectionManager.clearPendingCreation(player.getUniqueId());
        player.sendMessage(MessageFormatter.deserialize("<yellow>Region creation cancelled</yellow>"));

        return true;
    }

    /**
     * Finalizes pending region creation when both positions are set.
     *
     * @param player the player
     * @return true if handled
     */
    private boolean finalizePendingCreation(Player player) {
        Optional<SelectionManager.PendingCreation> pendingOpt = selectionManager.getPendingCreation(player.getUniqueId());
        if (pendingOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "No pending region creation found"));
            return true;
        }

        SelectionManager.PendingCreation pending = pendingOpt.get();
        Optional<SelectionManager.PlayerSelection> selectionOpt = selectionManager.getSelection(player.getUniqueId());

        if (selectionOpt.isEmpty() || !selectionOpt.get().isComplete()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Selection incomplete"));
            return true;
        }

        SelectionManager.PlayerSelection selection = selectionOpt.get();
        SubregionService.SubregionCreationResult result = subregionService.createSubregion(
                pending.guildId(), player.getUniqueId(), pending.name(),
                selection.pos1(), selection.pos2(), pending.type());

        if (result.success()) {
            var region = result.region();
            String typeInfo = pending.type() != null
                    ? " <yellow>[" + helper.formatTypeDisplayName(pending.type()) + "]</yellow>"
                    : "";
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Created region <gold>\"" + pending.name() + "\"</gold>" + typeInfo + " (" + region.getVolume() + " blocks)</green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.errorMessage()));
        }

        // Clear selection state
        selectionManager.clearSelection(player.getUniqueId());
        selectionManager.clearPendingCreation(player.getUniqueId());

        return true;
    }
}
