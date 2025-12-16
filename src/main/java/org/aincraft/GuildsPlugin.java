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
import org.aincraft.subregion.SelectionManager;
import org.aincraft.subregion.SubregionService;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.aincraft.subregion.RegionMovementTracker;
import org.aincraft.subregion.RegionEntryNotifier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Chunk;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public class GuildsPlugin extends JavaPlugin {
    private Injector injector;
    private GuildManager guildManager;
    private RegionComponent regionComponent;
    private SubregionTypeRegistry typeRegistry;

    @Override
    public void onEnable() {
        this.injector = Guice.createInjector(new GuildsModule(this));
        this.guildManager = injector.getInstance(GuildManager.class);

        // Initialize region component
        GuildService guildService = injector.getInstance(GuildService.class);
        SubregionService subregionService = injector.getInstance(SubregionService.class);
        SelectionManager selectionManager = injector.getInstance(SelectionManager.class);
        this.typeRegistry = injector.getInstance(SubregionTypeRegistry.class);
        this.regionComponent = new RegionComponent(guildService, subregionService, selectionManager, typeRegistry);

        // Register protection listener
        GuildProtectionListener protectionListener = injector.getInstance(GuildProtectionListener.class);
        getServer().getPluginManager().registerEvents(protectionListener, this);

        // Register region movement tracking and notifications
        RegionMovementTracker movementTracker = injector.getInstance(RegionMovementTracker.class);
        RegionEntryNotifier entryNotifier = injector.getInstance(RegionEntryNotifier.class);
        getServer().getPluginManager().registerEvents(movementTracker, this);
        getServer().getPluginManager().registerEvents(entryNotifier, this);

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

    private void registerGuildCommands(Commands commands) {
        commands.register(
            Commands.literal("g")
                .executes(context -> {
                    context.getSource().getSender().sendMessage("Usage: /g <create|join|leave|delete|info|list|claim|unclaim|kick|role>");
                    return 1;
                })
                .then(Commands.literal("create")
                    .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(context -> {
                            return handleCreateGuild(context,
                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"),
                                "");
                        })
                        .then(Commands.argument("description", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                            .executes(context -> {
                                return handleCreateGuild(context,
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"),
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "description"));
                            })
                        )
                    )
                )
                .then(Commands.literal("join")
                    .then(Commands.argument("guildName", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(context -> {
                            return handleJoinGuild(context,
                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "guildName"));
                        })
                    )
                )
                .then(Commands.literal("leave")
                    .executes(this::handleLeaveGuild)
                )
                .then(Commands.literal("delete")
                    .executes(this::handleDeleteGuild)
                )
                .then(Commands.literal("info")
                    .executes(this::handleGuildInfo)
                )
                .then(Commands.literal("list")
                    .executes(this::handleGuildList)
                )
                .then(Commands.literal("claim")
                    .executes(this::handleClaimChunk)
                )
                .then(Commands.literal("unclaim")
                    .executes(this::handleUnclaimChunk)
                )
                .then(Commands.literal("kick")
                    .then(Commands.argument("playerName", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(context -> {
                            return handleKickMember(context,
                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "playerName"));
                        })
                    )
                )
                .then(Commands.literal("role")
                    .then(Commands.argument("action", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(context -> {
                            return handleRoleCommand(context,
                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "action"));
                        })
                        .then(Commands.argument("args", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                            .executes(context -> {
                                return handleRoleCommand(context,
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "action"),
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(context, "args"));
                            })
                        )
                    )
                )
                .then(Commands.literal("region")
                    .executes(context -> handleRegionCommand(context, new String[]{"region"}))
                    .then(Commands.argument("subcommand", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(context -> {
                            String subCmd = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "subcommand");
                            return handleRegionCommand(context, new String[]{"region", subCmd});
                        })
                        .then(Commands.argument("args", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                            .executes(context -> {
                                String subCmd = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "subcommand");
                                String args = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "args");
                                String[] fullArgs = ("region " + subCmd + " " + args).split(" ");
                                return handleRegionCommand(context, fullArgs);
                            })
                        )
                    )
                )
                .build(),
            "Guild management commands",
            List.of("guild")
        );
    }

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

    private int handleRoleCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String action) {
        return handleRoleCommand(context, action, "");
    }

    private int handleRoleCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String action, String args) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can manage roles!");
            return 0;
        }

        Player player = (Player) sender;
        // TODO: Implement role command handling
        player.sendMessage("§cRole management not yet implemented in plugin command!");
        return 0;
    }

    private int handleRegionCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String[] args) {
        CommandSender sender = context.getSource().getSender();
        regionComponent.execute(sender, args);
        return 1;
    }
}