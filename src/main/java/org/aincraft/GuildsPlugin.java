package org.aincraft;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.aincraft.database.SchemaManager;
import org.aincraft.claim.AutoClaimListener;
import org.aincraft.claim.AutoClaimManager;
import org.aincraft.claim.ClaimEntryNotifier;
import org.aincraft.claim.ClaimMovementTracker;
import org.aincraft.commands.components.AcceptComponent;
import org.aincraft.commands.components.AdminComponent;
import org.aincraft.commands.components.AllyComponent;
import org.aincraft.commands.components.AutoComponent;
import org.aincraft.commands.components.ClaimComponent;
import org.aincraft.commands.components.ColorComponent;
import org.aincraft.commands.components.CreateComponent;
import org.aincraft.commands.components.DeclineComponent;
import org.aincraft.commands.components.DescriptionComponent;
import org.aincraft.commands.components.DisbandComponent;
import org.aincraft.commands.components.EnemyComponent;
import org.aincraft.commands.components.InfoComponent;
import org.aincraft.commands.components.InviteComponent;
import org.aincraft.commands.components.InvitesComponent;
import org.aincraft.commands.components.JoinComponent;
import org.aincraft.commands.components.KickComponent;
import org.aincraft.commands.components.LeaveComponent;
import org.aincraft.commands.components.ListComponent;
import org.aincraft.commands.components.LogComponent;
import org.aincraft.commands.components.MapComponent;
import org.aincraft.commands.components.MemberComponent;
import org.aincraft.commands.components.NameComponent;
import org.aincraft.commands.components.NeutralComponent;
import org.aincraft.commands.components.OwnerComponent;
import org.aincraft.commands.components.region.RegionComponent;
import org.aincraft.commands.components.RoleComponent;
import org.aincraft.commands.components.SetspawnComponent;
import org.aincraft.commands.components.SkillsComponent;
import org.aincraft.commands.components.SpawnComponent;
import org.aincraft.commands.components.ToggleComponent;
import org.aincraft.commands.components.UnclaimComponent;
import org.aincraft.commands.components.GuildChatComponent;
import org.aincraft.commands.components.AllyChatComponent;
import org.aincraft.commands.components.OfficerChatComponent;
import org.aincraft.commands.components.LevelUpComponent;
import org.aincraft.commands.components.ProjectComponent;
import org.aincraft.chat.GuildChatListener;
import org.aincraft.project.BuffApplicationService;
import org.aincraft.project.BuffCategoryRegistry;
import org.aincraft.project.ProjectRegistry;
import org.aincraft.project.listeners.QuestProgressListener;
import org.aincraft.inject.GuildsModule;
import org.aincraft.progression.ProgressionConfig;
import org.aincraft.progression.listeners.ProgressionXpListener;
import org.aincraft.progression.listeners.ProgressionPlaytimeTask;
import org.aincraft.listeners.GuildProtectionListener;
import org.aincraft.map.GuildColorMapper;
import org.aincraft.map.GuildMapRenderer;
import org.aincraft.multiblock.MultiblockListener;
import org.aincraft.multiblock.MultiblockRegistry;
import org.aincraft.multiblock.patterns.GuildVaultPattern;
import org.aincraft.storage.ChunkClaimRepository;
import org.aincraft.subregion.RegionEntryNotifier;
import org.aincraft.subregion.RegionMovementTracker;
import org.aincraft.subregion.RegionPermissionService;
import org.aincraft.subregion.SelectionManager;
import org.aincraft.subregion.SelectionVisualizerListener;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.aincraft.vault.VaultComponent;
import org.aincraft.vault.VaultHandler;
import org.aincraft.vault.VaultService;
import org.aincraft.vault.gui.VaultGUIListener;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main Guilds plugin class - handles plugin lifecycle and command registration.
 * Delegates all command logic to injected component classes.
 */
public class GuildsPlugin extends JavaPlugin {
    private Injector injector;
    private GuildService guildService;
    private RelationshipService relationshipService;
    private SubregionService subregionService;
    private SubregionTypeRegistry typeRegistry;
    private MultiblockRegistry multiblockRegistry;
    private BuffCategoryRegistry buffCategoryRegistry;
    private GuildMapRenderer mapRenderer;
    private AutoClaimManager autoClaimManager;

    // Injected command components
    private CreateComponent createComponent;
    private JoinComponent joinComponent;
    private LeaveComponent leaveComponent;
    private DisbandComponent disbandComponent;
    private InfoComponent infoComponent;
    private ListComponent listComponent;
    private SpawnComponent spawnComponent;
    private SetspawnComponent setspawnComponent;
    private ColorComponent colorComponent;
    private DescriptionComponent descriptionComponent;
    private NameComponent nameComponent;
    private ToggleComponent toggleComponent;
    private MapComponent mapComponent;
    private KickComponent kickComponent;
    private ClaimComponent claimComponent;
    private UnclaimComponent unclaimComponent;
    private AutoComponent autoComponent;
    private InviteComponent inviteComponent;
    private AcceptComponent acceptComponent;
    private DeclineComponent declineComponent;
    private InvitesComponent invitesComponent;
    private RoleComponent roleComponent;
    private MemberComponent memberComponent;
    private OwnerComponent ownerComponent;
    private RegionComponent regionComponent;
    private VaultComponent vaultComponent;
    private LogComponent logComponent;
    private AllyComponent allyComponent;
    private EnemyComponent enemyComponent;
    private NeutralComponent neutralComponent;
    private GuildChatComponent guildChatComponent;
    private AllyChatComponent allyChatComponent;
    private OfficerChatComponent officerChatComponent;
    private AdminComponent adminComponent;
    private LevelUpComponent levelUpComponent;
    private ProjectComponent projectComponent;
    private SkillsComponent skillsComponent;

    @Override
    public void onEnable() {
        this.injector = Guice.createInjector(new GuildsModule(this));

        // Initialize database schema (must be first)
        injector.getInstance(SchemaManager.class);

        // Initialize core services
        this.guildService = injector.getInstance(GuildService.class);
        this.relationshipService = injector.getInstance(RelationshipService.class);
        this.subregionService = injector.getInstance(SubregionService.class);
        this.typeRegistry = injector.getInstance(SubregionTypeRegistry.class);
        this.multiblockRegistry = injector.getInstance(MultiblockRegistry.class);
        this.buffCategoryRegistry = injector.getInstance(BuffCategoryRegistry.class);

        // Initialize command components
        initializeComponents();

        // Initialize map renderer
        ChunkClaimRepository chunkClaimRepository = injector.getInstance(ChunkClaimRepository.class);
        GuildColorMapper colorMapper = new GuildColorMapper();
        org.aincraft.service.GuildLifecycleService lifecycleService = injector.getInstance(org.aincraft.service.GuildLifecycleService.class);
        org.aincraft.service.GuildMemberService memberServiceForMap = injector.getInstance(org.aincraft.service.GuildMemberService.class);
        this.mapRenderer = new GuildMapRenderer(lifecycleService, memberServiceForMap, chunkClaimRepository, colorMapper, relationshipService);

        // Register listeners
        registerListeners();

        // Register claim tracking
        registerClaimTracking();

        // Register auto-claim/unclaim system
        registerAutoClaimSystem();

        // Register multiblock system
        registerMultiblockSystem();

        // Register progression system
        registerProgressionSystem();

        // Register project system
        registerProjectSystem();

        // Register commands
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            registerGuildCommands(commands);
            registerChatCommands(commands);
        });

        getLogger().info("Guilds plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (autoClaimManager != null) {
            autoClaimManager.clearAll();
        }
        // Save all open vault inventories before shutdown
        if (injector != null) {
            var vaultManager = injector.getInstance(org.aincraft.vault.gui.SharedVaultInventoryManager.class);
            if (vaultManager != null) {
                vaultManager.saveAllAndClear();
            }
        }
        getLogger().info("Guilds plugin disabled!");
    }

    private void initializeComponents() {
        createComponent = injector.getInstance(CreateComponent.class);
        joinComponent = injector.getInstance(JoinComponent.class);
        leaveComponent = injector.getInstance(LeaveComponent.class);
        disbandComponent = injector.getInstance(DisbandComponent.class);
        infoComponent = injector.getInstance(InfoComponent.class);
        listComponent = injector.getInstance(ListComponent.class);
        spawnComponent = injector.getInstance(SpawnComponent.class);
        setspawnComponent = injector.getInstance(SetspawnComponent.class);
        colorComponent = injector.getInstance(ColorComponent.class);
        descriptionComponent = injector.getInstance(DescriptionComponent.class);
        nameComponent = injector.getInstance(NameComponent.class);
        toggleComponent = injector.getInstance(ToggleComponent.class);
        mapComponent = injector.getInstance(MapComponent.class);
        kickComponent = injector.getInstance(KickComponent.class);
        claimComponent = injector.getInstance(ClaimComponent.class);
        unclaimComponent = injector.getInstance(UnclaimComponent.class);
        autoComponent = injector.getInstance(AutoComponent.class);
        inviteComponent = injector.getInstance(InviteComponent.class);
        acceptComponent = injector.getInstance(AcceptComponent.class);
        declineComponent = injector.getInstance(DeclineComponent.class);
        invitesComponent = injector.getInstance(InvitesComponent.class);
        roleComponent = injector.getInstance(RoleComponent.class);
        memberComponent = injector.getInstance(MemberComponent.class);
        ownerComponent = injector.getInstance(OwnerComponent.class);
        regionComponent = injector.getInstance(RegionComponent.class);

        VaultService vaultService = injector.getInstance(VaultService.class);
        org.aincraft.vault.gui.SharedVaultInventoryManager sharedVaultInventoryManager = injector.getInstance(org.aincraft.vault.gui.SharedVaultInventoryManager.class);
        org.aincraft.progression.storage.ProgressionLogRepository progressionLogRepository = injector.getInstance(org.aincraft.progression.storage.ProgressionLogRepository.class);
        vaultComponent = new VaultComponent(vaultService, sharedVaultInventoryManager);
        logComponent = injector.getInstance(LogComponent.class);

        allyComponent = injector.getInstance(AllyComponent.class);
        enemyComponent = injector.getInstance(EnemyComponent.class);
        neutralComponent = injector.getInstance(NeutralComponent.class);
        guildChatComponent = injector.getInstance(GuildChatComponent.class);
        allyChatComponent = injector.getInstance(AllyChatComponent.class);
        officerChatComponent = injector.getInstance(OfficerChatComponent.class);
        adminComponent = injector.getInstance(AdminComponent.class);
        levelUpComponent = injector.getInstance(LevelUpComponent.class);
        projectComponent = injector.getInstance(ProjectComponent.class);
        skillsComponent = injector.getInstance(SkillsComponent.class);
    }

    private void registerListeners() {
        GuildProtectionListener protectionListener = injector.getInstance(GuildProtectionListener.class);
        getServer().getPluginManager().registerEvents(protectionListener, this);

        SelectionVisualizerListener selectionVisualizerListener = injector.getInstance(SelectionVisualizerListener.class);
        getServer().getPluginManager().registerEvents(selectionVisualizerListener, this);

        VaultHandler vaultHandler = injector.getInstance(VaultHandler.class);
        VaultGUIListener vaultGUIListener = injector.getInstance(VaultGUIListener.class);
        getServer().getPluginManager().registerEvents(vaultHandler, this);
        getServer().getPluginManager().registerEvents(vaultGUIListener, this);


        GuildChatListener chatListener = injector.getInstance(GuildChatListener.class);
        getServer().getPluginManager().registerEvents(chatListener, this);
    }

    private void registerClaimTracking() {
        RegionMovementTracker movementTracker = injector.getInstance(RegionMovementTracker.class);
        RegionEntryNotifier entryNotifier = injector.getInstance(RegionEntryNotifier.class);
        getServer().getPluginManager().registerEvents(movementTracker, this);
        getServer().getPluginManager().registerEvents(entryNotifier, this);

        ClaimMovementTracker claimTracker = injector.getInstance(ClaimMovementTracker.class);
        ClaimEntryNotifier claimNotifier = injector.getInstance(ClaimEntryNotifier.class);
        getServer().getPluginManager().registerEvents(claimTracker, this);
        getServer().getPluginManager().registerEvents(claimNotifier, this);
    }

    private void registerAutoClaimSystem() {
        this.autoClaimManager = injector.getInstance(AutoClaimManager.class);
        AutoClaimListener autoClaimListener = injector.getInstance(AutoClaimListener.class);
        getServer().getPluginManager().registerEvents(autoClaimListener, this);
    }

    private void registerMultiblockSystem() {
        MultiblockListener multiblockListener = injector.getInstance(MultiblockListener.class);
        getServer().getPluginManager().registerEvents(multiblockListener, this);
        multiblockRegistry.registerBuiltIn(GuildVaultPattern.create());
    }

    private void registerProgressionSystem() {
        // Register XP listener
        ProgressionXpListener xpListener = injector.getInstance(ProgressionXpListener.class);
        getServer().getPluginManager().registerEvents(xpListener, this);

        // Schedule playtime task
        ProgressionConfig progressionConfig = injector.getInstance(ProgressionConfig.class);
        ProgressionPlaytimeTask playtimeTask = injector.getInstance(ProgressionPlaytimeTask.class);
        int intervalTicks = progressionConfig.getPlaytimeCheckInterval() * 20; // Convert seconds to ticks
        playtimeTask.runTaskTimer(this, intervalTicks, intervalTicks);

        getLogger().info("Guild progression system registered");
    }

    private void registerProjectSystem() {
        // Register quest progress listener
        QuestProgressListener questListener = injector.getInstance(QuestProgressListener.class);
        getServer().getPluginManager().registerEvents(questListener, this);

        // Schedule buff expiration cleanup
        ProjectRegistry registry = injector.getInstance(ProjectRegistry.class);
        BuffApplicationService buffService = injector.getInstance(BuffApplicationService.class);
        int intervalTicks = registry.getExpirationCheckInterval() * 20;
        getServer().getScheduler().runTaskTimerAsynchronously(this, buffService::cleanupExpiredBuffs, intervalTicks, intervalTicks);

        getLogger().info("Guild project system registered");
    }

    public SubregionTypeRegistry getTypeRegistry() {
        return typeRegistry;
    }

    public MultiblockRegistry getMultiblockRegistry() {
        return multiblockRegistry;
    }

    /**
     * Gets the buff category registry for registering custom buffs.
     * This is the public API for external plugins to register custom buff types.
     *
     * @return the buff category registry
     */
    public BuffCategoryRegistry getBuffCategoryRegistry() {
        return buffCategoryRegistry;
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
        String[] colors = {"clear", "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
                           "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
        for (String color : colors) {
            builder.suggest(color);
        }
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
                    context.getSource().getSender().sendMessage("Usage: /g <create|join|leave|disband|info|list|claim|unclaim|kick|spawn|setspawn|role|member|region|map|vault|ally|enemy|neutral|invite|accept|decline|invites|admin>");
                    return 1;
                })
                .then(Commands.literal("create")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            String desc = "";
                            createComponent.execute(context.getSource().getSender(), new String[]{"create", name, desc});
                            return 1;
                        })
                        .then(Commands.argument("description", StringArgumentType.greedyString())
                            .executes(context -> {
                                String name = StringArgumentType.getString(context, "name");
                                String desc = StringArgumentType.getString(context, "description");
                                createComponent.execute(context.getSource().getSender(), new String[]{"create", name, desc});
                                return 1;
                            }))))
                .then(Commands.literal("join")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> {
                            String guildName = StringArgumentType.getString(context, "guildName");
                            joinComponent.execute(context.getSource().getSender(), new String[]{"join", guildName});
                            return 1;
                        })))
                .then(Commands.literal("leave")
                    .executes(context -> {
                        leaveComponent.execute(context.getSource().getSender(), new String[]{"leave"});
                        return 1;
                    }))
                .then(Commands.literal("disband")
                    .executes(context -> {
                        disbandComponent.execute(context.getSource().getSender(), new String[]{"disband"});
                        return 1;
                    }))
                .then(Commands.literal("info")
                    .executes(context -> {
                        infoComponent.execute(context.getSource().getSender(), new String[]{"info"});
                        return 1;
                    })
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> {
                            String guildName = StringArgumentType.getString(context, "guildName");
                            infoComponent.execute(context.getSource().getSender(), new String[]{"info", guildName});
                            return 1;
                        })))
                .then(Commands.literal("list")
                    .executes(context -> {
                        listComponent.execute(context.getSource().getSender(), new String[]{"list"});
                        return 1;
                    })
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int page = IntegerArgumentType.getInteger(context, "page");
                            listComponent.execute(context.getSource().getSender(), new String[]{"list", String.valueOf(page)});
                            return 1;
                        })))
                .then(Commands.literal("claim")
                    .executes(context -> {
                        claimComponent.execute(context.getSource().getSender(), new String[]{"claim"});
                        return 1;
                    }))
                .then(Commands.literal("unclaim")
                    .executes(context -> {
                        unclaimComponent.execute(context.getSource().getSender(), new String[]{"unclaim"});
                        return 1;
                    })
                    .then(Commands.literal("all")
                        .executes(context -> {
                            unclaimComponent.execute(context.getSource().getSender(), new String[]{"unclaim", "all"});
                            return 1;
                        })))
                .then(Commands.literal("auto")
                    .executes(context -> {
                        autoComponent.execute(context.getSource().getSender(), new String[]{"auto"});
                        return 1;
                    })
                    .then(Commands.literal("claim")
                        .executes(context -> {
                            autoComponent.execute(context.getSource().getSender(), new String[]{"auto", "claim"});
                            return 1;
                        }))
                    .then(Commands.literal("unclaim")
                        .executes(context -> {
                            autoComponent.execute(context.getSource().getSender(), new String[]{"auto", "unclaim"});
                            return 1;
                        }))
                    .then(Commands.literal("off")
                        .executes(context -> {
                            autoComponent.execute(context.getSource().getSender(), new String[]{"auto", "off"});
                            return 1;
                        })))
                .then(Commands.literal("kick")
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "playerName");
                            kickComponent.execute(context.getSource().getSender(), new String[]{"kick", playerName});
                            return 1;
                        })))
                .then(Commands.literal("invite")
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "playerName");
                            inviteComponent.execute(context.getSource().getSender(), new String[]{"invite", playerName});
                            return 1;
                        })))
                .then(Commands.literal("accept")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> {
                            String guildName = StringArgumentType.getString(context, "guildName");
                            acceptComponent.execute(context.getSource().getSender(), new String[]{"accept", guildName});
                            return 1;
                        })))
                .then(Commands.literal("decline")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> {
                            String guildName = StringArgumentType.getString(context, "guildName");
                            declineComponent.execute(context.getSource().getSender(), new String[]{"decline", guildName});
                            return 1;
                        })))
                .then(Commands.literal("invites")
                    .executes(context -> {
                        invitesComponent.execute(context.getSource().getSender(), new String[]{"invites"});
                        return 1;
                    }))
                .then(Commands.literal("spawn")
                    .executes(context -> {
                        spawnComponent.execute(context.getSource().getSender(), new String[]{"spawn"});
                        return 1;
                    }))
                .then(Commands.literal("setspawn")
                    .executes(context -> {
                        setspawnComponent.execute(context.getSource().getSender(), new String[]{"setspawn"});
                        return 1;
                    }))
                .then(Commands.literal("color")
                    .then(Commands.argument("color", StringArgumentType.word())
                        .suggests(this::suggestColors)
                        .executes(context -> {
                            String color = StringArgumentType.getString(context, "color");
                            colorComponent.execute(context.getSource().getSender(), new String[]{"color", color});
                            return 1;
                        }))
                    .executes(context -> {
                        colorComponent.execute(context.getSource().getSender(), new String[]{"color"});
                        return 1;
                    }))
                .then(Commands.literal("description")
                    .then(Commands.argument("description", StringArgumentType.greedyString())
                        .executes(context -> {
                            String description = StringArgumentType.getString(context, "description");
                            descriptionComponent.execute(context.getSource().getSender(), new String[]{"description", description});
                            return 1;
                        }))
                    .executes(context -> {
                        descriptionComponent.execute(context.getSource().getSender(), new String[]{"description"});
                        return 1;
                    }))
                .then(Commands.literal("name")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            nameComponent.execute(context.getSource().getSender(), new String[]{"name", name});
                            return 1;
                        }))
                    .executes(context -> {
                        nameComponent.execute(context.getSource().getSender(), new String[]{"name"});
                        return 1;
                    }))
                .then(Commands.literal("toggle")
                    .then(Commands.argument("setting", StringArgumentType.word())
                        .suggests(this::suggestToggleSettings)
                        .executes(context -> {
                            String setting = StringArgumentType.getString(context, "setting");
                            toggleComponent.execute(context.getSource().getSender(), new String[]{"toggle", setting});
                            return 1;
                        }))
                    .executes(context -> {
                        toggleComponent.execute(context.getSource().getSender(), new String[]{"toggle"});
                        return 1;
                    }))
                .then(Commands.literal("role")
                    .executes(context -> {
                        roleComponent.execute(context.getSource().getSender(), new String[]{"role"});
                        return 1;
                    })
                    .then(Commands.literal("editor")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(this::suggestRoleNames)
                            .executes(context -> {
                                String name = StringArgumentType.getString(context, "name");
                                roleComponent.execute(context.getSource().getSender(), new String[]{"role", "editor", name});
                                return 1;
                            })))
                    .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("perms", StringArgumentType.word())
                                .then(Commands.argument("priority", IntegerArgumentType.integer())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");
                                        String perms = StringArgumentType.getString(context, "perms");
                                        int priority = IntegerArgumentType.getInteger(context, "priority");
                                        roleComponent.execute(context.getSource().getSender(), new String[]{"role", "create", name, perms, String.valueOf(priority)});
                                        return 1;
                                    })))))
                    .then(Commands.literal("copy")
                        .then(Commands.argument("source", StringArgumentType.word())
                            .suggests(this::suggestRoleNames)
                            .then(Commands.argument("newName", StringArgumentType.word())
                                .executes(context -> {
                                    String source = StringArgumentType.getString(context, "source");
                                    String newName = StringArgumentType.getString(context, "newName");
                                    roleComponent.execute(context.getSource().getSender(), new String[]{"role", "copy", source, newName});
                                    return 1;
                                }))))
                    .then(Commands.literal("delete")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(this::suggestRoleNames)
                            .executes(context -> {
                                String name = StringArgumentType.getString(context, "name");
                                roleComponent.execute(context.getSource().getSender(), new String[]{"role", "delete", name});
                                return 1;
                            })))
                    .then(Commands.literal("perm")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(this::suggestRoleNames)
                            .then(Commands.argument("perm", StringArgumentType.word())
                                .suggests(this::suggestPermissions)
                                .then(Commands.argument("value", StringArgumentType.word())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");
                                        String perm = StringArgumentType.getString(context, "perm");
                                        String value = StringArgumentType.getString(context, "value");
                                        roleComponent.execute(context.getSource().getSender(), new String[]{"role", "perm", name, perm, value});
                                        return 1;
                                    })))))
                    .then(Commands.literal("priority")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(this::suggestRoleNames)
                            .then(Commands.argument("priority", IntegerArgumentType.integer())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    int priority = IntegerArgumentType.getInteger(context, "priority");
                                    roleComponent.execute(context.getSource().getSender(), new String[]{"role", "priority", name, String.valueOf(priority)});
                                    return 1;
                                }))))
                    .then(Commands.literal("list")
                        .executes(context -> {
                            roleComponent.execute(context.getSource().getSender(), new String[]{"role", "list"});
                            return 1;
                        }))
                    .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .suggests(this::suggestRoleNames)
                            .executes(context -> {
                                String name = StringArgumentType.getString(context, "name");
                                roleComponent.execute(context.getSource().getSender(), new String[]{"role", "info", name});
                                return 1;
                            })))
                    .then(Commands.literal("assign")
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(this::suggestPlayerNames)
                            .then(Commands.argument("role", StringArgumentType.word())
                                .suggests(this::suggestRoleNames)
                                .executes(context -> {
                                    String player = StringArgumentType.getString(context, "player");
                                    String role = StringArgumentType.getString(context, "role");
                                    roleComponent.execute(context.getSource().getSender(), new String[]{"role", "assign", player, role});
                                    return 1;
                                }))))
                    .then(Commands.literal("unassign")
                        .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(this::suggestPlayerNames)
                            .then(Commands.argument("role", StringArgumentType.word())
                                .suggests(this::suggestRoleNames)
                                .executes(context -> {
                                    String player = StringArgumentType.getString(context, "player");
                                    String role = StringArgumentType.getString(context, "role");
                                    roleComponent.execute(context.getSource().getSender(), new String[]{"role", "unassign", player, role});
                                    return 1;
                                })))))
                .then(Commands.literal("member")
                    .executes(context -> {
                        memberComponent.execute(context.getSource().getSender(), new String[]{"member"});
                        return 1;
                    })
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "playerName");
                            memberComponent.execute(context.getSource().getSender(), new String[]{"member", playerName});
                            return 1;
                        })))
                .then(Commands.literal("owner")
                    .executes(context -> {
                        ownerComponent.execute(context.getSource().getSender(), new String[]{"owner"});
                        return 1;
                    }))
                .then(Commands.literal("map")
                    .executes(context -> {
                        mapComponent.execute(context.getSource().getSender(), new String[]{"map", "1"});
                        return 1;
                    })
                    .then(Commands.argument("size", IntegerArgumentType.integer(1, 5))
                        .executes(context -> {
                            int size = IntegerArgumentType.getInteger(context, "size");
                            mapComponent.execute(context.getSource().getSender(), new String[]{"map", String.valueOf(size)});
                            return 1;
                        })))
                .then(registerRegionCommands())
                .then(registerVaultCommands())
                .then(registerLogCommands())
                .then(Commands.literal("ally")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> {
                            String guildName = StringArgumentType.getString(context, "guildName");
                            allyComponent.execute(context.getSource().getSender(), new String[]{"ally", guildName});
                            return 1;
                        }))
                    .then(Commands.literal("accept")
                        .then(Commands.argument("guildName", StringArgumentType.word())
                            .suggests(this::suggestGuildNames)
                            .executes(context -> {
                                String guildName = StringArgumentType.getString(context, "guildName");
                                allyComponent.execute(context.getSource().getSender(), new String[]{"ally", "accept", guildName});
                                return 1;
                            })))
                    .then(Commands.literal("reject")
                        .then(Commands.argument("guildName", StringArgumentType.word())
                            .suggests(this::suggestGuildNames)
                            .executes(context -> {
                                String guildName = StringArgumentType.getString(context, "guildName");
                                allyComponent.execute(context.getSource().getSender(), new String[]{"ally", "reject", guildName});
                                return 1;
                            })))
                    .then(Commands.literal("break")
                        .then(Commands.argument("guildName", StringArgumentType.word())
                            .suggests(this::suggestGuildNames)
                            .executes(context -> {
                                String guildName = StringArgumentType.getString(context, "guildName");
                                allyComponent.execute(context.getSource().getSender(), new String[]{"ally", "break", guildName});
                                return 1;
                            })))
                    .then(Commands.literal("list")
                        .executes(context -> {
                            allyComponent.execute(context.getSource().getSender(), new String[]{"ally", "list"});
                            return 1;
                        })))
                .then(Commands.literal("enemy")
                    .then(Commands.literal("declare")
                        .then(Commands.argument("guildName", StringArgumentType.word())
                            .suggests(this::suggestGuildNames)
                            .executes(context -> {
                                String guildName = StringArgumentType.getString(context, "guildName");
                                enemyComponent.execute(context.getSource().getSender(), new String[]{"enemy", "declare", guildName});
                                return 1;
                            })))
                    .then(Commands.literal("list")
                        .executes(context -> {
                            enemyComponent.execute(context.getSource().getSender(), new String[]{"enemy", "list"});
                            return 1;
                        })))
                .then(Commands.literal("neutral")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> {
                            String guildName = StringArgumentType.getString(context, "guildName");
                            neutralComponent.execute(context.getSource().getSender(), new String[]{"neutral", guildName});
                            return 1;
                        })))
                .then(Commands.literal("upgrade")
                    .executes(context -> {
                        levelUpComponent.execute(context.getSource().getSender(), new String[]{"upgrade"});
                        return 1;
                    })
                    .then(Commands.literal("confirm")
                        .executes(context -> {
                            levelUpComponent.execute(context.getSource().getSender(), new String[]{"upgrade", "confirm"});
                            return 1;
                        })))
                .then(Commands.literal("skills")
                    .executes(context -> {
                        skillsComponent.execute(context.getSource().getSender(), new String[]{"skills"});
                        return 1;
                    }))
                .then(registerProjectCommands())
                .then(registerAdminCommands())
                .build(),
            "Guild management commands",
            List.of("guild")
        );
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> registerAdminCommands() {
        return Commands.literal("admin")
            .executes(context -> {
                adminComponent.execute(context.getSource().getSender(), new String[]{"admin"});
                return 1;
            })
            .then(Commands.literal("disband")
                .then(Commands.argument("guildName", StringArgumentType.word())
                    .suggests(this::suggestGuildNames)
                    .executes(context -> {
                        String guildName = StringArgumentType.getString(context, "guildName");
                        adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "disband", guildName});
                        return 1;
                    })))
            .then(Commands.literal("addchunk")
                .then(Commands.argument("guildName", StringArgumentType.word())
                    .suggests(this::suggestGuildNames)
                    .executes(context -> {
                        String guildName = StringArgumentType.getString(context, "guildName");
                        adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "addchunk", guildName});
                        return 1;
                    })))
            .then(Commands.literal("removechunk")
                .executes(context -> {
                    adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "removechunk"});
                    return 1;
                }))
            .then(Commands.literal("setowner")
                .then(Commands.argument("guildName", StringArgumentType.word())
                    .suggests(this::suggestGuildNames)
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(context -> {
                            String guildName = StringArgumentType.getString(context, "guildName");
                            String playerName = StringArgumentType.getString(context, "playerName");
                            adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "setowner", guildName, playerName});
                            return 1;
                        }))))
            .then(Commands.literal("bypass")
                .executes(context -> {
                    adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "bypass"});
                    return 1;
                }))
            .then(Commands.literal("set")
                .then(Commands.literal("level")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                            .executes(context -> {
                                String guildName = StringArgumentType.getString(context, "guildName");
                                int level = IntegerArgumentType.getInteger(context, "level");
                                adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "set", "level", guildName, String.valueOf(level)});
                                return 1;
                            }))))
                .then(Commands.literal("xp")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                            .executes(context -> {
                                String guildName = StringArgumentType.getString(context, "guildName");
                                long amount = LongArgumentType.getLong(context, "amount");
                                adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "set", "xp", guildName, String.valueOf(amount)});
                                return 1;
                            })))))
            .then(Commands.literal("add")
                .then(Commands.literal("level")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .then(Commands.argument("levels", IntegerArgumentType.integer(1))
                            .executes(context -> {
                                String guildName = StringArgumentType.getString(context, "guildName");
                                int levels = IntegerArgumentType.getInteger(context, "levels");
                                adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "add", "level", guildName, String.valueOf(levels)});
                                return 1;
                            }))))
                .then(Commands.literal("xp")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(context -> {
                                String guildName = StringArgumentType.getString(context, "guildName");
                                long amount = LongArgumentType.getLong(context, "amount");
                                adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "add", "xp", guildName, String.valueOf(amount)});
                                return 1;
                            })))))
            .then(Commands.literal("remove")
                .then(Commands.literal("level")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .then(Commands.argument("levels", IntegerArgumentType.integer(1))
                            .executes(context -> {
                                String guildName = StringArgumentType.getString(context, "guildName");
                                int levels = IntegerArgumentType.getInteger(context, "levels");
                                adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "remove", "level", guildName, String.valueOf(levels)});
                                return 1;
                            }))))
                .then(Commands.literal("xp")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(context -> {
                                String guildName = StringArgumentType.getString(context, "guildName");
                                long amount = LongArgumentType.getLong(context, "amount");
                                adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "remove", "xp", guildName, String.valueOf(amount)});
                                return 1;
                            })))))
            .then(Commands.literal("reset")
                .then(Commands.literal("level")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> {
                            String guildName = StringArgumentType.getString(context, "guildName");
                            adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "reset", "level", guildName});
                            return 1;
                        })))
                .then(Commands.literal("xp")
                    .then(Commands.argument("guildName", StringArgumentType.word())
                        .suggests(this::suggestGuildNames)
                        .executes(context -> {
                            String guildName = StringArgumentType.getString(context, "guildName");
                            adminComponent.execute(context.getSource().getSender(), new String[]{"admin", "reset", "xp", guildName});
                            return 1;
                        }))))
            .build();
    }

    private void registerChatCommands(Commands commands) {
        // /gc command - Guild chat
        commands.register(
            Commands.literal("gc")
                .executes(context -> {
                    guildChatComponent.execute(context.getSource().getSender(), new String[]{});
                    return 1;
                })
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        guildChatComponent.execute(context.getSource().getSender(), new String[]{message});
                        return 1;
                    }))
                .build(),
            "Guild chat",
            List.of("guildchat")
        );

        // /ac command - Ally chat
        commands.register(
            Commands.literal("ac")
                .executes(context -> {
                    allyChatComponent.execute(context.getSource().getSender(), new String[]{});
                    return 1;
                })
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        allyChatComponent.execute(context.getSource().getSender(), new String[]{message});
                        return 1;
                    }))
                .build(),
            "Ally chat",
            List.of("allychat")
        );

        // /oc command - Officer chat
        commands.register(
            Commands.literal("oc")
                .executes(context -> {
                    officerChatComponent.execute(context.getSource().getSender(), new String[]{});
                    return 1;
                })
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        officerChatComponent.execute(context.getSource().getSender(), new String[]{message});
                        return 1;
                    }))
                .build(),
            "Officer chat",
            List.of("officerchat")
        );
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> registerRegionCommands() {
        return Commands.literal("region")
            .executes(context -> {
                regionComponent.execute(context.getSource().getSender(), new String[]{"region"});
                return 1;
            })
            .then(Commands.literal("pos1")
                .executes(context -> {
                    regionComponent.execute(context.getSource().getSender(), new String[]{"region", "pos1"});
                    return 1;
                }))
            .then(Commands.literal("pos2")
                .executes(context -> {
                    regionComponent.execute(context.getSource().getSender(), new String[]{"region", "pos2"});
                    return 1;
                }))
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        regionComponent.execute(context.getSource().getSender(), new String[]{"region", "create", name});
                        return 1;
                    })
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(this::suggestRegionTypes)
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            String type = StringArgumentType.getString(context, "type");
                            regionComponent.execute(context.getSource().getSender(), new String[]{"region", "create", name, type});
                            return 1;
                        }))))
            .then(Commands.literal("cancel")
                .executes(context -> {
                    regionComponent.execute(context.getSource().getSender(), new String[]{"region", "cancel"});
                    return 1;
                }))
            .then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        regionComponent.execute(context.getSource().getSender(), new String[]{"region", "delete", name});
                        return 1;
                    })))
            .then(Commands.literal("list")
                .executes(context -> {
                    regionComponent.execute(context.getSource().getSender(), new String[]{"region", "list"});
                    return 1;
                }))
            .then(Commands.literal("info")
                .executes(context -> {
                    regionComponent.execute(context.getSource().getSender(), new String[]{"region", "info"});
                    return 1;
                })
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        regionComponent.execute(context.getSource().getSender(), new String[]{"region", "info", name});
                        return 1;
                    })))
            .then(Commands.literal("visualize")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        regionComponent.execute(context.getSource().getSender(), new String[]{"region", "visualize", name});
                        return 1;
                    })))
            .then(Commands.literal("types")
                .executes(context -> {
                    regionComponent.execute(context.getSource().getSender(), new String[]{"region", "types"});
                    return 1;
                }))
            .then(Commands.literal("settype")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(this::suggestRegionTypes)
                        .executes(context -> {
                            String regionName = StringArgumentType.getString(context, "regionName");
                            String type = StringArgumentType.getString(context, "type");
                            regionComponent.execute(context.getSource().getSender(), new String[]{"region", "settype", regionName, type});
                            return 1;
                        }))))
            .then(Commands.literal("addowner")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(context -> {
                            String regionName = StringArgumentType.getString(context, "regionName");
                            String playerName = StringArgumentType.getString(context, "playerName");
                            regionComponent.execute(context.getSource().getSender(), new String[]{"region", "addowner", regionName, playerName});
                            return 1;
                        }))))
            .then(Commands.literal("removeowner")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .suggests(this::suggestRegionNames)
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests(this::suggestPlayerNames)
                        .executes(context -> {
                            String regionName = StringArgumentType.getString(context, "regionName");
                            String playerName = StringArgumentType.getString(context, "playerName");
                            regionComponent.execute(context.getSource().getSender(), new String[]{"region", "removeowner", regionName, playerName});
                            return 1;
                        }))))
            .then(Commands.literal("role")
                .then(Commands.literal("create")
                    .then(Commands.argument("regionName", StringArgumentType.word())
                        .suggests(this::suggestRegionNames)
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .then(Commands.argument("permissions", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    String regionName = StringArgumentType.getString(context, "regionName");
                                    String roleName = StringArgumentType.getString(context, "roleName");
                                    int perms = IntegerArgumentType.getInteger(context, "permissions");
                                    regionComponent.execute(context.getSource().getSender(), new String[]{"region", "role", "create", regionName, roleName, String.valueOf(perms)});
                                    return 1;
                                })))))
                .then(Commands.literal("delete")
                    .then(Commands.argument("regionName", StringArgumentType.word())
                        .suggests(this::suggestRegionNames)
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .executes(context -> {
                                String regionName = StringArgumentType.getString(context, "regionName");
                                String roleName = StringArgumentType.getString(context, "roleName");
                                regionComponent.execute(context.getSource().getSender(), new String[]{"region", "role", "delete", regionName, roleName});
                                return 1;
                            }))))
                .then(Commands.literal("list")
                    .then(Commands.argument("regionName", StringArgumentType.word())
                        .suggests(this::suggestRegionNames)
                        .executes(context -> {
                            String regionName = StringArgumentType.getString(context, "regionName");
                            regionComponent.execute(context.getSource().getSender(), new String[]{"region", "role", "list", regionName});
                            return 1;
                        })))
                .then(Commands.literal("assign")
                    .then(Commands.argument("regionName", StringArgumentType.word())
                        .suggests(this::suggestRegionNames)
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .then(Commands.argument("playerName", StringArgumentType.word())
                                .suggests(this::suggestPlayerNames)
                                .executes(context -> {
                                    String regionName = StringArgumentType.getString(context, "regionName");
                                    String roleName = StringArgumentType.getString(context, "roleName");
                                    String playerName = StringArgumentType.getString(context, "playerName");
                                    regionComponent.execute(context.getSource().getSender(), new String[]{"region", "role", "assign", regionName, roleName, playerName});
                                    return 1;
                                })))))
                .then(Commands.literal("unassign")
                    .then(Commands.argument("regionName", StringArgumentType.word())
                        .suggests(this::suggestRegionNames)
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .then(Commands.argument("playerName", StringArgumentType.word())
                                .suggests(this::suggestPlayerNames)
                                .executes(context -> {
                                    String regionName = StringArgumentType.getString(context, "regionName");
                                    String roleName = StringArgumentType.getString(context, "roleName");
                                    String playerName = StringArgumentType.getString(context, "playerName");
                                    regionComponent.execute(context.getSource().getSender(), new String[]{"region", "role", "unassign", regionName, roleName, playerName});
                                    return 1;
                                })))))
                .then(Commands.literal("members")
                    .then(Commands.argument("regionName", StringArgumentType.word())
                        .suggests(this::suggestRegionNames)
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .executes(context -> {
                                String regionName = StringArgumentType.getString(context, "regionName");
                                String roleName = StringArgumentType.getString(context, "roleName");
                                regionComponent.execute(context.getSource().getSender(), new String[]{"region", "role", "members", regionName, roleName});
                                return 1;
                            })))))
            .build();
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> registerVaultCommands() {
        return Commands.literal("vault")
            .executes(context -> {
                vaultComponent.execute(context.getSource().getSender(), new String[]{"vault"});
                return 1;
            })
            .then(Commands.literal("open")
                .executes(context -> {
                    vaultComponent.execute(context.getSource().getSender(), new String[]{"vault", "open"});
                    return 1;
                }))
            .then(Commands.literal("info")
                .executes(context -> {
                    vaultComponent.execute(context.getSource().getSender(), new String[]{"vault", "info"});
                    return 1;
                }))
            .then(Commands.literal("destroy")
                .then(Commands.literal("confirm")
                    .executes(context -> {
                        vaultComponent.execute(context.getSource().getSender(), new String[]{"vault", "destroy", "confirm"});
                        return 1;
                    })))
            .build();
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> registerProjectCommands() {
        return Commands.literal("project")
            .executes(context -> {
                projectComponent.execute(context.getSource().getSender(), new String[]{"project"});
                return 1;
            })
            .then(Commands.literal("list")
                .executes(context -> {
                    projectComponent.execute(context.getSource().getSender(), new String[]{"project", "list"});
                    return 1;
                }))
            .then(Commands.literal("start")
                .then(Commands.argument("projectId", StringArgumentType.word())
                    .executes(context -> {
                        String projectId = StringArgumentType.getString(context, "projectId");
                        projectComponent.execute(context.getSource().getSender(), new String[]{"project", "start", projectId});
                        return 1;
                    })))
            .then(Commands.literal("progress")
                .executes(context -> {
                    projectComponent.execute(context.getSource().getSender(), new String[]{"project", "progress"});
                    return 1;
                }))
            .then(Commands.literal("contribute")
                .executes(context -> {
                    projectComponent.execute(context.getSource().getSender(), new String[]{"project", "contribute"});
                    return 1;
                }))
            .then(Commands.literal("complete")
                .executes(context -> {
                    projectComponent.execute(context.getSource().getSender(), new String[]{"project", "complete"});
                    return 1;
                }))
            .then(Commands.literal("abandon")
                .executes(context -> {
                    projectComponent.execute(context.getSource().getSender(), new String[]{"project", "abandon"});
                    return 1;
                }))
            .then(Commands.literal("buffs")
                .executes(context -> {
                    projectComponent.execute(context.getSource().getSender(), new String[]{"project", "buffs"});
                    return 1;
                }))
            .build();
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> registerLogCommands() {
        return Commands.literal("log")
            .then(Commands.literal("claim")
                .executes(context -> {
                    logComponent.execute(context.getSource().getSender(), new String[]{"log", "claim"});
                    return 1;
                })
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int page = IntegerArgumentType.getInteger(context, "page");
                        logComponent.execute(context.getSource().getSender(), new String[]{"log", "claim", String.valueOf(page)});
                        return 1;
                    })))
            .then(Commands.literal("vault")
                .executes(context -> {
                    logComponent.execute(context.getSource().getSender(), new String[]{"log", "vault"});
                    return 1;
                })
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int page = IntegerArgumentType.getInteger(context, "page");
                        logComponent.execute(context.getSource().getSender(), new String[]{"log", "vault", String.valueOf(page)});
                        return 1;
                    })))
            .then(Commands.literal("progression")
                .executes(context -> {
                    logComponent.execute(context.getSource().getSender(), new String[]{"log", "progression"});
                    return 1;
                })
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int page = IntegerArgumentType.getInteger(context, "page");
                        logComponent.execute(context.getSource().getSender(), new String[]{"log", "progression", String.valueOf(page)});
                        return 1;
                    })))
            .build();
    }
}
