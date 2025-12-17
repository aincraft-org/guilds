package org.aincraft.commands.components;

import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.subregion.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Command component for managing subregions.
 * Handles: pos1, pos2, create, delete, list, info, addowner, removeowner, types, settype, setperm, removeperm, listperms, role
 */
public class RegionComponent implements GuildCommand {
    private final GuildService guildService;
    private final SubregionService subregionService;
    private final SelectionManager selectionManager;
    private final SubregionTypeRegistry typeRegistry;
    private final RegionPermissionService regionPermissionService;

    public RegionComponent(GuildService guildService, SubregionService subregionService,
                           SelectionManager selectionManager, SubregionTypeRegistry typeRegistry,
                           RegionPermissionService regionPermissionService) {
        this.guildService = guildService;
        this.subregionService = subregionService;
        this.selectionManager = selectionManager;
        this.typeRegistry = typeRegistry;
        this.regionPermissionService = regionPermissionService;
    }

    @Override
    public String getName() {
        return "region";
    }

    @Override
    public String getPermission() {
        return "guilds.region";
    }

    @Override
    public String getUsage() {
        return "/g region <pos1|pos2|create|delete|list|info|types|settype|addowner|removeowner|setperm|removeperm|listperms|role>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use region commands"));
            return true;
        }

        if (args.length < 2) {
            showHelp(player);
            return true;
        }

        String subCommand = args[1].toLowerCase();

        return switch (subCommand) {
            case "pos1" -> handlePos1(player);
            case "pos2" -> handlePos2(player);
            case "create" -> handleCreate(player, args);
            case "delete" -> handleDelete(player, args);
            case "list" -> handleList(player);
            case "info" -> handleInfo(player, args);
            case "types" -> handleTypes(player);
            case "settype" -> handleSetType(player, args);
            case "addowner" -> handleAddOwner(player, args);
            case "removeowner" -> handleRemoveOwner(player, args);
            case "setperm" -> handleSetPerm(player, args);
            case "removeperm" -> handleRemovePerm(player, args);
            case "listperms" -> handleListPerms(player, args);
            case "role" -> handleRole(player, args);
            default -> {
                showHelp(player);
                yield true;
            }
        };
    }

    private void showHelp(Player player) {
        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Region Commands", ""));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region pos1", "Set first corner at your location"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region pos2", "Set second corner at your location"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region create <name> [type]", "Create region from selection"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region delete <name>", "Delete a region"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region list", "List your guild's regions"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region info <name>", "Show region details"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region types", "List available region types"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region settype <name> <type>", "Change region type"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region addowner <region> <player>", "Add region owner"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region removeowner <region> <player>", "Remove region owner"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region setperm <region> player <player> <perms>", "Set player permissions"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region setperm <region> role <role> <perms>", "Set role permissions"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region removeperm <region> player <player>", "Remove player permissions"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region removeperm <region> role <role>", "Remove role permissions"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region listperms <region>", "List region permissions"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region role create <region> <name> <perms>", "Create region role"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region role delete <region> <name>", "Delete region role"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region role list <region>", "List region roles"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region role assign <region> <role> <player>", "Assign player to role"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region role unassign <region> <role> <player>", "Unassign player from role"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g region role members <region> <role>", "List role members"));
    }

    private boolean handlePos1(Player player) {
        Location loc = player.getLocation();
        selectionManager.setPos1(player.getUniqueId(), loc);

        player.sendMessage(MessageFormatter.deserialize(
                "<green>Position 1 set at <gold>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</gold></green>"));

        if (selectionManager.hasCompleteSelection(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>Selection complete. Use <yellow>/g region create <name></yellow> to create the region.</gray>"));
        } else {
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>Now set position 2 with <yellow>/g region pos2</yellow></gray>"));
        }

        return true;
    }

    private boolean handlePos2(Player player) {
        Location loc = player.getLocation();
        selectionManager.setPos2(player.getUniqueId(), loc);

        player.sendMessage(MessageFormatter.deserialize(
                "<green>Position 2 set at <gold>" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "</gold></green>"));

        if (selectionManager.hasCompleteSelection(player.getUniqueId())) {
            SelectionManager.PlayerSelection sel = selectionManager.getSelection(player.getUniqueId()).get();
            long volume = calculateVolume(sel.pos1(), sel.pos2());
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>Selection complete (" + volume + " blocks). Use <yellow>/g region create <name></yellow> to create the region.</gray>"));
        } else {
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>Now set position 1 with <yellow>/g region pos1</yellow></gray>"));
        }

        return true;
    }

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region create <name> [type]"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        if (!selectionManager.hasCompleteSelection(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "You need to set both positions first. Use /g region pos1 and /g region pos2"));
            return true;
        }

        String name = args[2];
        String type = args.length >= 4 ? args[3].toLowerCase() : null;

        // Validate type if provided
        if (type != null && !typeRegistry.isRegistered(type)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Unknown region type: " + type + ". Use /g region types to see available types."));
            return true;
        }

        SelectionManager.PlayerSelection sel = selectionManager.getSelection(player.getUniqueId()).get();

        SubregionService.SubregionCreationResult result = subregionService.createSubregion(
                guild.getId(), player.getUniqueId(), name, sel.pos1(), sel.pos2(), type);

        if (result.success()) {
            Subregion region = result.region();
            String typeInfo = type != null ? " [" + typeRegistry.getType(type).map(SubregionType::getDisplayName).orElse(type) + "]" : "";
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Created region <gold>" + name + "</gold>" + typeInfo + " (" + region.getVolume() + " blocks)</green>"));
            selectionManager.clearSelection(player.getUniqueId());
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.errorMessage()));
        }

        return true;
    }

    private boolean handleDelete(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region delete <name>"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        String name = args[2];
        if (subregionService.deleteSubregion(guild.getId(), player.getUniqueId(), name)) {
            player.sendMessage(MessageFormatter.deserialize("<green>Deleted region <gold>" + name + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Failed to delete region. It may not exist or you lack permission."));
        }

        return true;
    }

    private boolean handleList(Player player) {
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        List<Subregion> regions = subregionService.getGuildSubregions(guild.getId());

        if (regions.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "Your guild has no regions"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Guild Regions", " (" + regions.size() + ")"));
        for (Subregion region : regions) {
            String typeBadge = "";
            if (region.getType() != null) {
                String displayName = typeRegistry.getType(region.getType())
                        .map(SubregionType::getDisplayName)
                        .orElse(region.getType());
                typeBadge = "<yellow>[" + displayName.toUpperCase() + "]</yellow> ";
            }
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>• " + typeBadge + "<gold>" + region.getName() + "</gold> - " +
                    region.getWorld() + " (" + region.getVolume() + " blocks)</gray>"));
        }

        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        String name;
        if (args.length >= 3) {
            name = args[2];
        } else {
            // Try to find region at player's location
            Optional<Subregion> atLocation = subregionService.getSubregionAt(player.getLocation());
            if (atLocation.isEmpty()) {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region info <name> or stand inside a region"));
                return true;
            }
            name = atLocation.get().getName();
        }

        Optional<Subregion> regionOpt = subregionService.getSubregionByName(guild.getId(), name);
        if (regionOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Region not found: " + name));
            return true;
        }

        Subregion region = regionOpt.get();

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Region: " + region.getName(), ""));

        // Type info
        if (region.getType() != null) {
            Optional<SubregionType> typeOpt = typeRegistry.getType(region.getType());
            if (typeOpt.isPresent()) {
                SubregionType type = typeOpt.get();
                player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Type", type.getDisplayName() + " - " + type.getDescription()));
            } else {
                player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Type", region.getType()));
            }
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "World", region.getWorld()));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Bounds",
                region.getMinX() + "," + region.getMinY() + "," + region.getMinZ() + " to " +
                region.getMaxX() + "," + region.getMaxY() + "," + region.getMaxZ()));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Volume", region.getVolume() + " blocks"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Created", new Date(region.getCreatedAt()).toString()));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Owners", region.getOwners().size() + " player(s)"));

        if (region.getPermissions() != 0) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Custom Permissions", String.valueOf(region.getPermissions())));
        }

        return true;
    }

    private boolean handleTypes(Player player) {
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

    private boolean handleSetType(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region settype <name> <type>"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        String regionName = args[2];
        String typeId = args[3].toLowerCase();

        // Special case: "none" or "clear" removes the type
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
        if (!typeRegistry.isRegistered(typeId)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Unknown region type: " + typeId + ". Use /g region types to see available types."));
            return true;
        }

        if (subregionService.setSubregionType(guild.getId(), player.getUniqueId(), regionName, typeId)) {
            String displayName = typeRegistry.getType(typeId).map(SubregionType::getDisplayName).orElse(typeId);
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Set type of region <gold>" + regionName + "</gold> to <yellow>" + displayName + "</yellow></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Failed to update region type. Region may not exist or you lack permission."));
        }

        return true;
    }

    private boolean handleAddOwner(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region addowner <region> <player>"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        String regionName = args[2];
        String targetName = args[3];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Player not found or not online"));
            return true;
        }

        if (subregionService.addSubregionOwner(guild.getId(), player.getUniqueId(), regionName, target.getUniqueId())) {
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Added <gold>" + target.getName() + "</gold> as owner of <gold>" + regionName + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Failed to add owner. Region may not exist or you lack permission."));
        }

        return true;
    }

    private boolean handleRemoveOwner(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region removeowner <region> <player>"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        String regionName = args[2];
        String targetName = args[3];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Player not found or not online"));
            return true;
        }

        if (subregionService.removeSubregionOwner(guild.getId(), player.getUniqueId(), regionName, target.getUniqueId())) {
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Removed <gold>" + target.getName() + "</gold> as owner of <gold>" + regionName + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "Failed to remove owner. Region may not exist, you lack permission, or can't remove the creator."));
        }

        return true;
    }

    private boolean handleSetPerm(Player player, String[] args) {
        if (args.length < 6) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region setperm <region> <player|role> <target> <permissions>"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        String regionName = args[2];
        String subjectType = args[3].toLowerCase();
        String targetIdentifier = args[4];
        String permString = args[5];

        Optional<Subregion> regionOpt = subregionService.getSubregionByName(guild.getId(), regionName);
        if (regionOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Region not found: " + regionName));
            return true;
        }

        Subregion region = regionOpt.get();

        // Check if player can modify permissions
        if (!regionPermissionService.canModifyPermissions(region, player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to modify region permissions"));
            return true;
        }

        // Parse permissions
        int permissions;
        try {
            permissions = Integer.parseInt(permString);
        } catch (NumberFormatException e) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid permission value. Use an integer (e.g., " + GuildPermission.BUILD.getBit() + " for BUILD)"));
            return true;
        }

        switch (subjectType) {
            case "player" -> {
                Player target = Bukkit.getPlayer(targetIdentifier);
                if (target == null) {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Player not found or not online"));
                    return true;
                }

                regionPermissionService.setPlayerPermission(region.getId(), target.getUniqueId(), permissions, player.getUniqueId());
                player.sendMessage(MessageFormatter.deserialize(
                        "<green>Set permissions for <gold>" + target.getName() + "</gold> in region <gold>" + regionName + "</gold> to <yellow>" + permissions + "</yellow></green>"));
            }
            case "role" -> {
                regionPermissionService.setRolePermission(region.getId(), targetIdentifier, permissions, player.getUniqueId());
                player.sendMessage(MessageFormatter.deserialize(
                        "<green>Set permissions for role <gold>" + targetIdentifier + "</gold> in region <gold>" + regionName + "</gold> to <yellow>" + permissions + "</yellow></green>"));
            }
            default -> {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Subject type must be 'player' or 'role'"));
                return true;
            }
        }

        return true;
    }

    private boolean handleRemovePerm(Player player, String[] args) {
        if (args.length < 5) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region removeperm <region> <player|role> <target>"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        String regionName = args[2];
        String subjectType = args[3].toLowerCase();
        String targetIdentifier = args[4];

        Optional<Subregion> regionOpt = subregionService.getSubregionByName(guild.getId(), regionName);
        if (regionOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Region not found: " + regionName));
            return true;
        }

        Subregion region = regionOpt.get();

        // Check if player can modify permissions
        if (!regionPermissionService.canModifyPermissions(region, player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to modify region permissions"));
            return true;
        }

        boolean removed;
        switch (subjectType) {
            case "player" -> {
                Player target = Bukkit.getPlayer(targetIdentifier);
                if (target == null) {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Player not found or not online"));
                    return true;
                }

                removed = regionPermissionService.removePlayerPermission(region.getId(), target.getUniqueId());
                if (removed) {
                    player.sendMessage(MessageFormatter.deserialize(
                            "<green>Removed permissions for <gold>" + target.getName() + "</gold> from region <gold>" + regionName + "</gold></green>"));
                } else {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "No permissions found for that player"));
                }
            }
            case "role" -> {
                removed = regionPermissionService.removeRolePermission(region.getId(), targetIdentifier);
                if (removed) {
                    player.sendMessage(MessageFormatter.deserialize(
                            "<green>Removed permissions for role <gold>" + targetIdentifier + "</gold> from region <gold>" + regionName + "</gold></green>"));
                } else {
                    player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "No permissions found for that role"));
                }
            }
            default -> {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Subject type must be 'player' or 'role'"));
                return true;
            }
        }

        return true;
    }

    private boolean handleListPerms(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region listperms <region>"));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        String regionName = args[2];

        Optional<Subregion> regionOpt = subregionService.getSubregionByName(guild.getId(), regionName);
        if (regionOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Region not found: " + regionName));
            return true;
        }

        Subregion region = regionOpt.get();
        List<RegionPermission> permissions = regionPermissionService.getRegionPermissions(region.getId());

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Permissions for " + regionName, ""));

        if (region.getPermissions() != 0) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Default", String.valueOf(region.getPermissions())));
        }

        List<RegionPermission> playerPerms = regionPermissionService.getPlayerPermissions(region.getId());
        if (!playerPerms.isEmpty()) {
            player.sendMessage(MessageFormatter.deserialize("<yellow>Player Permissions:</yellow>"));
            for (RegionPermission perm : playerPerms) {
                String playerName = Bukkit.getOfflinePlayer(UUID.fromString(perm.getSubjectId())).getName();
                player.sendMessage(MessageFormatter.deserialize(
                        "<gray>  • <gold>" + (playerName != null ? playerName : perm.getSubjectId()) + "</gold>: " + perm.getPermissions() + "</gray>"));
            }
        }

        List<RegionPermission> rolePerms = regionPermissionService.getRolePermissions(region.getId());
        if (!rolePerms.isEmpty()) {
            player.sendMessage(MessageFormatter.deserialize("<yellow>Role Permissions:</yellow>"));
            for (RegionPermission perm : rolePerms) {
                player.sendMessage(MessageFormatter.deserialize(
                        "<gray>  • <gold>" + perm.getSubjectId() + "</gold>: " + perm.getPermissions() + "</gray>"));
            }
        }

        if (permissions.isEmpty() && region.getPermissions() == 0) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "No custom permissions set"));
        }

        return true;
    }

    private boolean handleRole(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region role <create|delete|list|assign|unassign|members> ..."));
            return true;
        }

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        String roleSubCommand = args[2].toLowerCase();

        return switch (roleSubCommand) {
            case "create" -> handleRoleCreate(player, guild, args);
            case "delete" -> handleRoleDelete(player, guild, args);
            case "list" -> handleRoleList(player, guild, args);
            case "assign" -> handleRoleAssign(player, guild, args);
            case "unassign" -> handleRoleUnassign(player, guild, args);
            case "members" -> handleRoleMembers(player, guild, args);
            default -> {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Unknown role subcommand: " + roleSubCommand));
                yield true;
            }
        };
    }

    private boolean handleRoleCreate(Player player, Guild guild, String[] args) {
        // /g region role create <region> <name> <permissions>
        if (args.length < 6) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region role create <region> <name> <permissions>"));
            return true;
        }

        String regionName = args[3];
        String roleName = args[4];
        String permString = args[5];

        Optional<Subregion> regionOpt = subregionService.getSubregionByName(guild.getId(), regionName);
        if (regionOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Region not found: " + regionName));
            return true;
        }

        Subregion region = regionOpt.get();

        if (!regionPermissionService.canModifyPermissions(region, player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to modify region roles"));
            return true;
        }

        int permissions;
        try {
            permissions = Integer.parseInt(permString);
        } catch (NumberFormatException e) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid permission value. Use an integer (e.g., " + GuildPermission.BUILD.getBit() + " for BUILD)"));
            return true;
        }

        RegionRole role = regionPermissionService.createRegionRole(region.getId(), roleName, permissions, player.getUniqueId());
        if (role == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "A role with that name already exists in this region"));
            return true;
        }

        player.sendMessage(MessageFormatter.deserialize(
                "<green>Created region role <gold>" + roleName + "</gold> with permissions <yellow>" + permissions + "</yellow></green>"));
        return true;
    }

    private boolean handleRoleDelete(Player player, Guild guild, String[] args) {
        // /g region role delete <region> <name>
        if (args.length < 5) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region role delete <region> <name>"));
            return true;
        }

        String regionName = args[3];
        String roleName = args[4];

        Optional<Subregion> regionOpt = subregionService.getSubregionByName(guild.getId(), regionName);
        if (regionOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Region not found: " + regionName));
            return true;
        }

        Subregion region = regionOpt.get();

        if (!regionPermissionService.canModifyPermissions(region, player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to modify region roles"));
            return true;
        }

        if (regionPermissionService.deleteRegionRole(region.getId(), roleName)) {
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Deleted region role <gold>" + roleName + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + roleName));
        }

        return true;
    }

    private boolean handleRoleList(Player player, Guild guild, String[] args) {
        // /g region role list <region>
        if (args.length < 4) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region role list <region>"));
            return true;
        }

        String regionName = args[3];

        Optional<Subregion> regionOpt = subregionService.getSubregionByName(guild.getId(), regionName);
        if (regionOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Region not found: " + regionName));
            return true;
        }

        Subregion region = regionOpt.get();
        List<RegionRole> roles = regionPermissionService.getRegionRoles(region.getId());

        if (roles.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "No roles defined for this region"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Roles for " + regionName, " (" + roles.size() + ")"));
        for (RegionRole role : roles) {
            int memberCount = regionPermissionService.getMembersWithRegionRole(region.getId(), role.getName()).size();
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>• <gold>" + role.getName() + "</gold> - perms: " + role.getPermissions() + ", members: " + memberCount + "</gray>"));
        }

        return true;
    }

    private boolean handleRoleAssign(Player player, Guild guild, String[] args) {
        // /g region role assign <region> <role> <player>
        if (args.length < 6) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region role assign <region> <role> <player>"));
            return true;
        }

        String regionName = args[3];
        String roleName = args[4];
        String targetName = args[5];

        Optional<Subregion> regionOpt = subregionService.getSubregionByName(guild.getId(), regionName);
        if (regionOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Region not found: " + regionName));
            return true;
        }

        Subregion region = regionOpt.get();

        if (!regionPermissionService.canModifyPermissions(region, player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to modify region roles"));
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Player not found or not online"));
            return true;
        }

        if (regionPermissionService.assignRegionRole(region.getId(), roleName, target.getUniqueId())) {
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Assigned <gold>" + target.getName() + "</gold> to role <gold>" + roleName + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + roleName));
        }

        return true;
    }

    private boolean handleRoleUnassign(Player player, Guild guild, String[] args) {
        // /g region role unassign <region> <role> <player>
        if (args.length < 6) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region role unassign <region> <role> <player>"));
            return true;
        }

        String regionName = args[3];
        String roleName = args[4];
        String targetName = args[5];

        Optional<Subregion> regionOpt = subregionService.getSubregionByName(guild.getId(), regionName);
        if (regionOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Region not found: " + regionName));
            return true;
        }

        Subregion region = regionOpt.get();

        if (!regionPermissionService.canModifyPermissions(region, player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to modify region roles"));
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Player not found or not online"));
            return true;
        }

        if (regionPermissionService.unassignRegionRole(region.getId(), roleName, target.getUniqueId())) {
            player.sendMessage(MessageFormatter.deserialize(
                    "<green>Unassigned <gold>" + target.getName() + "</gold> from role <gold>" + roleName + "</gold></green>"));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + roleName));
        }

        return true;
    }

    private boolean handleRoleMembers(Player player, Guild guild, String[] args) {
        // /g region role members <region> <role>
        if (args.length < 5) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g region role members <region> <role>"));
            return true;
        }

        String regionName = args[3];
        String roleName = args[4];

        Optional<Subregion> regionOpt = subregionService.getSubregionByName(guild.getId(), regionName);
        if (regionOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Region not found: " + regionName));
            return true;
        }

        Subregion region = regionOpt.get();

        Optional<RegionRole> roleOpt = regionPermissionService.getRegionRole(region.getId(), roleName);
        if (roleOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Role not found: " + roleName));
            return true;
        }

        List<UUID> members = regionPermissionService.getMembersWithRegionRole(region.getId(), roleName);

        if (members.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING, "No members assigned to role: " + roleName));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Members with " + roleName, " (" + members.size() + ")"));
        for (UUID memberId : members) {
            String memberName = Bukkit.getOfflinePlayer(memberId).getName();
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>• <gold>" + (memberName != null ? memberName : memberId.toString()) + "</gold></gray>"));
        }

        return true;
    }

    private long calculateVolume(Location pos1, Location pos2) {
        int dx = Math.abs(pos2.getBlockX() - pos1.getBlockX()) + 1;
        int dy = Math.abs(pos2.getBlockY() - pos1.getBlockY()) + 1;
        int dz = Math.abs(pos2.getBlockZ() - pos1.getBlockZ()) + 1;
        return (long) dx * dy * dz;
    }
}
