package dev.mintychochip.guilds.commands;



import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.commands.brigadier.ChatBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.MapBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.AllianceBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.PermBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.PlotBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.PlotTypeBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.QuestBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.SpecializationBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.TechTreeBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildBroadcastBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildLevelBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildPermBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildsGeneralBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.TerritoryBrigadierCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Central registry for all Brigadier commands in Guilds
 */
public class BrigadierCommandRegistry {

    /** The plugin. */
    private final JavaPlugin plugin;

    // Brigadier command handlers
    /** The guild command. */
    private final GuildBrigadierCommand guildCommand;
    /** The plot command. */
    private final PlotBrigadierCommand plotCommand;
    /** The guilds general command. */
    private final GuildsGeneralBrigadierCommand guildsGeneralCommand;
    /** The guild level command. */
    private final GuildLevelBrigadierCommand guildLevelCommand;
    /** The map command. */
    private final MapBrigadierCommand mapCommand;
    /** The perm command. */
    private final PermBrigadierCommand permCommand;
    /** The plot type command. */
    private final PlotTypeBrigadierCommand plotTypeCommand;
    /** The guild broadcast command. */
    private final GuildBroadcastBrigadierCommand guildBroadcastCommand;
    /** The guild perm command. */
    private final GuildPermBrigadierCommand guildPermCommand;
    /** The tech tree command. */
    private final TechTreeBrigadierCommand techTreeCommand;
    /** The chat command. */
    private final ChatBrigadierCommand chatCommand;
    /** The alliance command. */
    private final AllianceBrigadierCommand allianceCommand;
    /** The specialization command. */
    private final SpecializationBrigadierCommand specializationCommand;
    /** The territory command. */
    private final TerritoryBrigadierCommand territoryCommand;
    /** The quest command. */
    private final QuestBrigadierCommand questCommand;

    /**
     * Creates a new brigadier command registry instance.
     * @param plugin the plugin
     * @param guildCommand the guild command
     * @param plotCommand the plot command
     * @param guildsGeneralCommand the guilds general command
     * @param guildLevelCommand the guild level command
     * @param mapCommand the map command
     * @param permCommand the perm command
     * @param plotTypeCommand the plot type command
     * @param guildBroadcastCommand the guild broadcast command
     * @param guildPermCommand the guild perm command
     * @param techTreeCommand the tech tree command
     * @param chatCommand the chat command
     * @param allianceCommand the alliance command
     * @param specializationCommand the specialization command
     * @param territoryCommand the territory command
     * @param questCommand the quest command
     */
    public BrigadierCommandRegistry(JavaPlugin plugin,
                                    GuildBrigadierCommand guildCommand,
                                    PlotBrigadierCommand plotCommand,
                                    GuildsGeneralBrigadierCommand guildsGeneralCommand,
                                    GuildLevelBrigadierCommand guildLevelCommand,
                                    MapBrigadierCommand mapCommand,
                                    PermBrigadierCommand permCommand,
                                    PlotTypeBrigadierCommand plotTypeCommand,
                                    GuildBroadcastBrigadierCommand guildBroadcastCommand,
                                    GuildPermBrigadierCommand guildPermCommand,
                                    TechTreeBrigadierCommand techTreeCommand,
                                    ChatBrigadierCommand chatCommand,
                                    AllianceBrigadierCommand allianceCommand,
                                    SpecializationBrigadierCommand specializationCommand,
                                    TerritoryBrigadierCommand territoryCommand,
                                    QuestBrigadierCommand questCommand) {
        this.plugin = plugin;
        this.guildCommand = guildCommand;
        this.plotCommand = plotCommand;
        this.guildsGeneralCommand = guildsGeneralCommand;
        this.guildLevelCommand = guildLevelCommand;
        this.mapCommand = mapCommand;
        this.permCommand = permCommand;
        this.plotTypeCommand = plotTypeCommand;
        this.guildBroadcastCommand = guildBroadcastCommand;
        this.guildPermCommand = guildPermCommand;
        this.techTreeCommand = techTreeCommand;
        this.chatCommand = chatCommand;
        this.allianceCommand = allianceCommand;
        this.specializationCommand = specializationCommand;
        this.territoryCommand = territoryCommand;
        this.questCommand = questCommand;
    }

    /** Performs the register commands operation. */
    public void registerCommands() {
        LifecycleEventManager<? extends org.bukkit.plugin.Plugin> manager = plugin.getLifecycleManager();

        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            // Register all commands with Brigadier
            Commands commands = event.registrar();

            // Register main guild command with alias
            commands.register(guildCommand.buildCommand());
            commands.register(Commands.literal("t")
                .redirect(guildCommand.buildCommand())
                .build());

            commands.register(territoryCommand.buildCommand());
            commands.register(Commands.literal("guildsterritory")
                    .redirect(territoryCommand.buildCommand())
                    .build());
            commands.register(Commands.literal("at")
                    .redirect(territoryCommand.buildCommand())
                    .build());

            // Register plot command
            commands.register(plotCommand.buildCommand());

            // Register guilds general command
            commands.register(guildsGeneralCommand.buildCommand());

            // Register guild level command
            commands.register(guildLevelCommand.buildCommand());

            // Register map command with alias
            commands.register(mapCommand.buildCommand());
            commands.register(Commands.literal("map")
                .redirect(mapCommand.buildCommand())
                .build());

            // Register perm command
            commands.register(permCommand.buildCommand());

            // Register plot type command with alias
            commands.register(plotTypeCommand.buildCommand());
            commands.register(Commands.literal("ptype")
                .redirect(plotTypeCommand.buildCommand())
                .build());

            // Register broadcast command with aliases
            commands.register(guildBroadcastCommand.buildCommand());
            commands.register(Commands.literal("guildbroadcast")
                .redirect(guildBroadcastCommand.buildCommand())
                .build());
            commands.register(Commands.literal("tb")
                .redirect(guildBroadcastCommand.buildCommand())
                .build());

            // Register new guild perm command
            commands.register(guildPermCommand.buildCommand());

            // Register tech tree command with alias
            commands.register(techTreeCommand.buildCommand());
        commands.register(Commands.literal("tt")
                .redirect(techTreeCommand.buildCommand())
                .build());

        // Register chat command with aliases
        commands.register(chatCommand.buildCommand());
        commands.register(Commands.literal("guildchat")
            .redirect(chatCommand.buildCommand())
            .build());

        // Register alliance command with alias
        commands.register(allianceCommand.buildCommand());
        commands.register(Commands.literal("n")
                .redirect(allianceCommand.buildCommand())
                .build());

        // Register specialization command with alias
        commands.register(Commands.literal("guild")
            .then(specializationCommand.buildCommand())
            .build());

        // Register quest command with alias
        commands.register(questCommand.buildCommand());
        commands.register(Commands.literal("tq")
            .redirect(questCommand.buildCommand())
            .build());

            plugin.getLogger().info("All Brigadier commands registered successfully!");
        });
    }
}