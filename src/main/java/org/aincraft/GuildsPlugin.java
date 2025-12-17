package org.aincraft;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.commands.components.RegionComponent;
import org.aincraft.commands.components.UnclaimToggleComponent;
import org.aincraft.inject.GuildsModule;
import org.aincraft.util.ColorConverter;
import org.aincraft.listeners.GuildProtectionListener;
import org.aincraft.map.GuildColorMapper;
import org.aincraft.map.GuildMapRenderer;
import org.aincraft.storage.ChunkClaimRepository;
import org.aincraft.subregion.SelectionManager;
import org.aincraft.subregion.SelectionVisualizerListener;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.aincraft.subregion.RegionMovementTracker;
import org.aincraft.subregion.RegionEntryNotifier;
import org.aincraft.subregion.RegionPermissionService;
import org.aincraft.claim.ClaimMovementTracker;
import org.aincraft.claim.ClaimEntryNotifier;
import org.aincraft.claim.AutoClaimManager;
import org.aincraft.claim.AutoClaimListener;
import org.aincraft.claim.AutoClaimState;
import org.aincraft.claim.AutoUnclaimManager;
import org.aincraft.claim.AutoUnclaimListener;
import org.aincraft.multiblock.MultiblockListener;
import org.aincraft.multiblock.MultiblockRegistry;
import org.aincraft.multiblock.patterns.GuildVaultPattern;
import org.aincraft.vault.VaultComponent;
import org.aincraft.commands.components.LogComponent;
import org.aincraft.vault.VaultHandler;
import org.aincraft.vault.VaultService;
import org.aincraft.vault.gui.VaultGUIListener;
import org.aincraft.role.gui.RoleCreationGUIListener;
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
    private RelationshipService relationshipService;
    private SubregionService subregionService;
    private RegionComponent regionComponent;
    private VaultComponent vaultComponent;
    private LogComponent logComponent;
    private SubregionTypeRegistry typeRegistry;
    private MultiblockRegistry multiblockRegistry;
    private GuildMapRenderer mapRenderer;
    private AutoClaimManager autoClaimManager;
    private AutoUnclaimManager autoUnclaimManager;

    @Override
    public void onEnable() {
        this.injector = Guice.createInjector(new GuildsModule(this));
        this.guildManager = injector.getInstance(GuildManager.class);

        // Initialize services
        this.guildService = injector.getInstance(GuildService.class);
        this.relationshipService = injector.getInstance(RelationshipService.class);
        this.subregionService = injector.getInstance(SubregionService.class);
        SelectionManager selectionManager = injector.getInstance(SelectionManager.class);
        this.typeRegistry = injector.getInstance(SubregionTypeRegistry.class);
        RegionPermissionService regionPermissionService = injector.getInstance(RegionPermissionService.class);
        org.aincraft.subregion.RegionTypeLimitRepository limitRepository = injector.getInstance(org.aincraft.subregion.RegionTypeLimitRepository.class);
        this.regionComponent = new RegionComponent(guildService, subregionService, selectionManager, typeRegistry, regionPermissionService, limitRepository);

        // Initialize map renderer
        ChunkClaimRepository chunkClaimRepository = injector.getInstance(ChunkClaimRepository.class);
        GuildColorMapper colorMapper = new GuildColorMapper();
        this.mapRenderer = new GuildMapRenderer(guildService, chunkClaimRepository, colorMapper, relationshipService);

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

        // Register auto-claim system
        this.autoClaimManager = injector.getInstance(AutoClaimManager.class);
        AutoClaimListener autoClaimListener = injector.getInstance(AutoClaimListener.class);
        getServer().getPluginManager().registerEvents(autoClaimListener, this);

        // Register auto-unclaim system
        this.autoUnclaimManager = injector.getInstance(AutoUnclaimManager.class);
        AutoUnclaimListener autoUnclaimListener = injector.getInstance(AutoUnclaimListener.class);
        getServer().getPluginManager().registerEvents(autoUnclaimListener, this);

        // Register multiblock system
        this.multiblockRegistry = injector.getInstance(MultiblockRegistry.class);
        MultiblockListener multiblockListener = injector.getInstance(MultiblockListener.class);
        getServer().getPluginManager().registerEvents(multiblockListener, this);

        // Register vault pattern and listeners
        multiblockRegistry.registerBuiltIn(GuildVaultPattern.create());
        VaultService vaultService = injector.getInstance(VaultService.class);
        this.vaultComponent = new VaultComponent(vaultService);
        this.logComponent = new LogComponent(guildService, vaultService);
        VaultHandler vaultHandler = injector.getInstance(VaultHandler.class);
        VaultGUIListener vaultGUIListener = injector.getInstance(VaultGUIListener.class);
        getServer().getPluginManager().registerEvents(vaultHandler, this);
        getServer().getPluginManager().registerEvents(vaultGUIListener, this);

        // Register role creation wizard listener
        RoleCreationGUIListener roleGUIListener = injector.getInstance(RoleCreationGUIListener.class);
        getServer().getPluginManager().registerEvents(roleGUIListener, this);

        // Register selection visualizer listener (for cleanup on quit)
        SelectionVisualizerListener selectionVisualizerListener = injector.getInstance(SelectionVisualizerListener.class);
        getServer().getPluginManager().registerEvents(selectionVisualizerListener, this);

        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            registerGuildCommands(commands);
        });

        getLogger().info("Guilds plugin enabled!");
    }

    @Override
    public void onDisable() {
        // Clear auto-claim state
        if (autoClaimManager != null) {
            autoClaimManager.clearAll();
        }
        // Clear auto-unclaim state
        if (autoUnclaimManager != null) {
            autoUnclaimManager.clearAll();
        }
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

    private CompletableFuture<Suggestions> suggestColors(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        // Suggest named colors
        builder.suggest("clear");
        builder.suggest("black");
        builder.suggest("dark_blue");
        builder.suggest("dark_green");
        builder.suggest("dark_aqua");
        builder.suggest("dark_red");
        builder.suggest("dark_purple");
        builder.suggest("gold");
        builder.suggest("gray");
        builder.suggest("dark_gray");
        builder.suggest("blue");
        builder.suggest("green");
        builder.suggest("aqua");
        builder.suggest("red");
        builder.suggest("light_purple");
        builder.suggest("yellow");
        builder.suggest("white");

        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestToggleSettings(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        builder.suggest("explosions");
        builder.suggest("fire");
        builder.suggest("public");
        return builder.buildFuture();
    }

    // ==================== Command Registration ====================

    private void registerGuildCommands(Commands commands) {
        commands.register(
            Commands.literal("g")
                .executes(context -> {
                    context.getSource().getSender().sendMessage("Usage: /g <create|join|leave|disband|info|list|claim|unclaim|kick|spawn|setspawn|role|member|region|map|vault|ally|enemy|neutral>");
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
                // Disband command
                .then(Commands.literal("disband")
                    .executes(this::handleDisbandGuild)
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
                    .then(Commands.literal("toggle")
                        .executes(this::handleAutoClaimToggle)
                        .then(Commands.literal("silent")
                            .executes(this::handleAutoClaimToggleSilent)
                        )
                    )
                )
                // Unclaim command
                .then(Commands.literal("unclaim")
                    .executes(this::handleUnclaimChunk)
                    .then(Commands.literal("toggle")
                        .executes(this::handleAutoUnclaimToggle)
                        .then(Commands.literal("silent")
                            .executes(this::handleAutoUnclaimToggleSilent)
                        )
                    )
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
                // Invite command
                .then(Commands.literal("invite")
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(context -> handleInviteCommand(context, StringArgumentType.getString(context, "playerName")))
                    )
                )
                // Accept command
                .then(Commands.literal("accept")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> handleAcceptCommand(context, StringArgumentType.getString(context, "guildName")))
                    )
                )
                // Decline command
                .then(Commands.literal("decline")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> handleDeclineCommand(context, StringArgumentType.getString(context, "guildName")))
                    )
                )
                // Invites command
                .then(Commands.literal("invites")
                    .executes(this::handleInvitesCommand)
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
                        .suggests(this::suggestColors)
                        .executes(context -> handleColorCommand(context, StringArgumentType.getString(context, "color")))
                    )
                    .executes(this::handleColorCommandNoArg)
                )
                // Description command
                .then(Commands.literal("description")
                    .then(Commands.argument("description", StringArgumentType.greedyString())
                        .executes(context -> handleDescriptionCommand(context, StringArgumentType.getString(context, "description")))
                    )
                    .executes(this::handleDescriptionCommandNoArg)
                )
                // Name command
                .then(Commands.literal("name")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> handleNameCommand(context, StringArgumentType.getString(context, "name")))
                    )
                    .executes(this::handleNameCommandNoArg)
                )
                // Toggle command
                .then(Commands.literal("toggle")
                    .then(Commands.argument("setting", StringArgumentType.word())
                        .suggests(this::suggestToggleSettings)
                        .executes(context -> handleToggleCommand(context, StringArgumentType.getString(context, "setting")))
                    )
                    .executes(this::handleToggleCommandNoArg)
                )
                // Role command
                .then(registerRoleCommands())
                // Member command
                .then(Commands.literal("member")
                    .executes(context -> handleMemberCommand(context))
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(context -> handleMemberCommand(context, StringArgumentType.getString(context, "playerName")))
                    )
                )
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
                // Log command
                .then(registerLogCommands())
                // Ally command
                .then(registerAllyCommands())
                // Enemy command
                .then(registerEnemyCommands())
                // Neutral command
                .then(Commands.literal("neutral")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> handleNeutralCommand(context, StringArgumentType.getString(context, "guildName")))
                    )
                )
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
                    .then(Commands.argument("permissions", IntegerArgumentType.integer())
                        .executes(context -> handleRoleCommand(context, "create",
                            StringArgumentType.getString(context, "name"),
                            String.valueOf(IntegerArgumentType.getInteger(context, "permissions"))))
                        .then(Commands.argument("priority", IntegerArgumentType.integer())
                            .executes(context -> handleRoleCommand(context, "create",
                                StringArgumentType.getString(context, "name"),
                                String.valueOf(IntegerArgumentType.getInteger(context, "permissions")),
                                String.valueOf(IntegerArgumentType.getInteger(context, "priority"))))
                        )
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
            .then(Commands.literal("setperm")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .then(Commands.literal("player")
                        .then(Commands.argument("playerName", StringArgumentType.word())
                            .suggests(this::suggestPlayerNames)
                            .then(Commands.argument("permissions", IntegerArgumentType.integer(0))
                                .executes(context -> handleRegionCommand(context, new String[]{"region", "setperm",
                                    StringArgumentType.getString(context, "regionName"),
                                    "player",
                                    StringArgumentType.getString(context, "playerName"),
                                    String.valueOf(IntegerArgumentType.getInteger(context, "permissions"))}))
                            )
                        )
                    )
                    .then(Commands.literal("role")
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .then(Commands.argument("permissions", IntegerArgumentType.integer(0))
                                .executes(context -> handleRegionCommand(context, new String[]{"region", "setperm",
                                    StringArgumentType.getString(context, "regionName"),
                                    "role",
                                    StringArgumentType.getString(context, "roleName"),
                                    String.valueOf(IntegerArgumentType.getInteger(context, "permissions"))}))
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("removeperm")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .then(Commands.literal("player")
                        .then(Commands.argument("playerName", StringArgumentType.word())
                            .suggests(this::suggestPlayerNames)
                            .executes(context -> handleRegionCommand(context, new String[]{"region", "removeperm",
                                StringArgumentType.getString(context, "regionName"),
                                "player",
                                StringArgumentType.getString(context, "playerName")}))
                        )
                    )
                    .then(Commands.literal("role")
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .executes(context -> handleRegionCommand(context, new String[]{"region", "removeperm",
                                StringArgumentType.getString(context, "regionName"),
                                "role",
                                StringArgumentType.getString(context, "roleName")}))
                        )
                    )
                )
            )
            .then(Commands.literal("listperms")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .executes(context -> handleRegionCommand(context, new String[]{"region", "listperms",
                        StringArgumentType.getString(context, "regionName")}))
                )
            )
            .then(Commands.literal("role")
                .then(Commands.literal("create")
                    .then(Commands.argument("regionName", StringArgumentType.word())
                        .suggests(this::suggestRegionNames)
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .then(Commands.argument("permissions", IntegerArgumentType.integer(0))
                                .executes(context -> handleRegionCommand(context, new String[]{"region", "role", "create",
                                    StringArgumentType.getString(context, "regionName"),
                                    StringArgumentType.getString(context, "roleName"),
                                    String.valueOf(IntegerArgumentType.getInteger(context, "permissions"))}))
                            )
                        )
                    )
                )
                .then(Commands.literal("delete")
                    .then(Commands.argument("regionName", StringArgumentType.word())
                        .suggests(this::suggestRegionNames)
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .executes(context -> handleRegionCommand(context, new String[]{"region", "role", "delete",
                                StringArgumentType.getString(context, "regionName"),
                                StringArgumentType.getString(context, "roleName")}))
                        )
                    )
                )
                .then(Commands.literal("list")
                    .then(Commands.argument("regionName", StringArgumentType.word())
                        .suggests(this::suggestRegionNames)
                        .executes(context -> handleRegionCommand(context, new String[]{"region", "role", "list",
                            StringArgumentType.getString(context, "regionName")}))
                    )
                )
                .then(Commands.literal("assign")
                    .then(Commands.argument("regionName", StringArgumentType.word())
                        .suggests(this::suggestRegionNames)
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .then(Commands.argument("playerName", StringArgumentType.word())
                                .suggests(this::suggestPlayerNames)
                                .executes(context -> handleRegionCommand(context, new String[]{"region", "role", "assign",
                                    StringArgumentType.getString(context, "regionName"),
                                    StringArgumentType.getString(context, "roleName"),
                                    StringArgumentType.getString(context, "playerName")}))
                            )
                        )
                    )
                )
                .then(Commands.literal("unassign")
                    .then(Commands.argument("regionName", StringArgumentType.word())
                        .suggests(this::suggestRegionNames)
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .then(Commands.argument("playerName", StringArgumentType.word())
                                .suggests(this::suggestPlayerNames)
                                .executes(context -> handleRegionCommand(context, new String[]{"region", "role", "unassign",
                                    StringArgumentType.getString(context, "regionName"),
                                    StringArgumentType.getString(context, "roleName"),
                                    StringArgumentType.getString(context, "playerName")}))
                            )
                        )
                    )
                )
                .then(Commands.literal("members")
                    .then(Commands.argument("regionName", StringArgumentType.word())
                        .suggests(this::suggestRegionNames)
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .executes(context -> handleRegionCommand(context, new String[]{"region", "role", "members",
                                StringArgumentType.getString(context, "regionName"),
                                StringArgumentType.getString(context, "roleName")}))
                        )
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
            .then(Commands.literal("destroy")
                .then(Commands.literal("confirm")
                    .executes(context -> handleVaultCommand(context, new String[]{"vault", "destroy", "confirm"}))
                )
            )
            .build();
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> registerLogCommands() {
        return Commands.literal("log")
            .then(Commands.literal("claim")
                .executes(context -> handleLogCommand(context, new String[]{"log", "claim"}))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int page = IntegerArgumentType.getInteger(context, "page");
                        return handleLogCommand(context, new String[]{"log", "claim", String.valueOf(page)});
                    })
                )
            )
            .then(Commands.literal("vault")
                .executes(context -> handleLogCommand(context, new String[]{"log", "vault"}))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int page = IntegerArgumentType.getInteger(context, "page");
                        return handleLogCommand(context, new String[]{"log", "vault", String.valueOf(page)});
                    })
                )
            )
            .build();
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> registerAllyCommands() {
        return Commands.literal("ally")
            // Default: /g ally <guild-name> to propose
            .then(Commands.argument("guildName", StringArgumentType.word())
                .suggests(this::suggestGuildNames)
                .executes(context -> handleAllyCommand(context, null, StringArgumentType.getString(context, "guildName")))
            )
            .then(Commands.literal("accept")
                .then(Commands.argument("guildName", StringArgumentType.word())
                    .suggests(this::suggestGuildNames)
                    .executes(context -> handleAllyCommand(context, "accept", StringArgumentType.getString(context, "guildName")))
                )
            )
            .then(Commands.literal("reject")
                .then(Commands.argument("guildName", StringArgumentType.word())
                    .suggests(this::suggestGuildNames)
                    .executes(context -> handleAllyCommand(context, "reject", StringArgumentType.getString(context, "guildName")))
                )
            )
            .then(Commands.literal("break")
                .then(Commands.argument("guildName", StringArgumentType.word())
                    .suggests(this::suggestGuildNames)
                    .executes(context -> handleAllyCommand(context, "break", StringArgumentType.getString(context, "guildName")))
                )
            )
            .then(Commands.literal("list")
                .executes(context -> handleAllyCommand(context, "list", null))
            )
            .build();
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> registerEnemyCommands() {
        return Commands.literal("enemy")
            .then(Commands.literal("declare")
                .then(Commands.argument("guildName", StringArgumentType.word())
                    .suggests(this::suggestGuildNames)
                    .executes(context -> handleEnemyCommand(context, "declare", StringArgumentType.getString(context, "guildName")))
                )
            )
            .then(Commands.literal("list")
                .executes(context -> handleEnemyCommand(context, "list", null))
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

        // Display toggles
        String explosions = guild.isExplosionsAllowed() ? "§aEnabled" : "§cDisabled";
        String fire = guild.isFireAllowed() ? "§aEnabled" : "§cDisabled";
        String isPublic = guild.isPublic() ? "§aPublic" : "§cPrivate";

        sender.sendMessage("§7Explosions: " + explosions);
        sender.sendMessage("§7Fire Spread: " + fire);
        sender.sendMessage("§7Access: " + isPublic);

        int claimedChunks = guildService.getGuildChunkCount(guild.getId());
        int maxChunks = guild.getMaxChunks();
        sender.sendMessage("§7Chunks: §f" + claimedChunks + "/" + maxChunks);

        // Display relationships for player if available
        if (sender instanceof Player) {
            displayGuildRelationships((Player) sender, guild);
        }

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
            sender.sendMessage("§7• §f" + guild.getName() + " §8- §7" + guild.getMemberCount() + " members");
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

        // Convert to hex and validate
        String hexColor = ColorConverter.toHex(colorInput);
        if (hexColor == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Invalid color format. Use hex (#RRGGBB) or a named color"));
            return 0;
        }

        guild.setColor(hexColor);
        guildService.save(guild);
        player.sendMessage(MessageFormatter.deserialize("<green>Guild color set to <gold>" + hexColor + "</gold></green>"));
        return 1;
    }

    private boolean isValidColor(String color) {
        return ColorConverter.isValidColor(color);
    }

    private int handleDescriptionCommandNoArg(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g description <description> or /g description clear"));
        return 0;
    }

    private int handleDescriptionCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String description) {
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
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only the guild owner can change guild description"));
            return 0;
        }

        String descriptionInput = description.toLowerCase();

        // Handle clear command
        if (descriptionInput.equals("clear")) {
            guild.setDescription(null);
            guildService.save(guild);
            player.sendMessage(MessageFormatter.deserialize("<green>Guild description cleared</green>"));
            return 1;
        }

        guild.setDescription(description);
        guildService.save(guild);
        player.sendMessage(MessageFormatter.deserialize("<green>Guild description set to: <gold>" + description + "</gold></green>"));
        return 1;
    }

    private int handleNameCommandNoArg(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g name <name>"));
        return 0;
    }

    private int handleNameCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
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
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only the guild owner can change guild name"));
            return 0;
        }

        String newName = name.trim();

        // Validate name
        if (newName.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild name cannot be empty"));
            return 0;
        }

        if (newName.length() > 32) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Guild name cannot be longer than 32 characters"));
            return 0;
        }

        // Check if name already exists
        if (guildService.getGuildByName(newName) != null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "A guild with that name already exists"));
            return 0;
        }

        guild.setName(newName);
        guildService.save(guild);
        player.sendMessage(MessageFormatter.deserialize("<green>Guild name changed to: <gold>" + newName + "</gold></green>"));
        return 1;
    }

    private int handleToggleCommandNoArg(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g toggle <explosions|fire>"));
        return 0;
    }

    private int handleToggleCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String setting) {
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
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only the guild owner can toggle guild settings"));
            return 0;
        }

        String settingLower = setting.toLowerCase();
        switch (settingLower) {
            case "explosions", "explosion" -> {
                boolean newValue = !guild.isExplosionsAllowed();
                guild.setExplosionsAllowed(newValue);
                guildService.save(guild);
                String status = newValue ? "<green>enabled</green>" : "<red>disabled</red>";
                player.sendMessage(MessageFormatter.deserialize("<green>Explosions " + status + " in guild territory</green>"));
                return 1;
            }
            case "fire" -> {
                boolean newValue = !guild.isFireAllowed();
                guild.setFireAllowed(newValue);
                guildService.save(guild);
                String status = newValue ? "<green>enabled</green>" : "<red>disabled</red>";
                player.sendMessage(MessageFormatter.deserialize("<green>Fire spread " + status + " in guild territory</green>"));
                return 1;
            }
            default -> {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Unknown setting: " + setting));
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Available settings: explosions, fire"));
                return 0;
            }
        }
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

            // Auto-claim the chunk where guild was created
            ChunkKey chunk = ChunkKey.from(player.getLocation().getChunk());
            ClaimResult claimResult = guildService.claimChunk(guild.getId(), player.getUniqueId(), chunk);
            if (claimResult.isSuccess()) {
                player.sendMessage("§a✓ Automatically claimed chunk at §f" + chunk.x() + ", " + chunk.z());
                player.sendMessage("§a✓ Homeblock and spawn set!");
            } else {
                player.sendMessage("§eWarning: Could not auto-claim chunk. Reason: " + claimResult.getReason());
            }

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

    private int handleDisbandGuild(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can disband guilds!");
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
            player.sendMessage("§aYour guild has been disbanded!");
            return 1;
        } else {
            player.sendMessage("§cOnly guild owners can disband guilds!");
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
        player.sendMessage("§7Description: §f" + (guild.getDescription().isEmpty() ? "No description" : guild.getDescription()));
        player.sendMessage("§7Members: §f" + guild.getMemberCount() + "/" + guild.getMaxMembers());
        player.sendMessage("§7Owner: §f" + (guild.isOwner(player.getUniqueId()) ? "You" : "Not you"));
        player.sendMessage("§7Created: §f" + new Date(guild.getCreatedAt()));

        // Display toggles
        String explosions = guild.isExplosionsAllowed() ? "§aEnabled" : "§cDisabled";
        String fire = guild.isFireAllowed() ? "§aEnabled" : "§cDisabled";
        String isPublic = guild.isPublic() ? "§aPublic" : "§cPrivate";

        player.sendMessage("§7Explosions: " + explosions);
        player.sendMessage("§7Fire Spread: " + fire);
        player.sendMessage("§7Access: " + isPublic);

        int claimedChunks = guildService.getGuildChunkCount(guild.getId());
        int maxChunks = guild.getMaxChunks();
        player.sendMessage("§7Chunks: §f" + claimedChunks + "/" + maxChunks);

        // Display relationships
        displayGuildRelationships(player, guild);

        return 1;
    }

    /**
     * Displays guild relationships (allies and enemies).
     */
    private void displayGuildRelationships(Player player, Guild guild) {
        List<String> allies = relationshipService.getAllies(guild.getId());
        List<String> enemies = relationshipService.getEnemies(guild.getId());

        // Display allies
        if (!allies.isEmpty()) {
            player.sendMessage("§a✓ Allies: §2" + allies.size());
            for (String allyGuildId : allies) {
                Guild allyGuild = guildService.getGuildById(allyGuildId);
                if (allyGuild != null) {
                    player.sendMessage("  §2• " + allyGuild.getName());
                }
            }
        } else {
            player.sendMessage("§aAllies: §8None");
        }

        // Display enemies
        if (!enemies.isEmpty()) {
            player.sendMessage("§c✗ Enemies: §4" + enemies.size());
            for (String enemyGuildId : enemies) {
                Guild enemyGuild = guildService.getGuildById(enemyGuildId);
                if (enemyGuild != null) {
                    player.sendMessage("  §c• " + enemyGuild.getName());
                }
            }
        } else {
            player.sendMessage("§cEnemies: §8None");
        }
    }

    private int handleGuildList(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        if (guildManager.getAllGuilds().isEmpty()) {
            sender.sendMessage("§7No guilds exist yet!");
            return 1;
        }

        sender.sendMessage("§6=== Guild List ===");
        for (Guild guild : guildManager.getAllGuilds()) {
            sender.sendMessage("§7• §f" + guild.getName() + " §8- §7" + guild.getMemberCount() + " members");
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
        ClaimResult result = guildManager.claimChunk(player);
        if (result.isSuccess()) {
            Chunk chunk = player.getLocation().getChunk();
            player.sendMessage(MessageFormatter.format(
                "<green>Claimed chunk at <gold>%s, %s</gold></green>",
                chunk.getX(), chunk.getZ()));
            return 1;
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.getReason()));
            return 0;
        }
    }

    private int handleAutoClaimToggle(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use auto-claim"));
            return 0;
        }

        Player player = (Player) sender;

        // Check guild membership
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return 0;
        }

        // Toggle auto-claim (not silent)
        AutoClaimState newState = autoClaimManager.toggleAutoClaim(player.getUniqueId(), false);

        // Send feedback
        if (newState.isEnabled()) {
            player.sendMessage(MessageFormatter.deserialize("<green>Auto-claim enabled</green>"));
        } else {
            player.sendMessage(MessageFormatter.deserialize("<red>Auto-claim disabled</red>"));
        }

        return 1;
    }

    private int handleAutoClaimToggleSilent(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use auto-claim"));
            return 0;
        }

        Player player = (Player) sender;

        // Check guild membership
        Guild guild = guildService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return 0;
        }

        // Toggle auto-claim with silent mode
        AutoClaimState newState = autoClaimManager.toggleAutoClaim(player.getUniqueId(), true);

        // Send feedback
        if (newState.isEnabled()) {
            player.sendMessage(MessageFormatter.deserialize("<green>Auto-claim enabled in <yellow>silent mode</yellow></green>"));
        } else {
            player.sendMessage(MessageFormatter.deserialize("<red>Auto-claim disabled</red>"));
        }

        return 1;
    }

    private int handleAutoUnclaimToggle(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        UnclaimToggleComponent component = new UnclaimToggleComponent(guildService, autoUnclaimManager);
        component.execute(context.getSource().getSender(), new String[]{"unclaim", "toggle"});
        return 1;
    }

    private int handleAutoUnclaimToggleSilent(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        UnclaimToggleComponent component = new UnclaimToggleComponent(guildService, autoUnclaimManager);
        component.execute(context.getSource().getSender(), new String[]{"unclaim", "toggle", "silent"});
        return 1;
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

    private int handleInviteCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String playerName) {
        CommandSender sender = context.getSource().getSender();
        org.aincraft.commands.components.InviteComponent inviteComponent = injector.getInstance(org.aincraft.commands.components.InviteComponent.class);
        inviteComponent.execute(sender, new String[]{"invite", playerName});
        return 1;
    }

    private int handleAcceptCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String guildName) {
        CommandSender sender = context.getSource().getSender();
        org.aincraft.commands.components.AcceptComponent acceptComponent = injector.getInstance(org.aincraft.commands.components.AcceptComponent.class);
        acceptComponent.execute(sender, new String[]{"accept", guildName});
        return 1;
    }

    private int handleDeclineCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String guildName) {
        CommandSender sender = context.getSource().getSender();
        org.aincraft.commands.components.DeclineComponent declineComponent = injector.getInstance(org.aincraft.commands.components.DeclineComponent.class);
        declineComponent.execute(sender, new String[]{"decline", guildName});
        return 1;
    }

    private int handleInvitesCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        org.aincraft.commands.components.InvitesComponent invitesComponent = injector.getInstance(org.aincraft.commands.components.InvitesComponent.class);
        invitesComponent.execute(sender, new String[]{"invites"});
        return 1;
    }

    private int handleRoleCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String... args) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can manage roles!");
            return 0;
        }

        // Reconstruct arguments for component
        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = "role";
        System.arraycopy(args, 0, fullArgs, 1, args.length);

        // Delegate to role component
        org.aincraft.commands.components.RoleComponent roleComponent =
            new org.aincraft.commands.components.RoleComponent(guildService);
        roleComponent.execute(sender, fullArgs);
        return 1;
    }

    private int handleMemberCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String... args) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can check member info!");
            return 0;
        }

        // Reconstruct arguments for component
        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = "member";
        System.arraycopy(args, 0, fullArgs, 1, args.length);

        // Delegate to member component
        org.aincraft.commands.components.MemberComponent memberComponent =
            new org.aincraft.commands.components.MemberComponent(guildService, injector.getInstance(org.aincraft.storage.MemberRoleRepository.class));
        memberComponent.execute(sender, fullArgs);
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

    private int handleLogCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String[] args) {
        CommandSender sender = context.getSource().getSender();
        logComponent.execute(sender, args);
        return 1;
    }

    private int handleAllyCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String subcommand, String guildName) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can manage alliances"));
            return 0;
        }

        // Build args array based on whether subcommand is provided
        String[] args;
        if (subcommand == null) {
            // Direct ally: /g ally <guild-name>
            args = new String[]{"ally", guildName};
        } else if (guildName == null) {
            // List: /g ally list
            args = new String[]{"ally", subcommand};
        } else {
            // Subcommand with guild: /g ally accept <guild-name>
            args = new String[]{"ally", subcommand, guildName};
        }

        org.aincraft.commands.components.AllyComponent allyComponent =
            new org.aincraft.commands.components.AllyComponent(guildService, relationshipService);
        allyComponent.execute(sender, args);
        return 1;
    }

    private int handleEnemyCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String subcommand, String guildName) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can manage enemies"));
            return 0;
        }

        String[] args = guildName == null
            ? new String[]{"enemy", subcommand}
            : new String[]{"enemy", subcommand, guildName};

        org.aincraft.commands.components.EnemyComponent enemyComponent =
            new org.aincraft.commands.components.EnemyComponent(guildService, relationshipService);
        enemyComponent.execute(sender, args);
        return 1;
    }

    private int handleNeutralCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String guildName) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can declare neutral"));
            return 0;
        }

        String[] args = new String[]{"neutral", guildName};

        org.aincraft.commands.components.NeutralComponent neutralComponent =
            new org.aincraft.commands.components.NeutralComponent(guildService, relationshipService);
        neutralComponent.execute(sender, args);
        return 1;
    }
}