package org.aincraft.towny.commands.brigadier;

import com.google.inject.Inject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.commands.arguments.TownArgumentType;
import org.aincraft.towny.models.Location;
import org.aincraft.towny.models.TownBlock;
import org.aincraft.towny.plot.PlotTypeDefinition;
import org.aincraft.towny.plot.PlotTypeRegistry;
import org.aincraft.towny.services.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Brigadier implementation of the town command
 */
public class TownBrigadierCommand {

    private final TownyPlugin plugin;
    private final ResidentService residentService;
    private final TownService townService;
    private final PlotService plotService;
    private final PermissionService permissionService;
    private final TechTreeBrigadierCommand techTreeCommand;
    private final PlotTypeRegistry plotTypeRegistry;

    @Inject
    public TownBrigadierCommand(TownyPlugin plugin, ResidentService residentService,
                               TownService townService, PlotService plotService,
                               PermissionService permissionService,
                               TechTreeBrigadierCommand techTreeCommand,
                               PlotTypeRegistry plotTypeRegistry) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.townService = townService;
        this.plotService = plotService;
        this.permissionService = permissionService;
        this.techTreeCommand = techTreeCommand;
        this.plotTypeRegistry = plotTypeRegistry;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("town")
            .requires(source -> source.getSender().hasPermission("towny.town"))
            .executes(this::showHelp)
            // Create subcommand
            .then(Commands.literal("create")
                .requires(source -> source.getSender().hasPermission("towny.town.create"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(this::handleCreate)))
            // Join subcommand
            .then(Commands.literal("join")
                .requires(source -> source.getSender().hasPermission("towny.town.join"))
                .then(Commands.argument("town", TownArgumentType.town(townService))
                    .executes(this::handleJoin)))
            // Leave subcommand
            .then(Commands.literal("leave")
                .requires(source -> source.getSender().hasPermission("towny.town.leave"))
                .executes(this::handleLeave))
            // Delete subcommand
            .then(Commands.literal("delete")
                .requires(source -> source.getSender().hasPermission("towny.town.delete"))
                .then(Commands.literal("confirm")
                    .executes(this::handleDeleteConfirm))
                .executes(this::handleDelete))
            // Claim subcommand
            .then(Commands.literal("claim")
                .requires(source -> source.getSender().hasPermission("towny.town.claim"))
                .executes(this::handleClaim))
            // Unclaim subcommand
            .then(Commands.literal("unclaim")
                .requires(source -> source.getSender().hasPermission("towny.town.unclaim"))
                .executes(this::handleUnclaim))
            // List subcommand
            .then(Commands.literal("list")
                .requires(source -> source.getSender().hasPermission("towny.town.list"))
                .executes(this::handleList))
            // Info subcommand
            .then(Commands.literal("info")
                .requires(source -> source.getSender().hasPermission("towny.town.info"))
                .executes(this::handleOwnInfo)
                .then(Commands.argument("town", TownArgumentType.town(townService))
                    .executes(this::handleTownInfo)))
            // Spawn subcommand
            .then(Commands.literal("spawn")
                .requires(source -> source.getSender().hasPermission("towny.town.spawn"))
                .executes(this::handleOwnSpawn)
                .then(Commands.argument("town", TownArgumentType.town(townService))
                    .executes(this::handleTownSpawn)))
            // SetSpawn subcommand
            .then(Commands.literal("setspawn")
                .requires(source -> source.getSender().hasPermission("towny.town.setspawn"))
                .executes(this::handleSetSpawn))
            // Toggle subcommand
            .then(Commands.literal("toggle")
                .requires(source -> source.getSender().hasPermission("towny.town.toggle"))
                .executes(this::showToggleHelp)
                .then(Commands.literal("list")
                    .executes(this::handleToggleList))
                .then(Commands.argument("type", StringArgumentType.word())
                    .executes(this::handleToggle)
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("on");
                            builder.suggest("off");
                            builder.suggest("true");
                            builder.suggest("false");
                            builder.suggest("enable");
                            builder.suggest("disable");
                            return builder.buildFuture();
                        })
                        .executes(this::handleToggleValue))))
            // Tech tree subcommand
            .then(techTreeCommand.buildCommand())
            .build();
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§6╔══════════════════════════════════════════════╗");
        sender.sendMessage("§6║          §e§lTOWN COMMANDS§r§6                    ║");
        sender.sendMessage("§6╠══════════════════════════════════════════════╣");
        sender.sendMessage("§6║ §f/town create §7<name>                       §6║");
        sender.sendMessage("§6║   §8» Create a new town                      §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/town join §7<town>                         §6║");
        sender.sendMessage("§6║   §8» Join an existing town                  §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/town leave                                §6║");
        sender.sendMessage("§6║   §8» Leave your current town                §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/town claim                                §6║");
        sender.sendMessage("§6║   §8» Claim the chunk you're in             §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/town unclaim                              §6║");
        sender.sendMessage("§6║   §8» Unclaim the chunk you're in           §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/town spawn §7[town]                        §6║");
        sender.sendMessage("§6║   §8» Teleport to town spawn                §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/town info §7[town]                         §6║");
        sender.sendMessage("§6║   §8» Show town information                  §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/town list                                 §6║");
        sender.sendMessage("§6║   §8» List all towns                         §6║");
        sender.sendMessage("§6╚══════════════════════════════════════════════╝");
        return Command.SINGLE_SUCCESS;
    }

    private int handleCreate(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String townName = StringArgumentType.getString(ctx, "name");
        UUID playerUuid = player.getUniqueId();

        // Check if player already has a town
        var resident = residentService.getResident(playerUuid);
        if (resident.isPresent() && resident.get().hasTown()) {
            player.sendMessage("§cYou are already in a town: " + resident.get().getTown());
            return 0;
        }

        // Check if town already exists
        if (townService.townExists(townName)) {
            player.sendMessage("§cA town with that name already exists!");
            return 0;
        }

        // Validate town name
        if (townName.length() < 3 || townName.length() > 20) {
            player.sendMessage("§cTown name must be between 3 and 20 characters!");
            return 0;
        }

        try {
            // Ensure resident exists before creating town
            if (!residentService.residentExists(playerUuid)) {
                residentService.createResident(playerUuid, player.getName());
                plugin.getLogger().info("Created resident record for player: " + player.getName() + " (" + playerUuid + ")");
            }

            // Get player's current location for home block
            org.bukkit.Location bukkitLocation = player.getLocation();
            Location homeBlockLocation = new Location(
                bukkitLocation.getX(),
                bukkitLocation.getY(),
                bukkitLocation.getZ(),
                bukkitLocation.getYaw(),
                bukkitLocation.getPitch(),
                bukkitLocation.getWorld().getName()
            );

            // Create town with home block at player's current location
            townService.createTown(townName, playerUuid, homeBlockLocation);

            // Get chunk coordinates for display and auto-claim
            int[] chunkCoords = homeBlockLocation.getChunkCoordinates();
            org.bukkit.Chunk chunk = player.getLocation().getChunk();
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();
            String world = player.getWorld().getName();

            // Auto-claim the home block chunk
            try {
                plotService.claimTownBlock(chunkX, chunkZ, world, townName);
                player.sendMessage("§aSuccessfully created town: §e" + townName);
                player.sendMessage("§aYou are now the mayor of §e" + townName);
                player.sendMessage("§7Home block set at chunk [" + chunkCoords[0] + ", " + chunkCoords[1] + "] and automatically claimed!");
                player.sendMessage("§7Town spawn automatically set at your current location");
            } catch (Exception claimError) {
                player.sendMessage("§aSuccessfully created town: §e" + townName);
                player.sendMessage("§aYou are now the mayor of §e" + townName);
                player.sendMessage("§7Home block set at chunk [" + chunkCoords[0] + ", " + chunkCoords[1] + "]");
                player.sendMessage("§7Town spawn automatically set at your current location");
                player.sendMessage("§eWarning: Could not auto-claim home block chunk: " + claimError.getMessage());
            }
        } catch (Exception e) {
            player.sendMessage("§cFailed to create town: " + e.getMessage());
            plugin.getLogger().warning("Failed to create town " + townName + " for player " + player.getName() + ": " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleJoin(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String townName = TownArgumentType.getTownName(ctx, "town");
        UUID playerUuid = player.getUniqueId();

        // Check if player already has a town
        var resident = residentService.getResident(playerUuid);
        if (resident.isPresent() && resident.get().hasTown()) {
            player.sendMessage("§cYou are already in a town: " + resident.get().getTown());
            return 0;
        }

        try {
            boolean success = townService.addResidentToTown(townName, playerUuid);
            if (success) {
                player.sendMessage("§aSuccessfully joined town: §e" + townName);
            } else {
                player.sendMessage("§cFailed to join town. It may be full or closed.");
            }
        } catch (Exception e) {
            player.sendMessage("§cFailed to join town: " + e.getMessage());
            plugin.getLogger().warning("Failed to join town " + townName + " for player " + player.getName() + ": " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleLeave(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();

        if (residentService.getResident(playerUuid).isPresent()) {
            var resident = residentService.getResident(playerUuid).get();
            if (!resident.hasTown()) {
                player.sendMessage("§cYou are not in a town!");
                return 0;
            }

            String townName = resident.getTown();

            // Check if player is the mayor
            if (permissionService.hasTownAdmin(playerUuid, townName)) {
                player.sendMessage("§cYou cannot leave your town while you are the mayor! Set a new mayor first.");
                return 0;
            }

            try {
                boolean success = townService.removeResidentFromTown(townName, playerUuid);
                if (success) {
                    player.sendMessage("§aYou have left town: §e" + townName);
                } else {
                    player.sendMessage("§cFailed to leave town.");
                }
            } catch (Exception e) {
                player.sendMessage("§cFailed to leave town: " + e.getMessage());
                plugin.getLogger().warning("Failed to leave town for player " + player.getName() + ": " + e.getMessage());
            }
        } else {
            player.sendMessage("§cYour resident data could not be loaded!");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleDelete(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasTown()) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        String townName = resident.getTown();

        // Check if player is the mayor
        if (!permissionService.hasTownAdmin(playerUuid, townName)) {
            player.sendMessage("§cOnly the mayor can delete the town!");
            return 0;
        }

        // Get town info for confirmation
        var townOpt = townService.getTown(townName);
        if (townOpt.isEmpty()) {
            player.sendMessage("§cFailed to load town data!");
            return 0;
        }

        var town = townOpt.get();
        int claimCount = plotService.getTownBlockCount(townName);

        player.sendMessage("§cAre you sure you want to delete §e" + townName + "§c?");
        player.sendMessage("§eThis action cannot be undone!");
        player.sendMessage("§7Town has " + town.getResidentCount() + " resident(s) and a balance of $" + String.format("%.2f", town.getBalance()));
        player.sendMessage("§7Town has " + claimCount + " claimed chunk(s) that will be unclaimed");
        player.sendMessage("§aType §f/town delete confirm §ato confirm deletion.");

        return Command.SINGLE_SUCCESS;
    }

    private int handleDeleteConfirm(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasTown()) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        String townName = resident.getTown();

        // Check if player is the mayor
        if (!permissionService.hasTownAdmin(playerUuid, townName)) {
            player.sendMessage("§cOnly the mayor can delete the town!");
            return 0;
        }

        int claimCount = plotService.getTownBlockCount(townName);

        // Delete the town
        try {
            boolean success = townService.deleteTown(townName);
            if (success) {
                player.sendMessage("§aTown §e" + townName + " §ahas been deleted!");
                player.sendMessage("§7All residents have been removed and " + claimCount + " chunk(s) have been unclaimed.");
            } else {
                player.sendMessage("§cFailed to delete town!");
            }
        } catch (Exception e) {
            player.sendMessage("§cFailed to delete town: " + e.getMessage());
            plugin.getLogger().warning("Failed to delete town " + townName + " for player " + player.getName() + ": " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleClaim(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasTown()) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        String townName = resident.getTown();

        // Check if player has permission to claim
        if (!permissionService.hasPermission(playerUuid, "claim", "town", townName)) {
            player.sendMessage("§cYou don't have permission to claim land for your town!");
            return 0;
        }

        // Get the chunk player is standing in
        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        String world = player.getWorld().getName();

        // Check if chunk is already claimed
        if (plotService.townBlockExists(chunkX, chunkZ, world)) {
            player.sendMessage("§cThis chunk is already claimed!");
            return 0;
        }

        // Check if town has reached its claim limit
        int currentClaims = plotService.getTownBlockCount(townName);
        var townOpt = townService.getTown(townName);
        if (townOpt.isPresent()) {
            var levelData = townOpt.get().getLevelData();
            int maxClaims = levelData.getMaxClaimLimit();
            if (levelData.isAtClaimLimit(currentClaims)) {
                player.sendMessage("§cYour town has reached its claim limit! §7(" + currentClaims + "/" + maxClaims + " chunks)");
                player.sendMessage("§7Level up your town to increase the claim limit.");
                return 0;
            }
        }

        // Check if this claim is adjacent to an existing town claim
        if (!isAdjacentToTownClaim(chunkX, chunkZ, world, townName)) {
            player.sendMessage("§cClaims must be adjacent to your existing town chunks!");
            player.sendMessage("§7You can only claim chunks that touch your town's territory.");
            return 0;
        }

        // Claim the chunk
        try {
            boolean success = plotService.claimTownBlock(chunkX, chunkZ, world, townName);
            if (success) {
                player.sendMessage("§aSuccessfully claimed chunk [" + chunkX + ", " + chunkZ + "] for §e" + townName + "§a!");
                plugin.getLogger().info("Player " + player.getName() + " claimed chunk [" + chunkX + ", " + chunkZ + "] for town " + townName);
            } else {
                player.sendMessage("§cFailed to claim chunk!");
            }
        } catch (Exception e) {
            player.sendMessage("§cFailed to claim chunk: " + e.getMessage());
            plugin.getLogger().warning("Failed to claim chunk for player " + player.getName() + ": " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleUnclaim(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasTown()) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        String townName = resident.getTown();

        // Check if player has permission to unclaim
        if (!permissionService.hasPermission(playerUuid, "unclaim", "town", townName)) {
            player.sendMessage("§cYou don't have permission to unclaim land for your town!");
            return 0;
        }

        // Get the chunk player is standing in
        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        String world = player.getWorld().getName();

        // Check if chunk is claimed by this town
        var townBlock = plotService.getTownBlock(chunkX, chunkZ, world);
        if (townBlock.isEmpty()) {
            player.sendMessage("§cThis chunk is not claimed!");
            return 0;
        }

        // Get the town that owns this chunk
        var blockTown = townService.getTownById(townBlock.get().getTownId());
        if (blockTown.isEmpty() || !blockTown.get().getName().equals(townName)) {
            player.sendMessage("§cThis chunk doesn't belong to your town!");
            return 0;
        }

        // Unclaim the chunk
        try {
            boolean success = plotService.unclaimTownBlock(chunkX, chunkZ, world);
            if (success) {
                player.sendMessage("§aSuccessfully unclaimed chunk [" + chunkX + ", " + chunkZ + "]!");
                plugin.getLogger().info("Player " + player.getName() + " unclaimed chunk [" + chunkX + ", " + chunkZ + "] from town " + townName);
            } else {
                player.sendMessage("§cFailed to unclaim chunk!");
            }
        } catch (Exception e) {
            player.sendMessage("§cFailed to unclaim chunk: " + e.getMessage());
            plugin.getLogger().warning("Failed to unclaim chunk for player " + player.getName() + ": " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleList(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var towns = townService.getAllTowns();

        if (towns.isEmpty()) {
            sender.sendMessage("§eThere are no towns yet.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§e=== Towns (" + towns.size() + ") ===");
        for (int i = 0; i < Math.min(towns.size(), 10); i++) {
            var town = towns.get(i);
            int residentCount = townService.getTownResidentCount(town.getName());

            sender.sendMessage("§f" + (i + 1) + ". §a" + town.getName() + " §7(" + residentCount + " residents)");
        }

        if (towns.size() > 10) {
            sender.sendMessage("§7And " + (towns.size() - 10) + " more...");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleOwnInfo(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();

        if (residentService.getResident(playerUuid).isPresent()) {
            var resident = residentService.getResident(playerUuid).get();
            if (!resident.hasTown()) {
                player.sendMessage("§cYou are not in a town!");
                return Command.SINGLE_SUCCESS;
            }

            String townName = resident.getTown();
            if (townService.getTown(townName).isPresent()) {
                var town = townService.getTown(townName).get();
                player.sendMessage("§e=== " + townName + " ===");
                player.sendMessage("§fMayor: §a" + town.getMayorUuid());
                player.sendMessage("§fResidents: §a" + town.getResidentCount());
                player.sendMessage("§fBalance: §6$" + String.format("%.2f", town.getBalance()));
                player.sendMessage("§fOpen: " + (town.isOpen() ? "§aYes" : "§cNo"));
                sendPlotTypeBreakdown(player, townName);
            } else {
                player.sendMessage("§cTown information could not be loaded.");
            }
        } else {
            player.sendMessage("§cYour resident data could not be loaded!");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleTownInfo(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        String townName = TownArgumentType.getTownName(ctx, "town");

        if (townService.getTown(townName).isPresent()) {
            var town = townService.getTown(townName).get();
            sender.sendMessage("§e=== " + townName + " ===");
            sender.sendMessage("§fMayor: §a" + town.getMayorUuid());
            sender.sendMessage("§fResidents: §a" + town.getResidentCount());
            sender.sendMessage("§fBalance: §6$" + String.format("%.2f", town.getBalance()));
            sender.sendMessage("§fOpen: " + (town.isOpen() ? "§aYes" : "§cNo"));
            sendPlotTypeBreakdown(sender, townName);
        } else {
            sender.sendMessage("§cTown information could not be loaded.");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleOwnSpawn(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();

        if (residentService.getResident(playerUuid).isPresent()) {
            var resident = residentService.getResident(playerUuid).get();
            if (!resident.hasTown()) {
                player.sendMessage("§cYou are not in a town!");
                return 0;
            }

            String townName = resident.getTown();

            // Check if player can teleport to this town's spawn
            if (!townService.canTeleportToSpawn(playerUuid, townName)) {
                player.sendMessage("§cYou cannot teleport to " + townName + "'s spawn!");
                return 0;
            }

            // Get spawn location
            var spawnLocation = townService.getTownSpawn(townName);
            if (spawnLocation.isEmpty()) {
                player.sendMessage("§cTown " + townName + " does not have a spawn point set!");
                return 0;
            }

            // Convert our Location to Bukkit Location
            Location townSpawn = spawnLocation.get();
            org.bukkit.Location bukkitLocation = new org.bukkit.Location(
                org.bukkit.Bukkit.getWorld(townSpawn.getWorld()),
                townSpawn.getX(),
                townSpawn.getY(),
                townSpawn.getZ(),
                townSpawn.getYaw(),
                townSpawn.getPitch()
            );

            // Teleport player
            player.teleport(bukkitLocation);
            player.sendMessage("§aTeleported to §e" + townName + " §aspawn!");

        } else {
            player.sendMessage("§cYour resident data could not be loaded!");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleTownSpawn(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String townName = TownArgumentType.getTownName(ctx, "town");
        UUID playerUuid = player.getUniqueId();

        // Check if player can teleport to this town's spawn
        if (!townService.canTeleportToSpawn(playerUuid, townName)) {
            player.sendMessage("§cYou cannot teleport to " + townName + "'s spawn!");
            return 0;
        }

        // Get spawn location
        var spawnLocation = townService.getTownSpawn(townName);
        if (spawnLocation.isEmpty()) {
            player.sendMessage("§cTown " + townName + " does not have a spawn point set!");
            return 0;
        }

        // Convert our Location to Bukkit Location
        Location townSpawn = spawnLocation.get();
        org.bukkit.Location bukkitLocation = new org.bukkit.Location(
            org.bukkit.Bukkit.getWorld(townSpawn.getWorld()),
            townSpawn.getX(),
            townSpawn.getY(),
            townSpawn.getZ(),
            townSpawn.getYaw(),
            townSpawn.getPitch()
        );

        // Teleport player
        player.teleport(bukkitLocation);
        player.sendMessage("§aTeleported to §e" + townName + " §aspawn!");

        return Command.SINGLE_SUCCESS;
    }

    private int handleSetSpawn(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasTown()) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        String townName = resident.getTown();

        // Check if player has permission to set spawn
        if (!permissionService.hasPermission(playerUuid, "set_spawn", "town", townName)) {
            player.sendMessage("§cYou don't have permission to set the town spawn!");
            return 0;
        }

        // Get the town to check home block
        var townOpt = townService.getTown(townName);
        if (townOpt.isEmpty()) {
            player.sendMessage("§cFailed to load town data!");
            return 0;
        }

        var town = townOpt.get();
        if (town.getHomeBlock() == null) {
            player.sendMessage("§cYour town does not have a home block set!");
            player.sendMessage("§7A home block must be set before setting a spawn.");
            return 0;
        }

        // Get player's current location
        org.bukkit.Location bukkitLocation = player.getLocation();
        Location townSpawn = new Location(
            bukkitLocation.getX(),
            bukkitLocation.getY(),
            bukkitLocation.getZ(),
            bukkitLocation.getYaw(),
            bukkitLocation.getPitch(),
            bukkitLocation.getWorld().getName()
        );

        // Check if player is in the home block chunk
        int[] spawnChunk = townSpawn.getChunkCoordinates();
        int[] homeBlockChunk = town.getHomeBlock().getChunkCoordinates();

        if (spawnChunk[0] != homeBlockChunk[0] || spawnChunk[1] != homeBlockChunk[1]) {
            player.sendMessage("§cYou must be in your town's home block chunk to set the spawn!");
            player.sendMessage("§7Your chunk: [" + spawnChunk[0] + ", " + spawnChunk[1] + "]");
            player.sendMessage("§7Home block chunk: [" + homeBlockChunk[0] + ", " + homeBlockChunk[1] + "]");
            return 0;
        }

        // Check world matches
        if (!townSpawn.getWorld().equals(town.getHomeBlock().getWorld())) {
            player.sendMessage("§cYou must be in the same world as your town's home block!");
            return 0;
        }

        // Set the town spawn
        if (townService.setTownSpawn(townName, townSpawn)) {
            player.sendMessage("§aTown spawn set for §e" + townName + "§a!");
            player.sendMessage("§7Spawn location: " + townSpawn.toDisplayString());
        } else {
            player.sendMessage("§cFailed to set town spawn!");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int showToggleHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Town Toggle Commands ===");
        sender.sendMessage("§7/town toggle list§f - Show current toggle states");
        sender.sendMessage("§7/town toggle <type>§f - Toggle a setting");
        sender.sendMessage("§7/town toggle <type> <on|off>§f - Set a setting");
        sender.sendMessage("");
        sender.sendMessage("§7Available toggles:");
        sender.sendMessage("§f  pvp§7 - Player vs Player combat");
        sender.sendMessage("§f  fire§7 - Fire spread");
        sender.sendMessage("§f  explosions§7 - Explosions");
        sender.sendMessage("§f  mobs§7 - Mob spawning");
        sender.sendMessage("§f  public§7 - Public access");
        return Command.SINGLE_SUCCESS;
    }

    private int handleToggleList(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        var resident = residentService.getResident(playerUuid);
        if (resident.isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        if (!resident.get().hasTown()) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        String townName = resident.get().getTown();

        // Check if player has permission to toggle town settings
        if (!permissionService.hasTownAdmin(playerUuid, townName)) {
            player.sendMessage("§cYou don't have permission to toggle town settings!");
            return 0;
        }

        // Show current toggle states
        var toggles = townService.getTownToggles(townName);
        if (toggles.isEmpty()) {
            player.sendMessage("§cFailed to load toggle states!");
            return 0;
        }

        player.sendMessage("§e=== §a" + townName + " §eToggles ===");
        player.sendMessage("§7PvP: " + (toggles.get("pvp") ? "§aENABLED" : "§cDISABLED"));
        player.sendMessage("§7Fire: " + (toggles.get("fire") ? "§aENABLED" : "§cDISABLED"));
        player.sendMessage("§7Explosions: " + (toggles.get("explosions") ? "§aENABLED" : "§cDISABLED"));
        player.sendMessage("§7Mobs: " + (toggles.get("mobs") ? "§aENABLED" : "§cDISABLED"));
        player.sendMessage("§7Public: " + (toggles.get("public") ? "§aENABLED" : "§cDISABLED"));

        return Command.SINGLE_SUCCESS;
    }

    private int handleToggle(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String toggleType = StringArgumentType.getString(ctx, "type");
        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        var resident = residentService.getResident(playerUuid);
        if (resident.isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        if (!resident.get().hasTown()) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        String townName = resident.get().getTown();

        // Check if player has permission to toggle town settings
        if (!permissionService.hasTownAdmin(playerUuid, townName)) {
            player.sendMessage("§cYou don't have permission to toggle town settings!");
            return 0;
        }

        if (!isValidToggleType(toggleType)) {
            player.sendMessage("§cUnknown toggle type: " + toggleType);
            showToggleHelp(ctx);
            return 0;
        }

        boolean success = townService.toggleTownPermission(townName, toggleType, playerUuid);
        if (success) {
            boolean newState = townService.getTownToggle(townName, toggleType);
            String displayName = getToggleDisplayName(toggleType);
            player.sendMessage("§aToggled §e" + displayName + " §a" +
                             (newState ? "§aON" : "§cOFF"));
        } else {
            player.sendMessage("§cFailed to toggle " + toggleType + "!");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleToggleValue(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String toggleType = StringArgumentType.getString(ctx, "type");
        String valueStr = StringArgumentType.getString(ctx, "value").toLowerCase();
        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        var resident = residentService.getResident(playerUuid);
        if (resident.isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        if (!resident.get().hasTown()) {
            player.sendMessage("§cYou are not in a town!");
            return 0;
        }

        String townName = resident.get().getTown();

        // Check if player has permission to toggle town settings
        if (!permissionService.hasTownAdmin(playerUuid, townName)) {
            player.sendMessage("§cYou don't have permission to toggle town settings!");
            return 0;
        }

        if (!isValidToggleType(toggleType)) {
            player.sendMessage("§cUnknown toggle type: " + toggleType);
            showToggleHelp(ctx);
            return 0;
        }

        boolean value;
        if (valueStr.equals("on") || valueStr.equals("true") || valueStr.equals("enable")) {
            value = true;
        } else if (valueStr.equals("off") || valueStr.equals("false") || valueStr.equals("disable")) {
            value = false;
        } else {
            player.sendMessage("§cInvalid value. Use: on/off, true/false, or enable/disable");
            return 0;
        }

        boolean success = townService.setTownToggle(townName, toggleType, value, playerUuid);
        if (success) {
            String displayName = getToggleDisplayName(toggleType);
            player.sendMessage("§aSet §e" + displayName + " §ato " +
                             (value ? "§aON" : "§cOFF"));
        } else {
            player.sendMessage("§cFailed to set " + toggleType + " to " + valueStr + "!");
        }

        return Command.SINGLE_SUCCESS;
    }

    private boolean isValidToggleType(String toggleType) {
        return toggleType.equals("pvp") || toggleType.equals("fire") ||
               toggleType.equals("explosions") || toggleType.equals("mobs") ||
               toggleType.equals("public");
    }

    private String getToggleDisplayName(String toggleType) {
        switch (toggleType.toLowerCase()) {
            case "pvp": return "PvP";
            case "fire": return "Fire Spread";
            case "explosions": return "Explosions";
            case "mobs": return "Mob Spawning";
            case "public": return "Public Access";
            default: return toggleType;
        }
    }

    private boolean isAdjacentToTownClaim(int chunkX, int chunkZ, String world, String townName) {
        // Get all town blocks for this town
        var townBlocks = plotService.getTownBlocksInTown(townName);

        // If town has no claims yet, allow first claim (should only happen if home block wasn't claimed)
        if (townBlocks.isEmpty()) {
            return true;
        }

        // Check all 4 adjacent chunks (N, S, E, W)
        int[][] adjacentOffsets = {
            {0, 1},   // North
            {0, -1},  // South
            {1, 0},   // East
            {-1, 0}   // West
        };

        for (int[] offset : adjacentOffsets) {
            int adjacentX = chunkX + offset[0];
            int adjacentZ = chunkZ + offset[1];

            // Check if there's a town block at this adjacent position
            var adjacentBlock = plotService.getTownBlock(adjacentX, adjacentZ, world);
            if (adjacentBlock.isPresent()) {
                // Check if it belongs to this town
                var blockTown = townService.getTownById(adjacentBlock.get().getTownId());
                if (blockTown.isPresent() && blockTown.get().getName().equals(townName)) {
                    return true; // Found an adjacent claim from this town
                }
            }
        }

        return false; // No adjacent claims found
    }

    private void sendPlotTypeBreakdown(org.bukkit.command.CommandSender sender, String townName) {
        List<TownBlock> blocks = plotService.getTownBlocksInTown(townName);

        if (blocks.isEmpty()) {
            sender.sendMessage("§fPlots: §70 total");
            return;
        }

        // Count plots by type
        Map<String, Long> typeCounts = blocks.stream()
            .collect(Collectors.groupingBy(
                b -> b.getPlotType() != null ? b.getPlotType() : "default",
                Collectors.counting()
            ));

        // Build display with display names from registry
        sender.sendMessage("§fPlots: §7" + blocks.size() + " total");
        typeCounts.forEach((type, count) -> {
            String displayName = plotTypeRegistry.getPlotType(type)
                .map(PlotTypeDefinition::getDisplayName)
                .orElse(type);
            sender.sendMessage("  §8- §f" + displayName + ": §a" + count);
        });
    }
}