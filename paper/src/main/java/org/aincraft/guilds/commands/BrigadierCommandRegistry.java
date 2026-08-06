package org.aincraft.guilds.commands;



import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.commands.brigadier.BlueprintBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.ChatBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.MapBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.NationBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.PermBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.PlotBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.PlotTypeBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.QuestBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.SpecializationBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.TechTreeBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.TownBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.TownBroadcastBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.TownLevelBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.TownPermBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.GuildsGeneralBrigadierCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Central registry for all Brigadier commands in Guilds
 */
public class BrigadierCommandRegistry {

    private final JavaPlugin plugin;

    // Brigadier command handlers
    private final TownBrigadierCommand townCommand;
    private final PlotBrigadierCommand plotCommand;
    private final GuildsGeneralBrigadierCommand guildsGeneralCommand;
    private final TownLevelBrigadierCommand townLevelCommand;
    private final MapBrigadierCommand mapCommand;
    private final PermBrigadierCommand permCommand;
    private final PlotTypeBrigadierCommand plotTypeCommand;
    private final TownBroadcastBrigadierCommand townBroadcastCommand;
    private final TownPermBrigadierCommand townPermCommand;
    private final TechTreeBrigadierCommand techTreeCommand;
    private final ChatBrigadierCommand chatCommand;
    private final NationBrigadierCommand nationCommand;
    private final SpecializationBrigadierCommand specializationCommand;
    private final QuestBrigadierCommand questCommand;
    private final BlueprintBrigadierCommand blueprintCommand;

    public BrigadierCommandRegistry(JavaPlugin plugin,
                                    TownBrigadierCommand townCommand,
                                    PlotBrigadierCommand plotCommand,
                                    GuildsGeneralBrigadierCommand guildsGeneralCommand,
                                    TownLevelBrigadierCommand townLevelCommand,
                                    MapBrigadierCommand mapCommand,
                                    PermBrigadierCommand permCommand,
                                    PlotTypeBrigadierCommand plotTypeCommand,
                                    TownBroadcastBrigadierCommand townBroadcastCommand,
                                    TownPermBrigadierCommand townPermCommand,
                                    TechTreeBrigadierCommand techTreeCommand,
                                    ChatBrigadierCommand chatCommand,
                                    NationBrigadierCommand nationCommand,
                                    SpecializationBrigadierCommand specializationCommand,
                                    QuestBrigadierCommand questCommand,
                                    BlueprintBrigadierCommand blueprintCommand) {
        this.plugin = plugin;
        this.townCommand = townCommand;
        this.plotCommand = plotCommand;
        this.guildsGeneralCommand = guildsGeneralCommand;
        this.townLevelCommand = townLevelCommand;
        this.mapCommand = mapCommand;
        this.permCommand = permCommand;
        this.plotTypeCommand = plotTypeCommand;
        this.townBroadcastCommand = townBroadcastCommand;
        this.townPermCommand = townPermCommand;
        this.techTreeCommand = techTreeCommand;
        this.chatCommand = chatCommand;
        this.nationCommand = nationCommand;
        this.specializationCommand = specializationCommand;
        this.questCommand = questCommand;
        this.blueprintCommand = blueprintCommand;
    }

    public void registerCommands() {
        LifecycleEventManager<? extends org.bukkit.plugin.Plugin> manager = plugin.getLifecycleManager();

        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            // Register all commands with Brigadier
            Commands commands = event.registrar();

            // Register main town command with alias
            commands.register(townCommand.buildCommand());
            commands.register(Commands.literal("t")
                .redirect(townCommand.buildCommand())
                .build());

            // Register plot command
            commands.register(plotCommand.buildCommand());

            // Register guilds general command
            commands.register(guildsGeneralCommand.buildCommand());

            // Register town level command
            commands.register(townLevelCommand.buildCommand());

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
            commands.register(townBroadcastCommand.buildCommand());
            commands.register(Commands.literal("townbroadcast")
                .redirect(townBroadcastCommand.buildCommand())
                .build());
            commands.register(Commands.literal("tb")
                .redirect(townBroadcastCommand.buildCommand())
                .build());

            // Register new town perm command
            commands.register(townPermCommand.buildCommand());

            // Register blueprint
            commands.register(blueprintCommand.buildCommand());

            // Register tech tree command with alias
            commands.register(techTreeCommand.buildCommand());
        commands.register(Commands.literal("tt")
                .redirect(techTreeCommand.buildCommand())
                .build());

        // Register chat command with aliases
        commands.register(chatCommand.buildCommand());
        commands.register(Commands.literal("townchat")
            .redirect(chatCommand.buildCommand())
            .build());

        // Register nation command with alias
        commands.register(nationCommand.buildCommand());
        commands.register(Commands.literal("n")
                .redirect(nationCommand.buildCommand())
                .build());

        // Register specialization command with alias
        commands.register(Commands.literal("town")
            .then(specializationCommand.buildCommand())
            .build());

        // Register quest command with alias
        commands.register(questCommand.buildCommand());
            commands.register(Commands.literal("bp")
                .redirect(blueprintCommand.buildCommand())
                .build());
        commands.register(Commands.literal("tq")
            .redirect(questCommand.buildCommand())
            .build());

            plugin.getLogger().info("All Brigadier commands registered successfully!");
        });
    }
}