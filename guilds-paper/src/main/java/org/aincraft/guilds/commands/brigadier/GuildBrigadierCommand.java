package org.aincraft.guilds.commands.brigadier;


import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.commands.arguments.GuildArgumentType;
import org.aincraft.guilds.commands.arguments.GovernmentFormArgumentType;
import org.aincraft.guilds.GuildsGovernanceSource;
import org.aincraft.guilds.models.Location;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.plot.PlotTypeDefinition;
import org.aincraft.guilds.plot.PlotTypeRegistry;
import org.aincraft.guilds.commands.TopRankings;
import org.aincraft.guilds.services.AllianceService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.GuildLevelService;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.services.GuildProjectService;
import org.aincraft.guilds.services.MintGuildBankService;
import org.aincraft.guilds.services.MintTransferPort;
import org.aincraft.guilds.storage.StorageFacilityOpener;
import de.flog99.mapgui.MapGui;
import java.math.BigDecimal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Brigadier implementation of the guild command
 */
public class GuildBrigadierCommand {

    private final JavaPlugin plugin;
    private final ResidentService residentService;
    private final GuildService guildService;
    private final PlotService plotService;
    private final PermissionService permissionService;
    private final TechTreeBrigadierCommand techTreeCommand;
    private final MapBrigadierCommand mapCommand;
    private final PlotTypeRegistry plotTypeRegistry;
    private final GuildsGovernanceSource governanceSource;
    private final AllianceService allianceService;
    private final GuildLevelService guildLevelService;
    private final TechTreeService techTreeService;
    private final GuildProjectService guildProjectService;
    private volatile MintGuildBankService mintGuildBankService;
    private volatile StorageFacilityOpener storageFacilityOpener;

    public GuildBrigadierCommand(JavaPlugin plugin, ResidentService residentService,
                               GuildService guildService, PlotService plotService,
                               PermissionService permissionService,
                               TechTreeBrigadierCommand techTreeCommand,
                               MapBrigadierCommand mapCommand,
                               PlotTypeRegistry plotTypeRegistry,
                               GuildsGovernanceSource governanceSource,
                               AllianceService allianceService,
                               GuildLevelService guildLevelService,
                               TechTreeService techTreeService,
                               GuildProjectService guildProjectService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.guildService = guildService;
        this.plotService = plotService;
        this.permissionService = permissionService;
        this.techTreeCommand = techTreeCommand;
        this.mapCommand = mapCommand;
        this.plotTypeRegistry = plotTypeRegistry;
        this.governanceSource = governanceSource;
        this.allianceService = allianceService;
        this.guildLevelService = guildLevelService;
        this.techTreeService = techTreeService;
        this.guildProjectService = guildProjectService;
    }

    public void setMintGuildBankService(MintGuildBankService bank) {
        this.mintGuildBankService = bank;
    }

    public void setStorageFacilityOpener(StorageFacilityOpener opener) {
        this.storageFacilityOpener = opener;
    }


    public LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("guild")
            .requires(source -> source.getSender().hasPermission("guilds.guild"))
            .executes(this::showHelp)
            // Create subcommand
            .then(Commands.literal("create")
                .requires(source -> source.getSender().hasPermission("guilds.guild.create"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(this::handleCreate)))
            // `new` mirrors create for the familiar `/g new` flow.
            .then(Commands.literal("new")
                .requires(source -> source.getSender().hasPermission("guilds.guild.create"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(this::handleCreate)))
            // Join subcommand
            .then(Commands.literal("join")
                .requires(source -> source.getSender().hasPermission("guilds.guild.join"))
                .then(Commands.argument("guild", GuildArgumentType.guild(guildService))
                    .executes(this::handleJoin)))
            // Leave subcommand
            .then(Commands.literal("leave")
                .requires(source -> source.getSender().hasPermission("guilds.guild.leave"))
                .executes(this::handleLeave))
            // Delete subcommand
            .then(Commands.literal("delete")
                .requires(source -> source.getSender().hasPermission("guilds.guild.delete"))
                .then(Commands.literal("confirm")
                    .executes(this::handleDeleteConfirm))
                .executes(this::handleDelete))
            // Claim subcommand
            .then(Commands.literal("claim")
                .requires(source -> source.getSender().hasPermission("guilds.guild.claim"))
                .executes(this::handleClaim))
            // Unclaim subcommand
            .then(Commands.literal("unclaim")
                .requires(source -> source.getSender().hasPermission("guilds.guild.unclaim"))
                .executes(this::handleUnclaim))
            // List subcommand
            .then(Commands.literal("list")
                .requires(source -> source.getSender().hasPermission("guilds.guild.list"))
                .executes(this::handleList))
            .then(Commands.literal("top")
                .executes(this::showTopHelp)
                .then(Commands.literal("residents")
                    .executes(this::handleTopResidents))
                .then(Commands.literal("guilds")
                    .executes(this::handleTopGuilds))
                .then(Commands.literal("land")
                    .executes(this::handleTopLand))
                .then(Commands.literal("alliances")
                    .executes(this::handleTopAlliances)))
            // Info subcommand
            .then(Commands.literal("info")
                .requires(source -> source.getSender().hasPermission("guilds.guild.info"))
                .executes(this::handleOwnInfo)
                .then(Commands.argument("guild", GuildArgumentType.guild(guildService))
                    .executes(this::handleGuildInfo)))
            // Spawn subcommand
            .then(Commands.literal("spawn")
                .requires(source -> source.getSender().hasPermission("guilds.guild.spawn"))
                .executes(this::handleOwnSpawn)
                .then(Commands.argument("guild", GuildArgumentType.guild(guildService))
                    .executes(this::handleGuildSpawn)))
            // SetSpawn subcommand
            .then(Commands.literal("setspawn")
                .requires(source -> source.getSender().hasPermission("guilds.guild.setspawn"))
                .executes(this::handleSetSpawn))
            // Toggle subcommand
            .then(Commands.literal("toggle")
                .requires(source -> source.getSender().hasPermission("guilds.guild.toggle"))
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
            // Mint-backed cash guild bank; SQL Guild.balance remains separate.
            .then(Commands.literal("bank")
                .executes(this::handleBankBalance)
                .then(Commands.literal("open")
                    .executes(this::handleBankOpen))
                .then(Commands.literal("deposit")
                    .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(ctx -> handleBankTransfer(ctx, true))))
                .then(Commands.literal("withdraw")
                    .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(ctx -> handleBankTransfer(ctx, false)))))
            .then(Commands.literal("storage")
                .executes(this::handleStorage))
            .then(mapCommand.buildCommand())
            // "upgrade" is now the MapGUI upgrade screen — /g upgrade → GuildUpgradeScreen (MapGUI 2.0.0)
            .then(Commands.literal("upgrade")
                .requires(source -> source.getSender().hasPermission("guilds.techtree"))
                .executes(this::handleUpgradeMap))
            // Government subcommand: the guild (guild) picks its governance form
            .then(Commands.literal("government")
                .then(Commands.argument("form", GovernmentFormArgumentType.form())
                    .executes(this::handleGovernment)))
            .build();
    }

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§6╔══════════════════════════════════════════════╗");
        sender.sendMessage("§6║          §e§lGUILD COMMANDS§r§6                    ║");
        sender.sendMessage("§6╠══════════════════════════════════════════════╣");
        sender.sendMessage("§6║ §f/guild create §7<name>                       §6║");
        sender.sendMessage("§6║   §8» Create a new guild                      §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/guild join §7<guild>                         §6║");
        sender.sendMessage("§6║   §8» Join an existing guild                  §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/guild leave                                §6║");
        sender.sendMessage("§6║   §8» Leave your current guild                §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/guild claim                                §6║");
        sender.sendMessage("§6║   §8» Claim the chunk you're in             §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/guild unclaim                              §6║");
        sender.sendMessage("§6║   §8» Unclaim the chunk you're in           §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/guild spawn §7[guild]                        §6║");
        sender.sendMessage("§6║   §8» Teleport to guild spawn                §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/guild info §7[guild]                         §6║");
        sender.sendMessage("§6║   §8» Show guild information                  §6║");
        sender.sendMessage("§6║                                              §6║");
        sender.sendMessage("§6║ §f/guild list                                 §6║");
        sender.sendMessage("§6║   §8» List all guilds                         §6║");
        sender.sendMessage("§6╚══════════════════════════════════════════════╝");
        return Command.SINGLE_SUCCESS;
    }

    private int handleCreate(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String guildName = StringArgumentType.getString(ctx, "name");
        UUID playerUuid = player.getUniqueId();

        // Check if player already has a guild
        var resident = residentService.getResident(playerUuid);
        if (resident.isPresent() && resident.get().hasGuild()) {
            player.sendMessage("§cYou are already in a guild: " + resident.get().getGuild());
            return 0;
        }

        // Check if guild already exists
        if (guildService.guildExists(guildName)) {
            player.sendMessage("§cA guild with that name already exists!");
            return 0;
        }

        // Validate guild name
        if (guildName.length() < 3 || guildName.length() > 20) {
            player.sendMessage("§cGuild name must be between 3 and 20 characters!");
            return 0;
        }

        try {
            // Ensure resident exists before creating guild
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

            // Create guild with home block at player's current location
            guildService.createGuild(guildName, playerUuid, homeBlockLocation);

            // Get chunk coordinates for display and auto-claim
            int[] chunkCoords = homeBlockLocation.getChunkCoordinates();
            org.bukkit.Chunk chunk = player.getLocation().getChunk();
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();
            String world = player.getWorld().getName();

            // Auto-claim the home block chunk
            try {
                plotService.claimGuildBlock(chunkX, chunkZ, world, guildName);
                player.sendMessage("§aSuccessfully created guild: §e" + guildName);
                player.sendMessage("§aYou are now the mayor of §e" + guildName);
                player.sendMessage("§7Home block set at chunk [" + chunkCoords[0] + ", " + chunkCoords[1] + "] and automatically claimed!");
                player.sendMessage("§7Guild spawn automatically set at your current location");
            } catch (Exception claimError) {
                player.sendMessage("§aSuccessfully created guild: §e" + guildName);
                player.sendMessage("§aYou are now the mayor of §e" + guildName);
                player.sendMessage("§7Home block set at chunk [" + chunkCoords[0] + ", " + chunkCoords[1] + "]");
                player.sendMessage("§7Guild spawn automatically set at your current location");
                player.sendMessage("§eWarning: Could not auto-claim home block chunk: " + claimError.getMessage());
            }
        } catch (Exception e) {
            player.sendMessage("§cFailed to create guild: " + e.getMessage());
            plugin.getLogger().warning("Failed to create guild " + guildName + " for player " + player.getName() + ": " + e.getMessage());
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleJoin(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String guildName = GuildArgumentType.getGuildName(ctx, "guild");
        UUID playerUuid = player.getUniqueId();

        // Check if player already has a guild
        var resident = residentService.getResident(playerUuid);
        if (resident.isPresent() && resident.get().hasGuild()) {
            player.sendMessage("§cYou are already in a guild: " + resident.get().getGuild());
            return 0;
        }

        try {
            boolean success = guildService.addResidentToGuild(guildName, playerUuid);
            if (success) {
                player.sendMessage("§aSuccessfully joined guild: §e" + guildName);
            } else {
                player.sendMessage("§cFailed to join guild. It may be full or closed.");
            }
        } catch (Exception e) {
            player.sendMessage("§cFailed to join guild: " + e.getMessage());
            plugin.getLogger().warning("Failed to join guild " + guildName + " for player " + player.getName() + ": " + e.getMessage());
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
            if (!resident.hasGuild()) {
                player.sendMessage("§cYou are not in a guild!");
                return 0;
            }

            String guildName = resident.getGuild();

            // Check if player is the mayor
            if (permissionService.hasGuildAdmin(playerUuid, guildName)) {
                player.sendMessage("§cYou cannot leave your guild while you are the mayor! Set a new mayor first.");
                return 0;
            }

            try {
                boolean success = guildService.removeResidentFromGuild(guildName, playerUuid);
                if (success) {
                    player.sendMessage("§aYou have left guild: §e" + guildName);
                } else {
                    player.sendMessage("§cFailed to leave guild.");
                }
            } catch (Exception e) {
                player.sendMessage("§cFailed to leave guild: " + e.getMessage());
                plugin.getLogger().warning("Failed to leave guild for player " + player.getName() + ": " + e.getMessage());
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

        // Check if player is in a guild
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasGuild()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        String guildName = resident.getGuild();

        // Check if player is the mayor
        if (!permissionService.hasGuildAdmin(playerUuid, guildName)) {
            player.sendMessage("§cOnly the mayor can delete the guild!");
            return 0;
        }

        // Get guild info for confirmation
        var guildOpt = guildService.getGuild(guildName);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cFailed to load guild data!");
            return 0;
        }

        var guild = guildOpt.get();
        int claimCount = plotService.getGuildBlockCount(guildName);

        player.sendMessage("§cAre you sure you want to delete §e" + guildName + "§c?");
        player.sendMessage("§eThis action cannot be undone!");
        player.sendMessage("§7Guild has " + guild.getResidentCount() + " resident(s) and a balance of $" + String.format("%.2f", guild.getBalance()));
        player.sendMessage("§7Guild has " + claimCount + " claimed chunk(s) that will be unclaimed");
        player.sendMessage("§aType §f/guild delete confirm §ato confirm deletion.");

        return Command.SINGLE_SUCCESS;
    }

    private int handleDeleteConfirm(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();

        // Check if player is in a guild
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasGuild()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        String guildName = resident.getGuild();

        // Check if player is the mayor
        if (!permissionService.hasGuildAdmin(playerUuid, guildName)) {
            player.sendMessage("§cOnly the mayor can delete the guild!");
            return 0;
        }

        int claimCount = plotService.getGuildBlockCount(guildName);

        // Delete the guild
        try {
            boolean success = guildService.deleteGuild(guildName);
            if (success) {
                player.sendMessage("§aGuild §e" + guildName + " §ahas been deleted!");
                player.sendMessage("§7All residents have been removed and " + claimCount + " chunk(s) have been unclaimed.");
            } else {
                player.sendMessage("§cFailed to delete guild!");
            }
        } catch (Exception e) {
            player.sendMessage("§cFailed to delete guild: " + e.getMessage());
            plugin.getLogger().warning("Failed to delete guild " + guildName + " for player " + player.getName() + ": " + e.getMessage());
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

        // Check if player is in a guild
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasGuild()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        String guildName = resident.getGuild();

        // Check if player has permission to claim
        if (!permissionService.hasPermission(playerUuid, "claim", "guild", guildName)) {
            player.sendMessage("§cYou don't have permission to claim land for your guild!");
            return 0;
        }

        // Get the chunk player is standing in
        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        String world = player.getWorld().getName();

        // Check if chunk is already claimed
        if (plotService.guildBlockExists(chunkX, chunkZ, world)) {
            player.sendMessage("§cThis chunk is already claimed!");
            return 0;
        }

        // Check if guild has reached its claim limit
        int currentClaims = plotService.getGuildBlockCount(guildName);
        var guildOpt = guildService.getGuild(guildName);
        if (guildOpt.isPresent()) {
            var levelData = guildOpt.get().getLevelData();
            int maxClaims = levelData.getMaxClaimLimit();
            if (levelData.isAtClaimLimit(currentClaims)) {
                player.sendMessage("§cYour guild has reached its claim limit! §7(" + currentClaims + "/" + maxClaims + " chunks)");
                player.sendMessage("§7Level up your guild to increase the claim limit.");
                return 0;
            }
        }

        // Check if this claim is adjacent to an existing guild claim
        if (!isAdjacentToGuildClaim(chunkX, chunkZ, world, guildName)) {
            player.sendMessage("§cClaims must be adjacent to your existing guild chunks!");
            player.sendMessage("§7You can only claim chunks that touch your guild's territory.");
            return 0;
        }

        // Claim the chunk
        try {
            boolean success = plotService.claimGuildBlock(chunkX, chunkZ, world, guildName);
            if (success) {
                player.sendMessage("§aSuccessfully claimed chunk [" + chunkX + ", " + chunkZ + "] for §e" + guildName + "§a!");
                plugin.getLogger().info("Player " + player.getName() + " claimed chunk [" + chunkX + ", " + chunkZ + "] for guild " + guildName);
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

        // Check if player is in a guild
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasGuild()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        String guildName = resident.getGuild();

        // Check if player has permission to unclaim
        if (!permissionService.hasPermission(playerUuid, "unclaim", "guild", guildName)) {
            player.sendMessage("§cYou don't have permission to unclaim land for your guild!");
            return 0;
        }

        // Get the chunk player is standing in
        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        String world = player.getWorld().getName();

        // Check if chunk is claimed by this guild
        var guildBlock = plotService.getGuildBlock(chunkX, chunkZ, world);
        if (guildBlock.isEmpty()) {
            player.sendMessage("§cThis chunk is not claimed!");
            return 0;
        }

        // Get the guild that owns this chunk
        var blockGuild = guildService.getGuildById(guildBlock.get().getGuildId());
        if (blockGuild.isEmpty() || !blockGuild.get().getName().equals(guildName)) {
            player.sendMessage("§cThis chunk doesn't belong to your guild!");
            return 0;
        }

        // Unclaim the chunk
        try {
            boolean success = plotService.unclaimGuildBlock(chunkX, chunkZ, world);
            if (success) {
                player.sendMessage("§aSuccessfully unclaimed chunk [" + chunkX + ", " + chunkZ + "]!");
                plugin.getLogger().info("Player " + player.getName() + " unclaimed chunk [" + chunkX + ", " + chunkZ + "] from guild " + guildName);
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
        var guilds = guildService.getAllGuilds();

        if (guilds.isEmpty()) {
            sender.sendMessage("§eThere are no guilds yet.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§e=== Guilds (" + guilds.size() + ") ===");
        for (int i = 0; i < Math.min(guilds.size(), 10); i++) {
            var guild = guilds.get(i);
            int residentCount = guildService.getGuildResidentCount(guild.getName());

            sender.sendMessage("§f" + (i + 1) + ". §a" + guild.getName() + " §7(" + residentCount + " residents)");
        }

        if (guilds.size() > 10) {
            sender.sendMessage("§7And " + (guilds.size() - 10) + " more...");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int showTopHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Top Commands ===");
        sender.sendMessage("§f/g top residents§7 - Top residents by guild count");
        sender.sendMessage("§f/g top guilds§7 - Top guilds by resident count");
        sender.sendMessage("§f/g top land§7 - Top guilds by land count");
        sender.sendMessage("§f/g top alliances§7 - Top alliances by member-guild count");
        return Command.SINGLE_SUCCESS;
    }

    private int handleTopResidents(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var ranked = TopRankings.guildsByResidentCount(guildService.getGuildsByPopulation());

        if (ranked.isEmpty()) {
            sender.sendMessage("§eNo guilds found yet.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§e=== Top Residents ===");
        for (int i = 0; i < Math.min(ranked.size(), 10); i++) {
            var ranking = ranked.get(i);
            sender.sendMessage("§f" + (i + 1) + ". §a" + ranking.guild().getName()
                    + " §7- §e" + ranking.value() + " residents");
        }

        if (ranked.size() > 10) {
            sender.sendMessage("§7And " + (ranked.size() - 10) + " more guilds...");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleTopGuilds(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var ranked = TopRankings.guildsByResidentCount(guildService.getAllGuilds());

        if (ranked.isEmpty()) {
            sender.sendMessage("§eNo guilds found yet.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§e=== Top Guilds by Residents ===");
        for (int i = 0; i < Math.min(ranked.size(), 10); i++) {
            var ranking = ranked.get(i);
            sender.sendMessage("§f" + (i + 1) + ". §a" + ranking.guild().getName()
                    + " §7- §e" + ranking.value() + " residents");
        }

        if (ranked.size() > 10) {
            sender.sendMessage("§7And " + (ranked.size() - 10) + " more guilds...");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleTopLand(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var ranked = TopRankings.guildsByLandCount(
                guildService.getAllGuilds(),
                guild -> plotService.getGuildBlockCount(guild.getName()));

        if (ranked.isEmpty()) {
            sender.sendMessage("§eNo guilds found yet.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§e=== Top Guilds by Land ===");
        for (int i = 0; i < Math.min(ranked.size(), 10); i++) {
            var ranking = ranked.get(i);
            sender.sendMessage("§f" + (i + 1) + ". §a" + ranking.guild().getName()
                    + " §7- §e" + ranking.value() + " chunks");
        }

        if (ranked.size() > 10) {
            sender.sendMessage("§7And " + (ranked.size() - 10) + " more guilds...");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleTopAlliances(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        var alliances = TopRankings.alliancesByGuildCount(allianceService.getAllAlliances());

        if (alliances.isEmpty()) {
            sender.sendMessage("§eNo alliances found yet.");
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage("§e=== Top Alliances by Guilds ===");
        for (int i = 0; i < Math.min(alliances.size(), 10); i++) {
            var alliance = alliances.get(i);
            sender.sendMessage("§f" + (i + 1) + ". §a" + alliance.getName()
                    + " §7- §e" + alliance.getGuildCount() + " guilds");
        }

        if (alliances.size() > 10) {
            sender.sendMessage("§7And " + (alliances.size() - 10) + " more alliances...");
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
            if (!resident.hasGuild()) {
                player.sendMessage("§cYou are not in a guild!");
                return Command.SINGLE_SUCCESS;
            }

            String guildName = resident.getGuild();
            if (guildService.getGuild(guildName).isPresent()) {
                var guild = guildService.getGuild(guildName).get();
                player.sendMessage("§e=== " + guildName + " ===");
                player.sendMessage("§fMayor: §a" + guild.getMayorUuid());
                player.sendMessage("§fResidents: §a" + guild.getResidentCount());
                appendGuildBank(player, guild);
                player.sendMessage("§fOpen: " + (guild.isOpen() ? "§aYes" : "§cNo"));
                appendGuildBank(player, guild);
                sendPlotTypeBreakdown(player, guildName);
            } else {
                player.sendMessage("§cGuild information could not be loaded.");
            }
        } else {
            player.sendMessage("§cYour resident data could not be loaded!");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleGuildInfo(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        String guildName = GuildArgumentType.getGuildName(ctx, "guild");

        if (guildService.getGuild(guildName).isPresent()) {
            var guild = guildService.getGuild(guildName).get();
            sender.sendMessage("§e=== " + guildName + " ===");
            sender.sendMessage("§fMayor: §a" + guild.getMayorUuid());
            sender.sendMessage("§fResidents: §a" + guild.getResidentCount());
            appendGuildBank(sender, guild);
            sender.sendMessage("§fOpen: " + (guild.isOpen() ? "§aYes" : "§cNo"));
            appendGuildBank(sender, guild);
            sendPlotTypeBreakdown(sender, guildName);
        } else {
            sender.sendMessage("§cGuild information could not be loaded.");
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
            if (!resident.hasGuild()) {
                player.sendMessage("§cYou are not in a guild!");
                return 0;
            }

            String guildName = resident.getGuild();

            // Check if player can teleport to this guild's spawn
            if (!guildService.canTeleportToSpawn(playerUuid, guildName)) {
                player.sendMessage("§cYou cannot teleport to " + guildName + "'s spawn!");
                return 0;
            }

            // Get spawn location
            var spawnLocation = guildService.getGuildSpawn(guildName);
            if (spawnLocation.isEmpty()) {
                player.sendMessage("§cGuild " + guildName + " does not have a spawn point set!");
                return 0;
            }

            // Convert our Location to Bukkit Location
            Location guildSpawn = spawnLocation.get();
            org.bukkit.Location bukkitLocation = new org.bukkit.Location(
                org.bukkit.Bukkit.getWorld(guildSpawn.getWorld()),
                guildSpawn.getX(),
                guildSpawn.getY(),
                guildSpawn.getZ(),
                guildSpawn.getYaw(),
                guildSpawn.getPitch()
            );

            // Teleport player
            player.teleport(bukkitLocation);
            player.sendMessage("§aTeleported to §e" + guildName + " §aspawn!");

        } else {
            player.sendMessage("§cYour resident data could not be loaded!");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int handleGuildSpawn(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        String guildName = GuildArgumentType.getGuildName(ctx, "guild");
        UUID playerUuid = player.getUniqueId();

        // Check if player can teleport to this guild's spawn
        if (!guildService.canTeleportToSpawn(playerUuid, guildName)) {
            player.sendMessage("§cYou cannot teleport to " + guildName + "'s spawn!");
            return 0;
        }

        // Get spawn location
        var spawnLocation = guildService.getGuildSpawn(guildName);
        if (spawnLocation.isEmpty()) {
            player.sendMessage("§cGuild " + guildName + " does not have a spawn point set!");
            return 0;
        }

        // Convert our Location to Bukkit Location
        Location guildSpawn = spawnLocation.get();
        org.bukkit.Location bukkitLocation = new org.bukkit.Location(
            org.bukkit.Bukkit.getWorld(guildSpawn.getWorld()),
            guildSpawn.getX(),
            guildSpawn.getY(),
            guildSpawn.getZ(),
            guildSpawn.getYaw(),
            guildSpawn.getPitch()
        );

        // Teleport player
        player.teleport(bukkitLocation);
        player.sendMessage("§aTeleported to §e" + guildName + " §aspawn!");

        return Command.SINGLE_SUCCESS;
    }

    private int handleSetSpawn(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();

        // Check if player is in a guild
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasGuild()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        String guildName = resident.getGuild();

        // Check if player has permission to set spawn
        if (!permissionService.hasPermission(playerUuid, "set_spawn", "guild", guildName)) {
            player.sendMessage("§cYou don't have permission to set the guild spawn!");
            return 0;
        }

        // Get the guild to check home block
        var guildOpt = guildService.getGuild(guildName);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cFailed to load guild data!");
            return 0;
        }

        var guild = guildOpt.get();
        if (guild.getHomeBlock() == null) {
            player.sendMessage("§cYour guild does not have a home block set!");
            player.sendMessage("§7A home block must be set before setting a spawn.");
            return 0;
        }

        // Get player's current location
        org.bukkit.Location bukkitLocation = player.getLocation();
        Location guildSpawn = new Location(
            bukkitLocation.getX(),
            bukkitLocation.getY(),
            bukkitLocation.getZ(),
            bukkitLocation.getYaw(),
            bukkitLocation.getPitch(),
            bukkitLocation.getWorld().getName()
        );

        // Check if player is in the home block chunk
        int[] spawnChunk = guildSpawn.getChunkCoordinates();
        int[] homeBlockChunk = guild.getHomeBlock().getChunkCoordinates();

        if (spawnChunk[0] != homeBlockChunk[0] || spawnChunk[1] != homeBlockChunk[1]) {
            player.sendMessage("§cYou must be in your guild's home block chunk to set the spawn!");
            player.sendMessage("§7Your chunk: [" + spawnChunk[0] + ", " + spawnChunk[1] + "]");
            player.sendMessage("§7Home block chunk: [" + homeBlockChunk[0] + ", " + homeBlockChunk[1] + "]");
            return 0;
        }

        // Check world matches
        if (!guildSpawn.getWorld().equals(guild.getHomeBlock().getWorld())) {
            player.sendMessage("§cYou must be in the same world as your guild's home block!");
            return 0;
        }

        // Set the guild spawn
        if (guildService.setGuildSpawn(guildName, guildSpawn)) {
            player.sendMessage("§aGuild spawn set for §e" + guildName + "§a!");
            player.sendMessage("§7Spawn location: " + guildSpawn.toDisplayString());
        } else {
            player.sendMessage("§cFailed to set guild spawn!");
        }

        return Command.SINGLE_SUCCESS;
    }

    private int showToggleHelp(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        sender.sendMessage("§e=== Guild Toggle Commands ===");
        sender.sendMessage("§7/guild toggle list§f - Show current toggle states");
        sender.sendMessage("§7/guild toggle <type>§f - Toggle a setting");
        sender.sendMessage("§7/guild toggle <type> <on|off>§f - Set a setting");
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

        // Check if player is in a guild
        var resident = residentService.getResident(playerUuid);
        if (resident.isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        if (!resident.get().hasGuild()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        String guildName = resident.get().getGuild();

        // Check if player has permission to toggle guild settings
        if (!permissionService.hasGuildAdmin(playerUuid, guildName)) {
            player.sendMessage("§cYou don't have permission to toggle guild settings!");
            return 0;
        }

        // Show current toggle states
        var toggles = guildService.getGuildToggles(guildName);
        if (toggles.isEmpty()) {
            player.sendMessage("§cFailed to load toggle states!");
            return 0;
        }

        player.sendMessage("§e=== §a" + guildName + " §eToggles ===");
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

        // Check if player is in a guild
        var resident = residentService.getResident(playerUuid);
        if (resident.isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        if (!resident.get().hasGuild()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        String guildName = resident.get().getGuild();

        // Check if player has permission to toggle guild settings
        if (!permissionService.hasGuildAdmin(playerUuid, guildName)) {
            player.sendMessage("§cYou don't have permission to toggle guild settings!");
            return 0;
        }

        if (!isValidToggleType(toggleType)) {
            player.sendMessage("§cUnknown toggle type: " + toggleType);
            showToggleHelp(ctx);
            return 0;
        }

        boolean success = guildService.toggleGuildPermission(guildName, toggleType, playerUuid);
        if (success) {
            boolean newState = guildService.getGuildToggle(guildName, toggleType);
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

        // Check if player is in a guild
        var resident = residentService.getResident(playerUuid);
        if (resident.isEmpty()) {
            player.sendMessage("§cYour resident data could not be loaded!");
            return 0;
        }

        if (!resident.get().hasGuild()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        String guildName = resident.get().getGuild();

        // Check if player has permission to toggle guild settings
        if (!permissionService.hasGuildAdmin(playerUuid, guildName)) {
            player.sendMessage("§cYou don't have permission to toggle guild settings!");
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

        boolean success = guildService.setGuildToggle(guildName, toggleType, value, playerUuid);
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

    private boolean isAdjacentToGuildClaim(int chunkX, int chunkZ, String world, String guildName) {
        // Get all guild blocks for this guild
        var guildBlocks = plotService.getGuildBlocksInGuild(guildName);

        // If guild has no claims yet, allow first claim (should only happen if home block wasn't claimed)
        if (guildBlocks.isEmpty()) {
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

            // Check if there's a guild block at this adjacent position
            var adjacentBlock = plotService.getGuildBlock(adjacentX, adjacentZ, world);
            if (adjacentBlock.isPresent()) {
                // Check if it belongs to this guild
                var blockGuild = guildService.getGuildById(adjacentBlock.get().getGuildId());
                if (blockGuild.isPresent() && blockGuild.get().getName().equals(guildName)) {
                    return true; // Found an adjacent claim from this guild
                }
            }
        }

        return false; // No adjacent claims found
    }

    private void appendGuildBank(org.bukkit.command.CommandSender sender, org.aincraft.guilds.models.Guild guild) {
        MintGuildBankService bank = mintGuildBankService;
        BigDecimal limit = bank == null
                ? new org.aincraft.guilds.territory.economy.GuildBankCapacity().forLevel(guild.getGuildLevel())
                : bank.limitFor(guild);
        String limitText = limit.toPlainString();
        if (bank == null) {
            sender.sendMessage("§fGuild Bank: §7unavailable §8/ §6$" + limitText);
            return;
        }
        bank.guildBalance(guild.getId()).thenAccept(result ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (result.status() == MintGuildBankService.Status.COMMITTED && result.value() != null) {
                        sender.sendMessage("§fGuild Bank: §6$" + result.value().toPlainString()
                                + " §8/ §6$" + limitText);
                    } else {
                        sender.sendMessage("§fGuild Bank: §7unavailable §8/ §6$" + limitText);
                    }
                }));
    }

    private void sendPlotTypeBreakdown(org.bukkit.command.CommandSender sender, String guildName) {
        List<GuildBlock> blocks = plotService.getGuildBlocksInGuild(guildName);

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

    /**
     * /guild government &lt;form&gt; — the guild (guild) picks its governance form;
     * seats derive from guild roles (mayor, assistants, residents). Mayor only.
     */
    private int handleGovernment(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }

        UUID playerUuid = player.getUniqueId();
        org.aincraft.guilds.territory.model.GovernmentForm form = GovernmentFormArgumentType.getForm(ctx, "form");

        var residentOpt = residentService.getResident(playerUuid);
        if (residentOpt.isEmpty() || !residentOpt.get().hasGuild()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        String guildName = residentOpt.get().getGuild();
        var guildOpt = guildService.getGuild(guildName);
        if (guildOpt.isEmpty()) {
            player.sendMessage("§cFailed to load guild data!");
            return 0;
        }

        var guild = guildOpt.get();

        // Governance-form changes are mayor-only (hasGuildAdmin would admit assistants).
        if (guild.getMayorUuid() == null || !guild.getMayorUuid().equals(playerUuid)) {
            player.sendMessage("§cOnly the mayor can change the guild's government!");
            return 0;
        }

        if (!governanceSource.setGuildForm(guild.getId(), form)) {
            player.sendMessage("§cFailed to persist the governance form!");
            return 0;
        }
        player.sendMessage("§aGuild government is now " + form.name()
                + " — seat holders derive from guild roles.");
        return Command.SINGLE_SUCCESS;
    }
    private int handleBankOpen(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof org.bukkit.entity.Player player)) {
            ctx.getSource().getSender().sendMessage("§cThis command can only be used by players."); return 0;
        }
        MintGuildBankService bank = mintGuildBankService;
        if (bank == null) { player.sendMessage("§cMint guild banks are unavailable."); return 0; }
        var resident = residentService.getResident(player.getUniqueId());
        if (resident.isEmpty() || !resident.get().hasGuild()) { player.sendMessage("§cYou are not in a guild!"); return 0; }
        var guild = guildService.getGuild(resident.get().getGuild());
        if (guild.isEmpty()) { player.sendMessage("§cYour guild could not be resolved."); return 0; }
        bank.openAccount(player.getUniqueId(), guild.get().getId()).thenAccept(result ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (result.status() == MintGuildBankService.Status.COMMITTED
                            || result.status() == MintGuildBankService.Status.REJECTED) {
                        player.sendMessage(result.status() == MintGuildBankService.Status.COMMITTED
                                ? "§aGuild bank account opened." : "§cGuild bank account could not be opened.");
                    } else {
                        player.sendMessage("§cYou are not a current guild member.");
                    }
                }));
        player.sendMessage("§7Opening Mint guild bank account...");
        return Command.SINGLE_SUCCESS;
    }

    private int handleBankBalance(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof org.bukkit.entity.Player player)) {
            ctx.getSource().getSender().sendMessage("§cThis command can only be used by players."); return 0;
        }
        MintGuildBankService bank = mintGuildBankService;
        if (bank == null) { player.sendMessage("§cMint guild banks are unavailable."); return 0; }
        var resident = residentService.getResident(player.getUniqueId());
        if (resident.isEmpty() || !resident.get().hasGuild()) { player.sendMessage("§cYou are not in a guild!"); return 0; }
        var guild = guildService.getGuild(resident.get().getGuild());
        if (guild.isEmpty()) { player.sendMessage("§cYour guild could not be resolved."); return 0; }
        bank.balance(player.getUniqueId(), guild.get().getId())
                .thenAccept(result -> sendBankResult(player, result, "Balance: "));
        player.sendMessage("§7Checking Mint guild bank balance...");
        return Command.SINGLE_SUCCESS;
    }

    private int handleBankTransfer(CommandContext<CommandSourceStack> ctx, boolean deposit) {
        if (!(ctx.getSource().getSender() instanceof org.bukkit.entity.Player player)) {
            ctx.getSource().getSender().sendMessage("§cThis command can only be used by players."); return 0;
        }
        MintGuildBankService bank = mintGuildBankService;
        if (bank == null) { player.sendMessage("§cMint guild banks are unavailable."); return 0; }
        var resident = residentService.getResident(player.getUniqueId());
        if (resident.isEmpty() || !resident.get().hasGuild()) { player.sendMessage("§cYou are not in a guild!"); return 0; }
        var guildOpt = guildService.getGuild(resident.get().getGuild());
        if (guildOpt.isEmpty()) { player.sendMessage("§cYour guild could not be resolved."); return 0; }
        String guild = guildOpt.get().getId();
        String permission = deposit ? "deposit" : "withdraw";
        if (!permissionService.hasPermission(player.getUniqueId(), permission, "guild", guild)) {
            player.sendMessage("§cYou do not have permission to " + permission + " from the guild bank."); return 0;
        }
        final BigDecimal amount;
        try { amount = new BigDecimal(StringArgumentType.getString(ctx, "amount"));
            if (amount.signum() <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) { player.sendMessage("§cAmount must be a positive decimal."); return 0; }
        String key = "command-bank-" + UUID.randomUUID();
        var stage = deposit ? bank.deposit(player.getUniqueId(), guild, amount, key)
                : bank.withdraw(player.getUniqueId(), guild, amount, key);
        stage.thenAccept(result -> sendBankResult(player, result, deposit ? "Deposited: " : "Withdrawn: "));
        player.sendMessage("§7Submitting Mint bank transfer...");
        return Command.SINGLE_SUCCESS;
    }

    private void sendBankResult(org.bukkit.entity.Player player, MintGuildBankService.Result result, String prefix) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            switch (result.status()) {
                case COMMITTED -> player.sendMessage("§a" + prefix + (result.value() == null ? "transfer committed" : result.value()));
                case INSUFFICIENT_FUNDS -> player.sendMessage("§cInsufficient Mint funds.");
                case REJECTED, CAPACITY_EXCEEDED, UNAUTHORIZED -> player.sendMessage("§cMint rejected the operation.");
                case UNAVAILABLE -> player.sendMessage("§cMint guild bank is unavailable.");
            }
        });
    }

    private int handleStorage(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof org.bukkit.entity.Player player)) {
            ctx.getSource().getSender().sendMessage("§cThis command can only be used by players.");
            return 0;
        }
        StorageFacilityOpener opener = storageFacilityOpener;
        if (opener == null) {
            player.sendMessage("§cGuild storage is unavailable.");
            return 0;
        }
        StorageFacilityOpener.Result result = opener.tryOpenAtLocation(player);
        if (result.outcome() == StorageFacilityOpener.Outcome.OPENED) {
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage("§c" + result.message());
        return 0;
    }
    private int handleUpgradeMap(CommandContext<CommandSourceStack> ctx) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return 0;
        }
        if (!player.hasPermission("guilds.techtree")) {
            player.sendMessage("§cNo permission");
            return 0;
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("MapGUI")) {
            player.sendMessage("§cMapGUI is not available on this server.");
            return 0;
        }
        var resident = residentService.getResident(player.getUniqueId());
        if (resident.isEmpty() || !resident.get().hasGuild()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }
        try {
            MapGui.get().open(player, new org.aincraft.guilds.gui.GuildUpgradeScreen(
                    plugin, guildService, residentService, guildLevelService, techTreeService, guildProjectService, player));
        } catch (Exception e) {
            player.sendMessage("§cFailed to open upgrade screen: " + e.getMessage());
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

}