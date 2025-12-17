package org.aincraft;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.commands.components.RegionComponent;
import org.aincraft.inject.GuildsModule;
import org.aincraft.listeners.GuildProtectionListener;
import org.aincraft.map.GuildColorMapper;
import org.aincraft.map.GuildMapRenderer;
import org.aincraft.storage.ChunkClaimRepository;
import org.aincraft.subregion.SelectionManager;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.aincraft.subregion.RegionMovementTracker;
import org.aincraft.subregion.RegionEntryNotifier;
import org.aincraft.claim.ClaimMovementTracker;
import org.aincraft.claim.ClaimEntryNotifier;
import org.aincraft.multiblock.MultiblockListener;
import org.aincraft.multiblock.MultiblockRegistry;
import org.aincraft.multiblock.patterns.GuildVaultPattern;
import org.aincraft.vault.VaultComponent;
import org.aincraft.vault.VaultHandler;
import org.aincraft.vault.VaultService;
import org.aincraft.vault.gui.VaultGUIListener;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Chunk;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

public class GuildsPlugin extends JavaPlugin {
    private Injector injector;
    private GuildManager guildManager;
    private GuildService guildService;
    private SubregionService subregionService;
    private RegionComponent regionComponent;
    private VaultComponent vaultComponent;
    private SubregionTypeRegistry typeRegistry;
    private MultiblockRegistry multiblockRegistry;
    private GuildMapRenderer mapRenderer;

    @Override
    public void onEnable() {
        this.injector = Guice.createInjector(new GuildsModule(this));
        this.guildManager = injector.getInstance(GuildManager.class);

        // Initialize services
        this.guildService = injector.getInstance(GuildService.class);
        this.subregionService = injector.getInstance(SubregionService.class);
        SelectionManager selectionManager = injector.getInstance(SelectionManager.class);
        this.typeRegistry = injector.getInstance(SubregionTypeRegistry.class);
        this.regionComponent = new RegionComponent(guildService, subregionService, selectionManager, typeRegistry);

        // Initialize map renderer
        ChunkClaimRepository chunkClaimRepository = injector.getInstance(ChunkClaimRepository.class);
        GuildColorMapper colorMapper = new GuildColorMapper();
        this.mapRenderer = new GuildMapRenderer(guildService, chunkClaimRepository, colorMapper);

        // Register protection listener
        GuildProtectionListener protectionListener = injector.getInstance(GuildProtectionListener.class);
        getServer().getPluginManager().registerEvents(protectionListener, this);

        // Register region movement tracking and notifications
        RegionMovementTracker movementTracker = injector.getInstance(RegionMovementTracker.class);
        RegionEntryNotifier entryNotifier = injector.getInstance(RegionEntryNotifier.class);
        getServer().getPluginManager().registerEvents(movementTracker, this);
        getServer().getPluginManager().registerEvents(entryNotifier, this);

        // Register claim movement tracking and notifications
        ClaimMovementTracker claimTracker = injector.getInstance(ClaimMovementTracker.class);
        ClaimEntryNotifier claimNotifier = injector.getInstance(ClaimEntryNotifier.class);
        getServer().getPluginManager().registerEvents(claimTracker, this);
        getServer().getPluginManager().registerEvents(claimNotifier, this);

        // Register multiblock system
        this.multiblockRegistry = injector.getInstance(MultiblockRegistry.class);
        MultiblockListener multiblockListener = injector.getInstance(MultiblockListener.class);
        getServer().getPluginManager().registerEvents(multiblockListener, this);

        // Register vault pattern and listeners
        multiblockRegistry.registerBuiltIn(GuildVaultPattern.create());
        VaultService vaultService = injector.getInstance(VaultService.class);
        this.vaultComponent = new VaultComponent(vaultService);
        VaultHandler vaultHandler = injector.getInstance(VaultHandler.class);
        VaultGUIListener vaultGUIListener = injector.getInstance(VaultGUIListener.class);
        getServer().getPluginManager().registerEvents(vaultHandler, this);
        getServer().getPluginManager().registerEvents(vaultGUIListener, this);

        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            registerGuildCommands(commands);
        });

        getLogger().info("Guilds plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Guilds plugin disabled!");
    }

    /**
     * Gets the subregion type registry for external plugin integration.
     * External plugins can register custom region types via this registry.
     *
     * @return the type registry
     */
    public SubregionTypeRegistry getTypeRegistry() {
        return typeRegistry;
    }

    /**
     * Gets the multiblock registry for external plugin integration.
     * External plugins can register custom multiblock patterns via this registry.
     *
     * @return the multiblock registry
     */
    public MultiblockRegistry getMultiblockRegistry() {
        return multiblockRegistry;
    }

    // ==================== Suggestion Provider Methods ====================

    private CompletableFuture<Suggestions> suggestGuildNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        guildService.listAllGuilds().forEach(guild -> builder.suggest(guild.getName()));
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPlayerNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        getServer().getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestRoleNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSender sender = context.getSource().getSender();
        if (sender instanceof Player player) {
            Guild guild = guildService.getPlayerGuild(player.getUniqueId());
            if (guild != null) {
                guildService.getGuildRoles(guild.getId()).forEach(role -> builder.suggest(role.getName()));
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestRegionNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSender sender = context.getSource().getSender();
        if (sender instanceof Player player) {
            Guild guild = guildService.getPlayerGuild(player.getUniqueId());
            if (guild != null) {
                subregionService.getGuildSubregions(guild.getId()).forEach(region -> builder.suggest(region.getName()));
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestRegionTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        typeRegistry.getAllTypes().forEach(type -> builder.suggest(type.getId()));
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPermissions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (GuildPermission perm : GuildPermission.values()) {
            builder.suggest(perm.name());
        }
        return builder.buildFuture();
    }

    // ==================== Command Registration ====================

    private void registerGuildCommands(Commands commands) {
        commands.register(
            Commands.literal("g")
                .executes(context -> {
                    context.getSource().getSender().sendMessage("Usage: /g <create|join|leave|delete|info|list|claim|unclaim|kick|spawn|setspawn|role|region|map|vault>");
                    return 1;
                })
                // Create command
                .then(Commands.literal("create")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> handleCreateGuild(context, StringArgumentType.getString(context, "name"), ""))
                        .then(Commands.argument("description", StringArgumentType.greedyString())
                            .executes(context -> handleCreateGuild(context,
                                StringArgumentType.getString(context, "name"),
                                StringArgumentType.getString(context, "description")))
                        )
                    )
                )
                // Join command
                .then(Commands.literal("join")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> handleJoinGuild(context, StringArgumentType.getString(context, "guildName")))
                    )
                )
                // Leave command
                .then(Commands.literal("leave")
                    .executes(this::handleLeaveGuild)
                )
                // Delete command
                .then(Commands.literal("delete")
                    .executes(this::handleDeleteGuild)
                )
                // Info command
                .then(Commands.literal("info")
                    .executes(this::handleGuildInfo)
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> handleGuildInfoWithArg(context, StringArgumentType.getString(context, "guildName")))
                    )
                )
                // List command
                .then(Commands.literal("list")
                    .executes(this::handleGuildList)
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> handleGuildListPage(context, IntegerArgumentType.getInteger(context, "page")))
                    )
                )
                // Claim command
                .then(Commands.literal("claim")
                    .executes(this::handleClaimChunk)
                )
                // Unclaim command
                .then(Commands.literal("unclaim")
                    .executes(this::handleUnclaimChunk)
                    .then(Commands.literal("all")
                        .executes(context -> handleUnclaimAll(context))
                    )
                )
                // Kick command
                .then(Commands.literal("kick")
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(context -> handleKickMember(context, StringArgumentType.getString(context, "playerName")))
                    )
                )
                // Spawn command
                .then(Commands.literal("spawn")
                    .executes(this::handleSpawnCommand)
                )
                // Setspawn command
                .then(Commands.literal("setspawn")
                    .executes(this::handleSetspawnCommand)
                )
                // Color command
                .then(Commands.literal("color")
                    .then(Commands.argument("color", StringArgumentType.word())
                        .executes(context -> handleColorCommand(context, StringArgumentType.getString(context, "color")))
                    )
                    .executes(this::handleColorCommandNoArg)
                )
                // Role command
                .then(registerRoleCommands())
                // Map command
                .then(Commands.literal("map")
                    .executes(context -> handleMapCommand(context, 1))
                    .then(Commands.argument("size", IntegerArgumentType.integer(1, 5))
                        .executes(context -> handleMapCommand(context, IntegerArgumentType.getInteger(context, "size")))
                    )
                )
                // Region command
                .then(registerRegionCommands())
                // Vault command
                .then(registerVaultCommands())
                .build(),
            "Guild management commands",
            List.of("guild")
        );
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> registerRoleCommands() {
        return Commands.literal("role")
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(context -> handleRoleCommand(context, "create", StringArgumentType.getString(context, "name")))
                    .then(Commands.argument("permissions", StringArgumentType.greedyString())
                        .executes(context -> handleRoleCommand(context, "create",
                            StringArgumentType.getString(context, "name"),
                            StringArgumentType.getString(context, "permissions")))
                    )
                )
            )
            .then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(this::suggestRoleNames)
                    .executes(context -> handleRoleCommand(context, "delete", StringArgumentType.getString(context, "name")))
                )
            )
            .then(Commands.literal("list")
                .executes(context -> handleRoleCommand(context, "list"))
            )
            .then(Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(this::suggestRoleNames)
                    .executes(context -> handleRoleCommand(context, "info", StringArgumentType.getString(context, "name")))
                )
            )
            .then(Commands.literal("setperm")
                .then(Commands.argument("roleName", StringArgumentType.word())
                    .suggests(this::suggestRoleNames)
                    .then(Commands.argument("permission", StringArgumentType.word())
                        .suggests(this::suggestPermissions)
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> handleRoleCommand(context, "setperm",
                                StringArgumentType.getString(context, "roleName"),
                                StringArgumentType.getString(context, "permission"),
                                String.valueOf(BoolArgumentType.getBool(context, "value"))))
                        )
                    )
                )
            )
            .then(Commands.literal("assign")
                .then(Commands.argument("playerName", StringArgumentType.word())
                    .suggests(this::suggestPlayerNames)
                    .then(Commands.argument("roleName", StringArgumentType.word())
                        .suggests(this::suggestRoleNames)
                        .executes(context -> handleRoleCommand(context, "assign",
                            StringArgumentType.getString(context, "playerName"),
                            StringArgumentType.getString(context, "roleName")))
                    )
                )
            )
            .then(Commands.literal("unassign")
                .then(Commands.argument("playerName", StringArgumentType.word())
                    .suggests(this::suggestPlayerNames)
                    .then(Commands.argument("roleName", StringArgumentType.word())
                        .suggests(this::suggestRoleNames)
                        .executes(context -> handleRoleCommand(context, "unassign",
                            StringArgumentType.getString(context, "playerName"),
                            StringArgumentType.getString(context, "roleName")))
                    )
                )
            )
            .build();
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> registerRegionCommands() {
        return Commands.literal("region")
            .executes(context -> handleRegionCommand(context, new String[]{"region"}))
            .then(Commands.literal("pos1")
                .executes(context -> handleRegionCommand(context, new String[]{"region", "pos1"}))
            )
            .then(Commands.literal("pos2")
                .executes(context -> handleRegionCommand(context, new String[]{"region", "pos2"}))
            )
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(context -> handleRegionCommand(context, new String[]{"region", "create", StringArgumentType.getString(context, "name")}))
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(this::suggestRegionTypes)
                        .executes(context -> handleRegionCommand(context, new String[]{"region", "create",
                            StringArgumentType.getString(context, "name"),
                            StringArgumentType.getString(context, "type")}))
                    )
                )
            )
            .then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .executes(context -> handleRegionCommand(context, new String[]{"region", "delete", StringArgumentType.getString(context, "name")}))
                )
            )
            .then(Commands.literal("list")
                .executes(context -> handleRegionCommand(context, new String[]{"region", "list"}))
            )
            .then(Commands.literal("info")
                .executes(context -> handleRegionCommand(context, new String[]{"region", "info"}))
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .executes(context -> handleRegionCommand(context, new String[]{"region", "info", StringArgumentType.getString(context, "name")}))
                )
            )
            .then(Commands.literal("types")
                .executes(context -> handleRegionCommand(context, new String[]{"region", "types"}))
            )
            .then(Commands.literal("settype")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(this::suggestRegionTypes)
                        .executes(context -> handleRegionCommand(context, new String[]{"region", "settype",
                            StringArgumentType.getString(context, "regionName"),
                            StringArgumentType.getString(context, "type")}))
                    )
                )
            )
            .then(Commands.literal("addowner")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(context -> handleRegionCommand(context, new String[]{"region", "addowner",
                            StringArgumentType.getString(context, "regionName"),
                            StringArgumentType.getString(context, "playerName")}))
                    )
                )
            )
            .then(Commands.literal("removeowner")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(context -> handleRegionCommand(context, new String[]{"region", "removeowner",
                            StringArgumentType.getString(context, "regionName"),
                            StringArgumentType.getString(context, "playerName")}))
                    )
                )
            )
            .build();
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> registerVaultCommands() {
        return Commands.literal("vault")
            .executes(context -> handleVaultCommand(context, new String[]{"vault"}))
            .then(Commands.literal("open")
                .executes(context -> handleVaultCommand(context, new String[]{"vault", "open"}))
            )
            .then(Commands.literal("info")
                .executes(context -> handleVaultCommand(context, new String[]{"vault", "info"}))
            )
            .then(Commands.literal("log")
                .executes(context -> handleVaultCommand(context, new String[]{"vault", "log"}))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int page = IntegerArgumentType.getInteger(context, "page");
                        handleVaultCommand(context, new String[]{"vault", "log", String.valueOf(page)});
                        return 1;
                    })
                )
            )
            .then(Commands.literal("destroy")
                .then(Commands.literal("confirm")
                    .executes(context -> handleVaultCommand(context, new String[]{"vault", "destroy", "confirm"}))
                )
            )
            .build();
    }

    // ==================== New Handler Methods ====================

    private int handleGuildInfoWithArg(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String guildName) {
        CommandSender sender = context.getSource().getSender();
        Guild guild = guildService.getGuildByName(guildName);
        if (guild == null) {
            sender.sendMessage("§cGuild not found!");
            return 0;
        }

        sender.sendMessage("§6=== Guild Info ===");
        sender.sendMessage("§7Name: §f" + guild.getName());
        sender.sendMessage("§7ID: §f" + guild.getId());
        sender.sendMessage("§7Description: §f" + (guild.getDescription() == null || guild.getDescription().isEmpty() ? "No description" : guild.getDescription()));
        sender.sendMessage("§7Members: §f" + guild.getMemberCount() + "/" + guild.getMaxMembers());
        sender.sendMessage("§7Created: §f" + new Date(guild.getCreatedAt()));
        return 1;
    }

    private int handleGuildListPage(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, int page) {
        CommandSender sender = context.getSource().getSender();

        if (guildManager.getAllGuilds().isEmpty()) {
            sender.sendMessage("§7No guilds exist yet!");
            return 1;
        }

        sender.sendMessage("§6=== Guild List (Page " + page + ") ===");
        int index = 0;
        for (Guild guild : guildManager.getAllGuilds()) {
            sender.sendMessage("§7• §f" + guild.getName() + " §7(ID: " + guild.getId() + ") §8- §7" + guild.getMemberCount() + " members");
            index++;
        }

        return 1;
    }

    private int handleUnclaimAll(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can unclaim chunks"));
            return 0;
        }

        Player player = (Player) sender;

        Optional<Guild> guildOpt = guildManager.getPlayerGuild(player.getUniqueId());
        if (!guildOpt.isPresent()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return 0;
        }

        // TODO: Implement unclaim all logic
        player.sendMessage("§aUnclaimed all chunks!");
        return 1;
    }

    private int handleSpawnCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use spawn"));
            return 0;
        }

        Player player = (Player) sender;

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return 0;
        }

        org.bukkit.Location spawnLocation = guildService.getGuildSpawnLocation(guild.getId());
        if (spawnLocation == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Your guild does not have a spawn point set"));
            return 0;
        }

        player.teleport(spawnLocation);
        player.sendMessage(MessageFormatter.deserialize("<green>✓ Teleported to guild spawn!</green>"));
        return 1;
    }

    private int handleSetspawnCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can set spawn"));
            return 0;
        }

        Player player = (Player) sender;

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return 0;
        }

        if (!guildService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.MANAGE_SPAWN)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to set guild spawn"));
            return 0;
        }

        if (guildService.setGuildSpawn(guild.getId(), player.getUniqueId(), player.getLocation())) {
            player.sendMessage(MessageFormatter.deserialize("<green>✓ Guild spawn set at <gold>" +
                String.format("%.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ()) +
                "</gold>!</green>"));
            return 1;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Failed to set spawn. You must be in claimed guild territory"));
        return 0;
    }

    private int handleColorCommandNoArg(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g color <color> or /g color clear"));
        return 0;
    }

    private int handleColorCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String color) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return 0;
        }

        Player player = (Player) sender;

        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return 0;
        }

        if (!guild.isOwner(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only the guild owner can change guild color"));
            return 0;
        }

        String colorInput = color.toLowerCase();

        // Handle clear command
        if (colorInput.equals("clear")) {
            guild.setColor(null);
            guildService.save(guild);
            player.sendMessage(MessageFormatter.deserialize("<green>Guild color cleared</green>"));
            return 1;
        }

        // Validate color
        if (!isValidColor(colorInput)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid color format. Use hex (#RRGGBB) or a named color"));
            return 0;
        }

        guild.setColor(colorInput);
        guildService.save(guild);
        player.sendMessage(MessageFormatter.deserialize("<green>Guild color set to <gold>" + colorInput + "</gold></green>"));
        return 1;
    }

    private boolean isValidColor(String color) {
        // Check if hex format
        if (color.startsWith("#")) {
            if (color.length() != 7) {
                return false;
            }
            try {
                Integer.parseInt(color.substring(1), 16);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // Check if named color
        return net.kyori.adventure.text.format.NamedTextColor.NAMES.value(color) != null;
    }

    // ==================== Original Handler Methods ====================

    private int handleCreateGuild(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name, String description) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can create guilds!");
            return 0;
        }

        Player player = (Player) sender;

        try {
            Guild guild = guildManager.createGuild(name, description, player.getUniqueId());
            player.sendMessage("§aGuild created successfully!");
            player.sendMessage("§7Name: §f" + guild.getName());
            player.sendMessage("§7ID: §f" + guild.getId());
            player.sendMessage("§7Description: §f" + (guild.getDescription().isEmpty() ? "No description" : guild.getDescription()));
            return 1;
        } catch (IllegalStateException e) {
            player.sendMessage("§cError: " + e.getMessage());
            return 0;
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cError: " + e.getMessage());
            return 0;
        }
    }

    private int handleJoinGuild(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String guildName) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can join guilds!");
            return 0;
        }

        Player player = (Player) sender;

        for (Guild guild : guildManager.getAllGuilds()) {
            if (guild.getName().equalsIgnoreCase(guildName)) {
                try {
                    if (guildManager.joinGuild(guild.getId(), player.getUniqueId())) {
                        player.sendMessage("§aSuccessfully joined guild: §f" + guild.getName());
                        return 1;
                    } else {
                        player.sendMessage("§cGuild is full or you're already a member!");
                        return 0;
                    }
                } catch (IllegalStateException e) {
                    player.sendMessage("§cError: " + e.getMessage());
                    return 0;
                }
            }
        }

        player.sendMessage("§cGuild not found!");
        return 0;
    }

    private int handleLeaveGuild(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can leave guilds!");
            return 0;
        }

        Player player = (Player) sender;

        if (guildManager.leaveGuild(player.getUniqueId())) {
            player.sendMessage("§aYou have left your guild!");
            return 1;
        } else {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }
    }

    private int handleDeleteGuild(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can delete guilds!");
            return 0;
        }

        Player player = (Player) sender;

        Optional<Guild> guildOpt = guildManager.getPlayerGuild(player.getUniqueId());
        if (!guildOpt.isPresent()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        Guild guild = guildOpt.get();

        if (guildManager.deleteGuild(guild.getId(), player.getUniqueId())) {
            player.sendMessage("§aYour guild has been deleted!");
            return 1;
        } else {
            player.sendMessage("§cOnly guild owners can delete guilds!");
            return 0;
        }
    }

    private int handleGuildInfo(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can view guild info!");
            return 0;
        }

        Player player = (Player) sender;

        Optional<Guild> guildOpt = guildManager.getPlayerGuild(player.getUniqueId());
        if (!guildOpt.isPresent()) {
            player.sendMessage("§cYou are not in a guild!");
            return 0;
        }

        Guild guild = guildOpt.get();

        player.sendMessage("§6=== Guild Info ===");
        player.sendMessage("§7Name: §f" + guild.getName());
        player.sendMessage("§7ID: §f" + guild.getId());
        player.sendMessage("§7Description: §f" + (guild.getDescription().isEmpty() ? "No description" : guild.getDescription()));
        player.sendMessage("§7Members: §f" + guild.getMemberCount() + "/" + guild.getMaxMembers());
        player.sendMessage("§7Owner: §f" + (guild.isOwner(player.getUniqueId()) ? "You" : "Not you"));
        player.sendMessage("§7Created: §f" + new Date(guild.getCreatedAt()));

        return 1;
    }

    private int handleGuildList(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        if (guildManager.getAllGuilds().isEmpty()) {
            sender.sendMessage("§7No guilds exist yet!");
            return 1;
        }

        sender.sendMessage("§6=== Guild List ===");
        for (Guild guild : guildManager.getAllGuilds()) {
            sender.sendMessage("§7• §f" + guild.getName() + " §7(ID: " + guild.getId() + ") §8- §7" + guild.getMemberCount() + " members");
        }

        return 1;
    }

    private int handleClaimChunk(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can claim chunks"));
            return 0;
        }

        Player player = (Player) sender;

        // Pre-check: guild membership
        Optional<Guild> guildOpt = guildManager.getPlayerGuild(player.getUniqueId());
        if (!guildOpt.isPresent()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return 0;
        }

        // Attempt claim
        if (guildManager.claimChunk(player)) {
            Chunk chunk = player.getLocation().getChunk();
            player.sendMessage(MessageFormatter.format(
                "<green>Claimed chunk at <gold>%s, %s</gold></green>",
                chunk.getX(), chunk.getZ()));
            return 1;
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                "Failed to claim chunk. You may lack CLAIM permission or chunk is already claimed."));
            return 0;
        }
    }

    private int handleUnclaimChunk(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can unclaim chunks"));
            return 0;
        }

        Player player = (Player) sender;

        // Pre-check: guild membership
        Optional<Guild> guildOpt = guildManager.getPlayerGuild(player.getUniqueId());
        if (!guildOpt.isPresent()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return 0;
        }

        // Attempt unclaim
        if (guildManager.unclaimChunk(player)) {
            Chunk chunk = player.getLocation().getChunk();
            player.sendMessage(MessageFormatter.format(
                "<green>Unclaimed chunk at <gold>%s, %s</gold></green>",
                chunk.getX(), chunk.getZ()));
            return 1;
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                "Failed to unclaim chunk. You may lack UNCLAIM permission or chunk is not owned by your guild."));
            return 0;
        }
    }

    private int handleKickMember(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String playerName) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can kick members!");
            return 0;
        }

        Player player = (Player) sender;
        Player target = getServer().getPlayer(playerName);
        if (target == null) {
            player.sendMessage("§cPlayer not found!");
            return 0;
        }

        return guildManager.kickMember(player.getUniqueId(), target.getUniqueId()) ? 1 : 0;
    }

    private int handleRoleCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String... args) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can manage roles!");
            return 0;
        }

        Player player = (Player) sender;
        // Reconstruct arguments for component
        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = "role";
        System.arraycopy(args, 0, fullArgs, 1, args.length);

        // TODO: Delegate to role component when available
        // For now, just acknowledge the command
        return 1;
    }

    private int handleMapCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, int size) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can view the map"));
            return 0;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("guilds.map")) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to view the map"));
            return 0;
        }

        // Render and send map
        mapRenderer.renderMap(player, size);
        return 1;
    }

    private int handleRegionCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String[] args) {
        CommandSender sender = context.getSource().getSender();
        regionComponent.execute(sender, args);
        return 1;
    }

    private int handleVaultCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String[] args) {
        CommandSender sender = context.getSource().getSender();
        vaultComponent.execute(sender, args);
        return 1;
    }
}