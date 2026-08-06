package org.aincraft.towny.commands;

import com.google.inject.Inject;
import com.google.inject.Injector;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.commands.brigadier.BlueprintBrigadierCommand;
import org.aincraft.towny.commands.brigadier.ChatBrigadierCommand;
import org.aincraft.towny.commands.brigadier.MapBrigadierCommand;
import org.aincraft.towny.commands.brigadier.NationBrigadierCommand;
import org.aincraft.towny.commands.brigadier.PermBrigadierCommand;
import org.aincraft.towny.commands.brigadier.PlotBrigadierCommand;
import org.aincraft.towny.commands.brigadier.PlotTypeBrigadierCommand;
import org.aincraft.towny.commands.brigadier.QuestBrigadierCommand;
import org.aincraft.towny.commands.brigadier.SpecializationBrigadierCommand;
import org.aincraft.towny.commands.brigadier.TechTreeBrigadierCommand;
import org.aincraft.towny.commands.brigadier.TownBrigadierCommand;
import org.aincraft.towny.commands.brigadier.TownBroadcastBrigadierCommand;
import org.aincraft.towny.commands.brigadier.TownLevelBrigadierCommand;
import org.aincraft.towny.commands.brigadier.TownPermBrigadierCommand;
import org.aincraft.towny.commands.brigadier.TownyGeneralBrigadierCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Central registry for all Brigadier commands in Towny
 */
public class BrigadierCommandRegistry {

    private final TownyPlugin plugin;
    private final Injector injector;

    // Brigadier command handlers
    private TownBrigadierCommand townCommand;
    private PlotBrigadierCommand plotCommand;
    private TownyGeneralBrigadierCommand townyGeneralCommand;
    private TownLevelBrigadierCommand townLevelCommand;
    private MapBrigadierCommand mapCommand;
    private PermBrigadierCommand permCommand;
    private PlotTypeBrigadierCommand plotTypeCommand;
    private TownBroadcastBrigadierCommand townBroadcastCommand;
    private TownPermBrigadierCommand townPermCommand;
    private TechTreeBrigadierCommand techTreeCommand;
    private ChatBrigadierCommand chatCommand;
    private NationBrigadierCommand nationCommand;
    private SpecializationBrigadierCommand specializationCommand;
    private QuestBrigadierCommand questCommand;
    private BlueprintBrigadierCommand blueprintCommand;

    @Inject
    public BrigadierCommandRegistry(TownyPlugin plugin, Injector injector) {
        this.plugin = plugin;
        this.injector = injector;
        initializeCommands();
    }

    private void initializeCommands() {
        // Initialize all command handlers
        this.townCommand = injector.getInstance(TownBrigadierCommand.class);
        this.plotCommand = injector.getInstance(PlotBrigadierCommand.class);
        this.townyGeneralCommand = injector.getInstance(TownyGeneralBrigadierCommand.class);
        this.townLevelCommand = injector.getInstance(TownLevelBrigadierCommand.class);
        this.mapCommand = injector.getInstance(MapBrigadierCommand.class);
        this.permCommand = injector.getInstance(PermBrigadierCommand.class);
        this.plotTypeCommand = injector.getInstance(PlotTypeBrigadierCommand.class);
        this.townBroadcastCommand = injector.getInstance(TownBroadcastBrigadierCommand.class);
        this.townPermCommand = injector.getInstance(TownPermBrigadierCommand.class);
        this.techTreeCommand = injector.getInstance(TechTreeBrigadierCommand.class);
        this.chatCommand = injector.getInstance(ChatBrigadierCommand.class);
        this.nationCommand = injector.getInstance(NationBrigadierCommand.class);
        this.specializationCommand = injector.getInstance(SpecializationBrigadierCommand.class);
        this.questCommand = injector.getInstance(QuestBrigadierCommand.class);
        this.blueprintCommand = injector.getInstance(BlueprintBrigadierCommand.class);
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

            // Register towny general command
            commands.register(townyGeneralCommand.buildCommand());

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