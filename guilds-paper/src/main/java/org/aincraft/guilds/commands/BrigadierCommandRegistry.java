package org.aincraft.guilds.commands;



import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.commands.brigadier.ChatBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.AllianceBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.PermBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.PlotBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.PlotTypeBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.QuestBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.SpecializationBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.TechTreeBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.GuildBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.GuildBroadcastBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.GuildLevelBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.GuildPermBrigadierCommand;
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
    private final GuildBrigadierCommand guildCommand;
    private final PlotBrigadierCommand plotCommand;
    private final GuildsGeneralBrigadierCommand guildsGeneralCommand;
    private final GuildLevelBrigadierCommand guildLevelCommand;
    private final PermBrigadierCommand permCommand;
    private final PlotTypeBrigadierCommand plotTypeCommand;
    private final GuildBroadcastBrigadierCommand guildBroadcastCommand;
    private final GuildPermBrigadierCommand guildPermCommand;
    private final TechTreeBrigadierCommand techTreeCommand;
    private final ChatBrigadierCommand chatCommand;
    private final AllianceBrigadierCommand allianceCommand;
    private final SpecializationBrigadierCommand specializationCommand;
    private final QuestBrigadierCommand questCommand;

    public BrigadierCommandRegistry(JavaPlugin plugin,
                                    GuildBrigadierCommand guildCommand,
                                    PlotBrigadierCommand plotCommand,
                                    GuildsGeneralBrigadierCommand guildsGeneralCommand,
                                    GuildLevelBrigadierCommand guildLevelCommand,
                                    PermBrigadierCommand permCommand,
                                    PlotTypeBrigadierCommand plotTypeCommand,
                                    GuildBroadcastBrigadierCommand guildBroadcastCommand,
                                    GuildPermBrigadierCommand guildPermCommand,
                                    TechTreeBrigadierCommand techTreeCommand,
                                    ChatBrigadierCommand chatCommand,
                                    AllianceBrigadierCommand allianceCommand,
                                    SpecializationBrigadierCommand specializationCommand,
                                    QuestBrigadierCommand questCommand) {
        this.plugin = plugin;
        this.guildCommand = guildCommand;
        this.plotCommand = plotCommand;
        this.guildsGeneralCommand = guildsGeneralCommand;
        this.guildLevelCommand = guildLevelCommand;
        this.permCommand = permCommand;
        this.plotTypeCommand = plotTypeCommand;
        this.guildBroadcastCommand = guildBroadcastCommand;
        this.guildPermCommand = guildPermCommand;
        this.techTreeCommand = techTreeCommand;
        this.chatCommand = chatCommand;
        this.allianceCommand = allianceCommand;
        this.specializationCommand = specializationCommand;
        this.questCommand = questCommand;
    }

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
            commands.register(Commands.literal("town")
                .redirect(guildCommand.buildCommand())
                .build());
            commands.register(Commands.literal("g")
                .redirect(guildCommand.buildCommand())
                .build());

            // Register plot command
            commands.register(plotCommand.buildCommand());

            // Register guilds general command
            commands.register(guildsGeneralCommand.buildCommand());

            // Register guild level command
            commands.register(guildLevelCommand.buildCommand());

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

            // techtree top-level removed per request — use /g upgrade (redirects to tech tree)
            // (was: commands.register(techTreeCommand.buildCommand()); + "tt" alias)

        // Register chat command with aliases
        commands.register(chatCommand.buildCommand());
        commands.register(Commands.literal("guildchat")
            .redirect(chatCommand.buildCommand())
            .build());

        // Register alliance command with aliases
        commands.register(allianceCommand.buildCommand());
        commands.register(Commands.literal("n")
                .redirect(allianceCommand.buildCommand())
                .build());
        commands.register(Commands.literal("a")
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